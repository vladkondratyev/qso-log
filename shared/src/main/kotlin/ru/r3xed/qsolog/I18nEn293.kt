package ru.r3xed.qsolog

/** English texts of 2.9.3 (see [EN]): PTT timing, two CW sets, IOTA, transverters, duplicates, themes, the table log. */
internal val EN_2_9_3: Map<String, String> = mapOf(
    "PTT на время передачи" to "PTT while sending",
    "Порт PTT" to "PTT port",
    "Вторая линия порта ключа (%s)" to "The key port's second line (%s)",
    "Линия PTT" to "PTT line",
    "Опережение PTT: %s мс" to "PTT lead: %s ms",
    "Задержка отпускания PTT: %s мс" to "PTT tail: %s ms",
    "Опережение — сколько PTT включён до первого знака: трансиверу нужно время перейти на передачу (обычно 20–50 мс). Задержка отпускания — сколько PTT держится после последнего знака сообщения F1–F8; 0 — приём сразу. Если следующее сообщение нажато раньше, PTT не отпускается. После работы ключом с клавиатуры PTT держится 0,7 с, чтобы трансивер не переключался между буквами. PTT можно вывести на отдельный порт: CAT — через один COM-порт, ключ и PTT — через другой, или PTT — линией порта CAT (если трансивер не держит на ней RTS)." to
        "Lead is how long PTT is on before the first sign: the radio needs time to switch to transmit (usually 20–50 ms). Tail is how long PTT stays on after the last sign of an F1–F8 message; 0 — back to receive at once. If the next message is pressed sooner, PTT is not released. After keying from the keyboard PTT stays on for 0.7 s so the radio does not switch between letters. PTT can go to a port of its own: CAT on one COM port, the key and PTT on another, or PTT on a line of the CAT port (if the radio does not keep RTS on it).",
    "IOTA (остров)" to "IOTA (island)",
    "Системная клавиатура: имя, город по-русски" to "System keyboard: names and towns in any script",
    "Новая связь" to "New QSO",
    "Новый диапазон: на %s ещё не работали" to "New band: not worked on %s yet",
    "На %s уже работали, но другим видом" to "Worked on %s already, in another mode",
    "Повтор: %s %s уже есть в журнале" to "Dupe: %s %s is in the log already",
    "Трансвертеры" to "Transverters",
    "%s–%s МГц → %s МГц" to "%s–%s MHz → %s MHz",
    "Трансивер от, МГц" to "Radio from, MHz",
    "до, МГц" to "to, MHz",
    "В эфире от, МГц" to "On the air from, MHz",
    "Трансивер работает на ПЧ трансвертера (например, 28 МГц), а в журнал, на шкалу и в кластер идёт частота в эфире: 28,150 → 432,150 МГц, диапазон 70cm. Частота из журнала или со спота уходит на трансивер пересчитанной обратно. Укажите, какой участок трансивера и с какой частоты эфира начинается: 28–30 МГц → 432 МГц, 144–146 МГц → 1296 МГц. Выключите трансвертер переключателем, когда работаете на ПЧ-диапазоне напрямую. Если частоту читает и WSJT-X через сервер rigctld QSO-LOG, сдвиг трансвертера в WSJT-X не задавайте: он уже учтён." to
        "The radio works on the transverter's IF (28 MHz, say), and the log, the scale and the cluster get the frequency on the air: 28.150 → 432.150 MHz, the 70cm band. A frequency from the log or a spot goes to the radio converted back. Set the radio's range and the frequency on the air it starts at: 28–30 MHz → 432 MHz, 144–146 MHz → 1296 MHz. Switch the transverter off when you work the IF band directly. If WSJT-X reads the frequency through QSO-LOG's rigctld server, do not set a transverter offset in WSJT-X: it is already applied.",
    "Фон" to "Background",
    "Акцент: кнопки, выбранное" to "Accent: buttons, the chosen",
    "Фон групп и карточек" to "Group and card background",
    "Обводки и линии" to "Outlines and lines",
    "Второстепенный текст" to "Secondary text",
    "Фон полей ввода" to "Input field background",
    "Текст на акценте" to "Text on the accent",
    "Текст в группах" to "Text in groups",
    "Фон «уже работали»" to "\"Worked before\" background",
    "Обводка «уже работали», повтор на диапазоне" to "\"Worked before\" outline, band repeat",
    "Текст «уже работали»" to "\"Worked before\" text",
    "Успех, новый диапазон" to "Success, new band",
    "Ошибка, повтор" to "Error, dupe",
    "Контрастная обводка поля ввода" to "Contrast outline of the input field",
    "Основа: с какой темы начать" to "Base: the theme to start from",
    "Другая основа сбрасывает цвета к этой теме." to "Another base resets the colours to that theme.",
    "Скрыть дополнительные цвета" to "Hide the extra colours",
    "Дополнительно: ещё %s цветов" to "More: %s more colours",
    "Оттенок" to "Hue",
    "Насыщенность" to "Saturation",
    "Яркость" to "Brightness",
    "Код цвета" to "Colour code",
    "Контрастная обводка активного поля ввода" to "Contrast outline of the active input field",
    "Поле с курсором обводится толстой яркой рамкой — видно издалека и при плохом зрении. Цвет рамки — в «Своей» теме, «Дополнительно»." to
        "The field with the cursor gets a thick bright frame — seen from afar and with poor eyesight. Its colour is in the \"Custom\" theme, \"More\".",
    "стереть позывной (клавиша Ё): курсор — в позывной" to "wipe the callsign (the key left of 1): the cursor goes to the callsign",
    "Мощность (CATEGORY-POWER)" to "Power (CATEGORY-POWER)",
    "Не указывать" to "Leave out",
    "Помощь кластера (CATEGORY-ASSISTED)" to "Cluster help (CATEGORY-ASSISTED)",
    "Без помощи" to "Non-assisted",
    "С кластером" to "With the cluster",
    "Передатчики (CATEGORY-TRANSMITTER)" to "Transmitters (CATEGORY-TRANSMITTER)",
    "Операторы (OPERATORS)" to "Operators (OPERATORS)",
    "пусто — ваш позывной" to "empty — your callsign",
    "Имя и фамилия (NAME)" to "Name (NAME)",
    "Адрес (ADDRESS), до 6 строк" to "Address (ADDRESS), up to 6 lines",
    "Клуб (CLUB)" to "Club (CLUB)",
    "Мощность берётся из настроек станции, имя и адрес — из полей станции; всё введённое здесь запоминается для следующего отчёта. Пустые строки в файл не попадают." to
        "The power comes from the station settings, the name and address from the station fields; whatever you type here is kept for the next report. Empty lines stay out of the file.",
    "Журнал таблицей" to "Log as a table",
    "Вся история QSO — компактной таблицей на всё окно, как в классических логгерах: колонки выбираются кнопкой «Колонки», щелчок по заголовку сортирует, над таблицей — поиск, фильтры и группировка. Новое QSO открывается компактной карточкой поверх таблицы. Включается и в меню «Вид»." to
        "The whole QSO history as a compact table over the window, as in the classic loggers: pick the columns with \"Columns\", a click on a header sorts, above the table are the search, the filters and the grouping. A new QSO opens as a compact card over the table. Also in the View menu.",
    "Колонки" to "Columns",
    "Новое QSO" to "New QSO",
    "Группы: %s" to "Groups: %s",
    "Без групп" to "No groups",
    "Колонки таблицы" to "Table columns",
    "По умолчанию" to "Defaults",
    "RST отпр." to "RST s",
    "RST прин." to "RST r",
    "QSL отпр." to "QSL s",
    "QSL прин." to "QSL r",
    "№ отпр." to "Nr s",
    "№ прин." to "Nr r",
    "Своя тема" to "Custom",
    "По этому адресу не скрипт настроек QSO-LOG" to "This address is not the QSO-LOG settings script",
    "Это адрес скрипта журнала: для настроек нужен второй скрипт, из второй таблицы" to "This is the log script's address: the settings need a second script, in a second table",
    "Вставьте адрес веб-приложения второго скрипта (для настроек): https://script.google.com/macros/s/…/exec" to "Paste the web app address of the second (settings) script: https://script.google.com/macros/s/…/exec",
    "%s: получены настройки с другого устройства (%s). Перезапустите QSO-LOG, чтобы они вступили в силу." to "%s: settings received from another device (%s). Restart QSO-LOG for them to take effect.",
    "%s: настройки отправлены в таблицу (%s)" to "%s: settings sent to the table (%s)",
    "%s: настройки совпадают с таблицей" to "%s: settings match the table",
    "Настройки не синхронизированы в %s: %s" to "Settings not synced at %s: %s",
    "Синхронизация настроек программы" to "Program settings sync",
    "Синхронизировать настройки программы" to "Sync the program's settings",
    "Тема, диапазоны и виды, клавиши, макросы F1–F8, CAT и телеграф, станции, кластер, фильтры и прочие настройки — одинаковые на всех ваших устройствах. Не переносится то, что относится к самому устройству: COM-порты, пути к файлам, масштаб интерфейса. Журнал синхронизируется отдельно, скриптом выше: он остаётся как был." to
        "The theme, bands and modes, keys, F1–F8 macros, CAT and CW, stations, the cluster, filters and other settings — the same on all your devices. What belongs to the device itself stays: COM ports, file paths, the interface scale. The log syncs separately, with the script above, which stays as it was.",
    "Передавать и учётные записи внешних сервисов" to "Also sync the accounts of outside services",
    "Внимание: логины, пароли и ключи QRZ.ru, QRZ.com, HamQTH, QRZCQ, LoTW, eQSL, Club Log, DX-кластера и сетевого входа трансивера будут храниться в вашей Google Таблице в облаке Google, а не только на ваших устройствах. Любой, у кого есть доступ к этой таблице или адрес её скрипта, увидит их открытым текстом. Включайте, только если таблица — ваша личная и никому не открыта. Без этого переключателя учётные записи остаются только на устройстве, и на каждом их нужно ввести самому." to
        "Warning: the logins, passwords and keys of QRZ.ru, QRZ.com, HamQTH, QRZCQ, LoTW, eQSL, Club Log, the DX cluster and the radio's network login will be kept in your Google Table in Google's cloud, not only on your devices. Anyone with access to the table or its script address will see them in plain text. Turn it on only if the table is yours alone and shared with nobody. Without this switch the accounts stay on the device and are entered on each one by hand.",
    "Как получить второй адрес (токен) синхронизации" to "How to get the second sync address (token)",
    "Создайте НОВУЮ Google Таблицу, например «QSO-LOG настройки». Не используйте таблицу журнала: у неё уже есть свой скрипт, его не трогайте." to
        "Create a NEW Google Table, e.g. \"QSO-LOG settings\". Do not use the log's table: it has its own script — leave it alone.",
    "В новой таблице: «Расширения → Apps Script». Сотрите всё в редакторе и вставьте код второго скрипта — кнопка «Скопировать код скрипта настроек» ниже." to
        "In the new table: \"Extensions → Apps Script\". Clear the editor and paste the second script — the \"Copy the settings script\" button below.",
    "«Сохранить», затем «Начать развёртывание → Новое развёртывание», тип «Веб-приложение»: «Запуск от имени: я», «У кого есть доступ: все». Разрешите доступ своему аккаунту («Дополнительные настройки → Перейти»)." to
        "\"Save\", then \"Deploy → New deployment\", type \"Web app\": \"Execute as: me\", \"Who has access: anyone\". Allow access for your account (\"Advanced → Go to\").",
    "Скопируйте «URL веб-приложения» (…/exec) — это и есть токен синхронизации настроек — и вставьте в поле ниже. Он отличается от адреса скрипта журнала." to
        "Copy the \"Web app URL\" (…/exec) — that is the settings sync token — and paste it below. It differs from the log script's address.",
    "Введите этот же адрес на других устройствах и включите там синхронизацию настроек. Первое устройство отправит свои настройки, остальные их получат и попросят перезапуск." to
        "Enter the same address on the other devices and turn the settings sync on there. The first device sends its settings, the others receive them and ask for a restart.",
    "Скопировать код скрипта настроек" to "Copy the settings script",
    "Код скрипта настроек скопирован: вставьте его в «Расширения → Apps Script» новой таблицы" to "The settings script is copied: paste it into \"Extensions → Apps Script\" of the new table",
    "Адрес скрипта настроек (…/exec)" to "Settings script address (…/exec)",
    "Это адрес скрипта журнала: для настроек нужен второй скрипт в другой таблице" to "This is the log script's address: the settings need a second script in another table",
    "Синхронизировать настройки" to "Sync settings",
    "Настройки синхронизируются вместе с журналом (кнопка «Синхронизировать» и Ctrl+R) и при запуске программы. Если настройки менялись на двух устройствах, побеждает то, что позже записано в таблицу. Полученные настройки вступают в силу после перезапуска QSO-LOG." to
        "Settings sync together with the log (the \"Sync\" button and Ctrl+R) and when the program starts. If settings changed on two devices, the one written to the table later wins. Received settings take effect after QSO-LOG restarts.",
    "Поле позывного подсвечивается: красным — повтор на этом диапазоне и виде, оранжевым — диапазон тот же, вид другой, зелёным — новый диапазон. «Уже работали» — сеткой «диапазоны × CW / PH / DIGI»." to
        "The callsign field is coloured: red — a dupe on this band and mode, orange — this band in another mode, green — a new band. \"Worked before\" is a grid of bands × CW / PH / DIGI.",
    "«Журнал таблицей» (меню «Вид»): весь журнал компактной таблицей, свои колонки, сортировка по щелчку, группировка и фильтры; новое QSO — карточкой поверх таблицы." to
        "\"Log as a table\" (the View menu): the whole log as a compact table, your columns, sorting by a click, grouping and filters; a new QSO opens as a card over the table.",
    "Своя клавиатура работает и в поиске по журналу." to "The built-in keypad now works in the log search too.",
    "Телеграф: задержка отпускания и опережение PTT настраиваются (раньше PTT держался 0,7 с), PTT можно вывести на отдельный COM-порт." to
        "CW: the PTT tail and lead can be set (PTT used to stay on for 0.7 s), and PTT can go to a COM port of its own.",
    "Два набора макросов F1–F8: повседневные и для контеста, переключаются сами." to "Two F1–F8 macro sets: everyday and contest, switching by themselves.",
    "Трансвертеры (настройки CAT): 28 → 432, 144 → 1296 МГц — в журнал и на шкалу идёт частота в эфире." to
        "Transverters (CAT settings): 28 → 432, 144 → 1296 MHz — the log and the scale get the frequency on the air.",
    "«Своя тема»: любые цвета интерфейса; контрастная обводка активного поля ввода — в любой теме." to
        "\"Custom theme\": any interface colours; a contrast outline of the active input field — in any theme.",
    "Поле IOTA корреспондента, клавиша Ё стирает позывной, полный заголовок Cabrillo (POWER, OPERATORS, NAME, ADDRESS, EMAIL, CLUB…)." to
        "The station's IOTA field, the key left of 1 wipes the callsign, the full Cabrillo header (POWER, OPERATORS, NAME, ADDRESS, EMAIL, CLUB…).",
    "Синхронизация настроек программы через вторую Google Таблицу (по желанию — и учётные записи сервисов)." to
        "The program's settings sync through a second Google Table (optionally with the services' accounts too).",
    "Повседневные" to "Everyday",
    "Два набора F1–F8: «Контест» работает в режиме соревнований, «Повседневные» — в обычной карточке QSO. Переключаются сами." to
        "Two F1–F8 sets: \"Contest\" works in the contest mode, \"Everyday\" in the usual QSO card. They switch by themselves.",
)
