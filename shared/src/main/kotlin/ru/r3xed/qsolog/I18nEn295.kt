package ru.r3xed.qsolog

/** English texts of 2.9.5 (see [EN]): SWL, the 50–150% scale, filling in the stations' data, bulk delete, the HAMLOG mark, the rig panel. */
internal val EN_2_9_5: Map<String, String> = mapOf(
    // The scale
    "От 50% до 150% с шагом 10%: меньше 100% — компактный вид (кнопки, поля и отступы меньше, текст уменьшается мягче, в окно помещается больше журнала и карточки), больше 100% — всё крупнее, и таблица, и карточка." to
        "From 50% to 150% in steps of 10%: below 100% a compact look (smaller buttons, fields and gaps, the text shrinks less, more of the log and the card fits the window), above 100% everything is larger, the table and the card too.",
    "От 50% до 150% с шагом 10%: меньше 100% — компактный вид (кнопки, поля и отступы меньше, текст уменьшается мягче, при 50–60% карточка связи раскладывается в две колонки), больше 100% — всё крупнее. С клавиатуры: Ctrl + и Ctrl − (Ctrl 0 — 100%)." to
        "From 50% to 150% in steps of 10%: below 100% a compact look (smaller buttons, fields and gaps, the text shrinks less, at 50–60% the QSO card takes two columns), above 100% everything is larger. From a keyboard: Ctrl + and Ctrl − (Ctrl 0 for 100%).",
    "Клавиши: ⌘+ и ⌘− (⌘0 — 100%), ⌘ или Ctrl с колесом мыши, меню «Вид»." to "Keys: ⌘+ and ⌘− (⌘0 for 100%), ⌘ or Ctrl with the mouse wheel, the View menu.",
    "Клавиши: Ctrl + и Ctrl − (Ctrl 0 — 100%), Ctrl с колесом мыши, меню «Вид»." to "Keys: Ctrl + and Ctrl − (Ctrl 0 for 100%), Ctrl with the mouse wheel, the View menu.",
    "Масштаб: %s" to "Scale: %s",
    // Delete and fill in
    "Удалить выбранные (Delete)" to "Delete selected (Delete)",
    "Удалить выбранные…" to "Delete selected…",
    "Удалить выбранные" to "Delete selected",
    "Дозаполнить данные корреспондентов" to "Fill in stations' data",
    "Дозаполнить данные корреспондентов (выбранные)" to "Fill in stations' data (selected)",
    "Укажите учётную запись справочника (QRZ.ru, QRZ.com, HamQTH) в настройках" to "Enter a callbook account (QRZ.ru, QRZ.com, HamQTH) in the settings",
    "У всех связей есть имя и QTH" to "All QSOs have a name and QTH",
    "Дозаполнено связей: %s из %s. Остановлено: %s" to "Filled in: %s of %s QSOs. Stopped: %s",
    "Дозаполнено связей: %s из %s" to "Filled in: %s of %s QSOs",
    "Дозаполнение остановлено" to "Filling in stopped",
    "Данные корреспондентов получены для связей: %s" to "Stations' data received for QSOs: %s",
    "Дозаполнение данных корреспондентов: %s из %s" to "Filling in stations' data: %s of %s",
    // HAMLOG mark
    "Полоска «выгружено в HAMLOG»" to "Stripe \"uploaded to HAMLOG\"",
    // Rig panel
    "вне любительских диапазонов" to "outside the amateur bands",
    "цифра: щелчок — выше, правой — ниже, колесо — тем же разрядом" to
        "a digit: click to go up, right-click to go down, the wheel by that digit",
    // CW
    "Точность ключа каждой передачи записывается в файл cw-timing.txt (опоздание переключения и время, которое заняла линия порта). Если манипуляция нечёткая, пришлите этот файл разработчику." to
        "How exactly each message was keyed goes into the file cw-timing.txt (how late the switching came and how long the port line took). If the keying is not crisp, send this file to the developer.",
    "Открыть файл точности ключа" to "Open the keying accuracy file",
    // SWL
    "Наблюдатель (SWL)" to "Listener (SWL)",
    "Номер наблюдателя (например R3A-1234)" to "Listener number (e.g. R3A-1234)",
    "Связи этой станции записываются как наблюдения: позывной — кого слышали, «Работал с» — с кем он работал (на месте «RST принят»), RST — ваш рапорт. В ADIF у них SWL = Y. В LoTW, eQSL, Club Log, QRZ.com и HAMLOG наблюдения не выгружаются, QSL-наклейки печатаются как SWL-рапорт." to
        "QSOs of this station are logged as reports: the callsign is the station heard, \"Worked\" whom it was working (in place of \"RST rcvd\"), RST is your report. In ADIF they have SWL = Y. Reports do not go to LoTW, eQSL, Club Log, QRZ.com and HAMLOG; QSL labels print as SWL reports.",
    "Слышал (позывной)" to "Heard (callsign)",
    "Мой RST" to "My RST",
    "Работал с" to "Worked",
    "→ Работал с" to "→ Worked",
    "с %s" to "with %s",
    // What's new
    "Наблюдатели (SWL): станцию в «Моих станциях» можно отметить как наблюдательскую — номер наблюдателя вместо позывного, «Работал с» на месте «RST принят», SWL = Y в ADIF, SWL-рапорт на QSL-наклейке; в онлайн-журналы наблюдения не уходят." to
        "Listeners (SWL): a station in \"My stations\" can be marked as a listener's — the listener number instead of a callsign, \"Worked\" in place of \"RST rcvd\", SWL = Y in ADIF, an SWL report on the QSL label; reports do not go to online logbooks.",
    "Масштаб интерфейса от 50% до 150% с шагом 10%: ⌘/Ctrl + и −, Ctrl с колесом мыши, меню «Вид» — крупнее становятся и таблица, и карточка." to
        "Interface scale from 50% to 150% in steps of 10%: ⌘/Ctrl + and −, Ctrl with the mouse wheel, the View menu — the table and the card grow too.",
    "Масштаб интерфейса от 50% до 150% с шагом 10% (Настройки → Интерфейс, Ctrl + и − с клавиатуры)." to
        "Interface scale from 50% to 150% in steps of 10% (Settings → Interface, Ctrl + and − from a keyboard).",
    "«Дозаполнить данные корреспондентов»: имя, QTH и локатор для связей, записанных без интернета, — для выбранных или всего журнала. Связи, для которых справочник не ответил, дозаполняются сами, когда интернет вернётся." to
        "\"Fill in stations' data\": the name, QTH and locator for QSOs logged without internet — for the selected ones or the whole log. QSOs the callbook did not answer for are filled in by themselves once the internet is back.",
    "Удаление нескольких связей: выберите их и нажмите корзину (или Delete); в таблице строки выбираются Ctrl-щелчком и Shift-щелчком." to
        "Deleting several QSOs: select them and press the bin (or Delete); in the table rows are selected with Ctrl-click and Shift-click.",
    "Удаление нескольких связей: выберите их долгим нажатием и нажмите корзину." to "Deleting several QSOs: select them with a long press and tap the bin.",
    "Связи, выгруженные в HAMLOG, отмечены фиолетовой полоской слева (цвет меняется в «Своей теме»)." to
        "QSOs uploaded to HAMLOG have a purple stripe on the left (the colour can be changed in \"Custom theme\").",
    "Пульт трансивера: щелчок по цифре частоты — выше на этот разряд, правой кнопкой — ниже, колесо над цифрой — тем же шагом; показывается частота, на которую встал трансивер; шкала диапазона больше не пропадает, кнопки не уезжают." to
        "Rig panel: click a digit of the frequency to go up by that digit, right-click to go down, the wheel over a digit tunes by it; the panel shows the frequency the radio actually went to; the band scale no longer disappears and the buttons do not move.",
    "Пульт трансивера показывает частоту, на которую встал трансивер; шкала диапазона больше не пропадает." to
        "The rig panel shows the frequency the radio actually went to; the band scale no longer disappears.",
    "Телеграф через COM-порт компьютера под Windows: более чёткая манипуляция; точность каждой передачи записывается в файл cw-timing.txt." to
        "CW through a computer COM port on Windows: crisper keying; how exactly each message was keyed goes into the file cw-timing.txt.",
    "Исправлено: экран «Прохождение» мог закрываться с ошибкой цвета." to "Fixed: the Propagation screen could close with a colour error.",
)
