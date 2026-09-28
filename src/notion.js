'use strict';
/**
 * Notion 페이지의 블록을 읽어 위젯에 표시할 간단한 구조로 변환한다.
 * - 특정 소제목(heading) 아래 구간만 잘라내기 지원
 * - 읽기 전용 (작성 기능 없음)
 */

const NOTION_VERSION = '2022-06-28';

async function notionFetch(token, path, { method = 'GET', body } = {}) {
  const res = await fetch(`https://api.notion.com/v1${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Notion-Version': NOTION_VERSION,
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const err = new Error(`Notion API ${res.status}: ${text.slice(0, 300)}`);
    err.status = res.status;
    throw err;
  }
  return res.json();
}

/** 노션 URL 또는 ID 문자열에서 32자리 ID를 뽑아 하이픈 형식으로 변환 */
function normalizeId(input) {
  if (!input) return '';
  const s = String(input).trim();
  // 이미 하이픈 UUID 형태면 그대로 사용
  const uuid = s.match(/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/);
  if (uuid) return uuid[0].toLowerCase();
  // URL/문자열에서 하이픈 없는 32자리 ID 추출 (경계를 지켜 앞뒤 문자를 끌어오지 않도록)
  const m = s.match(/(?:^|[^0-9a-fA-F])([0-9a-fA-F]{32})(?![0-9a-fA-F])/);
  if (!m) return s;
  const id = m[1].toLowerCase();
  return `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
}

function richTextToPlain(rt) {
  if (!Array.isArray(rt)) return '';
  return rt.map((t) => (t.plain_text !== undefined ? t.plain_text : (t.text && t.text.content) || '')).join('');
}

/** 블록 목록을 페이지네이션 포함해 모두 가져온다 */
async function listChildren(token, blockId) {
  const out = [];
  let cursor;
  do {
    const qs = new URLSearchParams({ page_size: '100' });
    if (cursor) qs.set('start_cursor', cursor);
    const data = await notionFetch(token, `/blocks/${blockId}/children?${qs.toString()}`);
    out.push(...(data.results || []));
    cursor = data.has_more ? data.next_cursor : undefined;
  } while (cursor);
  return out;
}

const HEADINGS = new Set(['heading_1', 'heading_2', 'heading_3']);

/** 노션 블록 → 위젯용 단순 아이템 */
function blockToItem(b) {
  const t = b.type;
  const data = b[t] || {};
  const text = richTextToPlain(data.rich_text);
  switch (t) {
    case 'heading_1': return { type: 'h1', text };
    case 'heading_2': return { type: 'h2', text };
    case 'heading_3': return { type: 'h3', text };
    case 'paragraph': return { type: 'p', text };
    case 'bulleted_list_item': return { type: 'bullet', text };
    case 'numbered_list_item': return { type: 'number', text };
    case 'to_do': return { type: 'todo', text, checked: !!data.checked };
    case 'quote': return { type: 'quote', text };
    case 'callout': return { type: 'callout', text, icon: data.icon && data.icon.emoji };
    case 'toggle': return { type: 'toggle', text, id: b.id };
    case 'code': return { type: 'code', text, lang: data.language };
    case 'divider': return { type: 'divider', text: '' };
    case 'child_page': return { type: 'p', text: '📄 ' + (data.title || '') };
    default:
      return text ? { type: 'p', text } : null;
  }
}

/**
 * 페이지(또는 블록)에서 표시할 아이템 목록을 가져온다.
 * opts:
 *  - sectionHeading: 이 제목과 일치하는 heading 아래부터 다음 동급 heading 전까지만 (없으면 전체)
 *  - includeHeading: 구간 제목 자체도 표시할지 (기본 true)
 *  - limit: 최대 아이템 수 (0이면 무제한)
 *  - expandToggles: 토글 내부 내용까지 펼쳐서 표시
 */
async function getNoteItems(token, pageIdOrUrl, opts = {}) {
  const { sectionHeading = '', includeHeading = true, limit = 0, expandToggles = true } = opts;
  const pageId = normalizeId(pageIdOrUrl);
  let blocks = await listChildren(token, pageId);

  // 쉼표로 여러 섹션 지정 가능: "오늘 할 일, 메모" → 지정한 순서대로 이어서 표시
  let notFoundAny = false;
  if (sectionHeading && sectionHeading.trim()) {
    const names = sectionHeading.split(',').map((s) => s.trim()).filter(Boolean);
    const picked = [];
    for (const name of names) {
      const target = name.toLowerCase();
      const startIdx = blocks.findIndex(
        (b) => HEADINGS.has(b.type) && richTextToPlain(b[b.type].rich_text).trim().toLowerCase() === target
      );
      if (startIdx < 0) {
        notFoundAny = true;
        picked.push({ __missing: name });
        continue;
      }
      const level = Number(blocks[startIdx].type.slice(-1));
      let endIdx = blocks.length;
      for (let i = startIdx + 1; i < blocks.length; i++) {
        const b = blocks[i];
        if (HEADINGS.has(b.type) && Number(b.type.slice(-1)) <= level) { endIdx = i; break; }
      }
      const slice = blocks.slice(includeHeading ? startIdx : startIdx + 1, endIdx);
      if (picked.length) picked.push({ __divider: true }); // 섹션 사이 구분선
      picked.push(...slice);
    }
    blocks = picked;
  }

  const items = [];
  for (const b of blocks) {
    if (b.__divider) { items.push({ type: 'divider', text: '' }); continue; }
    if (b.__missing) { items.push({ type: 'p', text: `("${b.__missing}" 섹션을 찾지 못했습니다)` }); continue; }
    const item = blockToItem(b);
    if (item) items.push(item);
    if (expandToggles && b.type === 'toggle' && b.has_children) {
      try {
        const kids = await listChildren(token, b.id);
        for (const k of kids) {
          const ki = blockToItem(k);
          if (ki) items.push({ ...ki, indent: 1 });
        }
      } catch (_) { /* 무시 */ }
    }
    if (limit && items.length >= limit) break;
  }
  return { items: limit ? items.slice(0, limit) : items, notFound: notFoundAny };
}

/** 페이지 제목 조회 */
async function getPageTitle(token, pageIdOrUrl) {
  const pageId = normalizeId(pageIdOrUrl);
  const page = await notionFetch(token, `/pages/${pageId}`);
  const props = page.properties || {};
  for (const key of Object.keys(props)) {
    const p = props[key];
    if (p.type === 'title') return richTextToPlain(p.title);
  }
  return '';
}

module.exports = { getNoteItems, getPageTitle, normalizeId, richTextToPlain, notionFetch };
