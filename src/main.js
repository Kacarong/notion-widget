'use strict';
const { app, BrowserWindow, ipcMain, Tray, Menu, shell, dialog } = require('electron');
const path = require('path');
const cfgStore = require('./config');
const notion = require('./notion');

// ── 자동 업데이트 (GitHub Releases) ─────────────────────
let autoUpdater = null;
try { autoUpdater = require('electron-updater').autoUpdater; } catch (_) { /* 개발 환경 */ }

function setupAutoUpdate() {
  if (!autoUpdater || !app.isPackaged) return;
  autoUpdater.autoDownload = true;          // 새 버전 나오면 자동 다운로드
  autoUpdater.autoInstallOnAppQuit = true;  // 종료 시 자동 설치

  autoUpdater.on('update-downloaded', async (info) => {
    const res = await dialog.showMessageBox({
      type: 'info',
      buttons: ['지금 재시작', '나중에'],
      defaultId: 0,
      title: '업데이트 준비 완료',
      message: `새 버전 ${info.version} 을(를) 받았습니다.`,
      detail: '지금 재시작하면 바로 적용됩니다. 나중을 고르면 다음 종료 시 자동 적용됩니다.',
    });
    if (res.response === 0) {
      setImmediate(() => autoUpdater.quitAndInstall());
    }
  });
  autoUpdater.on('error', (err) => console.error('[update]', err && err.message));

  autoUpdater.checkForUpdates().catch(() => {});
  // 6시간마다 재확인
  setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 6 * 60 * 60 * 1000);
}

async function checkUpdateManually() {
  if (!autoUpdater || !app.isPackaged) {
    dialog.showMessageBox({ type: 'info', message: '개발 모드에서는 업데이트 확인을 건너뜁니다.' });
    return;
  }
  try {
    const r = await autoUpdater.checkForUpdates();
    const latest = r && r.updateInfo && r.updateInfo.version;
    if (latest && latest !== app.getVersion()) {
      dialog.showMessageBox({ type: 'info', message: `새 버전 ${latest} 을(를) 내려받는 중입니다.` });
    } else {
      dialog.showMessageBox({ type: 'info', message: `최신 버전입니다. (v${app.getVersion()})` });
    }
  } catch (e) {
    dialog.showMessageBox({ type: 'error', message: '업데이트 확인 실패', detail: String(e && e.message) });
  }
}

let win = null;
let settingsWin = null;
let tray = null;

function createWindow() {
  const cfg = cfgStore.load();
  const b = cfg.bounds || {};
  win = new BrowserWindow({
    width: b.width || 340,
    height: b.height || 460,
    x: b.x ?? undefined,
    y: b.y ?? undefined,
    frame: false,
    transparent: true,
    resizable: !cfg.locked,
    movable: !cfg.locked,
    skipTaskbar: true,
    alwaysOnTop: !!cfg.alwaysOnTop,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  if (cfg.alwaysOnTop) win.setAlwaysOnTop(true, 'screen-saver');
  win.setIgnoreMouseEvents(!!cfg.clickThrough, { forward: true });
  win.loadFile(path.join(__dirname, 'widget.html'));

  const persistBounds = () => {
    if (!win || win.isDestroyed()) return;
    const nb = win.getBounds();
    cfgStore.save({ bounds: nb });
  };
  win.on('moved', persistBounds);
  win.on('resized', persistBounds);
  win.on('closed', () => { win = null; });
}

function openSettings() {
  if (settingsWin && !settingsWin.isDestroyed()) { settingsWin.focus(); return; }
  settingsWin = new BrowserWindow({
    width: 460, height: 640, title: 'Notion 위젯 설정',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true, nodeIntegration: false,
    },
  });
  settingsWin.setMenuBarVisibility(false);
  settingsWin.loadFile(path.join(__dirname, 'settings.html'));
  settingsWin.on('closed', () => { settingsWin = null; });
}

function buildTray() {
  try {
    tray = new Tray(path.join(__dirname, 'icon.png'));
  } catch (_) {
    return; // 아이콘 없으면 트레이 생략
  }
  const cfg = cfgStore.load();
  const menu = Menu.buildFromTemplate([
    { label: '설정 열기', click: openSettings },
    { label: '지금 새로고침', click: () => win && win.webContents.send('refresh-now') },
    { label: '업데이트 확인', click: checkUpdateManually },
    { type: 'separator' },
    {
      label: '항상 위에 표시', type: 'checkbox', checked: !!cfg.alwaysOnTop,
      click: (item) => { cfgStore.save({ alwaysOnTop: item.checked }); applyRuntime(); },
    },
    {
      label: '창 잠금(이동/크기 고정)', type: 'checkbox', checked: !!cfg.locked,
      click: (item) => { cfgStore.save({ locked: item.checked }); applyRuntime(); },
    },
    {
      label: '클릭 통과', type: 'checkbox', checked: !!cfg.clickThrough,
      click: (item) => { cfgStore.save({ clickThrough: item.checked }); applyRuntime(); },
    },
    { type: 'separator' },
    { label: '종료', click: () => app.quit() },
  ]);
  tray.setToolTip('Notion 메모 위젯');
  tray.setContextMenu(menu);
  tray.on('double-click', openSettings);
}

function applyRuntime() {
  const cfg = cfgStore.load();
  if (win && !win.isDestroyed()) {
    win.setAlwaysOnTop(!!cfg.alwaysOnTop, 'screen-saver');
    win.setResizable(!cfg.locked);
    win.setMovable(!cfg.locked);
    win.setIgnoreMouseEvents(!!cfg.clickThrough, { forward: true });
    win.webContents.send('config-changed', cfg);
  }
}

// ── IPC ────────────────────────────────────────────────
ipcMain.handle('config:get', () => cfgStore.load());
ipcMain.handle('config:set', (_e, patch) => {
  const saved = cfgStore.save(patch || {});
  applyRuntime();
  if (settingsWin && !settingsWin.isDestroyed()) settingsWin.webContents.send('config-changed', saved);
  return saved;
});
ipcMain.handle('notion:fetch', async () => {
  const cfg = cfgStore.load();
  if (!cfg.token || !cfg.pageId) return { ok: false, error: 'not-configured' };
  try {
    const [{ items, notFound }, title] = await Promise.all([
      notion.getNoteItems(cfg.token, cfg.pageId, {
        sectionHeading: cfg.sectionHeading,
        includeHeading: cfg.includeHeading,
        limit: Number(cfg.limit) || 0,
      }),
      cfg.showTitle ? notion.getPageTitle(cfg.token, cfg.pageId).catch(() => '') : Promise.resolve(''),
    ]);
    return { ok: true, items, title, notFound, at: Date.now() };
  } catch (e) {
    return { ok: false, error: e.message || String(e) };
  }
});
ipcMain.handle('notion:test', async (_e, { token, pageId, sectionHeading }) => {
  try {
    const { items, notFound } = await notion.getNoteItems(token, pageId, { sectionHeading, limit: 5 });
    return { ok: true, count: items.length, notFound, preview: items.slice(0, 5) };
  } catch (e) {
    return { ok: false, error: e.message || String(e) };
  }
});
ipcMain.handle('app:openSettings', () => openSettings());
ipcMain.handle('app:quit', () => app.quit());
ipcMain.handle('app:openExternal', (_e, url) => shell.openExternal(url));
ipcMain.handle('app:setAutoLaunch', (_e, enable) => {
  app.setLoginItemSettings({ openAtLogin: !!enable, args: [] });
  return app.getLoginItemSettings().openAtLogin;
});
ipcMain.handle('app:getAutoLaunch', () => app.getLoginItemSettings().openAtLogin);

app.whenReady().then(() => {
  createWindow();
  buildTray();
  setupAutoUpdate();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', (e) => { e.preventDefault(); }); // 트레이 상주
