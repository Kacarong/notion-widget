'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  getConfig: () => ipcRenderer.invoke('config:get'),
  setConfig: (patch) => ipcRenderer.invoke('config:set', patch),
  fetchNotes: () => ipcRenderer.invoke('notion:fetch'),
  testConnection: (args) => ipcRenderer.invoke('notion:test', args),
  openSettings: () => ipcRenderer.invoke('app:openSettings'),
  quit: () => ipcRenderer.invoke('app:quit'),
  openExternal: (url) => ipcRenderer.invoke('app:openExternal', url),
  setAutoLaunch: (v) => ipcRenderer.invoke('app:setAutoLaunch', v),
  getAutoLaunch: () => ipcRenderer.invoke('app:getAutoLaunch'),
  onConfigChanged: (cb) => ipcRenderer.on('config-changed', (_e, cfg) => cb(cfg)),
  onRefreshNow: (cb) => ipcRenderer.on('refresh-now', () => cb()),
});
