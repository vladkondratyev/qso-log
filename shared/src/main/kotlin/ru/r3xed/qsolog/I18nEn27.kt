package ru.r3xed.qsolog

/** English texts of 2.7 (see [EN]): the telegraph from the keyboard, Icom on the network, LoTW, HamQTH and QRZCQ. */
internal val EN_2_7: Map<String, String> = mapOf(
    "HamQTH (по префиксу)" to "HamQTH (by prefix)",
    "Подтверждения LoTW" to "LoTW confirmations",
    "отметить подтверждённые связи: в журнале они станут зелёными" to "mark the confirmed QSOs: they turn green in the log",
    "Логин (позывной)" to "Login (callsign)",
    "Подтверждена в LoTW" to "Confirmed in LoTW",
    "4. HamQTH — справочник" to "4. HamQTH — callbook",
    "Бесплатная учётная запись hamqth.com (регистрация на сайте). Имя, QTH, локатор и координаты станции — для зарубежных и российских позывных; спрашивается после QRZ.ru и QRZ.com, а также когда они нашли позывной без QTH или координат." to
        "A free hamqth.com account (register on the site). Name, QTH, locator and coordinates of the station, for foreign and Russian callsigns; asked after QRZ.ru and QRZ.com, and when they found the callsign without a QTH or coordinates.",
    "Справочник qrzcq.com. Поиск через программы QRZCQ даёт только премиум-участникам; спрашивается последним — когда остальные не нашли позывной, QTH или координаты." to
        "The qrzcq.com callbook. QRZCQ gives lookups to programs only for premium members; asked last — when the others did not find the callsign, the QTH or the coordinates.",
    "6. HamQTH — страна и область" to "6. HamQTH — country and region",
    "Автоматически проверять новую версию при запуске программы" to "Check for a new version when the app starts",
    "Обновить" to "Update",
    "Сеть Icom (LAN, Wi-Fi)" to "Icom network (LAN, Wi-Fi)",
    "TCP-адаптер" to "TCP adapter",
    "Адрес трансивера (IP или имя)" to "Radio address (IP or name)",
    "Имя пользователя (Network User)" to "User name (Network User)",
    "Пароль пользователя" to "User password",
    "Как в RS-BA1, wfview и SDR-Control: программа входит в трансивер по его сетевому протоколу (UDP, порты 50001–50003). В меню трансивера: Network → Network Control — ON; Network User1 — имя и пароль (права Administrator); Control Port — 50001. Адрес трансивера — в его меню (Network → IP Address), в том числе когда телефон подключён к точке доступа самого трансивера." to
        "As RS-BA1, wfview and SDR-Control do: the app logs in to the radio by its network protocol (UDP, ports 50001–50003). In the radio's menu: Network → Network Control — ON; Network User1 — a name and password (Administrator rights); Control Port — 50001. The radio's address is in its menu (Network → IP Address), also when the phone is on the radio's own access point.",
    "Как в RS-BA1, wfview и SDR-Control: программа входит в трансивер по его сетевому протоколу (UDP, порты 50001–50003). В меню трансивера: Network → Network Control — ON; Network User1 — имя и пароль (права Administrator); Control Port — 50001. Адрес трансивера — в его меню (Network → IP Address), в том числе когда компьютер подключён к точке доступа самого трансивера." to
        "As RS-BA1, wfview and SDR-Control do: the app logs in to the radio by its network protocol (UDP, ports 50001–50003). In the radio's menu: Network → Network Control — ON; Network User1 — a name and password (Administrator rights); Control Port — 50001. The radio's address is in its menu (Network → IP Address), also when the computer is on the radio's own access point.",
    "Одновременно трансивер обслуживает одну программу: закройте RS-BA1, wfview или SDR-Control. Если подключение оборвалось, трансивер держит его ещё около минуты." to
        "The radio serves one program at a time: close RS-BA1, wfview or SDR-Control. When a connection breaks, the radio keeps it for about a minute.",
    "Трансивер за сетевым CAT-адаптером или сервером последовательного порта (ser2net, ESP32, Wi-Fi-адаптер): программа шлёт команды CI-V прямо в TCP-соединение." to
        "A radio behind a network CAT adapter or a serial port server (ser2net, ESP32, a Wi-Fi adapter): the app sends the CI-V commands straight into the TCP connection.",
    "Если трансивер молчит, программа сама переберёт скорости и запомнит ту, на которой он ответил." to "If the radio stays silent, the app tries the other speeds and keeps the one it answered at.",
    "Держать линию RTS включённой" to "Keep the RTS line on",
    "Kenwood TS-590S/SG, TS-890S, TS-990S отвечают, только пока включена линия RTS (аппаратное управление потоком RTS/CTS) — для них включено само. Другим трансиверам RTS может включить передачу или телеграф (меню «USB SEND», «USB Keying»): держите выключенной." to
        "Kenwood TS-590S/SG, TS-890S, TS-990S answer only while the RTS line is on (RTS/CTS hardware flow control) — for them it is on by itself. For other radios RTS may key the transmitter or CW (the “USB SEND”, “USB Keying” menus): keep it off.",
    "Как это сделано: как в N1MM, Log4OM, WSJT-X и FT8CN — программа несколько раз в секунду спрашивает у трансивера частоту, вид связи и S-метр, а частоту, диапазон и вид меняет по вашей команде с пульта (нажмите на строку TRX в карточке) или кнопками карточки. В эфир она сама ничего не передаёт: линии DTR и RTS USB-порта держит выключенными, чтобы трансивер не перешёл на передачу (RTS включает только тем трансиверам, которым она нужна для обмена). Подключение: кабель USB от трансивера к телефону через переходник OTG (у ICOM с USB-разъёмом — напрямую, у остальных — кабель CI-V/CAT с адаптером CP210x, FTDI, CH340 или PL2303), Bluetooth или сеть." to
        "How it works: as in N1MM, Log4OM, WSJT-X and FT8CN — the app asks the radio for its frequency, mode and S-meter several times a second, and changes the frequency, band and mode at your command from the panel (tap the TRX line of the card) or the card's buttons. It transmits nothing by itself: the DTR and RTS lines of the USB port are kept off so the radio does not go to transmit (RTS is on only for the radios that need it to talk). Connection: the radio's USB cable to the phone through an OTG adapter (Icoms with a USB socket directly, others with a CI-V/CAT cable on a CP210x, FTDI, CH340 or PL2303 adapter), Bluetooth or the network.",
    "Как это сделано: как в N1MM, Log4OM, WSJT-X и FT8CN — программа несколько раз в секунду спрашивает у трансивера частоту, вид связи и S-метр, а частоту, диапазон и вид меняет по вашей команде с пульта (нажмите на строку TRX в карточке) или кнопками карточки. В эфир она сама ничего не передаёт: линии DTR и RTS USB-порта держит выключенными, чтобы трансивер не перешёл на передачу (RTS включает только тем трансиверам, которым она нужна для обмена). Подключение: кабель USB от трансивера (у ICOM с USB-разъёмом — напрямую, у остальных — кабель CI-V/CAT с адаптером CP210x, FTDI, CH340 или PL2303) или сеть. COM-порт одновременно может открыть только одна программа: если трансивером уже управляет WSJT-X или другой логгер, подключайтесь через rigctld." to
        "How it works: as in N1MM, Log4OM, WSJT-X and FT8CN — the app asks the radio for its frequency, mode and S-meter several times a second, and changes the frequency, band and mode at your command from the panel (click the TRX line of the card) or the card's buttons. It transmits nothing by itself: the DTR and RTS lines of the USB port are kept off so the radio does not go to transmit (RTS is on only for the radios that need it to talk). Connection: the radio's USB cable (Icoms with a USB socket directly, others with a CI-V/CAT cable on a CP210x, FTDI, CH340 or PL2303 adapter) or the network. Only one program can open a COM port at a time: if WSJT-X or another logger already controls the radio, connect through rigctld.",
    " · подтверждена в LoTW %s" to " · confirmed in LoTW %s",
    "Передача с клавиатуры" to "CW from the keyboard",
    "Ключ на внешней клавиатуре" to "Key on an external keyboard",
    "Выкл." to "Off",
    "Нажмите клавишу на внешней клавиатуре…" to "Press a key on the external keyboard…",
    "не назначено" to "not assigned",
    "Клавиши по умолчанию" to "Default keys",
    "Ключ можно не носить с собой: пока карточка QSO в виде CW, клавиши внешней клавиатуры работают ключом. «Прямой ключ» — ключ замкнут, пока клавиша нажата. «Ямбический A/B» — электронный ключ: по умолчанию левый Ctrl или [ — точка, правый Ctrl или ] — тире (так шлют и USB-адаптеры манипуляторов), прямой ключ — любой Ctrl; в режиме B ключ дописывает знак, начатый при сжатых манипуляторах. Клавиши можно переназначить: «Назначить» и нажмите нужную клавишу. Скорость — та же, что у сообщений. Когда в карточке начинают работать ключом, под кнопками сообщений появляется лента переданных знаков. Звук через динамик телефона или компьютера — когда не слышно самоконтроля трансивера; cwdaemon принимает только текст: ему уходят готовые буквы." to
        "No need to carry a key: while the QSO card is in CW, keys of an external keyboard work as the key. “Straight key” — the key is closed while a key is held. “Iambic A/B” — an electronic keyer: by default left Ctrl or [ — dot, right Ctrl or ] — dash (as USB paddle adapters send them), a straight key — either Ctrl; in mode B the keyer finishes the element started while both paddles were squeezed. The keys can be reassigned: “Assign” and press the key. The speed is that of the messages. Once the key is worked in a card, a tape of the characters sent appears under the message buttons. The sound from the phone's or computer's speaker is for when the radio's own sidetone is not heard; cwdaemon takes only text: it gets the finished letters.",
    "буквы уходят в эфир по мере набора" to "letters go on the air as you type",
    "КЛЮЧ" to "KEY",
    "Ямб A" to "Iamb A",
    "Ямб B" to "Iamb B",
    "Прямой ключ: нажмите и держите" to "Straight key: press and hold",
    "Пробел или Ctrl — ключ. Нажмите на рамку, чтобы клавиатура работала ключом." to "Space or Ctrl is the key. Click the frame so the keyboard works as the key.",
    "Левый Ctrl или [ — точка, правый Ctrl или ] — тире. Нажмите на рамку, чтобы клавиатура работала ключом." to "Left Ctrl or [ — dot, right Ctrl or ] — dash. Click the frame so the keyboard works as the key.",
    "Лента: здесь появятся переданные знаки" to "Tape: the characters sent appear here",
    "Очистить ленту" to "Clear the tape",
    "Загрузить другой файл .p12…" to "Load another .p12 file…",
    "Удалить сертификат" to "Remove the certificate",
    "Пароль файла .p12 (если задавали в TQSL)" to ".p12 file password (if set in TQSL)",
    "Загрузить сертификат .p12…" to "Load the .p12 certificate…",
    "Как в RUMlog и SDR-Control: связи подписываются вашим сертификатом прямо здесь и уходят в LoTW по кнопке — TQSL не нужен. Файл .p12: в TQSL на компьютере — вкладка Callsign Certificates → ваш позывной → «Save the callsign certificate» (Сохранить сертификат); пароль — тот, что вы задали при сохранении (или пусто)." to
        "As in RUMlog and SDR-Control: the QSOs are signed with your certificate right here and go to LoTW at a tap — no TQSL needed. The .p12 file: in TQSL on a computer — the Callsign Certificates tab → your callsign → “Save the callsign certificate”; the password is the one you set when saving (or empty).",
    "Позывной и страна — из сертификата, локатор — из «Моей станции», зоны — как в Station Location в TQSL (можно не указывать)." to
        "The callsign and the country come from the certificate, the locator from “My station”, the zones as in TQSL's Station Location (may be left empty).",
    "Логин сайта LoTW" to "LoTW website login",
    "Пароль сайта LoTW" to "LoTW website password",
    "С логином сайта программа скачивает подтверждения: ⋮ → «Экспорт и импорт» → «Подтверждения LoTW». Подтверждённые связи отмечаются в журнале зелёным." to
        "With the website login the app downloads the confirmations: ⋮ → “Export and import” → “LoTW confirmations”. Confirmed QSOs are marked green in the log.",
    "Или через TQSL: Station Location" to "Or through TQSL: Station Location",
    "Без файла .p12 QSO-LOG передаёт связи в TQSL, он подписывает их и отправляет в LoTW. Station Location — название места станции, как оно записано в TQSL (Station → Station Locations)." to
        "Without a .p12 file QSO-LOG passes the QSOs to TQSL, which signs and sends them to LoTW. Station Location is the name of the station location as written in TQSL (Station → Station Locations).",
    "Без файла .p12 телефон сохраняет связи в файл, а подписываете вы его в TQSL на компьютере («Sign a log and upload it»)." to
        "Without a .p12 file the phone saves the QSOs to a file, and you sign it in TQSL on a computer (“Sign a log and upload it”).",
    "Файл не в UTF-8: русские буквы прочитаются неверно. Скорее всего, это Windows-1251 — так пишут LogHX, UR5EQF и другие программы." to
        "The file is not UTF-8: Cyrillic letters will be read wrong. Most likely it is Windows-1251, as LogHX, UR5EQF and other programs write.",
    "Первые записи" to "First records",
    "Если вместо букв — непонятные знаки, выберите другую кодировку." to "If you see garbage instead of letters, pick another encoding.",
    "В файле не найдено связей ADIF." to "No ADIF QSOs found in the file.",
    "Импортировать (%s)" to "Import (%s)",
    "Передать сообщение %s: %s" to "Send message %s: %s",
    "Скрыть передачу с клавиатуры" to "Hide CW from the keyboard",
    "Звук самоконтроля через динамик" to "Sidetone from the speaker",
    "В карточке QSO с видом CW нажмите значок клавиатуры у кнопок F1–F8. «Текст» — набранные буквы уходят в эфир по мере набора. «Прямой ключ» — пока нажата кнопка КЛЮЧ, Пробел или Ctrl, ключ замкнут. «Ямбический A/B» — электронный ключ: левый Ctrl или [ — точка, правый Ctrl или ] — тире, на телефоне — кнопки · и —; в режиме B ключ дописывает знак, начатый при сжатых манипуляторах. Скорость — та же, что у сообщений. Внизу — лента переданных знаков. Звук через динамик телефона или компьютера — когда не слышно самоконтроля трансивера; cwdaemon принимает только текст: при работе ключом ему уходят готовые буквы." to
        "In a QSO card in CW tap the keyboard icon next to the F1–F8 buttons. “Text” — the letters typed go on the air as you type. “Straight key” — the key is closed while the KEY button, Space or Ctrl is held. “Iambic A/B” — an electronic keyer: left Ctrl or [ — dot, right Ctrl or ] — dash, on the phone — the · and — buttons; in mode B the keyer finishes the element started while both paddles were squeezed. The speed is that of the messages. Below is the tape of the characters sent. The sound from the phone's or computer's speaker is for when the radio's own sidetone is not heard; cwdaemon takes only text: when keying by hand it gets the finished letters.",
    "Сообщения передаются, когда в карточке QSO выбран вид CW: клавиши F1–F8 (их можно переназначить в «Дополнительных настройках»), кнопки под частотой, Esc — остановить, PageUp и PageDown (или Alt+↑ и Alt+↓) — скорость. Так работает N1MM: программа замыкает ключ линией DTR или RTS USB-адаптера." to
        "The messages are sent when the QSO card is in CW: keys F1–F8 (they can be reassigned in “Additional settings”), the buttons under the frequency, Esc to stop, PageUp and PageDown (or Alt+↑ and Alt+↓) for the speed. This is how N1MM works: the app keys with the DTR or RTS line of a USB adapter.",
    "Связи подписываются вашим сертификатом LoTW (%s) и сразу уходят в LoTW." to "The QSOs are signed with your LoTW certificate (%s) and go to LoTW at once.",
    "Загрузите в настройках файл сертификата .p12 из TQSL — и связи будут уходить в LoTW прямо отсюда. Пока его нет, сохраните файл, перенесите на компьютер и подпишите в TQSL («Sign a log and upload it»)." to
        "Load the .p12 certificate file from TQSL in the settings, and the QSOs will go to LoTW right from here. Until then, save a file, move it to a computer and sign it in TQSL (“Sign a log and upload it”).",
    "Сертификат LoTW для %s загружен" to "LoTW certificate for %s loaded",
    "Нет интернета" to "No internet",
    "Сертификат LoTW (.p12)" to "LoTW certificate (.p12)",
    "OmniRig держит трансивер сам, QSO-LOG спрашивает у него частоту, вид и передачу и перестраивает трансивер через него — вместе с другими программами, работающими через OmniRig (MixW, JTDX, N1MM, HDSDR). Установите OmniRig (dxatlas.com/omnirig), выберите в нём трансивер и COM-порт для Rig 1 или Rig 2 и тот же номер здесь." to
        "OmniRig holds the radio itself; QSO-LOG asks it for the frequency, mode and transmit state and tunes the radio through it — together with other programs working through OmniRig (MixW, JTDX, N1MM, HDSDR). Install OmniRig (dxatlas.com/omnirig), choose the radio and the COM port for Rig 1 or Rig 2 in it, and the same number here.",
    "OmniRig работает только в Windows" to "OmniRig works only on Windows",
    "Не удалось запустить PowerShell для OmniRig: %s" to "Could not start PowerShell for OmniRig: %s",
    "OmniRig не найден: установите OmniRig и настройте в нём трансивер (Rig 1 или Rig 2). %s" to "OmniRig not found: install OmniRig and set up the radio in it (Rig 1 or Rig 2). %s",
    "OmniRig не ответил" to "OmniRig did not answer",
    "Связи подписывает и отправляет программа TQSL от ARRL с вашим сертификатом: QSO-LOG запускает её сама, с Station Location из настроек. Или загрузите в настройках файл сертификата .p12 — тогда TQSL не нужен." to
        "The QSOs are signed and sent by ARRL's TQSL with your certificate: QSO-LOG runs it itself, with the Station Location from the settings. Or load the .p12 certificate file in the settings — then TQSL is not needed.",
    "трансивер не отвечает" to "the radio does not answer",
    "Не найден адрес %s" to "Address %s not found",
    "Трансивер занят: к нему подключена программа на компьютере %s. Закройте её или подождите." to "The radio is busy: a program on the computer %s is connected to it. Close it or wait.",
    "Трансивер не отвечает по адресу %s, порт UDP %s. Проверьте IP, что в меню трансивера включено Network Control, и порт (Network → Control Port, обычно 50001)." to
        "The radio does not answer at %s, UDP port %s. Check the IP, that Network Control is on in the radio's menu, and the port (Network → Control Port, usually 50001).",
    "Трансивер не ответил на вход. Проверьте имя и пароль Network User в меню трансивера." to "The radio did not answer the login. Check the Network User name and password in the radio's menu.",
    "Трансивер не открыл поток CI-V. Перезагрузите трансивер или подождите пару минут и переподключитесь." to "The radio did not open the CI-V stream. Restart the radio or wait a couple of minutes and reconnect.",
    "Трансивер отклонил имя или пароль. В меню трансивера: Network → Network User1 — имя (ID) и пароль, права Administrator; те же имя и пароль впишите здесь." to
        "The radio refused the name or password. In the radio's menu: Network → Network User1 — the name (ID) and password, Administrator rights; enter the same name and password here.",
    "Трансивер прервал сеанс. Переподключаюсь…" to "The radio ended the session. Reconnecting…",
    "Трансивер отказал в подключении CI-V. Перезагрузите трансивер или подождите пару минут: он ещё держит прошлое подключение." to
        "The radio refused the CI-V connection. Restart the radio or wait a couple of minutes: it still holds the previous connection.",
    "Трансивер закрыл подключение" to "The radio closed the connection",
    "Нет связи с трансивером: %s" to "No connection to the radio: %s",
    "%s бод" to "%s baud",
    "Неверный пароль файла .p12" to "Wrong .p12 file password",
    "Это не файл сертификата .p12: %s" to "This is not a .p12 certificate file: %s",
    "В файле нет сертификата позывного LoTW с ключом. Сохраните в TQSL сертификат позывного (Save the callsign certificate), а не запрос" to
        "The file has no LoTW callsign certificate with a key. Save the callsign certificate in TQSL (Save the callsign certificate), not a request",
    "Сертификат LoTW для %s истёк: обновите его в TQSL и загрузите новый файл .p12" to "The LoTW certificate for %s has expired: renew it in TQSL and load the new .p12 file",
    "LoTW ответил %s, попробуйте позже" to "LoTW answered %s, try again later",
    "LoTW не принял файл: %s" to "LoTW did not accept the file: %s",
    "Сертификатом %s нельзя подписать эти связи: позывной станции другой или даты вне срока сертификата" to
        "The %s certificate cannot sign these QSOs: the station callsign differs or the dates are outside the certificate's range",
    "не подписано связей: %s — позывной станции не %s или дата вне срока сертификата" to "QSOs not signed: %s — the station callsign is not %s or the date is outside the certificate's range",
    "LoTW: подтверждено связей: %s" to "LoTW: QSOs confirmed: %s",
    ", отмечены раньше: %s" to ", marked before: %s",
    ", нет в журнале: %s" to ", not in the log: %s",
    "LoTW не принял логин или пароль сайта" to "LoTW did not accept the website login or password",
    "LoTW: неожиданный ответ сервера" to "LoTW: unexpected server answer",
    "Укажите логин и пароль сайта LoTW" to "Enter the LoTW website login and password",
    "Укажите логин и пароль HamQTH" to "Enter the HamQTH login and password",
    "HamQTH не принял логин или пароль" to "HamQTH did not accept the login or password",
    "HamQTH временно не пускает: слишком много попыток входа" to "HamQTH is refusing for a while: too many login attempts",
    "Укажите логин и пароль QRZCQ" to "Enter the QRZCQ login and password",
    "QRZCQ: поиск через программы доступен только с премиум-подпиской QRZCQ" to "QRZCQ: lookups from programs need a QRZCQ premium membership",
    "QRZCQ не принял логин или пароль" to "QRZCQ did not accept the login or password",
    "QRZCQ не вернул ключ сессии" to "QRZCQ returned no session key",
    "Ключ на внешней клавиатуре: в карточке QSO с видом CW клавиши (левый и правый Ctrl или [ и ]) работают прямым или ямбическим ключом, а под кнопками появляется лента переданных знаков; звук через динамик, скорость — PageUp и PageDown." to
        "A key on an external keyboard: in a QSO card in CW the keys (left and right Ctrl, or [ and ]) work as a straight or iambic key, and a tape of the characters sent appears under the buttons; sound from the speaker, speed with PageUp and PageDown.",
    "Своя клавиатура в карточке QSO с видом CW: над буквами ряд F1–F8, скорость − и + и «Стоп»." to "The app's keypad in a QSO card in CW: a row of F1–F8, speed − and + and Stop above the letters.",
    "Icom по сети (IC-705, IC-7610, IC-7760, IC-9700…): вход по имени и паролю пользователя трансивера, как в RS-BA1 и wfview, — в локальной сети и через точку доступа IC-705." to
        "Icom over the network (IC-705, IC-7610, IC-7760, IC-9700…): login with the radio's user name and password, as in RS-BA1 and wfview — on the local network and through the IC-705 access point.",
    "LoTW без TQSL: загрузите сертификат .p12 — связи подписываются и уходят в LoTW по кнопке, а «Подтверждения LoTW» отмечают подтверждённые связи зелёным." to
        "LoTW without TQSL: load the .p12 certificate — QSOs are signed and go to LoTW at a tap, and “LoTW confirmations” mark the confirmed QSOs green.",
    "Справочники HamQTH и QRZCQ: если QRZ.ru нашёл позывной без QTH или координат, их ищут дальше по списку." to
        "HamQTH and QRZCQ callbooks: when QRZ.ru finds a callsign without a QTH or coordinates, they are looked up further down the list.",
    "CAT через OmniRig (Windows); Kenwood TS-590S, TS-890S, TS-990S работают (линия RTS), программа сама подбирает скорость порта; чёткая манипуляция телеграфом в Windows." to
        "CAT through OmniRig (Windows); Kenwood TS-590S, TS-890S, TS-990S work (the RTS line), the app finds the port speed itself; crisp CW keying on Windows.",
    "Kenwood TS-590S, TS-890S, TS-990S работают (линия RTS), программа сама подбирает скорость порта." to "Kenwood TS-590S, TS-890S, TS-990S work (the RTS line), the app finds the port speed itself.",
    "Импорт ADIF с выбором кодировки (UTF-8, Windows-1251, KOI8-R, CP866) и просмотром первых записей; проверка новой версии при запуске; время связей из JTDX больше не сдвигается на часовой пояс." to
        "ADIF import with a choice of encoding (UTF-8, Windows-1251, KOI8-R, CP866) and a preview of the first records; a check for a new version at start; QSO times from JTDX no longer shift by the time zone.",
    // 2.7.1
    "Открывать карточку QSO при выборе корреспондента" to "Open the QSO card when a station is picked",
    "Двойной щелчок по позывному в WSJT-X или JTDX сразу открывает карточку нового QSO: QTH, карта, расстояние, «уже работали». Когда в WSJT-X нажимают OK в окне «Log QSO», карточка заполняется и сохраняется; если связь не записали и позывной в WSJT-X стёрли, карточка закрывается. Карточку, которую вы заполняете сами, программа не трогает." to
        "A double click on a callsign in WSJT-X or JTDX opens the new QSO card at once: QTH, map, distance, \"Worked before\". When OK is pressed in WSJT-X's \"Log QSO\" window, the card is filled in and saved; if the contact was not logged and the callsign was cleared in WSJT-X, the card closes. A card you are filling in yourself is left alone.",
    "Ключ на экране" to "On-screen key",
    "Кнопки ключа появляются на своей клавиатуре в карточке QSO с видом CW, под рядом F1–F8: «·» и «—» для ямбического ключа или одна кнопка «КЛЮЧ». Ключ замкнут, пока палец на кнопке; на ямбическом можно нажать обе кнопки сразу." to
        "Key buttons appear on the app's keypad in a QSO card in CW, under the F1–F8 row: \"·\" and \"—\" for the iambic key or one \"KEY\" button. The key is closed while the finger is on the button; on the iambic key both buttons can be pressed at once.",
    "Карточка QSO по двойному щелчку в WSJT-X и JTDX: сразу видны QTH, карта и расстояние, а связь, записанная в WSJT-X, сохраняется вместе с этой карточкой (включается в настройках цифровых видов)." to
        "The QSO card on a double click in WSJT-X and JTDX: QTH, map and distance are seen at once, and the contact logged in WSJT-X is saved with this card (switched on in the digital modes settings).",
    "Ключ на экране: кнопки «·» и «—» или «КЛЮЧ» на своей клавиатуре в карточке QSO с видом CW (включается в настройках CW)." to
        "On-screen key: \"·\" and \"—\" or \"KEY\" buttons on the app's keypad in a QSO card in CW (switched on in the CW settings).",
    "Ряд F1–F8 на своей клавиатуре: все клавиши одного размера, «Стоп» занимает две; скорость меняется в карточке." to
        "The F1–F8 row on the app's keypad: all keys of one size, Stop takes two; the speed is changed in the card.",
)
