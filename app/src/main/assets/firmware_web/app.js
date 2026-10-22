(function () {
  'use strict';

  // ---- helpers ------------------------------------------------------------------------------------------------
  const $app = document.getElementById('app');
  const LANGS = Object.keys(window.I18N);
  const FAILURES = {
    INCOMPATIBLE_FILE: 'FirmwareFailIncompat', NOT_SERIAL: 'FirmwareFailNotSerial', NO_ANSWER: 'FirmwareFailNoAnswer',
    BAD_ADDRESS: 'FirmwareFailAddress', WRONG_DEVICE: 'FirmwareFailWrongDevice', FILE_INVALID: 'FirmwareFailFileInvalid',
    WRONG_CHIP: 'FirmwareFailNeedsRp2040', PERMISSION_DENIED: 'FirmwareFailPermission', NO_BOOTLOADER: 'FirmwareFailBootsel',
    VERIFY_MISMATCH: 'FirmwareFailVerify', CANCELLED: 'FirmwareFailCancelled',
  };
  const STAGES = {
    PREPARE: 'FirmwareStagePrepare', ENTER_BOOTSEL: 'FirmwareStageEnterBootsel', WAIT_BOOTSEL: 'FirmwareStageWaitBootsel',
    PERMISSION: 'FirmwareStagePermission', HANDSHAKE: 'FirmwareStageHandshake', ERASE: 'FirmwareStageErase', WRITE: 'FirmwareStageWrite',
    VERIFY: 'FirmwareStageVerify', REBOOT: 'FirmwareStageReboot', WAIT_DEVICE: 'FirmwareStageWaitDevice',
  };
  const MODE_KEYS = { USB: ['FirmwareModeUsb', 'FirmwareModeUsbDesc'], UART: ['FirmwareModeUart', 'FirmwareModeUartDesc'],
    DFU: ['FirmwareModeDfu', 'FirmwareModeDfuDesc'], RP2040: ['FirmwareModeRp2040', 'FirmwareModeRp2040Desc'] };
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

  try { const saved = JSON.parse(session.get('kp_wiz') || 'null'); if (saved) { wiz = Object.assign(wiz, saved); view = 'wizard'; } } catch (e) { /* ignore */ }
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
      if (st.flash.state === 'running' || st.flash.state === 'done') { if (view !== 'run') { view = 'run'; modal = null; } }
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
  };

  function header() {
    const langSel = h('select', { 'aria-label': t('language'), onchange: (e) => { lang = e.target.value; store.set('kp_lang', lang); applyLang(); render(true); } },
      LANGS.map((l) => h('option', { value: l, selected: l === lang }, window.LANGUAGE_NAMES[l])));
    const themeBtn = h('button', { class: 'btn secondary', 'aria-label': t('theme'), onclick: () => {
      theme = theme === 'dark' ? 'light' : 'dark'; store.set('kp_theme', theme); applyTheme(); render(true);
    } }, theme === 'dark' ? t('themeLight') : t('themeDark'));
    const tools = h('div', { class: 'tools' }, langSel, themeBtn);
    if (authed) tools.appendChild(h('button', { class: 'btn text', onclick: logout }, t('logout')));
    return h('header', { class: 'top' }, h('h1', { text: 'KlipPocket · ' + t('FirmwareTitle') }), tools);
  }

  async function logout() { try { await api('POST', '/api/logout', {}); } catch (e) { /* ignore */ } authed = false; st = null; render(true); }

  function loginView() {
    let input;
    const msg = h('p', { class: 'error', role: 'alert' });
    async function submit() {
      msg.textContent = '';
      try {
        await api('POST', '/api/login', { pin: input.value.trim() });
        authed = true; input.value = ''; await refresh();
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

  // ---- render -------------------------------------------------------------------------------------------------
  function signature() {
    return JSON.stringify([authed, view, Object.assign({}, wiz, { start: null }), modal && modal.type, modal && modal.name, upload && upload.name, offline, lang, theme, showLog, st && [st.files, st.devices, st.download, st.flash],
      st && Object.keys(experimentalCache).length]);
  }

  function render(force) {
    const sig = signature();
    if (!force && sig === lastSig) { applyWidths(); return; }
    lastSig = sig;
    const root = h('div', {}, header(), offline ? h('div', { class: 'banner', role: 'alert', text: t('connectionLost') }) : null);
    if (authed === false) root.appendChild(loginView());
    else if (authed === true && st) {
      root.appendChild(view === 'run' ? runView() : view === 'wizard' ? wizardView() : homeView());
      const m = upload ? uploadOverlay() : modalView();
      if (m) root.appendChild(m);
    }
    const focused = document.activeElement && document.activeElement.id;
    $app.replaceChildren(root);
    if (focused) { const again = document.getElementById(focused); if (again) again.focus(); }
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
    if (authed) await refresh(); else render(true);
    setInterval(() => { if (authed) refresh(); }, 2000);
  }
  render(true);
  start();
})();
