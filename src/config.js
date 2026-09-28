'use strict';
const fs = require('fs');
const path = require('path');
const os = require('os');

const DEFAULTS = {
  token: '',
  pageId: '',
  sectionHeading: '',
  includeHeading: true,
  limit: 0,
  refreshSeconds: 15,
  // 외형
  theme: 'dark',            // dark | light | glass
  opacity: 0.92,            // 0.3 ~ 1
  fontSize: 14,
  fontFamily: 'system',
  accent: '#4f8cff',
  showTitle: true,
  showCheckbox: true,
  alwaysOnTop: true,
  clickThrough: false,
  locked: false,            // 창 이동/크기조절 잠금
  bounds: { x: null, y: null, width: 340, height: 460 },
};

function configPath() {
  const dir = path.join(os.homedir(), '.notion-widget');
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  return path.join(dir, 'config.json');
}

function load() {
  try {
    const raw = JSON.parse(fs.readFileSync(configPath(), 'utf8'));
    return { ...DEFAULTS, ...raw, bounds: { ...DEFAULTS.bounds, ...(raw.bounds || {}) } };
  } catch (_) {
    return { ...DEFAULTS };
  }
}

function save(cfg) {
  const merged = { ...load(), ...cfg };
  fs.writeFileSync(configPath(), JSON.stringify(merged, null, 2), { mode: 0o600 });
  return merged;
}

module.exports = { load, save, configPath, DEFAULTS };
