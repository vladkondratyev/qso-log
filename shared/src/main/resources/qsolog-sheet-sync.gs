// QSO-LOG: синхронизация журнала через эту таблицу.
// Вставьте код целиком в «Расширения → Apps Script», сохраните и разверните как веб-приложение
// («Запуск от имени: я», «У кого есть доступ: все»). Адрес веб-приложения (…/exec) введите в QSO-LOG
// на каждом устройстве: первая синхронизация объединит журналы, дальше нажимайте «Синхронизировать».
//
// Лист «QSO-LOG» скрипт создаёт сам: первая строка — названия полей и их описание.
// Чтобы удалить связь на всех устройствах, поставьте Y в столбце deleted (строку не удаляйте).

var SHEET = 'QSO-LOG';
var VERSION = 1;
var COLUMNS = [
  ['uid', 'GUID связи, не менять'],
  ['updated_utc', 'Когда изменена (UTC)'],
  ['synced_utc', 'Когда записана в таблицу (UTC)'],
  ['deleted', 'Y — удалена'],
  ['call', 'Позывной'],
  ['date_utc', 'Дата UTC, ГГГГ-ММ-ДД'],
  ['time_utc', 'Время UTC, ЧЧ:ММ:СС'],
  ['band', 'Диапазон'],
  ['mode', 'Вид связи'],
  ['freq_mhz', 'Частота, МГц'],
  ['rst_sent', 'RST передан'],
  ['rst_rcvd', 'RST принят'],
  ['name', 'Имя'],
  ['qth', 'QTH'],
  ['country', 'Страна'],
  ['locator', 'QTH-локатор'],
  ['lat', 'Широта'],
  ['lon', 'Долгота'],
  ['distance_km', 'Расстояние, км'],
  ['bearing', 'Азимут, °'],
  ['power', 'Мощность'],
  ['qsl_sent', 'QSL отправлена (Y)'],
  ['qsl_rcvd', 'QSL получена (Y)'],
  ['comment', 'Комментарий'],
  ['my_call', 'Мой позывной'],
  ['my_locator', 'Мой локатор'],
  ['created_utc', 'Когда создана (UTC)'],
  ['adif_extra', 'Остальные поля ADIF'],
];

function doGet() {
  var sh = sheet_();
  header_(sh);
  return json_({ ok: true, app: 'QSO-LOG', version: VERSION, rows: Math.max(sh.getLastRow() - 1, 0) });
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    var req = JSON.parse(e.postData.contents);
    var sh = sheet_();
    var head = header_(sh);
    var idx = {};
    head.forEach(function (k, i) { idx[k] = i; });
    var now = new Date().toISOString();
    var last = sh.getLastRow();
    var data = last > 1 ? sh.getRange(2, 1, last - 1, head.length).getDisplayValues() : [];
    var changed = {};

    // Rows typed in by hand: give them a GUID so the devices can take them.
    data.forEach(function (r, i) {
      if (!r[idx.uid] && r[idx.call]) {
        r[idx.uid] = Utilities.getUuid();
        if (!r[idx.updated_utc]) r[idx.updated_utc] = now;
        r[idx.synced_utc] = now;
        changed[i] = true;
      }
    });

    var byUid = {}, byKey = {};
    data.forEach(function (r, i) {
      if (r[idx.uid]) byUid[r[idx.uid]] = i;
      var k = key_(r[idx.call], r[idx.date_utc], r[idx.time_utc], r[idx.band], r[idx.mode]);
      if (k && r[idx.deleted] !== 'Y') byKey[k] = i;
    });

    var remap = {};
    var cols = req.columns || [];
    (req.rows || []).forEach(function (v) {
      var inc = {};
      cols.forEach(function (c, j) { inc[c] = v[j] == null ? '' : String(v[j]); });
      if (!inc.uid) return;
      var i = byUid[inc.uid];
      // The same contact already sent by another device under its own GUID (a log copied by ADIF): one row.
      if (i === undefined && inc.deleted !== 'Y') {
        var k = key_(inc.call, inc.date_utc, inc.time_utc, inc.band, inc.mode);
        if (k && byKey[k] !== undefined) {
          // The table's copy stays as it is; the device takes it together with the table's GUID.
          remap[inc.uid] = data[byKey[k]][idx.uid];
          return;
        }
      }
      if (i === undefined && inc.deleted === 'Y') return; // deleted before it ever reached the table
      if (i === undefined) {
        var row = head.map(function (c) { return inc[c] || ''; });
        row[idx.synced_utc] = now;
        data.push(row);
        var n = data.length - 1;
        byUid[inc.uid] = n;
        var nk = key_(inc.call, inc.date_utc, inc.time_utc, inc.band, inc.mode);
        if (nk && inc.deleted !== 'Y') byKey[nk] = n;
        changed[n] = true;
        return;
      }
      var r = data[i];
      if ((inc.updated_utc || '') > (r[idx.updated_utc] || '')) {
        // A deletion only marks the row: what was deleted stays readable in the table.
        head.forEach(function (c, j) {
          if (c === 'uid' || c === 'synced_utc' || !inc.hasOwnProperty(c)) return;
          if (inc.deleted === 'Y' && c !== 'deleted' && c !== 'updated_utc') return;
          r[j] = inc[c];
        });
        r[idx.synced_utc] = now;
        changed[i] = true;
      }
    });

    // Plain text everywhere: "001" stays "001", 7.074 does not turn into a date.
    var rows = Object.keys(changed).map(Number);
    if (rows.length > 20) {
      sh.getRange(2, 1, data.length, head.length).setNumberFormat('@').setValues(data);
    } else {
      rows.forEach(function (i) {
        sh.getRange(i + 2, 1, 1, head.length).setNumberFormat('@').setValues([data[i]]);
      });
    }

    var since = req.since || '';
    var out = data.filter(function (r) { return !since || (r[idx.synced_utc] || '') > since; });
    return json_({ ok: true, version: VERSION, now: now, columns: head, rows: out, remap: remap });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

// A cell changed by hand: the row counts as changed, so the devices take it at the next sync.
function onEdit(e) {
  var sh = e.range.getSheet();
  if (sh.getName() !== SHEET || e.range.getLastRow() < 2) return;
  var head = sh.getRange(1, 1, 1, sh.getLastColumn()).getDisplayValues()[0].map(function (h) { return String(h).split('\n')[0].trim(); });
  var u = head.indexOf('updated_utc') + 1, s = head.indexOf('synced_utc') + 1;
  if (!u || !s) return;
  var now = new Date().toISOString();
  var first = Math.max(e.range.getRow(), 2);
  var count = e.range.getLastRow() - first + 1;
  var stamp = [];
  for (var i = 0; i < count; i++) stamp.push([now]);
  sh.getRange(first, u, count, 1).setNumberFormat('@').setValues(stamp);
  sh.getRange(first, s, count, 1).setNumberFormat('@').setValues(stamp);
}

function sheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(SHEET);
  if (sh) return sh;
  // An empty first sheet becomes the log; otherwise a new sheet, the user's data is not touched.
  var first = ss.getSheets()[0];
  if (first.getLastRow() === 0 && first.getLastColumn() === 0) return first.setName(SHEET);
  return ss.insertSheet(SHEET);
}

// Column keys from the first row (the key is the first line of the cell); missing columns are added at the end.
function header_(sh) {
  var width = Math.max(sh.getLastColumn(), 1);
  var head = sh.getLastRow() > 0
    ? sh.getRange(1, 1, 1, width).getDisplayValues()[0].map(function (h) { return String(h).split('\n')[0].trim(); })
    : [];
  while (head.length && !head[head.length - 1]) head.pop();
  var add = COLUMNS.filter(function (c) { return head.indexOf(c[0]) < 0; });
  if (add.length) {
    var start = head.length + 1;
    sh.getRange(1, start, 1, add.length)
      .setNumberFormat('@')
      .setValues([add.map(function (c) { return c[0] + '\n' + c[1]; })])
      .setFontWeight('bold').setWrap(true).setVerticalAlignment('top');
    sh.setFrozenRows(1);
    add.forEach(function (c) { head.push(c[0]); });
  }
  return head;
}

function key_(call, date, time, band, mode) {
  if (!call || !date) return '';
  return [String(call).toUpperCase(), date, String(time || '').substring(0, 5), String(band || '').toLowerCase(), String(mode || '').toUpperCase()].join('|');
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
