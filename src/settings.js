'use strict';
const $ = (id) => document.getElementById(id);
const FIELDS_TEXT = ['token', 'pageId', 'sectionHeading', 'accent', 'fontFamily'];
const FIELDS_NUM = ['limit', 'refreshSeconds', 'opacity', 'fontSize'];
const FIELDS_BOOL = ['includeHeading', 'showTitle', 'showCheckbox', 'alwaysOnTop', 'locked', 'clickThrough'];

function fillRangeLabels() {
  $('opacityVal').textContent = Number($('opacity').value).toFixed(2);
  $('fontSizeVal').textContent = $('fontSize').value + 'px';
}

async function load() {
  const cfg = await window.api.getConfig();
  for (const k of FIELDS_TEXT) if ($(k)) $(k).value = cfg[k] ?? '';
  for (const k of FIELDS_NUM) if ($(k)) $(k).value = cfg[k] ?? '';
  for (const k of FIELDS_BOOL) if ($(k)) $(k).checked = !!cfg[k];
  $('theme').value = cfg.theme || 'dark';
  fillRangeLabels();
  try { $('autoLaunch').checked = await window.api.getAutoLaunch(); } catch (_) {}
}

async function save() {
  const patch = {};
  for (const k of FIELDS_TEXT) if ($(k)) patch[k] = $(k).value.trim();
  for (const k of FIELDS_NUM) if ($(k)) patch[k] = Number($(k).value);
  for (const k of FIELDS_BOOL) if ($(k)) patch[k] = $(k).checked;
  patch.theme = $('theme').value;
  await window.api.setConfig(patch);
  try { await window.api.setAutoLaunch($('autoLaunch').checked); } catch (_) {}
  const btn = $('saveBtn');
  const old = btn.textContent;
  btn.textContent = '저장됨 ✓';
  setTimeout(() => { btn.textContent = old; }, 1200);
}

async function test() {
  const box = $('testResult');
  box.style.display = 'block';
  box.textContent = '확인 중...';
  box.className = '';
  const res = await window.api.testConnection({
    token: $('token').value.trim(),
    pageId: $('pageId').value.trim(),
    sectionHeading: $('sectionHeading').value.trim(),
  });
  if (!res.ok) {
    box.className = 'err';
    box.textContent = '실패: ' + res.error +
      '\n\n확인하세요:\n- 토큰이 맞는지\n- 페이지 ⋯ → 연결에서 통합을 추가했는지\n- 페이지 URL이 맞는지';
    return;
  }
  if (res.notFound) {
    box.className = 'err';
    box.textContent = '연결은 됐지만 지정한 섹션 제목을 찾지 못했습니다. 제목을 정확히 입력했는지 확인하세요.';
    return;
  }
  box.className = 'ok';
  box.textContent = `연결 성공! 미리보기(${res.count}개):\n` +
    res.preview.map((i) => ` ${i.type}: ${i.text}`).join('\n');
}

$('saveBtn').addEventListener('click', save);
$('testBtn').addEventListener('click', test);
$('closeBtn').addEventListener('click', () => window.close());
$('opacity').addEventListener('input', fillRangeLabels);
$('fontSize').addEventListener('input', fillRangeLabels);
$('openNotion').addEventListener('click', () => window.api.openExternal('https://www.notion.so/my-integrations'));

load();
