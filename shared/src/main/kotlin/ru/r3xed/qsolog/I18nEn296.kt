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
    "Сначала сохраните изменения (F12) или закройте карточку (Esc)" to "Save the changes first (F12) or close the card (Esc)",
    // The photo, full screen (phone) or zoomable (computer)
    "Открыть фото на весь экран" to "Open the photo full screen",
    "Открыть фото крупно" to "Open the photo large",
    "Закрыть (Esc)" to "Close (Esc)",
    "Колесо мыши или + и − — масштаб, перетаскивание — сдвиг, двойной щелчок — крупнее или обратно" to
        "Mouse wheel or + and − to zoom, drag to move, double-click to zoom in or back",
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
    // 2.9.7
    "Приложение закрылось с ошибкой:" to "The app closed with an error:",
    "QSO-LOG закрылся с ошибкой" to "QSO-LOG closed with an error",
    "Отправьте отчёт разработчику — так ошибку быстрее исправят. Откроется страница GitHub с текстом ошибки, версией и моделью устройства; данных журнала там нет. Допишите, что вводили перед закрытием." to
        "Send a report to the developer so the error gets fixed sooner. A GitHub page opens with the error's text, the version and the device model; nothing from the log. Add what you were typing before it closed.",
    "Сообщить" to "Report",
    "Не надо" to "No, thanks",
    "Имя файла при экспорте: позывной, дата и время — UA3DEMO_2026-11-15_164059.adi. В папке файлы идут по порядку, несколько выгрузок за день не путаются." to
        "Export file names: callsign, date and time — UA3DEMO_2026-11-15_164059.adi. Files sort in order in a folder, and several exports a day do not mix up.",
    "Карточка, открытая из WSJT-X, JTDX или MSHV, сразу в FT8 (или в виде программы), даже если трансивер в обычном USB." to
        "A card opened from WSJT-X, JTDX or MSHV is in FT8 (or the program's mode) right away, even with the radio in plain USB.",
    "Kenwood TS-590/890/990: цифровой режим (DA1) больше не перестаёт опрашиваться после случайного ответа «?;» — кнопка FT8 на пульте горит." to
        "Kenwood TS-590/890/990: the data mode (DA1) is no longer left unpolled after a stray \"?;\" answer, so the FT8 button on the rig panel lights up.",
    "Lab599 TX-500 и TX-500MP в списке трансиверов (протокол TS-2000)." to "Lab599 TX-500 and TX-500MP in the list of radios (the TS-2000 protocol).",
    "Пульт трансивера: строка под частотой больше не дёргается при смене показаний S-метра." to
        "Rig panel: the line under the frequency no longer jumps when the S-meter reading changes.",
    "LoTW: сертификаты .p12 из новых версий TQSL открываются и на старых Android (ошибка «PBKDF2 SecretKeyFactory not available»)." to
        "LoTW: .p12 certificates from newer TQSL versions open on older Android too (the \"PBKDF2 SecretKeyFactory not available\" error).",
    "Исправлено закрытие приложения на Android 8–12 при поиске позывного, у которого в QRZ.ru есть фото, и при передаче CW через USB-адаптер." to "Fixed: on Android 8–12 the app closed when a callsign with a photo on QRZ.ru was looked up, and when CW was sent through a USB adapter.",
    "Плотный журнал" to "Dense log",
    "Фото в старой связи: в сохранённой карточке без фото есть кнопка «Загрузить фото» — фото берётся из справочников и хранится с этой связью." to
        "A photo for an old contact: a saved card without a photo has a \"Get photo\" button; the photo comes from the callbooks and is kept with that contact.",
    "Фото можно сохранить в полном размере: откройте его крупно и нажмите «Сохранить фото»." to "A photo can be saved in full size: open it large and press \"Save photo\".",
    "Ширину журнала рядом с карточкой можно менять: перетащите линию между ними, двойной щелчок возвращает как было." to
        "The log's width beside the card can be changed: drag the line between them; a double click brings it back.",
    "Планшет в горизонтальном положении: журнал остаётся слева от карточки, линию между ними можно перетаскивать." to
        "A tablet held sideways: the log stays to the left of the card, and the line between them can be dragged.",
    "Соревнования: список участников по алфавиту (станции этого соревнования из журнала, в том числе прошлых лет). Клик подставляет позывной и постоянную часть контрольного номера, повторы подсвечены красным." to
        "Contests: a participants list A–Z (stations of this contest from the log, past years too). A click fills in the callsign and the constant part of the exchange; dupes are red.",
    "Первая связь с каждой страной отмечена звёздочкой в журнале." to "The first contact with each country has a star in the log.",
    "Нажмите на дату над связями — откроется календарь, журнал перейдёт к выбранному дню." to "Tap the date above the contacts: a calendar opens and the log goes to the chosen day.",
    "Плотный журнал (меню ⋮): одна строка на связь, на экране вдвое больше связей." to "Dense log (⋮ menu): one line a contact, twice as many on the screen.",
    "С версии 3.0: большой журнал занимает в несколько раз меньше памяти." to "Since 3.0: a large log takes several times less memory.",
    "Перейти к дате" to "Go to date",
    "Первая связь с этой страной" to "First contact with this country",
    "Скрыть список участников" to "Hide the participants list",
    "Показать список участников" to "Show the participants list",
    "Участники" to "Participants",
    "Здесь появятся станции этого соревнования из журнала (и прошлых лет) — клик подставит позывной и постоянную часть контрольного номера." to
        "Stations of this contest from the log (past years too) appear here; a click fills in the callsign and the constant part of the exchange.",
    "повтор" to "dupe",
    "Ищу фото…" to "Looking for a photo…",
    "Загрузить фото" to "Get photo",
    "Сохранить фото" to "Save photo",
    "Связь не найдена в журнале" to "The contact is not in the log",
    "В справочниках нет фото %s" to "The callbooks have no photo of %s",
    "Фото не скачалось, попробуйте позже" to "The photo did not download, try again later",
    "Справочник не ответил, попробуйте позже" to "The callbook did not answer, try again later",
    "Фото сохранено" to "Photo saved",
    "Не удалось сохранить фото" to "Could not save the photo",
    "Большой журнал (100 000 связей и больше) занимает в несколько раз меньше памяти. Программа больше не закрывается с ошибкой «Java heap space» на Windows и может взять до половины памяти компьютера." to
        "A large log (100,000 contacts and more) takes several times less memory. The program no longer closes with \"Java heap space\" on Windows and may use up to half of the computer's memory.",
    "Большой журнал (100 000 связей и больше) занимает в несколько раз меньше памяти телефона." to "A large log (100,000 contacts and more) takes several times less of the phone's memory.",
    "После правки связи журнал остаётся на том же месте и не прыгает в начало списка." to "After a contact is edited, the log stays where it was instead of jumping to the top.",
    "Поиск по журналу быстрее: журнал больше не читается из базы второй раз." to "Searching the log is faster: the log is no longer read from the database a second time.",
    "Исправлено закрытие программы при наборе позывного вида «A/P»." to "Fixed: the app closed when a callsign like \"A/P\" was typed.",
    "Если приложение закрылось с ошибкой, при следующем запуске оно предложит отправить отчёт разработчику." to
        "If the app closes with an error, on the next start it offers to send a report to the developer.",
    "Новое: QSO-LOG Web — версия в браузере без установки, работает и без интернета: qso-log.ru/app" to
        "New: QSO-LOG Web, a version in the browser with nothing to install; it works offline too: qso-log.ru/app",
)
