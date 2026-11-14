package ru.r3xed.qsolog

/** English texts of 2.9.6 (see [EN]): keys for every section, the cluster's keys, the date and time as digits, SWL, the HAMLOG dot. */
internal val EN_2_9_6: Map<String, String> = mapOf(
    // Keys
    "прохождение" to "propagation",
    "дипломы" to "awards",
    "справка и калькуляторы" to "reference and calculators",
    "онлайн-журналы: LoTW, eQSL, Club Log, QRZ.com, HAMLOG" to "online logbooks: LoTW, eQSL, Club Log, QRZ.com, HAMLOG",
    "подтверждения LoTW" to "LoTW confirmations",
    "импорт журнала из ADIF" to "import the log from ADIF",
    "выбрать часть журнала" to "select part of the log",
    "сделать копию журнала" to "back up the log",
    "журнал таблицей или списком" to "the log as a table or a list",
    "следующая своя станция (по кругу)" to "next own station (round)",
    "Следующая своя станция" to "Next own station",
    "Разделы и сервис" to "Sections and tools",
    "все команды по названию" to "all commands by name",
    "по спотам" to "through the spots",
    "перестроить трансивер и открыть карточку" to "tune the radio and open the card",
    "карточка без перестройки трансивера" to "the card without tuning the radio",
    "свой спот" to "own spot",
    "диапазон (n-й из тех, где есть споты) · все" to "band (the n-th of those with spots) · all",
    "обновить" to "refresh",
    "Дата и время в карточке" to "Date and time in the card",
    "время цифрами, двоеточие ставится само" to "the time as digits, the colon comes by itself",
    "дата цифрами: точки и год ставятся сами" to "the date as digits: the dots and the year come by themselves",
    "на минуту или день вперёд и назад" to "a minute or a day forward and back",
    "ДДММГГГГ" to "DDMMYYYY",
    "ЧЧММ" to "HHMM",
    // Stations, the card, the status bar
    "Добавить станцию (позывной, клубная, SWL)…" to "Add a station (callsign, club, SWL)…",
    "Сначала запишите или закройте карточку (Esc)" to "Save or close the card first (Esc)",
    "Станция одна: вторую (например, номер наблюдателя SWL) добавьте в настройках, «Мои станции»" to
        "There is one station: add another (for example, an SWL number) in the settings, \"My stations\"",
    "В работе: %s" to "In use: %s",
    "CAT включён: %s" to "CAT on: %s",
    "Ширина по содержимому" to "Fit to contents",
    "Точка «выгружено в HAMLOG»" to "Dot \"uploaded to HAMLOG\"",
    "Подробности записаны в файл %s — пришлите его, пожалуйста, вместе с описанием, что вы делали." to
        "The details are in the file %s — please send it with a description of what you were doing.",
    // What's new
    "Горячие клавиши для всех разделов и действий меню: DX-кластер ⌘⇧C, Прохождение ⌘⇧P, Дипломы ⌘⇧A, Справка ⌘⇧R, онлайн-журналы ⌘U, LoTW ⌘⇧L, импорт ADIF ⌘O, копия журнала ⌘B, таблица ⌘⇧T, следующая станция ⌘⇧N (на Windows и Linux — Ctrl). Клавиши видны в меню и в ⌘K; меняются в настройках." to
        "Keys for every section and menu action: DX cluster ⌘⇧C, Propagation ⌘⇧P, Awards ⌘⇧A, Reference ⌘⇧R, online logbooks ⌘U, LoTW ⌘⇧L, ADIF import ⌘O, log backup ⌘B, table ⌘⇧T, next station ⌘⇧N (Ctrl on Windows and Linux). The keys show in the menus and in ⌘K and can be changed in the settings.",
    "С внешней клавиатурой — горячие клавиши для разделов: DX-кластер Ctrl+Shift+C, Прохождение Ctrl+Shift+P, Дипломы Ctrl+Shift+A, Справка Ctrl+Shift+R, онлайн-журналы Ctrl+U, импорт ADIF Ctrl+O, следующая станция Ctrl+Shift+N; список — F1." to
        "With an external keyboard, keys for the sections: DX cluster Ctrl+Shift+C, Propagation Ctrl+Shift+P, Awards Ctrl+Shift+A, Reference Ctrl+Shift+R, online logbooks Ctrl+U, ADIF import Ctrl+O, next station Ctrl+Shift+N; the list is on F1.",
    "DX-кластер с клавиатуры: ↑ ↓ по спотам, Enter — перестроить трансивер и открыть карточку, Shift+Enter — только карточка, S — свой спот, цифры — диапазон." to
        "The DX cluster from the keyboard: ↑ ↓ through the spots, Enter tunes the radio and opens the card, Shift+Enter only the card, S your own spot, digits the band.",
    "Дата и время в карточке набираются цифрами: 1234 → 12:34, 931 → 09:31, 1411 → 14.11 этого года; ↑ и ↓ — на минуту или день." to
        "The date and time in the card are typed as digits: 1234 → 12:34, 931 → 09:31, 1411 → 14.11 of this year; ↑ and ↓ step a minute or a day.",
    "Переключить станцию (например, на номер наблюдателя SWL) можно щелчком по позывному в строке состояния — и в режиме таблицы; станция-наблюдатель помечена SWL." to
        "Switch the station (for example, to an SWL number) by clicking the callsign in the status bar, in the table view too; a listener's station is marked SWL.",
    "Станция-наблюдатель помечена SWL рядом с позывным; новую станцию можно добавить прямо из списка станций." to
        "A listener's station is marked SWL next to the callsign; a new station can be added right from the list of stations.",
    "Связи, выгруженные в HAMLOG, отмечены фиолетовой точкой рядом с точками экспорта, как ADIF и CSV." to
        "QSOs uploaded to HAMLOG have a purple dot next to the export dots, like ADIF and CSV.",
    "Kenwood TS-590/890/990: цифровой режим (USB с DA1) распознаётся — кнопка FT8 на пульте горит." to
        "Kenwood TS-590/890/990: the data mode (USB with DA1) is recognised and the FT8 button on the rig panel lights up.",
    "Экспорт и импорт открываются в папке, куда сохраняли в прошлый раз." to "Export and import open in the folder used last time.",
    "Ширина колонок таблицы меняется перетаскиванием края заголовка, двойной щелчок — по содержимому. Наведите на точки экспорта — видно, куда и когда выгружено." to
        "Drag a header's edge to change a table column's width, double-click to fit the contents. Hover over the export dots to see where and when a QSO went.",
    "Исправлено: после изменения масштаба колесом мыши могло появиться окно «Error» и программа закрывалась." to
        "Fixed: after changing the scale with the mouse wheel an \"Error\" window could appear and the program closed.",
)
