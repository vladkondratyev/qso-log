'use strict';
/*
 * QSO-LOG Web: the log in the browser. Everything stays in this browser (IndexedDB); nothing is sent anywhere.
 * What a browser allows of the full app: the log and the card, the country by prefix (cty.dat by AD1C), distance and
 * bearing by locator, repeat contacts, ADIF import and export, CSV, statistics, CAT over Web Serial, offline work.
 */
const VERSION = '2.9.7';

// ---------- texts ----------

const EN = {
  'Журнал': 'Log', 'Статистика': 'Statistics', 'Настройки': 'Settings', 'Установить': 'Install', 'Трансивер': 'Transceiver',
  'Поиск: позывной, имя, QTH': 'Search: callsign, name, QTH',
  'Журнал пуст. Нажмите «+ QSO» или клавишу Insert, чтобы записать первую связь.': 'The log is empty. Press “+ QSO” or the Insert key to log the first contact.',
  'Можно перенести журнал из другой программы: Настройки → Импорт ADIF.': 'You can bring a log from another program: Settings → Import ADIF.',
  'Моя станция': 'My station', 'Мой позывной': 'My callsign', 'Мой QTH-локатор': 'My locator', 'Мощность, Вт': 'Power, W',
  'Тема': 'Theme', 'Как в системе': 'As the system', 'Светлая': 'Light', 'Тёмная': 'Dark',
  'Обмен данными': 'Import and export', 'Экспорт ADIF': 'Export ADIF', 'Экспорт CSV': 'Export CSV', 'Импорт ADIF': 'Import ADIF',
  'Файл ADIF подходит для LoTW (через TQSL), eQSL, Club Log, QRZ.com, HAMLOG и программ QSO-LOG для Android и компьютера.':
    'The ADIF file suits LoTW (through TQSL), eQSL, Club Log, QRZ.com, HAMLOG and QSO-LOG for Android and the computer.',
  'Трансивер (CAT)': 'Transceiver (CAT)', 'Протокол': 'Protocol', 'Скорость, бод': 'Speed, baud', 'Адрес CI-V (ICOM)': 'CI-V address (ICOM)',
  'Подключить трансивер': 'Connect the transceiver', 'Отключить': 'Disconnect',
  'Через USB-кабель трансивера, в Chrome или Edge на компьютере. Новая карточка берёт частоту, диапазон и вид с трансивера.':
    'Over the transceiver’s USB cable, in Chrome or Edge on a computer. A new card takes the frequency, band and mode from the radio.',
  'Этот браузер не умеет работать с COM-портами (Web Serial). CAT доступен в Chrome и Edge на компьютере.':
    'This browser cannot use COM ports (Web Serial). CAT works in Chrome and Edge on a computer.',
  'Данные': 'Data', 'Удалить весь журнал': 'Delete the whole log', 'О программе': 'About',
  'QSO-LOG Web — облегчённая версия для браузера. Всё хранится только в этом браузере, на сервер ничего не уходит. Полная версия с поиском по QRZ.ru, онлайн-журналами, WSJT-X, DX-кластером и голосовыми заметками — для Android, Windows, macOS и Linux:':
    'QSO-LOG Web is a light version for the browser. Everything is kept only in this browser; nothing goes to a server. The full version with QRZ.ru lookups, online logbooks, WSJT-X, the DX cluster and voice notes is for Android, Windows, macOS and Linux:',
  'Новая связь (Insert)': 'New QSO (Insert)', 'ПОЗЫВНОЙ': 'CALLSIGN', 'Закрыть (Esc)': 'Close (Esc)',
  'Дата UTC': 'Date UTC', 'Время UTC': 'Time UTC', 'Частота, МГц': 'Frequency, MHz', 'RST отпр.': 'RST sent', 'RST прин.': 'RST rcvd',
  'Имя': 'Name', 'Локатор': 'Locator', 'Комментарий': 'Comment', 'QSL отправлена': 'QSL sent', 'QSL получена': 'QSL received',
  'Удалить': 'Delete', 'Записать': 'Save', 'Enter — записать, Esc — закрыть.': 'Enter saves, Esc closes.',
  'Все диапазоны': 'All bands', 'Все виды': 'All modes', 'Диапазон': 'Band', 'Вид': 'Mode',
  'Связей: %s': 'QSOs: %s', 'Показано %s из %s': 'Shown %s of %s',
  'Уже были связи: %s, последняя %s (%s %s)': 'Worked before: %s, last %s (%s %s)',
  'Повтор: этот диапазон и вид уже были': 'Dupe: this band and mode were worked already',
  'Связь записана: %s': 'Saved: %s', 'Изменения сохранены': 'Changes saved', 'Связь удалена': 'QSO deleted', 'Вернуть': 'Undo',
  'Введите позывной': 'Enter the callsign',
  'Импортировано связей: %s, пропущено повторов: %s': 'Imported QSOs: %s, skipped as repeats: %s',
  'В файле нет связей ADIF': 'The file has no ADIF records',
  'Удалить все связи (%s)? Это нельзя отменить. Сначала сохраните журнал в ADIF.': 'Delete all %s QSOs? This cannot be undone. Export ADIF first.',
  'Журнал удалён': 'The log was deleted', 'Журнал пуст': 'The log is empty',
  'Всего связей': 'QSOs in all', 'Позывных': 'Callsigns', 'Стран DXCC': 'DXCC entities', 'Сегодня (UTC)': 'Today (UTC)',
  'Диапазоны и виды': 'Bands and modes', 'Всего': 'Total', 'Больше всего связей': 'Most worked',
  'Связи по месяцам': 'QSOs by month',
  'Хранится в браузере: %s. Браузер не удалит журнал сам: %s.': 'Kept in this browser: %s. The browser will not clear the log by itself: %s.',
  'да': 'yes', 'не гарантировано — делайте экспорт ADIF': 'not guaranteed, export ADIF now and then',
  'Подключение…': 'Connecting…', 'Подключено: %s': 'Connected: %s', 'Трансивер не отвечает': 'The radio does not answer',
  'Отключено': 'Disconnected', 'Ошибка порта: %s': 'Port error: %s',
  'Версия %s · работает без интернета после первого открытия': 'Version %s · works offline after the first visit',
  'Есть несохранённые изменения. Закрыть карточку?': 'There are unsaved changes. Close the card?',
  'км': 'km', 'Новая версия загружена — обновится при следующем открытии.': 'A new version was downloaded and will start next time.',
};
let lang = load('lang') || ((navigator.language || '').toLowerCase().startsWith('ru') ? 'ru' : 'en');
function t(ru, ...args) {
  let s = lang === 'en' ? (EN[ru] || ru) : ru;
  for (const a of args) s = s.replace('%s', a);
  return s;
}
function applyTexts() {
  document.documentElement.lang = lang;
  document.querySelectorAll('[data-i18n]').forEach(el => { el.dataset.ru ??= el.textContent; el.textContent = t(el.dataset.ru); });
  document.querySelectorAll('[data-i18n-ph]').forEach(el => { el.placeholder = t(el.dataset.i18nPh); });
  document.querySelectorAll('[data-i18n-title]').forEach(el => { el.title = t(el.dataset.i18nTitle); });
  $('#lang').textContent = lang === 'ru' ? 'EN' : 'RU';
}

// ---------- small helpers ----------

const $ = s => document.querySelector(s);
function load(k) { try { return localStorage.getItem('qsolog.' + k); } catch { return null; } }
function save(k, v) { try { localStorage.setItem('qsolog.' + k, v); } catch { /* private mode: kept for this visit only */ } }
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pad = n => String(n).padStart(2, '0');

let toastTimer;
function toast(text, action, onAction) {
  const el = $('#toast');
  el.innerHTML = '';
  el.append(document.createTextNode(text));
  if (action) {
    const b = document.createElement('button');
    b.textContent = action;
    b.onclick = () => { el.hidden = true; onAction(); };
    el.append(b);
  }
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, action ? 6000 : 3000);
}

// ---------- bands, modes, reports ----------

const BANDS = [
  ['2200m', 0.1357, 0.1378], ['630m', 0.472, 0.479], ['160m', 1.8, 2.0], ['80m', 3.5, 4.0], ['60m', 5.25, 5.45],
  ['40m', 7.0, 7.3], ['30m', 10.1, 10.15], ['20m', 14.0, 14.35], ['17m', 18.068, 18.168], ['15m', 21.0, 21.45],
  ['12m', 24.89, 24.99], ['10m', 28.0, 29.7], ['6m', 50, 54], ['4m', 70, 70.5], ['2m', 144, 148], ['70cm', 430, 440], ['23cm', 1240, 1300],
];
const SHOWN_BANDS = ['160m', '80m', '40m', '30m', '20m', '17m', '15m', '12m', '10m', '6m', '2m', '70cm'];
const MODES = ['SSB', 'CW', 'FT8', 'FT4', 'RTTY', 'PSK31', 'AM', 'FM', 'DIGI', 'JT65', 'JS8', 'Q65', 'OLIVIA', 'SSTV', 'C4FM', 'DMR', 'DSTAR'];
const SHOWN_MODES = ['SSB', 'CW', 'FT8', 'FT4', 'RTTY', 'PSK31', 'AM', 'FM'];
// Modes written to ADIF as MODE + SUBMODE.
const SUBMODES = { FT4: 'MFSK', JS8: 'MFSK', Q65: 'MFSK', PSK31: 'PSK', C4FM: 'DIGITALVOICE', DMR: 'DIGITALVOICE', DSTAR: 'DIGITALVOICE' };

function bandFor(mhz) { const b = BANDS.find(([, lo, hi]) => mhz >= lo && mhz <= hi); return b ? b[0] : null; }
/** "14195" is taken as kHz (no band at 14195 MHz), "1296.2" stays MHz. */
function freqMhz(typed) {
  const v = parseFloat(String(typed).trim().replace(',', '.'));
  if (!isFinite(v)) return null;
  return v >= 1000 && !bandFor(v) && bandFor(v / 1000) ? v / 1000 : v;
}
function defaultRst(mode) {
  if (['CW', 'RTTY', 'PSK31', 'OLIVIA'].includes(mode)) return '599';
  if (['FT8', 'FT4', 'DIGI', 'JT65', 'JS8', 'Q65'].includes(mode)) return '-10';
  return '59';
}

// ---------- countries: cty.dat ----------

const cty = { exact: new Map(), prefixes: new Map(), ready: false };
async function loadCty() {
  try {
    const text = await (await fetch('cty.dat')).text();
    for (const rec of text.split(';')) {
      const p = rec.split(':');
      if (p.length < 9) continue;
      const f = p.map(s => s.trim());
      const e = { name: f[0], cq: +f[1], itu: +f[2], cont: f[3], lat: +f[4], lon: -f[5], prefix: f[7].replace('*', '') };
      for (let a of p.slice(8).join(':').split(',')) {
        a = a.trim();
        if (!a) continue;
        const cq = /\((\d+)\)/.exec(a), itu = /\[(\d+)\]/.exec(a);
        a = a.replace(/\(\d+\)|\[\d+\]|<[^>]*>|\{[^}]*\}|~[^~]*~/g, '').trim();
        const rule = { e, cq: cq ? +cq[1] : e.cq, itu: itu ? +itu[1] : e.itu };
        if (a.startsWith('=')) cty.exact.set(a.slice(1), rule); else if (!cty.prefixes.has(a)) cty.prefixes.set(a, rule);
      }
    }
    cty.ready = true;
  } catch { /* offline before the first visit cached it: no countries */ }
}
/** "DL/R0DEMO" → DL, "R0DEMO/P" → R0DEMO, "R0DEMO/9" → R9DEMO: the part that decides the country. */
function effectiveCall(call) {
  const parts = call.toUpperCase().trim().split('/').filter(Boolean);
  if (parts.length < 2) return parts[0] || '';
  const skip = new Set(['P', 'M', 'MM', 'AM', 'QRP', 'A', 'LH']);
  const m = parts.filter(x => !skip.has(x));
  if (m.length === 0) return parts[0];
  if (m.length === 1) return m[0];
  const [a, b] = m;
  if (b.length === 1 && /\d/.test(b)) return a.replace(/\d/, b);
  return a.length <= b.length ? a : b;
}
function country(call) {
  if (!cty.ready || !call) return null;
  const raw = call.toUpperCase().trim();
  if (cty.exact.has(raw)) return cty.exact.get(raw);
  const c = effectiveCall(raw);
  if (cty.exact.has(c)) return cty.exact.get(c);
  for (let n = c.length; n > 0; n--) { const r = cty.prefixes.get(c.slice(0, n)); if (r) return r; }
  return null;
}

// ---------- locators ----------

function isLocator(s) { return /^[A-R]{2}\d{2}([A-X]{2}(\d{2})?)?$/i.test((s || '').trim()); }
function locToLatLon(s) {
  s = (s || '').trim().toUpperCase();
  if (!isLocator(s)) return null;
  let lon = (s.charCodeAt(0) - 65) * 20 - 180, lat = (s.charCodeAt(1) - 65) * 10 - 90;
  lon += +s[2] * 2; lat += +s[3];
  let w = 2, h = 1;
  if (s.length >= 6) { w = 5 / 60; h = 2.5 / 60; lon += (s.charCodeAt(4) - 65) * w; lat += (s.charCodeAt(5) - 65) * h; }
  if (s.length >= 8) { w /= 10; h /= 10; lon += +s[6] * w; lat += +s[7] * h; }
  return { lat: lat + h / 2, lon: lon + w / 2 };
}
function distBearing(a, b) {
  const r = Math.PI / 180, la1 = a.lat * r, la2 = b.lat * r, dl = (b.lon - a.lon) * r;
  const d = Math.acos(Math.min(1, Math.max(-1, Math.sin(la1) * Math.sin(la2) + Math.cos(la1) * Math.cos(la2) * Math.cos(dl)))) * 6371;
  const br = (Math.atan2(Math.sin(dl) * Math.cos(la2), Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dl)) / r + 360) % 360;
  return { km: Math.round(d), deg: Math.round(br) };
}

// ---------- storage: IndexedDB ----------

let db;
let qsos = [];
function openDb() {
  return new Promise((ok, fail) => {
    const r = indexedDB.open('qsolog', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('qso', { keyPath: 'id', autoIncrement: true });
    r.onsuccess = () => ok(r.result);
    r.onerror = () => fail(r.error);
  });
}
function tx(mode, fn) {
  return new Promise((ok, fail) => {
    const x = db.transaction('qso', mode);
    const out = fn(x.objectStore('qso'));
    x.oncomplete = () => ok(out && out.result !== undefined ? out.result : out);
    x.onerror = () => fail(x.error);
  });
}
async function loadAll() { qsos = await tx('readonly', s => s.getAll()); }
async function put(q) { const id = await tx('readwrite', s => s.put(q)); q.id = id; return q; }
async function putMany(list) { await tx('readwrite', s => { for (const q of list) s.put(q); }); }
async function del(id) { await tx('readwrite', s => s.delete(id)); }
async function clearAll() { await tx('readwrite', s => s.clear()); }

// ---------- settings ----------

const settings = {
  get call() { return load('myCall') || ''; }, set call(v) { save('myCall', v); },
  get loc() { return load('myLoc') || ''; }, set loc(v) { save('myLoc', v); },
  get pwr() { return load('power') || ''; }, set pwr(v) { save('power', v); },
  get theme() { return load('theme') || ''; }, set theme(v) { save('theme', v); },
};
function applyTheme() {
  const th = settings.theme;
  if (th) document.documentElement.dataset.theme = th; else delete document.documentElement.dataset.theme;
}

// ---------- the log ----------

function utcParts(ms) {
  const d = new Date(ms);
  return { date: `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`, time: `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}` };
}
function shownDate(ms) { const p = utcParts(ms); return `${p.date.slice(8)}.${p.date.slice(5, 7)}.${p.date.slice(2, 4)} ${p.time}`; }
const byTime = (a, b) => b.time - a.time || b.id - a.id;

function fillFilters() {
  const fb = $('#fband'), fm = $('#fmode'), sb = fb.value, sm = fm.value;
  const bands = [...new Set([...SHOWN_BANDS.filter(b => qsos.some(q => q.band === b)), ...qsos.map(q => q.band).filter(Boolean)])];
  const modes = [...new Set(qsos.map(q => q.mode).filter(Boolean))];
  fb.innerHTML = `<option value="">${esc(t('Все диапазоны'))}</option>` + BANDS.map(b => b[0]).filter(b => bands.includes(b)).map(b => `<option>${b}</option>`).join('');
  fm.innerHTML = `<option value="">${esc(t('Все виды'))}</option>` + modes.sort().map(m => `<option>${esc(m)}</option>`).join('');
  fb.value = sb; fm.value = sm;
}

function renderList() {
  const qtext = $('#search').value.trim().toUpperCase(), fb = $('#fband').value, fm = $('#fmode').value;
  const shown = qsos.filter(q => (!fb || q.band === fb) && (!fm || q.mode === fm) &&
    (!qtext || [q.call, q.name, q.qth, q.comment, q.locator].some(v => (v || '').toUpperCase().includes(qtext)))).sort(byTime);
  $('#count').textContent = shown.length === qsos.length ? t('Связей: %s', qsos.length) : t('Показано %s из %s', shown.length, qsos.length);
  $('#empty').hidden = qsos.length > 0;
  $('#count').hidden = qsos.length === 0;
  const list = $('#list');
  list.hidden = qsos.length === 0;
  // A long log: the newest 500 rows; the search finds the rest.
  list.innerHTML = shown.slice(0, 500).map(q => {
    const who = [q.name, q.qth, q.country].filter(Boolean).join(' · ');
    return `<li tabindex="0" data-id="${q.id}"><span class="when">${shownDate(q.time)}</span><span class="call">${esc(q.call)}</span>` +
      `<span class="bm"><b>${esc(q.band || '')}</b> ${esc(q.mode || '')}</span><span class="who">${esc(who)}</span></li>`;
  }).join('');
}

// ---------- the card ----------

let editing = null;       // the contact being edited, or null for a new one
let cardOriginal = '';    // the form's values when it opened, to ask before losing changes
let followClock = false;  // a new card keeps the time running until the user changes it
let clockTimer;
const form = () => ({
  call: $('#f-call').value.trim().toUpperCase(), date: $('#f-date').value, time: $('#f-time').value,
  freq: $('#f-freq').value.trim(), band: card.band, mode: card.mode,
  rsts: $('#f-rsts').value.trim(), rstr: $('#f-rstr').value.trim(), power: $('#f-pwr').value.trim(),
  name: $('#f-name').value.trim(), qth: $('#f-qth').value.trim(), locator: $('#f-loc').value.trim(),
  comment: $('#f-comment').value.trim(), qslSent: $('#f-qsls').checked, qslRcvd: $('#f-qslr').checked,
});
const card = { band: '', mode: '' };

function chips(el, list, current, onPick) {
  const all = current && !list.includes(current) ? [...list, current] : list;
  el.innerHTML = all.map(v => `<button type="button" role="radio" aria-checked="${v === current}" data-v="${esc(v)}">${esc(v)}</button>`).join('');
  el.onclick = e => { const b = e.target.closest('button'); if (b) onPick(b.dataset.v); };
}
function setBand(b) { card.band = b; chips($('#f-bands'), SHOWN_BANDS, b, setBand); updateDupe(); }
function setMode(m) {
  const old = defaultRst(card.mode);
  for (const id of ['#f-rsts', '#f-rstr']) { const el = $(id); if (!el.value || el.value === old) el.value = defaultRst(m); }
  card.mode = m; chips($('#f-modes'), SHOWN_MODES, m, setMode); updateDupe();
}

function openCard(q) {
  editing = q || null;
  const now = Date.now(), last = [...qsos].sort(byTime)[0];
  const src = q || {
    call: '', time: now, band: rig.band || last?.band || '20m', mode: rig.mode || last?.mode || 'SSB',
    freq: rig.freq || (last?.freq ?? ''), power: settings.pwr,
  };
  const p = utcParts(src.time);
  $('#f-call').value = src.call || '';
  $('#f-date').value = p.date; $('#f-time').value = p.time;
  $('#f-freq').value = src.freq || '';
  card.mode = src.mode;
  $('#f-rsts').value = q ? q.rsts || '' : defaultRst(src.mode);
  $('#f-rstr').value = q ? q.rstr || '' : defaultRst(src.mode);
  $('#f-pwr').value = src.power || '';
  $('#f-name').value = src.name || ''; $('#f-qth').value = src.qth || ''; $('#f-loc').value = src.locator || '';
  $('#f-comment').value = src.comment || '';
  $('#f-qsls').checked = !!src.qslSent; $('#f-qslr').checked = !!src.qslRcvd;
  setBand(src.band); setMode(src.mode);
  $('#f-delete').hidden = !q;
  followClock = !q;
  clearInterval(clockTimer);
  clockTimer = setInterval(() => {
    if (!followClock || !$('#card').open) return;
    const n = utcParts(Date.now()); $('#f-date').value = n.date; $('#f-time').value = n.time;
  }, 5000);
  updateInfo();
  cardOriginal = JSON.stringify(form());
  $('#card').showModal();
  $('#f-call').focus();
}
function closeCard(force) {
  if (!force && JSON.stringify(form()) !== cardOriginal && !confirm(t('Есть несохранённые изменения. Закрыть карточку?'))) return;
  clearInterval(clockTimer);
  $('#card').close();
}

function updateInfo() {
  const call = $('#f-call').value.trim().toUpperCase();
  const parts = [];
  const c = country(call);
  if (c && call.length >= 2) parts.push(`<b>${esc(c.e.name)}</b>`, `CQ ${c.cq}`, `ITU ${c.itu}`, c.e.cont);
  const me = locToLatLon(settings.loc), them = locToLatLon($('#f-loc').value) || (c && call.length >= 3 ? { lat: c.e.lat, lon: c.e.lon, rough: true } : null);
  if (me && them) { const d = distBearing(me, them); parts.push(`${them.rough ? '≈ ' : ''}${d.km} ${t('км')} · ${d.deg}°`); }
  $('#f-info').innerHTML = parts.join(' · ');
  updateDupe();
}
function updateDupe() {
  const call = $('#f-call').value.trim().toUpperCase(), el = $('#f-dupe');
  const before = call.length >= 3 ? qsos.filter(q => q.call === call && q.id !== editing?.id).sort(byTime) : [];
  if (!before.length) { el.hidden = true; return; }
  const l = before[0];
  const dupe = before.some(q => q.band === card.band && q.mode === card.mode);
  el.textContent = (dupe ? t('Повтор: этот диапазон и вид уже были') + '. ' : '') + t('Уже были связи: %s, последняя %s (%s %s)', before.length, shownDate(l.time), l.band, l.mode);
  el.hidden = false;
}

/** A worked station's name, QTH and locator fill the card as soon as its callsign is typed. */
function fillFromLog() {
  const call = $('#f-call').value.trim().toUpperCase();
  if (editing || call.length < 3) return;
  const l = qsos.filter(q => q.call === call).sort(byTime)[0];
  if (!l) return;
  for (const [id, k] of [['#f-name', 'name'], ['#f-qth', 'qth'], ['#f-loc', 'locator']]) if (!$(id).value && l[k]) $(id).value = l[k];
}

async function saveCard() {
  const f = form();
  if (!f.call) { toast(t('Введите позывной')); $('#f-call').focus(); return; }
  const time = Date.parse(`${f.date}T${f.time}:00Z`);
  const mhz = freqMhz(f.freq);
  const c = country(f.call);
  const q = {
    ...(editing || {}), call: f.call, time: isFinite(time) ? time : Date.now(),
    band: (mhz && bandFor(mhz)) || f.band, mode: f.mode, freq: mhz ? String(+mhz.toFixed(6)) : f.freq,
    rsts: f.rsts, rstr: f.rstr, power: f.power, name: f.name, qth: f.qth, locator: f.locator.toUpperCase(),
    comment: f.comment, qslSent: f.qslSent, qslRcvd: f.qslRcvd,
    country: c ? c.e.name : (editing?.country || ''), cqz: c ? c.cq : editing?.cqz, ituz: c ? c.itu : editing?.ituz,
    myCall: editing?.myCall ?? settings.call, myLoc: editing?.myLoc ?? settings.loc,
    updated: Date.now(),
  };
  if (!editing) { q.created = Date.now(); q.uid = crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random(); }
  await put(q);
  if (editing) Object.assign(editing, q); else qsos.push(q);
  if (f.power) settings.pwr = f.power;
  closeCard(true);
  fillFilters(); renderList(); renderStats();
  toast(editing ? t('Изменения сохранены') : t('Связь записана: %s', q.call));
}

async function deleteCard() {
  const q = editing;
  if (!q) return;
  await del(q.id);
  qsos = qsos.filter(x => x.id !== q.id);
  closeCard(true);
  fillFilters(); renderList(); renderStats();
  toast(t('Связь удалена'), t('Вернуть'), async () => { await put(q); qsos.push(q); fillFilters(); renderList(); renderStats(); });
}

// ---------- ADIF and CSV ----------

const adifField = (k, v) => { v = String(v ?? ''); return v ? `<${k}:${new TextEncoder().encode(v).length}>${v} ` : ''; };
function toAdif(list) {
  const head = `QSO-LOG Web ${VERSION}\n` + adifField('ADIF_VER', '3.1.4') + adifField('PROGRAMID', 'QSO-LOG') + adifField('PROGRAMVERSION', VERSION) + '<EOH>\n';
  return head + list.slice().sort((a, b) => a.time - b.time).map(q => {
    const p = utcParts(q.time), sub = SUBMODES[q.mode];
    return adifField('CALL', q.call) + adifField('QSO_DATE', p.date.replace(/-/g, '')) + adifField('TIME_ON', p.time.replace(':', '') + '00') +
      adifField('BAND', q.band) + adifField('MODE', sub || q.mode) + (sub ? adifField('SUBMODE', q.mode) : '') + adifField('FREQ', q.freq) +
      adifField('RST_SENT', q.rsts) + adifField('RST_RCVD', q.rstr) + adifField('TX_PWR', q.power) + adifField('NAME', q.name) +
      adifField('QTH', q.qth) + adifField('GRIDSQUARE', q.locator) + adifField('COUNTRY', q.country) + adifField('CQZ', q.cqz) +
      adifField('ITUZ', q.ituz) + adifField('COMMENT', q.comment) + adifField('QSL_SENT', q.qslSent ? 'Y' : '') +
      adifField('QSL_RCVD', q.qslRcvd ? 'Y' : '') + adifField('STATION_CALLSIGN', q.myCall) + adifField('MY_GRIDSQUARE', q.myLoc) +
      adifField('APP_QSOLOG_UID', q.uid) + '<EOR>\n';
  }).join('');
}
/** ADIF records as maps of upper-case field names. Lengths count bytes in UTF-8 files: the text is read as bytes. */
function parseAdif(bytes) {
  const utf8 = new TextDecoder('utf-8', { fatal: false });
  let text;
  try { text = new TextDecoder('utf-8', { fatal: true }).decode(bytes); } catch { text = null; }
  // Not UTF-8: Windows-1251, as Russian loggers write.
  const dec = text === null ? new TextDecoder('windows-1251') : utf8;
  const out = [];
  let rec = {}, i = 0;
  const s = bytes;
  const eoh = indexOfCI(s, '<EOH>');
  if (eoh >= 0) i = eoh + 5;
  while (i < s.length) {
    if (s[i] !== 60) { i++; continue; } // '<'
    const close = s.indexOf(62, i); // '>'
    if (close < 0) break;
    const tag = dec.decode(s.subarray(i + 1, close));
    const [name, len] = tag.split(':');
    const n = name.trim().toUpperCase();
    if (n === 'EOR') { if (Object.keys(rec).length) out.push(rec); rec = {}; i = close + 1; continue; }
    const l = parseInt(len, 10);
    if (!isFinite(l)) { i = close + 1; continue; }
    rec[n] = dec.decode(s.subarray(close + 1, close + 1 + l)).trim();
    i = close + 1 + l;
  }
  return out;
}
function indexOfCI(bytes, word) {
  const w = [...word].map(c => c.charCodeAt(0));
  outer: for (let i = 0; i + w.length <= bytes.length; i++) {
    for (let k = 0; k < w.length; k++) { const b = bytes[i + k]; if (b !== w[k] && b !== w[k] + 32) continue outer; }
    return i;
  }
  return -1;
}
function fromAdif(r) {
  const d = r.QSO_DATE || '', tm = (r.TIME_ON || '0000').padEnd(6, '0');
  const time = Date.UTC(+d.slice(0, 4), +d.slice(4, 6) - 1, +d.slice(6, 8), +tm.slice(0, 2), +tm.slice(2, 4), +tm.slice(4, 6));
  const mhz = freqMhz(r.FREQ || '');
  const mode = (r.SUBMODE && MODES.includes(r.SUBMODE.toUpperCase()) ? r.SUBMODE : r.MODE || '').toUpperCase();
  return {
    call: (r.CALL || '').toUpperCase(), time, band: (r.BAND || (mhz && bandFor(mhz)) || '').toLowerCase(),
    mode: mode === 'USB' || mode === 'LSB' ? 'SSB' : mode, freq: r.FREQ || '', rsts: r.RST_SENT || '', rstr: r.RST_RCVD || '',
    power: r.TX_PWR || '', name: r.NAME || '', qth: r.QTH || '', locator: (r.GRIDSQUARE || '').toUpperCase(), country: r.COUNTRY || '',
    cqz: r.CQZ ? +r.CQZ : undefined, ituz: r.ITUZ ? +r.ITUZ : undefined, comment: r.COMMENT || r.NOTES || '',
    qslSent: (r.QSL_SENT || '').toUpperCase() === 'Y', qslRcvd: (r.QSL_RCVD || '').toUpperCase() === 'Y',
    myCall: r.STATION_CALLSIGN || r.OPERATOR || '', myLoc: r.MY_GRIDSQUARE || '', uid: r.APP_QSOLOG_UID || undefined,
    created: Date.now(), updated: Date.now(),
  };
}
async function importAdif(file) {
  const recs = parseAdif(new Uint8Array(await file.arrayBuffer()));
  if (!recs.length) { toast(t('В файле нет связей ADIF')); return; }
  const key = q => `${q.call}|${Math.round(q.time / 60000)}|${q.band}|${q.mode}`;
  const have = new Set(qsos.map(key));
  const fresh = [];
  for (const r of recs) {
    const q = fromAdif(r);
    if (!q.call || !isFinite(q.time) || have.has(key(q))) continue;
    if (!q.uid) q.uid = crypto.randomUUID ? crypto.randomUUID() : String(Math.random());
    have.add(key(q)); fresh.push(q);
  }
  await putMany(fresh);
  await loadAll();
  fillFilters(); renderList(); renderStats();
  toast(t('Импортировано связей: %s, пропущено повторов: %s', fresh.length, recs.length - fresh.length));
}
/** "UA3DEMO_2026-11-15_164059.adi": the export's local date and time, so files sort by name in the order made. */
function fileName(ext) {
  const d = new Date(), call = (settings.call || 'log').replace(/[\/\\]/g, '-');
  return `${call}_${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}.${ext}`;
}
function download(name, text, type) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([text], { type }));
  a.download = name;
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
}
function toCsv(list) {
  const cols = ['Дата', 'Время UTC', 'Позывной', 'Диапазон', 'Вид', 'Частота', 'RST отпр.', 'RST прин.', 'Имя', 'QTH', 'Локатор', 'Страна', 'Комментарий'];
  const cell = v => { v = String(v ?? ''); return /[;"\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v; };
  return '﻿' + cols.map(c => cell(t(c))).join(';') + '\r\n' + list.slice().sort((a, b) => a.time - b.time).map(q => {
    const p = utcParts(q.time);
    return [p.date, p.time, q.call, q.band, q.mode, q.freq, q.rsts, q.rstr, q.name, q.qth, q.locator, q.country, q.comment].map(cell).join(';');
  }).join('\r\n') + '\r\n';
}

// ---------- statistics ----------

function renderStats() {
  const el = $('#stats');
  if (!qsos.length) { el.innerHTML = `<p class="empty muted">${esc(t('Журнал пуст'))}</p>`; return; }
  const today = utcParts(Date.now()).date;
  const calls = new Set(qsos.map(q => q.call));
  const dxcc = new Set(qsos.map(q => q.country).filter(Boolean));
  const card = (n, l) => `<div class="stat"><div class="n">${n}</div><div class="l">${esc(l)}</div></div>`;
  let html = `<div class="stat-cards">${card(qsos.length, t('Всего связей'))}${card(calls.size, t('Позывных'))}${card(dxcc.size, t('Стран DXCC'))}` +
    `${card(qsos.filter(q => utcParts(q.time).date === today).length, t('Сегодня (UTC)'))}</div>`;
  const bands = BANDS.map(b => b[0]).filter(b => qsos.some(q => q.band === b));
  const modes = [...new Set(qsos.map(q => q.mode).filter(Boolean))].sort((a, b) => qsos.filter(q => q.mode === b).length - qsos.filter(q => q.mode === a).length);
  html += `<h2>${esc(t('Диапазоны и виды'))}</h2><div class="table-wrap"><table><thead><tr><th></th>${modes.map(m => `<th>${esc(m)}</th>`).join('')}<th>${esc(t('Всего'))}</th></tr></thead><tbody>`;
  for (const b of bands) {
    const row = qsos.filter(q => q.band === b);
    html += `<tr><td>${b}</td>${modes.map(m => `<td>${row.filter(q => q.mode === m).length || ''}</td>`).join('')}<td><b>${row.length}</b></td></tr>`;
  }
  html += '</tbody></table></div>';
  const months = {};
  for (const q of qsos) { const m = utcParts(q.time).date.slice(0, 7); months[m] = (months[m] || 0) + 1; }
  const ms = Object.keys(months).sort().slice(-12), max = Math.max(...ms.map(m => months[m]));
  html += `<h2>${esc(t('Связи по месяцам'))}</h2><div class="table-wrap"><table><tbody>` + ms.map(m =>
    `<tr><td>${m}</td><td style="width:100%;text-align:left"><span style="display:inline-block;height:10px;border-radius:4px;background:var(--accent);width:${Math.max(2, months[m] / max * 100)}%"></span></td><td>${months[m]}</td></tr>`).join('') + '</tbody></table></div>';
  const top = Object.entries(qsos.reduce((a, q) => (a[q.call] = (a[q.call] || 0) + 1, a), {})).sort((a, b) => b[1] - a[1]).slice(0, 10);
  html += `<h2>${esc(t('Больше всего связей'))}</h2><div class="table-wrap"><table><tbody>` + top.map(([c, n]) => `<tr><td>${esc(c)}</td><td>${n}</td></tr>`).join('') + '</tbody></table></div>';
  el.innerHTML = html;
}

// ---------- CAT over Web Serial ----------

const rig = { freq: '', band: '', mode: '', port: null, reader: null, timer: null, buf: [], lastAnswer: 0 };
const KENWOOD_MODES = { 1: 'LSB', 2: 'USB', 3: 'CW', 4: 'FM', 5: 'AM', 6: 'RTTY', 7: 'CW', 9: 'RTTY' };
const YAESU_MODES = { 1: 'LSB', 2: 'USB', 3: 'CW', 4: 'FM', 5: 'AM', 6: 'RTTY', 7: 'CW', 8: 'DATA', 9: 'RTTY', A: 'DATA', B: 'FM', C: 'DATA', D: 'AM', E: 'DATA' };
const ICOM_MODES = { 0: 'LSB', 1: 'USB', 2: 'AM', 3: 'CW', 4: 'RTTY', 5: 'FM', 7: 'CW', 8: 'RTTY' };
/** The radio's mode as the log's: USB/LSB → SSB, data modes keep a digital mode the card has, else FT8. */
function logMode(radio, data) {
  if (data) return ['FT8', 'FT4', 'PSK31', 'JS8', 'Q65', 'DIGI'].includes(card.mode || rig.mode) ? (card.mode || rig.mode) : 'FT8';
  return radio === 'USB' || radio === 'LSB' ? 'SSB' : radio;
}
function rigShown() {
  const b = $('#trx');
  b.hidden = !rig.port;
  if (!rig.port) return;
  const mhz = parseFloat(rig.freq);
  const silent = Date.now() - rig.lastAnswer > 4000;
  b.textContent = silent ? 'TRX —' : `TRX ${isFinite(mhz) ? (mhz * 1000).toFixed(1).replace(/\B(?=(\d{3})+(?!\d))/g, ' ') : '—'} ${rig.mode}`;
}
function rigUpdate(hz, mode) {
  if (hz) { rig.freq = String(+(hz / 1e6).toFixed(6)); rig.band = bandFor(hz / 1e6) || rig.band; }
  if (mode) rig.mode = mode;
  rig.lastAnswer = Date.now();
  rigShown();
  // An open new card follows the radio while its fields were not changed by hand.
  if ($('#card').open && !editing) {
    const fe = $('#f-freq');
    if (hz && document.activeElement !== fe) { fe.value = rig.freq; if (rig.band && rig.band !== card.band) setBand(rig.band); }
    if (mode && mode !== card.mode && MODES.includes(mode)) setMode(mode);
  }
}
async function catConnect() {
  if (rig.port) { await catClose(); $('#cat-status').textContent = t('Отключено'); $('#cat-connect').textContent = t('Подключить трансивер'); return; }
  const proto = $('#cat-proto').value, baud = +$('#cat-baud').value, civ = parseInt($('#cat-civ').value, 16) || 0x94;
  save('cat', JSON.stringify({ proto, baud, civ: $('#cat-civ').value }));
  try {
    const port = await navigator.serial.requestPort();
    $('#cat-status').textContent = t('Подключение…');
    await port.open({ baudRate: baud, dataBits: 8, stopBits: proto === 'yaesu' ? 1 : 1, parity: 'none', flowControl: 'none' });
    rig.port = port;
    $('#cat-connect').textContent = t('Отключить');
    readLoop(proto);
    const writer = port.writable.getWriter();
    const send = bytes => writer.write(bytes);
    const ascii = s => new TextEncoder().encode(s);
    rig.timer = setInterval(() => {
      if (proto === 'icom') {
        send(new Uint8Array([0xFE, 0xFE, civ, 0xE0, 0x03, 0xFD]));
        setTimeout(() => send(new Uint8Array([0xFE, 0xFE, civ, 0xE0, 0x04, 0xFD])), 150);
      } else send(ascii('FA;MD' + (proto === 'yaesu' ? '0' : '') + ';' + (proto === 'kenwood' ? 'DA;' : '')));
      rigShown();
      $('#cat-status').textContent = Date.now() - rig.lastAnswer > 4000 ? t('Трансивер не отвечает') : t('Подключено: %s', $('#cat-proto').selectedOptions[0].text);
    }, 1000);
    rig.writer = writer;
  } catch (e) {
    if (e && e.name !== 'NotFoundError') $('#cat-status').textContent = t('Ошибка порта: %s', e.message || e);
    await catClose();
  }
}
let kenwoodData = false;
async function readLoop(proto) {
  const port = rig.port;
  try {
    rig.reader = port.readable.getReader();
    for (;;) {
      const { value, done } = await rig.reader.read();
      if (done) break;
      rig.buf.push(...value);
      if (proto === 'icom') parseCiv(); else parseText(proto);
    }
  } catch { /* the port went away */ }
}
function parseText(proto) {
  let i;
  while ((i = rig.buf.indexOf(59)) >= 0) { // ';'
    const msg = String.fromCharCode(...rig.buf.splice(0, i + 1)).trim();
    if (msg.startsWith('FA')) rigUpdate(parseInt(msg.slice(2), 10), null);
    else if (msg.startsWith('MD')) {
      const c = msg.slice(proto === 'yaesu' ? 3 : 2, proto === 'yaesu' ? 4 : 3);
      const radio = (proto === 'yaesu' ? YAESU_MODES : KENWOOD_MODES)[c];
      if (radio) rigUpdate(null, logMode(radio === 'DATA' ? 'USB' : radio, radio === 'DATA' || (radio === 'USB' || radio === 'LSB') && kenwoodData));
    } else if (msg.startsWith('DA')) kenwoodData = msg[2] === '1';
  }
  if (rig.buf.length > 512) rig.buf = [];
}
function parseCiv() {
  for (;;) {
    const s = rig.buf.indexOf(0xFE);
    if (s < 0) { rig.buf = []; return; }
    const e = rig.buf.indexOf(0xFD, s);
    if (e < 0) { if (s > 0) rig.buf.splice(0, s); return; }
    const f = rig.buf.splice(0, e + 1).slice(s);
    // FE FE E0 <radio> <cmd> data FD: answers to us, or the radio's transceive broadcasts (to 00).
    if (f.length < 6 || f[1] !== 0xFE || (f[2] !== 0xE0 && f[2] !== 0x00)) continue;
    const cmd = f[4], d = f.slice(5, -1);
    if ((cmd === 0x03 || cmd === 0x00) && d.length >= 4) {
      let hz = 0, mul = 1;
      for (const b of d) { hz += ((b & 0x0F) + (b >> 4) * 10) * mul; mul *= 100; }
      rigUpdate(hz, null);
    } else if ((cmd === 0x04 || cmd === 0x01) && d.length >= 1) {
      const radio = ICOM_MODES[d[0]];
      if (radio) rigUpdate(null, logMode(radio, false));
    }
  }
}
async function catClose() {
  clearInterval(rig.timer);
  try { await rig.reader?.cancel(); } catch { }
  try { rig.reader?.releaseLock(); } catch { }
  try { rig.writer?.releaseLock(); } catch { }
  try { await rig.port?.close(); } catch { }
  Object.assign(rig, { port: null, reader: null, writer: null, timer: null, buf: [], freq: '', band: '', mode: '' });
  rigShown();
}

// ---------- start ----------

async function storageNote() {
  let used = '';
  try { const e = await navigator.storage.estimate(); used = `${(e.usage / 1024 / 1024).toFixed(1)} MB`; } catch { used = '—'; }
  let kept = false;
  try { kept = await navigator.storage.persisted() || await navigator.storage.persist(); } catch { }
  $('#storage-note').textContent = t('Хранится в браузере: %s. Браузер не удалит журнал сам: %s.', used, kept ? t('да') : t('не гарантировано — делайте экспорт ADIF'));
}

function showTab(name) {
  document.querySelectorAll('.tabs button').forEach(b => b.setAttribute('aria-selected', String(b.dataset.tab === name)));
  for (const n of ['log', 'stats', 'settings']) $('#tab-' + n).hidden = n !== name;
  if (name === 'settings') storageNote();
  save('tab', name);
}

async function main() {
  applyTheme();
  applyTexts();
  $('#version').textContent = t('Версия %s · работает без интернета после первого открытия', VERSION);
  db = await openDb();
  await loadAll();
  loadCty().then(() => { if ($('#card').open) updateInfo(); });
  fillFilters(); renderList(); renderStats();
  showTab(load('tab') || 'log');

  document.querySelector('.tabs').onclick = e => { const b = e.target.closest('button'); if (b) showTab(b.dataset.tab); };
  $('#search').oninput = renderList; $('#fband').onchange = renderList; $('#fmode').onchange = renderList;
  $('#list').onclick = e => { const li = e.target.closest('li'); if (li) openCard(qsos.find(q => q.id === +li.dataset.id)); };
  $('#list').onkeydown = e => { if (e.key === 'Enter') { const li = e.target.closest('li'); if (li) openCard(qsos.find(q => q.id === +li.dataset.id)); } };
  $('#add').onclick = () => openCard(null);
  $('#lang').onclick = () => { lang = lang === 'ru' ? 'en' : 'ru'; save('lang', lang); applyTexts(); fillFilters(); renderList(); renderStats(); $('#version').textContent = t('Версия %s · работает без интернета после первого открытия', VERSION); };

  // The card.
  $('#form').onsubmit = e => { e.preventDefault(); saveCard(); };
  $('#f-close').onclick = () => closeCard(false);
  $('#card').addEventListener('cancel', e => { e.preventDefault(); closeCard(false); });
  $('#f-delete').onclick = deleteCard;
  $('#f-call').addEventListener('input', e => {
    const el = e.target, pos = el.selectionStart;
    el.value = el.value.toUpperCase().replace(/[^A-Z0-9\/]/g, '');
    el.setSelectionRange(pos, pos);
    fillFromLog(); updateInfo();
  });
  $('#f-loc').addEventListener('input', updateInfo);
  for (const id of ['#f-date', '#f-time']) $(id).addEventListener('input', () => { followClock = false; });
  $('#f-freq').addEventListener('input', e => { const m = freqMhz(e.target.value); const b = m && bandFor(m); if (b && b !== card.band) setBand(b); });

  // Keys: Insert or Ctrl+N — a new card (outside the card and the text fields of the page).
  document.addEventListener('keydown', e => {
    if ($('#card').open) return;
    const typing = /INPUT|SELECT|TEXTAREA/.test(document.activeElement?.tagName || '');
    if (e.key === 'Insert' || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'n') || (!typing && e.key === '+')) { e.preventDefault(); openCard(null); }
    if (!typing && e.key === '/') { e.preventDefault(); showTab('log'); $('#search').focus(); }
  });

  // Settings.
  $('#s-call').value = settings.call; $('#s-loc').value = settings.loc; $('#s-pwr').value = settings.pwr; $('#s-theme').value = settings.theme;
  $('#s-call').oninput = e => { e.target.value = e.target.value.toUpperCase(); settings.call = e.target.value.trim(); };
  $('#s-loc').oninput = e => { settings.loc = e.target.value.trim(); };
  $('#s-pwr').oninput = e => { settings.pwr = e.target.value.trim(); };
  $('#s-theme').onchange = e => { settings.theme = e.target.value; applyTheme(); };
  $('#exp-adi').onclick = () => download(fileName('adi'), toAdif(qsos), 'text/plain');
  $('#exp-csv').onclick = () => download(fileName('csv'), toCsv(qsos), 'text/csv');
  $('#imp-adi').onchange = e => { const f = e.target.files[0]; if (f) importAdif(f); e.target.value = ''; };
  $('#wipe').onclick = async () => {
    if (!qsos.length || !confirm(t('Удалить все связи (%s)? Это нельзя отменить. Сначала сохраните журнал в ADIF.', qsos.length))) return;
    await clearAll(); qsos = []; fillFilters(); renderList(); renderStats(); toast(t('Журнал удалён'));
  };

  // CAT.
  if ('serial' in navigator) {
    try { const c = JSON.parse(load('cat') || '{}'); if (c.proto) $('#cat-proto').value = c.proto; if (c.baud) $('#cat-baud').value = c.baud; if (c.civ) $('#cat-civ').value = c.civ; } catch { }
    $('#cat-connect').onclick = catConnect;
    $('#trx').onclick = () => showTab('settings');
  } else { $('#cat-box').hidden = true; $('#cat-none').hidden = false; }

  // Install as an app.
  let installEvent;
  window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); installEvent = e; $('#install').hidden = false; });
  $('#install').onclick = async () => { if (!installEvent) return; installEvent.prompt(); await installEvent.userChoice; installEvent = null; $('#install').hidden = true; };

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').then(reg => {
      reg.addEventListener('updatefound', () => {
        const w = reg.installing;
        w?.addEventListener('statechange', () => { if (w.state === 'installed' && navigator.serviceWorker.controller) toast(t('Новая версия загружена — обновится при следующем открытии.')); });
      });
    }).catch(() => { });
  }
}

main();
