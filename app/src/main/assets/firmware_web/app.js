(function () {
  'use strict';

  // ---- helpers ------------------------------------------------------------------------------------------------
  const $app = document.getElementById('app');
  const LANGS = Object.keys(window.I18N);
  const FAILURES = {
    INCOMPATIBLE_FILE: 'FirmwareFailIncompat', NOT_SERIAL: 'FirmwareFailNotSerial', NO_ANSWER: 'FirmwareFailNoAnswer',
    BAD_ADDRESS: 'FirmwareFailAddress', WRONG_DEVICE: 'FirmwareFailWrongDevice', FILE_INVALID: 'FirmwareFailFileInvalid',
    WRONG_CHIP: 'FirmwareFailNeedsRp2040', PERMISSION_DENIED: 'FirmwareFailPermission', NO_BOOTLOADER: 'FirmwareFailBootsel', NO_BOOTLOADER_ADAPTER: 'FirmwareFailBootselAdapter',
    VERIFY_MISMATCH: 'FirmwareFailVerify', CANCELLED: 'FirmwareFailCancelled',
  };
  const STAGES = {
    PREPARE: 'FirmwareStagePrepare', ENTER_BOOTSEL: 'FirmwareStageEnterBootsel', WAIT_BOOTSEL: 'FirmwareStageWaitBootsel',
    PERMISSION: 'FirmwareStagePermission', HANDSHAKE: 'FirmwareStageHandshake', ERASE: 'FirmwareStageErase', WRITE: 'FirmwareStageWrite',
    VERIFY: 'FirmwareStageVerify', REBOOT: 'FirmwareStageReboot', WAIT_DEVICE: 'FirmwareStageWaitDevice',
  };
  const MODE_KEYS = { USB: ['FirmwareModeUsb', 'FirmwareModeUsbDesc'], UART: ['FirmwareModeUart', 'FirmwareModeUartDesc'],
    DFU: ['FirmwareModeDfu', 'FirmwareModeDfuDesc'], RP2040: ['FirmwareModeRp2040', 'FirmwareModeRp2040Desc'] };
  const STATE_KEYS = { IDLE: 'stateIDLE', STARTING: 'stateSTARTING', RUNNING: 'stateRUNNING', STOPPING: 'stateSTOPPING' };
  const ADDRESS_MODES = ['USB', 'UART', 'DFU'];

  const store = {
    get(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { window.localStorage.setItem(k, v); } catch (e) { /* private mode */ } },
  };
  const session = {
    get(k) { try { return window.sessionStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { window.sessionStorage.setItem(k, v); } catch (e) { /* private mode */ } },
  };

  function h(tag, attrs) {
    const node = document.createElement(tag);
    for (const k in (attrs || {})) {
      const v = attrs[k];
      if (v === null || v === undefined || v === false) continue;
      if (k === 'class') node.className = v;
      else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
      else if (k === 'text') node.textContent = v;
      else node.setAttribute(k, v === true ? '' : v);
    }
    for (let i = 2; i < arguments.length; i++) append(node, arguments[i]);
    return node;
  }
  function append(node, c) {
    if (Array.isArray(c)) c.forEach((x) => append(node, x));
    else if (c !== null && c !== undefined && c !== false) node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
  }

  // ---- language and theme -------------------------------------------------------------------------------------
  function matchLanguage(tag) {
    if (!tag || tag === 'system') return null;
    if (LANGS.includes(tag)) return tag;
    const low = tag.toLowerCase();
    if (low.startsWith('pt')) return 'pt-BR';
    if (low.startsWith('ru')) return 'ru';
    if (low === 'zh-tw' || low === 'zh-hk' || low.startsWith('zh-hant')) return 'zh-TW';
    if (low.startsWith('zh')) return 'zh-CN';
    if (low.startsWith('en')) return 'en';
    return null;
  }
  let lang = matchLanguage(store.get('kp_lang')) || matchLanguage(navigator.language) || 'en';
  let theme = store.get('kp_theme') || (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');

  function t(key) {
    let s = (window.I18N[lang] && window.I18N[lang][key]) || window.I18N.en[key] || key;
    for (let i = 1; i < arguments.length; i++) s = s.split('{' + (i - 1) + '}').join(String(arguments[i]));
    return s;
  }
  function applyTheme() { document.documentElement.setAttribute('data-theme', theme); }
  function applyLang() { document.documentElement.lang = lang; document.title = t('pageTitle'); }
  applyTheme();
  applyLang();

  // ---- state --------------------------------------------------------------------------------------------------
  let authed = null;                 // null = unknown yet
  let st = null;                     // last /api/state
  let view = 'home';                 // home | wizard | run
  let wiz = { step: 0, file: null, device: null, mode: null, start: '0x08000000' };
  let modal = null;                  // {type, ...}
  let upload = null;                 // {name, percent} while a file goes up
  let toastTimer = null;
  let offline = false;
  let showLog = false;
  let logText = '';
  let experimentalCache = {};
  let lastSig = '';
  let tab = ['firmware', 'settings', 'camera'].includes(store.get('kp_tab')) ? store.get('kp_tab') : 'dashboard';   // dashboard | settings | camera | firmware
  let dash = null;                   // last /api/dashboard
  let live = null;                   // last /api/metrics
  const HIST = 60;                   // seconds kept in the charts
  let hist = [];
  let logs = { sources: [], id: null, text: '', loading: false, live: false, tried: false };

  try { const saved = JSON.parse(session.get('kp_wiz') || 'null'); if (saved) { wiz = Object.assign(wiz, saved); view = 'wizard'; tab = 'firmware'; } } catch (e) { /* ignore */ }
  function saveWiz() { session.set('kp_wiz', JSON.stringify(wiz)); }
  function clearWiz() { wiz = { step: 0, file: null, device: null, mode: null, start: '0x08000000' }; session.set('kp_wiz', ''); }

  // ---- api ----------------------------------------------------------------------------------------------------
  async function api(method, path, body) {
    const opts = { method, credentials: 'same-origin', headers: { 'X-KlipPocket': '1' } };
    if (body !== undefined) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    let res;
    try { res = await fetch(path, opts); } catch (e) { setOffline(true); throw e; }
    setOffline(false);
    let data = {};
    try { data = await res.json(); } catch (e) { /* empty body */ }
    if (res.status === 401 && data.error === 'auth') { authed = false; render(true); throw Object.assign(new Error('auth'), { status: 401, data }); }
    if (!res.ok) throw Object.assign(new Error(data.error || String(res.status)), { status: res.status, data });
    return data;
  }
  function setOffline(v) { if (offline !== v) { offline = v; render(true); } }

  function toast(text, isError) {
    const old = document.querySelector('.toast');
    if (old) old.remove();
    clearTimeout(toastTimer);
    const n = h('div', { class: 'toast' + (isError ? ' err' : ''), role: 'status', text });
    document.body.appendChild(n);
    toastTimer = setTimeout(() => n.remove(), 5000);
  }

  function describeError(e) {
    const k = e && e.message;
    if (k === 'busy') return t('busy');
    if (k === 'link') return t('badLink');
    if (k === 'size') return t('uploadTooBig', Math.round(((st && st.maxBytes) || 16777216) / 1048576));
    if (k === 'type') return t('uploadType');
    if (k === 'address') return t('FirmwareFailAddress');
    if (k === 'device') return t('FirmwareFailWrongDevice');
    return t('serverError');
  }

  async function refresh() {
    if (authed === false) return;
    try {
      st = await api('GET', '/api/state');
      authed = true;
      if (!store.get('kp_lang')) { const l = matchLanguage(st.language); if (l && l !== lang) { lang = l; applyLang(); } }
      if (!store.get('kp_theme')) { const th = st.dark ? 'dark' : 'light'; if (th !== theme) { theme = th; applyTheme(); } }
      // a write in progress, or a result nobody has closed yet, owns the page: also after a reload or on another device
      if (st.flash.state === 'running' || st.flash.state === 'done') { if (view !== 'run') { view = 'run'; tab = 'firmware'; modal = null; } }
      else if (view === 'run') { view = 'home'; }
      render();
    } catch (e) { if (e.status === 401) render(true); }
  }

  // ---- views --------------------------------------------------------------------------------------------------
  function hex4(v) { return String(v).padStart(4, '0'); }
  function formatSize(n) { return n >= 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB'; }

  function icon(paths) {
    const ns = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    paths.forEach((d) => { const p = document.createElementNS(ns, 'path'); p.setAttribute('d', d); svg.appendChild(p); });
    return svg;
  }
  const ICONS = {
    upload: ['M12 16V4', 'M7 9l5-5 5 5', 'M4 20h16'],
    link: ['M10 14a4 4 0 0 0 5.6 0l3-3a4 4 0 0 0-5.6-5.6l-1 1', 'M14 10a4 4 0 0 0-5.6 0l-3 3a4 4 0 0 0 5.6 5.6l1-1'],
    chip: ['M7 7h10v10H7z', 'M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4'],
    dashboard: ['M3 3h8v8H3z', 'M13 3h8v5h-8z', 'M13 10h8v11h-8z', 'M3 13h8v8H3z'],
    camera: ['M4 8h3l2-3h6l2 3h3v11H4z', 'M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z'],
    gear: ['M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z', 'M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z'],
    play: ['M7 4l13 8-13 8z'],
    stop: ['M6 6h12v12H6z'],
    plus: ['M12 5v14', 'M5 12h14'],
    playAll: ['M4 5l8 7-8 7z', 'M12 5l8 7-8 7z'],
    stopAll: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M9 9h6v6H9z'],
  };
  // a round icon button: the label is its tooltip and its name for screen readers
  function iconBtn(name, label, onclick, extra) {
    return h('button', { class: 'ibtn' + (extra ? ' ' + extra : ''), title: label, 'aria-label': label, onclick }, icon(ICONS[name]));
  }

  function header() {
    const langSel = h('select', { 'aria-label': t('language'), onchange: (e) => { lang = e.target.value; store.set('kp_lang', lang); applyLang(); render(true); } },
      LANGS.map((l) => h('option', { value: l, selected: l === lang }, window.LANGUAGE_NAMES[l])));
    const themeBtn = h('button', { class: 'btn secondary', 'aria-label': t('theme'), onclick: () => {
      theme = theme === 'dark' ? 'light' : 'dark'; store.set('kp_theme', theme); applyTheme(); render(true);
    } }, theme === 'dark' ? t('themeLight') : t('themeDark'));
    const tools = h('div', { class: 'tools' }, langSel, themeBtn);
    if (authed) tools.appendChild(h('button', { class: 'btn text', onclick: logout }, t('logout')));
    return h('header', { class: 'top' }, h('h1', { text: t('pageTitle') }), tools);
  }

  async function logout() { try { await api('POST', '/api/logout', {}); } catch (e) { /* ignore */ } authed = false; st = null; dash = null; live = null; hist = []; cfg = null; cam = null; stopStream(); logs = { sources: [], id: null, text: '', loading: false, live: false, tried: false }; render(true); }

  function loginView() {
    let input;
    const msg = h('p', { class: 'error', role: 'alert' });
    async function submit() {
      msg.textContent = '';
      try {
        await api('POST', '/api/login', { pin: input.value.trim() });
        authed = true; input.value = ''; await refresh(); pollDashboard(); pollMetrics(); loadLogSources(false); loadSettings(); if (tab === 'camera') loadCamera().then(startStream);
      } catch (e) {
        if (e.status === 429) msg.textContent = t('pinLocked', (e.data && e.data.message) || '60');
        else if (e.status === 401 || e.status === 400) msg.textContent = t('pinWrong');
        else msg.textContent = t('serverError');
        input.select();
      }
    }
    input = h('input', { type: 'password', id: 'pininput', inputmode: 'numeric', maxlength: '6', autocomplete: 'one-time-code', 'aria-label': t('pinTitle'), onkeydown: (e) => { if (e.key === 'Enter') submit(); } });
    return h('div', { class: 'login' }, h('h2', { class: 'step', text: t('pinTitle') }), h('p', { class: 'muted', text: t('pinHint') }), input, msg,
      h('button', { class: 'btn', onclick: submit }, t('pinEnter')));
  }

  function deviceRow(d, pick, selected) {
    const details = d.vendor + '/' + d.product + ' - ' + d.path + (d.driver ? ' - ' + d.driver + '\n' + d.serialPath : '');
    return h('div', { class: 'row' + (pick ? ' pick' : '') + (selected ? ' selected' : ''), tabindex: pick ? '0' : null, role: pick ? 'radio' : null,
      'aria-checked': pick ? String(!!selected) : null, onclick: pick, onkeydown: pick ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); } } : null },
      h('div', { class: 'grow' }, h('div', { class: 'title', text: (d.label ? d.label + ': ' : '') + hex4(d.vendor) + ':' + hex4(d.product) }), h('div', { class: 'sub', text: details })),
      selected ? h('div', { class: 'dot' }) : null);
  }

  async function refreshDevices() {
    try { const r = await api('GET', '/api/devices'); st.devices = r.devices; render(true); } catch (e) { /* offline banner handles it */ }
  }

  function homeView() {
    const files = st.files;
    return h('div', {},
      h('h2', { class: 'section', text: t('FirmwareTitle') }),
      h('div', { class: 'tiles' },
        tile(ICONS.upload, t('upload'), pickFile),
        tile(ICONS.link, t('pasteLink'), () => { modal = { type: 'link' }; render(true); }),
        tile(ICONS.chip, t('flashFirmware'), () => { modal = { type: 'experimental' }; render(true); })),
      h('input', { type: 'file', id: 'filein', class: 'sr-only', accept: '.bin,.hex,.uf2', tabindex: '-1', 'aria-hidden': 'true',
        onchange: (e) => { const f = e.target.files[0]; e.target.value = ''; if (f) sendFile(f); } }),
      h('div', { class: 'drop', id: 'drop' }, t('uploadDrop')),
      h('h2', { class: 'section', text: t('FirmwareFiles') }),
      files.length === 0 ? h('p', { class: 'muted', text: t('FirmwareNoFiles') }) : files.map((f) =>
        h('div', { class: 'row' }, h('div', { class: 'grow' }, h('div', { class: 'title', text: f.name }), h('div', { class: 'sub', text: formatSize(f.size) })),
          h('span', { class: 'badge', text: f.kind }),
          h('button', { class: 'del', 'aria-label': t('FirmwareDelete') + ' ' + f.name, onclick: () => { modal = { type: 'delete', name: f.name }; render(true); } }, t('FirmwareDelete')))),
      files.length ? h('div', { class: 'actions' }, h('button', { class: 'btn text danger', onclick: () => { modal = { type: 'deleteAll' }; render(true); } }, t('FirmwareDeleteAll'))) : null,
      h('h2', { class: 'section', text: t('ListUSBTitle') }),
      h('div', { class: 'actions' }, h('button', { class: 'btn secondary', onclick: refreshDevices }, t('FirmwareRefresh'))),
      h('div', {}, st.devices.length === 0 ? h('p', { class: 'muted', text: t('ListUSBNoDevices') }) : st.devices.map((d) => deviceRow(d, null, false))));
  }

  function tile(paths, label, onclick) { return h('button', { class: 'tile', onclick }, icon(paths), label); }

  // ---- wizard ---------------------------------------------------------------------------------------------------
  const STEPS = 4;

  function deviceName(id) {
    const d = st.devices.find((x) => x.id === id);
    return d ? (d.label ? d.label + ' · ' : '') + hex4(d.vendor) + ':' + hex4(d.product) : id;
  }

  function selectedFile() { return st.files.find((f) => f.name === wiz.file) || null; }
  function selectedDevice() { return st.devices.find((d) => d.id === wiz.device) || null; }
  function needsAddress() { const f = selectedFile(); return wiz.mode && ADDRESS_MODES.includes(wiz.mode) && f && f.kind === 'BIN'; }
  function parseAddress(text) {
    const v = parseInt(String(text).trim().replace(/^0x/i, ''), 16);
    return Number.isFinite(v) && v >= 0x08000000 && v < 0x10000000 ? v : null;
  }
  function experimentalKey() { return [wiz.file, wiz.device, wiz.mode].join('|'); }

  async function loadExperimental() {
    if (!wiz.file || !wiz.device || !wiz.mode) return;
    const key = experimentalKey();
    if (key in experimentalCache) return;
    try {
      const r = await api('GET', '/api/experimental?file=' + encodeURIComponent(wiz.file) + '&device=' + encodeURIComponent(wiz.device) + '&mode=' + encodeURIComponent(wiz.mode));
      experimentalCache[key] = !!r.experimental;
      render(true);
    } catch (e) { /* ignore */ }
  }

  function choice(title, sub, selected, onclick, note) {
    return h('div', { class: 'row pick' + (selected ? ' selected' : ''), tabindex: '0', role: 'radio', 'aria-checked': String(!!selected), onclick,
      onkeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onclick(); } } },
      h('div', { class: 'grow' }, h('div', { class: 'title', text: title }), sub ? h('div', { class: 'sub', text: sub }) : null, note ? h('div', { class: 'note', text: note }) : null),
      selected ? h('div', { class: 'dot' }) : null);
  }

  function wizardView() {
    const step = wiz.step;
    const titles = ['FirmwareStepFile', 'FirmwareStepDevice', 'FirmwareStepMode', 'FirmwareWarnTitle'];
    let body;
    let canNext = false;
    if (step === 0) {
      body = st.files.length === 0 ? h('p', { class: 'muted', text: t('FirmwareNoFiles') }) : st.files.map((f) =>
        choice(f.name, f.kind + ' · ' + formatSize(f.size), f.name === wiz.file, () => { wiz.file = f.name; saveWiz(); render(true); }));
      canNext = !!selectedFile();
    } else if (step === 1) {
      body = [h('div', { class: 'actions' }, h('span', { class: 'muted grow', text: t('FirmwareSelectDevice') }),
        h('button', { class: 'btn secondary', onclick: refreshDevices }, t('FirmwareRefresh'))),
      st.devices.length === 0 ? h('p', { class: 'muted', text: t('ListUSBNoDevices') }) : st.devices.map((d) => deviceRow(d, () => { wiz.device = d.id; saveWiz(); render(true); }, d.id === wiz.device))];
      canNext = !!selectedDevice();
    } else if (step === 2) {
      loadExperimentalModes();
      body = st.modes.map((m) => choice(t(MODE_KEYS[m][0]), t(MODE_KEYS[m][1]), m === wiz.mode, () => { wiz.mode = m; saveWiz(); render(true); },
        experimentalCache[[wiz.file, wiz.device, m].join('|')] ? t('FirmwareExperimentalNote') : null));
      if (needsAddress()) {
        const ok = parseAddress(wiz.start) !== null;
        body.push(h('div', { class: 'muted', text: t('FirmwareStartAddress') }),
          h('input', { type: 'text', id: 'addrinput', value: wiz.start, 'aria-label': t('FirmwareStartAddress'), oninput: (e) => { wiz.start = e.target.value.trim(); saveWiz(); updateNext(); } }),
          h('p', { class: ok ? 'muted' : 'error', id: 'addrhint', text: t('FirmwareStartAddressHint') }));
      }
      canNext = !!wiz.mode && (!needsAddress() || parseAddress(wiz.start) !== null);
    } else {
      const exp = experimentalCache[experimentalKey()];
      const d = selectedDevice();
      loadExperimental();
      body = [h('p', { text: t('FirmwareWarnText') }),
        exp ? h('div', { class: 'box warn', text: t('FirmwareExperimentalWarn') }) : null,
        h('div', { class: 'summary', text: t('FirmwareSummary', wiz.file || '', d ? (d.label ? d.label + ' · ' : '') + hex4(d.vendor) + ':' + hex4(d.product) : '', wiz.mode ? t(MODE_KEYS[wiz.mode][0]) : '') })];
    }
    const nav = step < STEPS - 1
      ? h('div', { class: 'actions end' },
        h('button', { class: 'btn text', onclick: () => { if (step === 0) { clearWiz(); view = 'home'; } else { wiz.step--; saveWiz(); } render(true); } }, t(step === 0 ? 'FirmwareCancel' : 'FirmwareBack')),
        h('button', { class: 'btn', id: 'next', disabled: !canNext, onclick: () => { wiz.step++; saveWiz(); render(true); } }, t('FirmwareNext')))
      : h('div', { class: 'actions end' },
        h('button', { class: 'btn text', onclick: () => { wiz.step--; saveWiz(); render(true); } }, t('FirmwareBack')),
        h('button', { class: 'btn text', onclick: () => { clearWiz(); view = 'home'; render(true); } }, t('FirmwareCancel')),
        h('button', { class: 'btn danger', onclick: startFlash }, t('FirmwareConfirm')));
    return h('div', {},
      h('div', { class: 'stepof', text: t('FirmwareStepOf', step + 1, STEPS) }),
      h('h2', { class: 'step' + (step === 3 ? ' danger' : ''), text: t(titles[step]) }), body, nav);
  }

  function loadExperimentalModes() {
    if (!wiz.file || !wiz.device) return;
    st.modes.forEach((m) => {
      const key = [wiz.file, wiz.device, m].join('|');
      if (key in experimentalCache) return;
      experimentalCache[key] = false;
      api('GET', '/api/experimental?file=' + encodeURIComponent(wiz.file) + '&device=' + encodeURIComponent(wiz.device) + '&mode=' + encodeURIComponent(m))
        .then((r) => { experimentalCache[key] = !!r.experimental; render(true); }).catch(() => {});
    });
  }

  function updateNext() {
    const next = document.getElementById('next');
    const ok = parseAddress(wiz.start) !== null;
    if (next) next.disabled = !(wiz.mode && (!needsAddress() || ok));
    const hint = document.getElementById('addrhint');
    if (hint) hint.className = ok ? 'muted' : 'error';
  }

  async function startFlash() {
    const body = { file: wiz.file, device: wiz.device, mode: wiz.mode };
    if (needsAddress()) body.startAddress = '0x' + parseAddress(wiz.start).toString(16);
    try {
      await api('POST', '/api/flash', body);
      clearWiz(); view = 'run'; showLog = false; await refresh();
    } catch (e) { toast(describeError(e), true); await refresh(); }
  }

  // ---- progress and result ----------------------------------------------------------------------------------
  function runView() {
    const f = st.flash;
    if (f.state === 'running') {
      return h('div', { class: 'center' }, h('h2', { text: t('FirmwareFlashing') }),
        h('div', { class: 'bar active', role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(f.overall) }, h('span', { id: 'barfill', 'data-w': String(f.overall) })),
        h('p', { text: (STAGES[f.stage] ? t(STAGES[f.stage]) : f.stage) + ' · ' + f.overall + '%' }),
        f.stage === 'PERMISSION' ? h('p', { class: 'muted', text: t('usbPermission') }) : null,
        h('p', { class: 'muted', text: t('FirmwareKeepOpen') }),
        f.file ? h('div', { class: 'summary', text: t('FirmwareSummary', f.file, deviceName(f.device), f.mode && MODE_KEYS[f.mode] ? t(MODE_KEYS[f.mode][0]) : '') }) : null);
    }
    const ok = f.success;
    const reason = !ok ? t(FAILURES[f.failure] || 'FirmwareFailUsb') : null;
    return h('div', { class: 'center' },
      h('h2', { class: ok ? 'okc' : 'error', text: t(ok ? 'FirmwareSuccess' : 'FirmwareFailed') }),
      ok ? h('div', { class: 'bar' }, h('span', { id: 'barfill', 'data-w': '100' })) : null,
      reason ? h('p', { text: reason }) : null,
      !ok && f.detail ? h('p', { class: 'muted', text: t('FirmwareDetail', f.detail) }) : null,
      ok && f.noReturn ? h('p', { class: 'muted', text: t('FirmwareNoReturn') }) : null,
      h('div', { class: 'actions' }, h('button', { class: 'btn secondary', onclick: toggleLog }, t('log')), h('button', { class: 'btn', onclick: closeResult }, t('FirmwareDone'))),
      showLog ? h('pre', { class: 'log', text: logText }) : null);
  }

  async function toggleLog() {
    showLog = !showLog;
    if (showLog) { try { logText = (await api('GET', '/api/log')).log; } catch (e) { logText = ''; } }
    render(true);
  }
  async function closeResult() { try { await api('POST', '/api/flash/reset', {}); } catch (e) { /* ignore */ } showLog = false; view = 'home'; await refresh(); }

  // ---- uploads, links, dialogs --------------------------------------------------------------------------------
  function pickFile() { const input = document.getElementById('filein'); if (input) input.click(); }

  function sendFile(file) {
    const max = (st && st.maxBytes) || 16777216;
    const ext = file.name.split('.').pop().toLowerCase();
    if (!['bin', 'hex', 'uf2'].includes(ext)) { toast(t('uploadType'), true); return; }
    if (file.size > max) { toast(t('uploadTooBig', Math.round(max / 1048576)), true); return; }
    upload = { name: file.name, percent: 0 };
    render(true);
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', '/api/upload?name=' + encodeURIComponent(file.name));
    xhr.setRequestHeader('X-KlipPocket', '1');
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) { upload.percent = Math.round(e.loaded * 100 / e.total); updateUpload(); } };
    const done = (okay, message) => { upload = null; render(true); toast(okay ? t('uploadSaved', message) : t('uploadFailed', message), !okay); refresh(); };
    xhr.onload = () => {
      let data = {};
      try { data = JSON.parse(xhr.responseText); } catch (e) { /* not json */ }
      if (xhr.status === 200) done(true, data.saved || file.name);
      else if (xhr.status === 401) { upload = null; authed = false; render(true); }
      else done(false, describeError({ message: data.error }));
    };
    xhr.onerror = () => done(false, t('connectionLost'));
    xhr.send(file);
  }

  function updateUpload() {
    const bar = document.getElementById('upfill');
    const label = document.getElementById('uplabel');
    if (bar && upload) { bar.setAttribute('data-w', String(upload.percent)); bar.style.width = upload.percent + '%'; }
    if (label && upload) label.textContent = t('uploading', upload.percent);
  }

  function uploadOverlay() {
    return h('div', { class: 'overlay', role: 'alertdialog', 'aria-live': 'polite' },
      h('div', { class: 'dialog center' }, h('div', { class: 'spinner' }), h('h3', { id: 'uplabel', text: t('uploading', upload.percent) }),
        h('p', { class: 'muted', text: upload.name }),
        h('div', { class: 'bar active' }, h('span', { id: 'upfill', 'data-w': String(upload.percent) }))));
  }

  function dialog(title, body, actions) {
    return h('div', { class: 'overlay', role: 'dialog', 'aria-modal': 'true', onclick: (e) => { if (e.target.classList.contains('overlay')) { closeModal(); } } },
      h('div', { class: 'dialog' }, h('h3', { text: title }), body, h('div', { class: 'actions end' }, actions)));
  }

  async function closeModal() {
    const wasLink = modal && modal.type === 'link';
    modal = null;
    if (wasLink && st && (st.download.state === 'done' || st.download.state === 'error')) { try { await api('POST', '/api/download/reset', {}); } catch (e) { /* ignore */ } }
    await refresh();
    render(true);
  }

  function modalView() {
    if (!modal) return null;
    if (modal.type === 'experimental') {
      return dialog(t('experimentalTitle'), h('p', { text: t('experimentalText') }), [
        h('button', { class: 'btn text', onclick: closeModal }, t('FirmwareCancel')),
        h('button', { class: 'btn', onclick: () => { modal = null; clearWiz(); view = 'wizard'; saveWiz(); render(true); } }, t('continue'))]);
    }
    if (modal.type === 'delete') {
      return dialog(t('FirmwareDelete'), h('p', { text: t('FirmwareDeleteOneConfirm') + ' ' + modal.name }), [
        h('button', { class: 'btn text', onclick: closeModal }, t('FirmwareCancel')),
        h('button', { class: 'btn danger', onclick: async () => {
          try { await api('DELETE', '/api/files?name=' + encodeURIComponent(modal.name)); } catch (e) { toast(describeError(e), true); }
          if (wiz.file === modal.name) { wiz.file = null; saveWiz(); }
          modal = null; await refresh(); render(true);
        } }, t('FirmwareDelete'))]);
    }
    if (modal.type === 'deleteAll') {
      return dialog(t('FirmwareDeleteAll'), h('p', { text: t('FirmwareDeleteAllConfirm', st.files.length) }), [
        h('button', { class: 'btn text', onclick: closeModal }, t('FirmwareCancel')),
        h('button', { class: 'btn danger', onclick: async () => {
          try { await api('POST', '/api/files/delete-all', {}); } catch (e) { toast(describeError(e), true); }
          wiz.file = null; saveWiz(); modal = null; await refresh(); render(true);
        } }, t('FirmwareDeleteAll'))]);
    }
    if (modal.type === 'addPrinter') return addPrinterDialog();
    if (modal.type === 'clearLogs') {
      return dialog(t('LogsClearTitle'), h('p', { text: t('LogsClearMessage') }), [
        h('button', { class: 'btn text', onclick: () => { modal = null; render(true); } }, t('FirmwareCancel')),
        h('button', { class: 'btn danger', onclick: clearAllLogs }, t('LogsClear'))]);
    }
    if (modal.type === 'link') return linkDialog();
    return null;
  }

  function linkDialog() {
    const d = st.download;
    const running = d.state === 'running';
    let input;
    async function start() {
      const url = input.value.trim();
      if (!url) return;
      try { await api('POST', '/api/download', { url }); await refresh(); } catch (e) { toast(describeError(e), true); }
    }
    input = h('input', { type: 'url', id: 'linkinput', placeholder: t('linkPlaceholder'), 'aria-label': t('FirmwareDownloadUrl'), value: modal.url || '', disabled: running,
      oninput: (e) => { modal.url = e.target.value; }, onkeydown: (e) => { if (e.key === 'Enter') start(); } });
    const status = running
      ? [h('p', { text: t('FirmwareDownloading', d.percent + '%') }), h('div', { class: 'bar active' }, h('span', { id: 'dlfill', 'data-w': String(d.percent) }))]
      : d.state === 'done' ? h('p', { class: 'okc', text: t('FirmwareDownloaded', d.files.join(', ')) })
        : d.state === 'error' ? h('p', { class: 'error', text: t('FirmwareDownloadFailed', d.message) }) : null;
    return dialog(t('pasteLink'), h('div', {}, h('p', { class: 'muted', text: t('FirmwareDownloadHint') }), input, status), [
      h('button', { class: 'btn text', onclick: closeModal }, t('close')),
      h('button', { class: 'btn', disabled: running, onclick: start }, t('FirmwareDownloadStart'))]);
  }

  // ---- dashboard ----------------------------------------------------------------------------------------------
  function formatBytes(n) {
    if (n >= 1073741824) return (n / 1073741824).toFixed(1) + ' GB';
    return Math.round(n / 1048576) + ' MB';
  }
  function formatUptime(ms) {
    const m = Math.floor(ms / 60000);
    const d = Math.floor(m / 1440), hh = Math.floor((m % 1440) / 60), mm = m % 60;
    return (d ? d + 'd ' : '') + hh + 'h ' + mm + 'm';
  }
  function pct(v) { return v === null || v === undefined ? '–' : Math.round(v) + '%'; }
  function setText(id, text) { const n = document.getElementById(id); if (n && n.textContent !== text) n.textContent = text; }
  function setBar(id, value) { const n = document.getElementById(id); if (n) { n.setAttribute('data-w', String(Math.round(value))); n.style.width = Math.round(value) + '%'; } }

  function kv(label, value, id) {
    return h('div', { class: 'kv' }, h('span', { class: 'k', text: label }), h('span', { class: 'v', id, text: value }));
  }
  function meter(id) { return h('div', { class: 'bar thin' }, h('span', { id, 'data-w': '0' })); }

  function chartCard(id, title) {
    return h('div', { class: 'card' },
      h('div', { class: 'cardhead' }, h('h3', { text: title }), h('div', { class: 'legend', id: id + '-leg' })),
      h('canvas', { id: id + '-cv', class: 'chart', role: 'img', 'aria-label': title }),
      h('div', { class: 'muted small', text: t('seconds', HIST) }));
  }

  function mcuRows(i) {
    if (i.state !== 'RUNNING' || !i.mcus.length) return h('p', { class: 'muted small', text: t('mcuNone') });
    return i.mcus.map((m) => h('div', { class: 'row' },
      h('div', { class: 'grow' }, h('div', { class: 'title', text: m.name }),
        h('div', { class: 'sub', text: [t('mcuChip') + ': ' + (m.chip || '–'), t('mcuVersion') + ': ' + (m.version || '–'),
          m.clock ? t('mcuClock') + ': ' + (m.clock / 1e6).toFixed(m.clock % 1e6 ? 1 : 0) + ' MHz' : null].filter(Boolean).join('\n') }))));
  }

  function dashboardView() {
    if (!dash) return h('div', { class: 'center' }, h('div', { class: 'spinner' }));
    const hs = dash.host;
    const model = [hs.manufacturer, hs.model].filter(Boolean).join(' ');
    const hostRows = [
      kv(t('hostModel'), model + (hs.device ? ' (' + hs.device + ')' : '')),
      hs.soc ? kv(t('hostSoc'), hs.soc) : kv(t('hostSoc'), hs.hardware || hs.board || '–'),
      kv(t('hostCores'), hs.cores + (hs.maxMhz ? ' · ' + hs.maxMhz + ' MHz' : '')),
      kv(t('hostAbi'), (hs.abis || []).join(', ')),
      kv(t('hostAndroid'), hs.android + ' (API ' + hs.sdk + ')'),
      kv(t('hostKernel'), hs.kernel || '–'),
      kv(t('hostUptime'), '–', 'v-uptime'),
      kv(t('hostAddress'), (hs.addresses || []).join(', ') || '–'),
      kv(t('hostApp'), hs.app || '–')];
    return h('div', {},
      h('h2', { class: 'section', text: t('usageTitle') }),
      h('div', { class: 'stats', id: 'stats' }, statRows()),
      h('div', { class: 'charts one' }, chartCard('c-cpu', t('cpuTitle'))),
      h('div', { class: 'charts' }, chartCard('c-mem', t('memTitle')), chartCard('c-temp', t('tempTitle'))),
      h('p', { class: 'muted small', id: 'cpu-note', text: '' }),
      h('div', { class: 'cols' },
        h('div', { class: 'card' }, h('h3', { text: t('hostTitle') }), hostRows),
        h('div', { class: 'card' }, h('h3', { text: t('memTitle') }),
          kv(t('memTotal'), '–', 'm-total'), kv(t('memUsed'), '–', 'm-used'), kv(t('memAvailable'), '–', 'm-avail'), meter('m-bar'),
          kv(t('swapTitle'), '–', 'm-swap'),
          h('h3', { class: 'gap', text: t('storageTitle') }),
          dash.storage.map((v, n) => h('div', {},
            kv(v.id === 'internal' ? t('storageInternal') : t('storageCard') + (n > 1 ? ' ' + n : ''), t('storageFree', formatBytes(v.free), formatBytes(v.total))),
            h('div', { class: 'bar thin' }, h('span', { 'data-w': String(Math.round((v.total - v.free) * 100 / Math.max(1, v.total))) })))))),
      h('div', { class: 'secthead' }, h('h2', { class: 'section', text: t('printersTitle') }),
        h('div', { class: 'tools' },
          dash.instances.length ? (dash.instances.some((i) => i.state === 'RUNNING' || i.state === 'STARTING')
            ? iconBtn('stopAll', t('ctlStopAll'), () => control(null, 'stop')) : iconBtn('playAll', t('ctlStartAll'), () => control(null, 'start'))) : null,
          iconBtn('plus', t('NewInstance'), openAddPrinter))),
      dash.instances.length === 0 ? h('p', { class: 'muted', text: t('noPrinters') }) : dash.instances.map((i) => h('div', { class: 'card' },
        h('div', { class: 'cardhead' }, h('h3', { text: i.name || i.id }), h('div', { class: 'tools' }, h('span', { class: 'badge' + (i.state === 'RUNNING' ? ' on' : ''), text: t(STATE_KEYS[i.state] || 'stateIDLE') }),
          i.state === 'STOPPING' ? null : (i.state === 'IDLE' ? iconBtn('play', t('ctlStart'), () => control(i.id, 'start')) : iconBtn('stop', t('ctlStop'), () => control(i.id, 'stop'))))),
        mcuRows(i))),
      h('h2', { class: 'section', text: t('ListUSBTitle') }),
      dash.usb.length === 0 ? h('p', { class: 'muted', text: t('ListUSBNoDevices') })
        : dash.usb.map((d) => deviceRow({ label: d.label, vendor: d.vendor, product: d.product, path: d.path, driver: d.driver, serialPath: d.serialPath }, null, false)));
  }

  // ---- the cards at the top: all of them always, three per row at most, each row fills the line ----
  function rate(b) { return b >= 1048576 ? (b / 1048576).toFixed(1) + ' MB/s' : Math.round(b / 1024) + ' KB/s'; }
  // the usual rule of thumb: -100 dBm or worse is 0 %, -50 dBm or better is 100 %
  function wifiPercent(dbm) { return Math.max(0, Math.min(100, Math.round(2 * (dbm + 100)))); }
  const temp = (v) => v !== undefined ? v.toFixed(1) + ' °C' : null;
  // [key, label, wide (has a second line), value(m) -> [big, small] or null when this phone does not show it]
  const CARDS = [
    ['cpu', 'cpuTitle', true, (m) => {
      const v = m.cpu !== undefined ? m.cpu : m.appCpu;
      return v === undefined ? null : [pct(v), m.cpu !== undefined ? t('cpuApp') + ': ' + pct(m.appCpu) : t('cpuApp') + ' · ' + t('appProcesses', m.appProcesses)];
    }],
    ['mem', 'memTitle', true, (m) => m.memTotal ? [pct((m.memTotal - m.memAvailable) * 100 / m.memTotal), t('cpuApp') + ': ' + formatBytes(m.appRss)] : null],
    ['cpuTemp', 'cardCpuTemp', false, (m) => temp(m.cpuTemp) && [temp(m.cpuTemp), '']],
    ['freq', 'cardFreq', true, (m) => m.cpuMhz !== undefined ? [m.cpuMhz + ' MHz', dash && dash.host.maxMhz ? '≤ ' + dash.host.maxMhz + ' MHz' : ''] : null],
    ['wifi', 'cardWifi', true, (m) => m.wifiDbm !== undefined ? [wifiPercent(m.wifiDbm) + '%', m.wifiDbm + ' dBm' + (m.wifiMbps ? ' · ' + m.wifiMbps + ' Mbps' : '')] : null],
    ['gpuTemp', 'cardGpuTemp', false, (m) => temp(m.gpuTemp) && [temp(m.gpuTemp), '']],
    ['down', 'cardDown', true, (m) => m.netRx !== undefined ? [rate(m.netRx), ''] : null],
    ['up', 'cardUp', true, (m) => m.netTx !== undefined ? [rate(m.netTx), ''] : null],
    ['skinTemp', 'cardSkinTemp', false, (m) => temp(m.skinTemp) && [temp(m.skinTemp), '']],
    ['storage', 'storageTitle', true, () => {
      const v = dash && dash.storage[0];
      return v ? [pct((v.total - v.free) * 100 / Math.max(1, v.total)), t('storageFree', formatBytes(v.free), formatBytes(v.total))] : null;
    }],
    ['battery', 'cardBattery', false, (m) => m.battery ? [m.battery.percent + '%', m.battery.charging ? t('charging') : ''] : null],
    ['batTemp', 'cardBatTemp', false, (m) => m.battery ? [m.battery.temp.toFixed(1) + ' °C', ''] : null],
    ['voltage', 'cardVoltage', false, (m) => m.battery && m.battery.voltage ? [(m.battery.voltage / 1000).toFixed(2) + ' V', ''] : null],
    ['load', 'cardLoad', false, (m) => m.load !== undefined ? [m.load.toFixed(2), ''] : null],
  ];

  // one row: 1 card takes the line, 2 share it, 3 give more room to the two with more to say
  function growOf(row) {
    if (row.length < 3) return row.map(() => 1);
    const order = row.map((c, i) => i).sort((a, b) => (row[b][2] ? 1 : 0) - (row[a][2] ? 1 : 0) || a - b);
    const big = new Set(order.slice(0, 2));
    return row.map((c, i) => (big.has(i) ? 3 : 2));
  }

  function statRows() {
    const rows = [];
    for (let i = 0; i < CARDS.length; i += 3) rows.push(CARDS.slice(i, i + 3));
    return rows.map((row) => {
      const grow = growOf(row);
      return h('div', { class: 'statrow' }, row.map((c, i) => h('div', { class: 'stat fx' + grow[i] },
        h('div', { class: 'k', text: t(c[1]) }),
        c[0] === 'wifi' ? h('div', { class: 'valuebars' }, h('div', { class: 'big', id: 'sv-wifi', text: t('na') }), h('div', { class: 'bars', id: 'wifibars', 'aria-hidden': 'true' }, [1, 2, 3, 4].map((n) => h('i', { class: 'b' + n }))))
          : h('div', { class: 'big', id: 'sv-' + c[0], text: t('na') }),
        h('div', { class: 'muted small', id: 'ss-' + c[0], text: '' }))));
    });
  }

  function updateCards(m) {
    CARDS.forEach(([key, , , fn]) => {
      const v = fn(m);
      setText('sv-' + key, v ? v[0] : t('na'));
      setText('ss-' + key, v ? v[1] : '');
      const big = document.getElementById('sv-' + key);
      if (big) big.classList.toggle('na', !v);
    });
    const bars = document.getElementById('wifibars');
    if (bars) {
      const level = m.wifiDbm === undefined ? 0 : Math.ceil(wifiPercent(m.wifiDbm) / 25);
      [...bars.children].forEach((b, i) => b.classList.toggle('on', i < level));
    }
    const anyTemp = hist.some((p) => p.cpuTemp !== null || p.gpuTemp !== null || p.skinTemp !== null || p.batTemp !== null);
    const card = document.getElementById('c-temp-cv');
    if (card) card.parentNode.classList.toggle('hidden', !anyTemp);
  }

  // updates the numbers and charts in place: the page is not rebuilt every second
  function updateLive() {
    if (tab !== 'dashboard' || !live || !document.getElementById('stats')) return;
    const m = live;
    const used = m.memTotal - m.memAvailable;
    const usedPct = m.memTotal ? used * 100 / m.memTotal : 0;
    updateCards(m);
    setText('v-uptime', formatUptime(m.uptimeMs));
    setText('m-total', formatBytes(m.memTotal));
    setText('m-used', formatBytes(used) + ' (' + pct(usedPct) + ')');
    setText('m-avail', formatBytes(m.memAvailable));
    setBar('m-bar', usedPct);
    setText('m-swap', m.swapTotal ? formatBytes(m.swapTotal - m.swapFree) + ' / ' + formatBytes(m.swapTotal) : '–');
    setText('cpu-note', m.cpu === undefined ? t('cpuHidden') : '');
    drawCharts();
  }

  function pushHistory(m) {
    const usedPct = m.memTotal ? (m.memTotal - m.memAvailable) * 100 / m.memTotal : null;
    hist.push({
      cpu: m.cpu !== undefined ? m.cpu : null, appCpu: m.appCpu !== undefined ? m.appCpu : null,
      mem: usedPct, appMem: m.memTotal ? m.appRss * 100 / m.memTotal : null,
      cpuTemp: m.cpuTemp !== undefined ? m.cpuTemp : null, gpuTemp: m.gpuTemp !== undefined ? m.gpuTemp : null,
      skinTemp: m.skinTemp !== undefined ? m.skinTemp : null, batTemp: m.battery ? m.battery.temp : null,
    });
    if (hist.length > HIST) hist.shift();
  }

  function cssVar(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }

  // series: [{key, label, color}]; the values are percentages unless max is given
  function drawChart(id, series, unit, fixedMax) {
    const cv = document.getElementById(id + '-cv');
    const leg = document.getElementById(id + '-leg');
    if (!cv) return;
    const last = hist[hist.length - 1] || {};
    const active = series.filter((s) => hist.some((p) => p[s.key] !== null && p[s.key] !== undefined));
    if (leg) {
      leg.replaceChildren(...active.map((s) => h('span', { class: 'lg' }, h('i', { class: 'sw ' + s.cls }), s.label + ' ' + (last[s.key] === null || last[s.key] === undefined ? '–' : Math.round(last[s.key] * 10) / 10 + unit))));
    }
    const dpr = window.devicePixelRatio || 1;
    const w = Math.max(120, cv.clientWidth), hgt = 140;
    if (cv.width !== Math.round(w * dpr)) { cv.width = Math.round(w * dpr); cv.height = Math.round(hgt * dpr); }
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, w, hgt);
    let max = 100;
    if (fixedMax) { const top = Math.max(0, ...active.map((s) => Math.max(...hist.map((p) => p[s.key] || 0)))); max = Math.max(fixedMax, Math.ceil(top / 10) * 10); }
    const padL = 34, padB = 4, padT = 6, plotW = w - padL - 4, plotH = hgt - padB - padT;
    g.font = '11px system-ui, sans-serif';
    g.fillStyle = cssVar('--ink-muted');
    g.strokeStyle = cssVar('--line');
    g.lineWidth = 1;
    [0, 0.5, 1].forEach((f) => {
      const y = padT + plotH * (1 - f);
      g.beginPath(); g.moveTo(padL, y); g.lineTo(w - 4, y); g.stroke();
      g.textBaseline = f === 0 ? 'bottom' : f === 1 ? 'top' : 'middle';
      g.fillText(Math.round(max * f) + unit, 2, y);
    });
    active.forEach((s, n) => {
      const color = cssVar(s.color);
      g.strokeStyle = color; g.lineWidth = 2; g.lineJoin = 'round';
      g.beginPath();
      let started = false;
      let firstX = 0, lastX = 0;
      hist.forEach((p, k) => {
        const v = p[s.key];
        if (v === null || v === undefined) { started = false; return; }
        const x = padL + plotW * (k + (HIST - hist.length)) / (HIST - 1);
        const y = padT + plotH * (1 - Math.min(1, v / max));
        if (!started) { g.moveTo(x, y); started = true; if (!firstX) firstX = x; } else g.lineTo(x, y);
        lastX = x;
      });
      g.stroke();
      if (n === 0 && lastX > firstX) {
        g.lineTo(lastX, padT + plotH); g.lineTo(firstX, padT + plotH); g.closePath();
        g.globalAlpha = 0.12; g.fillStyle = color; g.fill(); g.globalAlpha = 1; g.fillStyle = cssVar('--ink-muted');
      }
    });
  }

  function drawCharts() {
    drawChart('c-cpu', [{ key: 'cpu', label: t('cpuDevice'), color: '--accent', cls: 'a' }, { key: 'appCpu', label: t('cpuApp'), color: '--ok', cls: 'b' }], '%');
    drawChart('c-mem', [{ key: 'mem', label: t('cpuDevice'), color: '--accent', cls: 'a' }, { key: 'appMem', label: t('cpuApp'), color: '--ok', cls: 'b' }], '%');
    drawChart('c-temp', [{ key: 'cpuTemp', label: t('tempCpu'), color: '--warn', cls: 'c' }, { key: 'gpuTemp', label: t('cardGpuTemp'), color: '--danger', cls: 'd' },
      { key: 'skinTemp', label: t('cardSkinTemp'), color: '--ok', cls: 'b' }, { key: 'batTemp', label: t('tempBattery'), color: '--accent', cls: 'a' }], ' °C', 60);
  }

  let metricsBusy = false;
  async function pollMetrics() {
    if (!authed || tab !== 'dashboard' || document.hidden || metricsBusy) return;
    metricsBusy = true;
    try { live = await api('GET', '/api/metrics'); pushHistory(live); updateLive(); } catch (e) { /* offline banner handles it */ }
    metricsBusy = false;
  }

  let dashBusy = false;
  async function pollDashboard() {
    if (!authed || tab !== 'dashboard' || document.hidden || dashBusy) return;
    dashBusy = true;
    try { const d = await api('GET', '/api/dashboard'); const changed = JSON.stringify(d) !== JSON.stringify(dash); dash = d; if (changed) render(true); } catch (e) { /* ignore */ }
    dashBusy = false;
  }

  // ---- logs ---------------------------------------------------------------------------------------------------
  function preferredLog() { return 'app'; }

  async function loadLogSources(keep) {
    try {
      logs.sources = (await api('GET', '/api/logs')).sources;
      logs.tried = true;
      if (!logs.sources.some((s) => s.id === logs.id)) logs.id = null;
      if (!logs.id) {
        const want = keep ? null : preferredLog();
        logs.id = (logs.sources.find((s) => s.id === want) || logs.sources[0] || {}).id || null;
      }
      render(true);
      await loadLog();
    } catch (e) { logs.tried = true; }
  }

  async function loadLog(quiet) {
    if (!logs.id) return;
    const id = logs.id;
    if (!quiet) { logs.loading = true; setLogText(t('LogsLoading')); }
    let text;
    try { text = (await api('GET', '/api/logs/read?id=' + encodeURIComponent(id))).text; } catch (e) { text = t('logsFailed'); }
    if (id !== logs.id) return;
    logs.loading = false;
    logs.text = text;
    setLogText(text);
  }

  function setLogText(text) {
    const pre = document.getElementById('logpre');
    if (!pre) return;
    const nearEnd = pre.scrollHeight - pre.scrollTop - pre.clientHeight < 40;
    const shown = text.trim() ? text : t('logsEmpty');
    if (pre.textContent !== shown) pre.textContent = shown;
    if (nearEnd || logs.loading === false) pre.scrollTop = pre.scrollHeight;
  }

  function selectLog(id) { logs.id = id; logs.text = ''; render(true); loadLog(); }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) return navigator.clipboard.writeText(text);
    // plain http is not a secure context: fall back to the old way
    return new Promise((resolve, reject) => {
      const ta = h('textarea', { class: 'sr-only', 'aria-hidden': 'true' });
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy') ? resolve() : reject(new Error('copy')); } catch (e) { reject(e); } finally { ta.remove(); }
    });
  }

  function logsView() {
    const tabs = h('div', { class: 'ltabs', role: 'tablist' }, logs.sources.map((s) =>
      h('button', { class: 'ltab' + (s.id === logs.id ? ' active' : ''), role: 'tab', 'aria-selected': String(s.id === logs.id), onclick: () => selectLog(s.id) }, s.label)));
    return h('div', { class: 'logs' },
      h('h2', { class: 'section', text: t('logsTitle') }),
      tabs,
      h('pre', { class: 'log tall', id: 'logpre', tabindex: '0', text: logs.loading ? t('LogsLoading') : (logs.text.trim() ? logs.text : t('logsEmpty')) }),
      h('div', { class: 'actions' },
        h('button', { class: 'btn secondary', onclick: () => loadLog() }, t('LogsRefresh')),
        h('button', { class: 'btn secondary', onclick: async () => { try { await copyText(logs.text); toast(t('LogsCopied')); } catch (e) { toast(t('serverError'), true); } } }, t('LogsCopy')),
        h('label', { class: 'check' }, h('input', { type: 'checkbox', id: 'logLive', checked: logs.live, onchange: (e) => { logs.live = e.target.checked; render(true); } }), t('logsLive')),
        h('span', { class: 'grow' }),
        h('button', { class: 'btn text danger', onclick: () => { modal = { type: 'clearLogs' }; render(true); } }, t('LogsClear'))));
  }

  async function clearAllLogs() {
    let ok = false;
    try { ok = (await api('POST', '/api/logs/clear', {})).cleared; } catch (e) { /* toast below */ }
    toast(t(ok ? 'LogsCleared' : 'LogsClearFailed'), !ok);
    modal = null;
    logs.text = '';
    await loadLogSources(true);
  }

  function setTab(next) {
    if (tab === next) return;
    if (tab === 'camera') stopStream();
    tab = next;
    store.set('kp_tab', tab);
    if (tab === 'dashboard') { pollDashboard(); pollMetrics(); }
    if (tab === 'settings') loadSettings();
    if (tab === 'camera') loadCamera().then(startStream);
    render(true);
  }

  function tabsBar() {
    const btn = (id, ic, label) => h('button', { class: 'tabbtn' + (tab === id ? ' active' : ''), role: 'tab', 'aria-selected': String(tab === id), title: label, 'aria-label': label, onclick: () => setTab(id) }, icon(ICONS[ic]));
    return h('nav', { class: 'tabs', role: 'tablist' }, btn('dashboard', 'dashboard', t('tabDashboard')), btn('camera', 'camera', t('tabCamera')),
      btn('firmware', 'chip', t('FirmwareTitle')), btn('settings', 'gear', t('tabSettings')));
  }

  // ---- settings -----------------------------------------------------------------------------------------------
  let cfg = null;                    // last /api/settings
  let form = { obicoCloud: null, obicoUrl: '', code: '' };

  async function loadSettings() {
    if (!authed || tab !== 'settings' || document.hidden) return;
    try {
      const c = await api('GET', '/api/settings');
      const changed = JSON.stringify(c) !== JSON.stringify(cfg);
      cfg = c;
      if (form.obicoCloud === null) { form.obicoCloud = c.obico.cloud; form.obicoUrl = c.obico.cloud ? '' : c.obico.serverUrl; }
      if (changed && !(document.activeElement && document.activeElement.tagName === 'INPUT')) render(true);
    } catch (e) { /* offline banner handles it */ }
  }

  async function setSetting(key, extra) {
    try {
      cfg = await api('POST', '/api/settings', Object.assign({ key }, extra || {}));
      toast(t('saved'));
      render(true);
      return true;
    } catch (e) {
      const k = e && e.data && e.data.error;
      toast(k === 'permission' ? t('cameraPermission') : k === 'link' ? t('ObicoInvalidCode') : k === 'network' ? t('ObicoNetworkError') : t('serverError'), true);
      await loadSettings();
      render(true);
      return false;
    }
  }

  function selectRow(label, s, onchange, id) {
    return h('label', { class: 'frow' }, h('span', { class: 'flabel', text: label }),
      h('select', { id, onchange: (e) => onchange(e.target.value) },
        s.options.map((o) => h('option', { value: String(o.id), selected: String(o.id) === String(s.value) }, o.label))));
  }

  function switchRow(label, checked, onchange, id) {
    return h('label', { class: 'frow' }, h('span', { class: 'flabel', text: label }),
      h('input', { type: 'checkbox', class: 'sw-input', id, checked, onchange: (e) => onchange(e.target.checked) }));
  }

  function settingsCard(title, children) { return h('div', { class: 'card' }, h('h3', { text: title }), children); }

  function settingsView() {
    if (!cfg) return h('div', { class: 'center' }, h('div', { class: 'spinner' }));
    const cam = cfg.camera, ob = cfg.obico;
    const zoomOk = cam.zoom.options.length > 1;
    const obicoSelf = form.obicoCloud === false;
    return h('div', {},
      h('p', { class: 'muted', text: t('settingsHint') }),
      h('div', { class: 'cols' },
        settingsCard(t('EngineFrontend'), [
          selectRow(t('FirmwareEngine'), cfg.engine, (v) => setSetting('engine', { value: v }), 's-engine'),
          selectRow(t('WebFrontend'), cfg.frontend, (v) => setSetting('frontend', { value: v }), 's-frontend')]),
        settingsCard(t('USB'), [selectRow(t('USBDeviceNaming'), cfg.usbNaming, (v) => setSetting('usbNaming', { value: Number(v) }), 's-usb')]),
        settingsCard(t('Camera'), [
          switchRow(t('EnableCamera'), cam.enabled, (v) => setSetting('cameraEnabled', { value: v }), 's-cam'),
          selectRow(t('CameraSource'), cam.source, (v) => setSetting('cameraSource', { value: v }), 's-camsrc'),
          selectRow(t('CameraRotation'), cam.rotation, (v) => setSetting('cameraRotation', { value: Number(v) }), 's-camrot'),
          selectRow(t('CameraResolution'), cam.resolution, (v) => setSetting('cameraResolution', { value: Number(v) }), 's-camres'),
          zoomOk ? selectRow(t('CameraZoom'), cam.zoom, (v) => setSetting('cameraZoom', { value: Number(v) }), 's-camzoom')
            : h('div', { class: 'frow' }, h('span', { class: 'flabel', text: t('CameraZoom') }), h('span', { class: 'muted', text: t('CameraZoomUnsupported') }))]),
        settingsCard(t('RemoteAccess'), [
          switchRow(t('EnableOctoEverywhere'), cfg.octoEverywhere.enabled, (v) => setSetting('octoEverywhere', { value: v }), 's-octo'),
          cfg.octoEverywhere.enabled ? (cfg.octoEverywhere.linkUrl
            ? h('p', {}, h('a', { href: cfg.octoEverywhere.linkUrl, target: '_blank', rel: 'noopener noreferrer', class: 'link' }, t('octoOpen')))
            : h('p', { class: 'muted small', text: t('LinkOctoEverywhereNotReady') })) : null,
          h('div', { class: 'sep' }),
          h('h4', { class: 'subhead', text: t('Obico') }),
          switchRow(t('EnableObico'), ob.enabled, (v) => setSetting('obico', { value: v }), 's-obico'),
          ob.enabled ? obicoBlock(ob, obicoSelf) : null])));
  }

  function obicoBlock(ob, self) {
    const server = [
      h('p', { class: 'muted small', text: t('ObicoServerHint') }),
      h('label', { class: 'frow' }, h('span', { class: 'flabel', text: t('ObicoServerCloud') }),
        h('input', { type: 'radio', name: 'observer', class: 'sw-input', checked: !self, onchange: () => { form.obicoCloud = true; render(true); } })),
      h('label', { class: 'frow' }, h('span', { class: 'flabel', text: t('ObicoServerSelfHosted') }),
        h('input', { type: 'radio', name: 'observer', class: 'sw-input', checked: self, onchange: () => { form.obicoCloud = false; render(true); } })),
      self ? h('input', { type: 'url', id: 'obicourl', class: 'wide', placeholder: t('ObicoServerSelfHostedHint'), value: form.obicoUrl, 'aria-label': t('ObicoServer'),
        oninput: (e) => { form.obicoUrl = e.target.value.trim(); } }) : null,
      h('div', { class: 'actions' }, h('button', { class: 'btn secondary', onclick: () => setSetting('obicoServer', { cloud: !self, url: form.obicoUrl }) }, t('save')))];
    const link = ob.linked
      ? [h('p', { class: 'okc', text: t('ObicoLinked') }), h('p', { class: 'muted small', text: t('ObicoAlreadyLinkedHint') }),
        h('div', { class: 'actions' }, h('button', { class: 'btn text danger', onclick: () => setSetting('obicoUnlink', {}) }, t('ObicoUnlink')))]
      : [h('p', { class: 'muted', text: t('ObicoNotLinked') }), h('p', { class: 'muted small', text: t('ObicoLinkHint') }),
        ob.passcode ? h('div', { class: 'box' }, h('div', { class: 'muted small', text: t('ObicoGeneratedCodeLabel') }), h('div', { class: 'code', text: ob.passcode }),
          ob.passlink ? h('a', { href: ob.passlink, target: '_blank', rel: 'noopener noreferrer', class: 'link' }, t('ObicoOpenLink')) : null)
          : h('p', { class: 'muted small', text: t('ObicoWaitingForCode') }),
        h('p', { class: 'muted small', text: t('ObicoManualCodeHint') }),
        h('input', { type: 'text', id: 'obicocode', class: 'wide', placeholder: t('ObicoLinkCodeHint'), value: form.code, 'aria-label': t('ObicoLinkCodeHint'),
          oninput: (e) => { form.code = e.target.value.trim(); }, onkeydown: (e) => { if (e.key === 'Enter') linkObico(); } }),
        h('div', { class: 'actions' }, h('button', { class: 'btn', onclick: linkObico }, t('ObicoLinkAction')))];
    return [h('div', { class: 'sep' }), server, h('div', { class: 'sep' }), link];
  }

  async function linkObico() {
    if (!form.code) return;
    if (await setSetting('obicoLink', { value: form.code })) form.code = '';
  }

  // ---- start / stop -------------------------------------------------------------------------------------------
  async function control(id, action) {
    try {
      dash = await api('POST', '/api/control', id === null ? { action } : { id, action });
      render(true);
    } catch (e) { toast(e && e.data && e.data.error === 'slots' ? t('ctlNoSlots') : t('serverError'), true); }
  }

  // ---- add a printer -------------------------------------------------------------------------------------------
  async function openAddPrinter() {
    modal = { type: 'addPrinter', info: null, name: '', config: null, autostart: false, busy: false };
    render(true);
    try {
      const info = await api('GET', '/api/printers/new');
      if (modal && modal.type === 'addPrinter') { modal.info = info; modal.config = info.default; render(true); }
    } catch (e) { modal = null; toast(t('serverError'), true); render(true); }
  }

  function addPrinterDialog() {
    const info = modal.info;
    let body;
    if (!info) body = h('div', { class: 'center' }, h('div', { class: 'spinner' }), h('p', { class: 'muted', text: t('InstanceConfigLoading') }));
    else body = h('div', {},
      info.canCreate ? null : h('p', { class: 'error', text: t('ctlNoSlots') }),
      h('label', { class: 'frow' }, h('span', { class: 'flabel', text: t('InstanceName') }),
        h('input', { type: 'text', id: 'pname', placeholder: info.defaultName || t('InstanceDefaultNameHint'), value: modal.name, maxlength: '60', 'aria-label': t('InstanceName'),
          oninput: (e) => { modal.name = e.target.value; } })),
      h('label', { class: 'frow' }, h('span', { class: 'flabel', text: t('InstanceConfig') }),
        h('select', { id: 'pconfig', onchange: (e) => { modal.config = e.target.value; } },
          info.templates.map((f) => h('option', { value: f, selected: f === modal.config }, f)))),
      h('label', { class: 'frow' }, h('span', { class: 'flabel', text: t('Autostart') }),
        h('input', { type: 'checkbox', class: 'sw-input', id: 'pauto', checked: modal.autostart, onchange: (e) => { modal.autostart = e.target.checked; } })));
    return dialog(t('NewInstance'), body, [
      h('button', { class: 'btn text', onclick: () => { modal = null; render(true); } }, t('FirmwareCancel')),
      h('button', { class: 'btn', disabled: !info || !info.canCreate || modal.busy, onclick: createPrinter }, t('InstanceCreate'))]);
  }

  async function createPrinter() {
    const m = modal;
    m.busy = true;
    render(true);
    try {
      dash = await api('POST', '/api/printers', { name: m.name, config: m.config, autostart: m.autostart });
      modal = null;
      toast(t('saved'));
    } catch (e) {
      m.busy = false;
      toast(e && e.data && e.data.error === 'slots' ? t('ctlNoSlots') : t('serverError'), true);
    }
    render(true);
  }

  // ---- camera -------------------------------------------------------------------------------------------------
  let cam = null;                    // last /api/camera
  let camImg = null;                 // the same <img> across renders, so the stream is not restarted
  let camState = 'idle';             // idle | live | down
  let camRetry = null;

  async function loadCamera() {
    try { const c = await api('GET', '/api/camera'); const changed = JSON.stringify(c) !== JSON.stringify(cam); cam = c; if (changed) render(true); } catch (e) { /* ignore */ }
  }

  function startStream() {
    stopStream();
    if (!cam || !cam.enabled || !cam.permission) return;
    camImg = camImg || h('img', { class: 'camimg', alt: '', draggable: 'false' });
    camImg.onload = () => { if (camState !== 'live') { camState = 'live'; render(true); } };
    camImg.onerror = () => { camState = 'down'; render(true); camRetry = setTimeout(() => { if (tab === 'camera') startStream(); }, 3000); };
    camImg.src = '/api/camera/stream?t=' + Date.now();
  }

  // leaving the tab closes the connection: the camera service does not encode frames for nobody
  function stopStream() {
    clearTimeout(camRetry);
    if (camImg) { camImg.onload = null; camImg.onerror = null; camImg.removeAttribute('src'); }
    camState = 'idle';
  }

  function tapFocus(e) {
    if (!camImg || camState !== 'live' || !cam.focusSupported) return;
    const box = e.currentTarget.getBoundingClientRect();
    const iw = camImg.naturalWidth, ih = camImg.naturalHeight;
    if (!iw || !ih) return;
    const aspect = iw / ih;
    const w = box.width / box.height > aspect ? box.height * aspect : box.width;
    const hh = box.width / box.height > aspect ? box.height : box.width / aspect;
    const left = (box.width - w) / 2, top = (box.height - hh) / 2;
    const px = e.clientX - box.left, py = e.clientY - box.top;
    const nx = (px - left) / w, ny = (py - top) / hh;
    if (nx < 0 || nx > 1 || ny < 0 || ny > 1) return;
    const ring = h('div', { class: 'ring' });
    ring.style.left = px + 'px'; ring.style.top = py + 'px';
    e.currentTarget.appendChild(ring);
    setTimeout(() => ring.remove(), 1400);
    api('POST', '/api/camera/focus', { x: nx, y: ny }).catch(() => {});
  }

  function cameraView() {
    if (!cam) return h('div', { class: 'center' }, h('div', { class: 'spinner' }));
    let body;
    if (!cam.enabled) {
      body = h('div', { class: 'box' }, h('p', { text: t('cameraOff') }),
        h('div', { class: 'actions' }, h('button', { class: 'btn', onclick: async () => { if (await setSetting('cameraEnabled', { value: true })) { await loadCamera(); startStream(); } } }, t('EnableCamera'))));
    } else if (!cam.permission) {
      body = h('div', { class: 'box', text: t('cameraPermission') });
    } else {
      body = h('div', { class: 'camwrap', onclick: tapFocus }, camImg || h('span'),
        camState !== 'live' ? h('div', { class: 'camnote', text: camState === 'down' ? t('cameraDown') : t('CameraPreviewConnecting') }) : null);
    }
    return h('div', {}, body,
      cam.enabled && cam.permission ? h('div', {},
        h('p', { class: 'muted small', text: t(cam.focusSupported ? 'CameraPreviewFocusHint' : 'CameraPreviewFocusUnsupported') }),
        cam.focusSupported ? h('p', { class: 'muted small', text: t('CameraPreviewFocusSaved') }) : null,
        cam.focusSupported ? h('div', { class: 'actions' }, h('button', { class: 'btn secondary', onclick: () => api('POST', '/api/camera/focus/reset', {}).then(() => toast(t('saved'))).catch(() => {}) }, t('CameraPreviewFocusReset'))) : null) : null);
  }

  // ---- render -------------------------------------------------------------------------------------------------
  function signature() {
    return JSON.stringify([authed, view, Object.assign({}, wiz, { start: null }), modal && modal.type, modal && modal.name, modal && modal.info, modal && modal.busy, upload && upload.name, offline, lang, theme, showLog, tab, cfg, form.obicoCloud, cam, camState, dash, logs.sources, logs.id, logs.live, st && [st.files, st.devices, st.download, st.flash],
      st && Object.keys(experimentalCache).length]);
  }

  function render(force) {
    const sig = signature();
    if (!force && sig === lastSig) { applyWidths(); return; }
    lastSig = sig;
    const root = h('div', {}, header(), offline ? h('div', { class: 'banner', role: 'alert', text: t('connectionLost') }) : null);
    if (authed === false) root.appendChild(loginView());
    else if (authed === true && st) {
      root.appendChild(tabsBar());
      if (tab === 'dashboard') root.appendChild(dashboardView());
      else if (tab === 'settings') root.appendChild(settingsView());
      else if (tab === 'camera') root.appendChild(cameraView());
      else root.appendChild(view === 'run' ? runView() : view === 'wizard' ? wizardView() : homeView());
      if (tab === 'dashboard') root.appendChild(logsView());
      const m = upload ? uploadOverlay() : modalView();
      if (m) root.appendChild(m);
    }
    const focused = document.activeElement && document.activeElement.id;
    const oldPre = document.getElementById('logpre');
    const keepScroll = oldPre ? oldPre.scrollTop : null;
    $app.replaceChildren(root);
    if (focused) { const again = document.getElementById(focused); if (again) again.focus(); }
    const pre = document.getElementById('logpre');
    if (pre) pre.scrollTop = keepScroll !== null ? keepScroll : pre.scrollHeight;
    updateLive();
    applyWidths();
    wireDrop();
  }

  // the content security policy forbids style attributes, so widths are set from the script
  function applyWidths() {
    document.querySelectorAll('[data-w]').forEach((n) => { n.style.width = n.getAttribute('data-w') + '%'; });
  }

  function wireDrop() {
    const drop = document.getElementById('drop');
    if (!drop) return;
    ['dragenter', 'dragover'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add('over'); }));
    ['dragleave', 'drop'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove('over'); }));
    drop.addEventListener('drop', (e) => { const f = e.dataTransfer && e.dataTransfer.files[0]; if (f) sendFile(f); });
  }

  // ---- start --------------------------------------------------------------------------------------------------
  async function start() {
    try { const s = await api('GET', '/api/session'); authed = !!s.authenticated; } catch (e) { authed = authed === null ? false : authed; }
    if (authed) { await refresh(); pollDashboard(); pollMetrics(); loadLogSources(false); loadSettings(); if (tab === 'camera') loadCamera().then(startStream); } else render(true);
    setInterval(() => { if (authed) refresh(); }, 2000);
    setInterval(pollMetrics, 1000);
    setInterval(pollDashboard, 5000);
    setInterval(loadSettings, 4000);
    setInterval(() => { if (authed && tab === 'camera' && !document.hidden) loadCamera(); }, 5000);
    setInterval(() => { if (authed && logs.live && !document.hidden && logs.id) loadLog(true); }, 3000);
  }
  render(true);
  start();
})();
