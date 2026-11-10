// QSO-LOG: синхронизация НАСТРОЕК программы через эту таблицу (журнал синхронизирует другой скрипт).
// 1. Создайте НОВУЮ Google Таблицу, например «QSO-LOG настройки» (не ту, где журнал).
// 2. «Расширения → Apps Script», удалите пример, вставьте этот код целиком, сохраните.
// 3. «Развернуть → Новое развёртывание → Веб-приложение»: «Запуск от имени: я», «У кого есть доступ: все».
// 4. Скопируйте адрес веб-приложения (…/exec) и вставьте его в QSO-LOG на каждом устройстве
//    (Настройки → Журнал связей → Синхронизация настроек).
//
// Лист «QSO-LOG настройки»: строка на каждую настройку (ключ и значение). Строки, начинающиеся с «_», служебные.
// Если включена передача учётных записей, здесь лежат и логины, пароли и ключи сервисов — не открывайте доступ
// к этой таблице другим людям. Адрес …/exec держите в секрете: по нему настройки может прочитать любой.

var SHEET = 'QSO-LOG настройки';
var VERSION = 1;

function doGet() {
  var sh = sheet_();
  var data = read_(sh);
  return json_({ ok: true, app: 'QSO-LOG-SETTINGS', version: VERSION, updated: data.meta._updated || '', device: data.meta._device || '', settings: data.settings });
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    var req = JSON.parse(e.postData.contents);
    var sh = sheet_();
    var now = new Date().toISOString();
    var rows = [['_updated', now], ['_device', String(req.device || '')]];
    var keys = Object.keys(req.settings || {}).sort();
    keys.forEach(function (k) { rows.push([k, String(req.settings[k])]); });
    sh.clearContents();
    sh.getRange(1, 1, 1, 2).setValues([['key', 'value']]).setFontWeight('bold');
    // Values as plain text: "0094", "+3" and dates stay as they were sent.
    sh.getRange(2, 1, rows.length, 2).setNumberFormat('@').setValues(rows);
    return json_({ ok: true, app: 'QSO-LOG-SETTINGS', updated: now, count: keys.length });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function sheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  return ss.getSheetByName(SHEET) || ss.insertSheet(SHEET);
}

function read_(sh) {
  var last = sh.getLastRow();
  var meta = {}, settings = {};
  if (last > 1) {
    sh.getRange(2, 1, last - 1, 2).getDisplayValues().forEach(function (r) {
      var k = String(r[0]);
      if (!k) return;
      if (k.charAt(0) === '_') meta[k] = r[1]; else settings[k] = r[1];
    });
  }
  return { meta: meta, settings: settings };
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
