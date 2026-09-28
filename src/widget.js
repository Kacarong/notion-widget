'use strict';
let timer = null;
let cfg = null;

const $ = (id) => document.getElementById(id);

function applyStyle(c) {
  document.body.className = c.theme === 'light' ? 'light' : c.theme === 'glass' ? 'glass' : '';
  const root = document.documentElement.style;
  root.setProperty('--fs', (c.fontSize || 14) + 'px');
  root.setProperty('--accent', c.accent || '#4f8cff');
  document.getElementById('card').style.opacity = String(c.opacity ?? 0.92);
  if (c.fontFamily && c.fontFamily !== 'system') {
    document.body.style.fontFamily = c.fontFamily;
  } else {
    document.body.style.fontFamily = '';
  }
}

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, (m) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
}

function render(items) {
  const el = $('content');
  if (!items || !items.length) {
    el.innerHTML = '<div class="item" style="color:var(--muted)">표시할 내용이 없습니다.</div>';
    return;
  }
  const html = items.map((it) => {
    const ind = it.indent ? ' indent' : '';
    switch (it.type) {
      case 'h1': return `<div class="item h1${ind}">${esc(it.text)}</div>`;
      case 'h2': return `<div class="item h2${ind}">${esc(it.text)}</div>`;
      case 'h3': return `<div class="item h3${ind}">${esc(it.text)}</div>`;
      case 'bullet': return `<div class="item bullet${ind}">${esc(it.text)}</div>`;
      case 'number': return `<div class="item number${ind}">${esc(it.text)}</div>`;
      case 'quote': return `<div class="item quote${ind}">${esc(it.text)}</div>`;
      case 'callout': return `<div class="item callout${ind}">${it.icon ? esc(it.icon) + ' ' : ''}${esc(it.text)}</div>`;
      case 'code': return `<div class="item code${ind}">${esc(it.text)}</div>`;
      case 'divider': return `<div class="divider"></div>`;
      case 'toggle': return `<div class="item toggle${ind}">${esc(it.text)}</div>`;
      case 'todo': {
        const box = cfg && cfg.showCheckbox === false ? '' :
          `<span class="box">${it.checked ? '☑' : '☐'}</span>`;
        return `<div class="item todo${it.checked ? ' done' : ''}${ind}">${box}<span class="txt">${esc(it.text)}</span></div>`;
      }
      default: return `<div class="item${ind}">${esc(it.text)}</div>`;
    }
  }).join('');
  el.innerHTML = html;
}

async function refresh() {
  const res = await window.api.fetchNotes();
  const status = $('status');
  if (!res.ok) {
    if (res.error === 'not-configured') {
      $('content').innerHTML =
        '<div class="item" style="color:var(--muted)">설정에서 Notion 토큰과 페이지를 먼저 입력하세요.<br>(우측 상단 ⚙ 버튼)</div>';
      status.textContent = '미설정';
      status.className = '';
    } else {
      status.textContent = '오류: ' + res.error;
      status.className = 'err';
    }
    return;
  }
  if (cfg && cfg.showTitle && res.title) $('title').textContent = res.title;
  render(res.items);
  status.textContent = res.notFound ? '섹션 없음' : '연결됨';
  status.className = res.notFound ? 'err' : '';
  $('time').textContent = new Date(res.at).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function schedule() {
  if (timer) clearInterval(timer);
  const sec = Math.max(3, Number(cfg && cfg.refreshSeconds) || 15);
  timer = setInterval(refresh, sec * 1000);
}

async function init() {
  cfg = await window.api.getConfig();
  applyStyle(cfg);
  if (cfg.showTitle === false) $('title').textContent = '';
  await refresh();
  schedule();

  $('refresh').addEventListener('click', refresh);
  $('settings').addEventListener('click', () => window.api.openSettings());

  window.api.onConfigChanged(async (next) => {
    cfg = next;
    applyStyle(cfg);
    await refresh();
    schedule();
  });
  window.api.onRefreshNow(refresh);
}

init();
