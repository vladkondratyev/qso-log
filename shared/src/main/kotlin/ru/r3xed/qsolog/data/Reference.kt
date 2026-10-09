package ru.r3xed.qsolog.data

/** Reference tables shown in the "Справка" screen. Texts in Russian go through tr() where they are shown. */
object Reference {
    /** One amateur band: its name, its limits as written in the decision, and a short note (Russian, translated on screen). */
    data class Band(val name: String, val range: String, val note: String = "")

    /**
     * Bands of the amateur service in the Russian Federation: decision of the State Radio Frequency Commission (ГКРЧ)
     * of 15 July 2010 No. 10-07-01, item 1 (as amended). Notes for 2200 m and 30 m follow the SRR band plan.
     */
    val BANDS_RU = listOf(
        Band("2200 м", "135,7–137,8 кГц", "CW и узкополосные цифровые виды, ЭИИМ до 1 Вт"),
        Band("160 м", "1810–2000 кГц"),
        Band("80 м", "3500–3800 кГц"),
        Band("40 м", "7000–7200 кГц"),
        Band("30 м", "10100–10150 кГц", "CW и узкополосные цифровые виды"),
        Band("20 м", "14000–14350 кГц"),
        Band("17 м", "18068–18168 кГц"),
        Band("15 м", "21000–21450 кГц"),
        Band("12 м", "24890–24990 кГц"),
        Band("10 м", "28000–29700 кГц"),
        Band("2 м", "144–146 МГц"),
        Band("70 см", "430–440 МГц"),
        Band("23 см", "1260–1300 МГц"),
        Band("6 см", "5650–5670 МГц", "Земля–космос (любительская спутниковая)"),
        Band("6 см", "5725–5850 МГц"),
        Band("3 см", "10–10,5 ГГц"),
        Band("1,2 см", "24–24,25 ГГц"),
        Band("6 мм", "47–47,2 ГГц"),
        Band("4 мм", "76–78 ГГц"),
        Band("2,5 мм", "122,25–123 ГГц"),
        Band("2 мм", "134–141 ГГц"),
        Band("1 мм", "241–250 ГГц"),
    )

    /** International Morse code: letter to dots and dashes. */
    val MORSE_LATIN = listOf(
        "A" to ".-", "B" to "-...", "C" to "-.-.", "D" to "-..", "E" to ".", "F" to "..-.", "G" to "--.", "H" to "....",
        "I" to "..", "J" to ".---", "K" to "-.-", "L" to ".-..", "M" to "--", "N" to "-.", "O" to "---", "P" to ".--.",
        "Q" to "--.-", "R" to ".-.", "S" to "...", "T" to "-", "U" to "..-", "V" to "...-", "W" to ".--", "X" to "-..-",
        "Y" to "-.--", "Z" to "--..",
    )

    /** Russian Morse code (the Cyrillic letters share the international signals). */
    val MORSE_CYRILLIC = listOf(
        "А" to ".-", "Б" to "-...", "В" to ".--", "Г" to "--.", "Д" to "-..", "Е" to ".", "Ж" to "...-", "З" to "--..",
        "И" to "..", "Й" to ".---", "К" to "-.-", "Л" to ".-..", "М" to "--", "Н" to "-.", "О" to "---", "П" to ".--.",
        "Р" to ".-.", "С" to "...", "Т" to "-", "У" to "..-", "Ф" to "..-.", "Х" to "....", "Ц" to "-.-.", "Ч" to "---.",
        "Ш" to "----", "Щ" to "--.-", "Ъ" to "--.--", "Ы" to "-.--", "Ь" to "-..-", "Э" to "..-..", "Ю" to "..--", "Я" to ".-.-",
    )

    /**
     * Russian chants for learning the letters, digits and signs: one syllable per element, long syllables (dashes) in capitals —
     * А ·− "ай-ДА", Б −··· "БА-ки-те-кут". The classic set taught in Russian radio clubs.
     */
    val MORSE_CHANTS = mapOf(
        "А" to "ай-ДА", "Б" to "БА-ки-те-кут", "В" to "ви-ДА-ЛА", "Г" to "ГА-РА-жи", "Д" to "ДО-ми-ки",
        "Е" to "есть", "Ж" to "жи-ву-те-ЛЯ", "З" to "ЗА-КА-ти-ки", "И" to "и-ди", "Й" to "йош-КАР-О-ЛА",
        "К" to "КАК-же-ТАК", "Л" to "лу-НА-ти-ки", "М" to "МА-МА", "Н" to "НО-мер", "О" to "О-КО-ЛО",
        "П" to "пи-ЛА-ПО-ёт", "Р" to "ре-ША-ет", "С" to "са-ма-ми", "Т" to "ТАК", "У" to "у-не-СУ",
        "Ф" to "фи-ли-МОН-чик", "Х" to "хи-ми-чи-те", "Ц" to "ЦА-пли-НА-ши", "Ч" to "ЧА-ША-ТО-нет",
        "Ш" to "ША-РО-ВА-РЫ", "Щ" to "ЩА-ВА-не-НА", "Ъ" to "ТВЁР-ДЫЙ-не-МЯГ-КИЙ", "Ы" to "Ы-не-НА-ДО",
        "Ь" to "ТО-мяг-кий-ЗНАК", "Э" to "э-ле-КТРО-ни-ки", "Ю" to "ю-ли-А-НА", "Я" to "я-МАЛ-я-МАЛ",
        // Digits: the classic chants.
        "1" to "и-ТОЛЬ-КО-ОД-НА", "2" to "две-не-ХО-РО-ШО", "3" to "три-те-бе-МА-ЛО", "4" to "чет-ве-ре-ти-КА",
        "5" to "пя-ти-ле-ти-е", "6" to "ПО-шес-ти-бе-ри", "7" to "ДАЙ-ДАЙ-за-ку-рить", "8" to "МО-ЛО-КО-ки-пит",
        "9" to "НО-НА-НО-НА-ми", "0" to "НОЛЬ-О-КО-ЛО-НОЛЬ",
        // Signs: the full stop has its classic chant, the others are mnemonics made by the same rule.
        "." to "то-ЧЕЧ-ка-ТО-чеч-КА", "," to "КРЮЧ-КИ-ка-ки-КРЮЧ-КИ", "?" to "у-ми-ТЕ-БЯ-спро-сить",
        "/" to "ДРОБЬ-ли-то-ДРОБЬ-ли", "=" to "РАЗ-де-ли-те-ВСЁ", "-" to "ЧЁР-точ-ку-пи-ши-ТАК",
        "@" to "со-БА-ЧКА-на-ДОМ-ке", "AR" to "и-ВСЁ-я-КОН-чил", "SK" to "вот-и-всё-ДО-сви-ДАНЬ",
        "KN" to "ТОЛЬ-ко-ТЫ-ОТ-веть",
    )

    /**
     * The Latin letter with the same signal as each Cyrillic one, shown as "A/А". Six Russian letters have no plain Latin
     * twin; for them the usual extended signs are given (Ö, CH, Ñ, É, Ü, Ä).
     */
    val MORSE_LATIN_TWIN: Map<String, String> = MORSE_CYRILLIC.associate { (cyr, code) ->
        cyr to (MORSE_LATIN.firstOrNull { it.second == code }?.first ?: mapOf(
            "---." to "Ö", "----" to "CH", "--.--" to "Ñ", "..-.." to "É", "..--" to "Ü", ".-.-" to "Ä",
        )[code].orEmpty())
    }

    val MORSE_DIGITS = listOf(
        "1" to ".----", "2" to "..---", "3" to "...--", "4" to "....-", "5" to ".....",
        "6" to "-....", "7" to "--...", "8" to "---..", "9" to "----.", "0" to "-----",
    )

    val MORSE_SIGNS = listOf(
        "." to ".-.-.-", "," to "--..--", "?" to "..--..", "/" to "-..-.", "=" to "-...-", "-" to "-....-",
        "@" to ".--.-.", "AR" to ".-.-.", "SK" to "...-.-", "KN" to "-.--.",
    )

    /** Spelling alphabets for reading callsigns: letter, ICAO/NATO word, the word Russian operators use for it. */
    val PHONETIC = listOf(
        Triple("A", "Alfa", "Анна"), Triple("B", "Bravo", "Борис"), Triple("C", "Charlie", "Цапля"),
        Triple("D", "Delta", "Дмитрий"), Triple("E", "Echo", "Елена"), Triple("F", "Foxtrot", "Фёдор"),
        Triple("G", "Golf", "Григорий"), Triple("H", "Hotel", "Харитон"), Triple("I", "India", "Иван"),
        Triple("J", "Juliett", "Иван краткий"), Triple("K", "Kilo", "Константин"), Triple("L", "Lima", "Леонид"),
        Triple("M", "Mike", "Михаил"), Triple("N", "November", "Николай"), Triple("O", "Oscar", "Ольга"),
        Triple("P", "Papa", "Павел"), Triple("Q", "Quebec", "Щука"), Triple("R", "Romeo", "Роман"),
        Triple("S", "Sierra", "Сергей"), Triple("T", "Tango", "Татьяна"), Triple("U", "Uniform", "Ульяна"),
        Triple("V", "Victor", "Женя"), Triple("W", "Whiskey", "Василий"), Triple("X", "X-ray", "Мягкий знак"),
        Triple("Y", "Yankee", "Игрек"), Triple("Z", "Zulu", "Зинаида"),
    )

    val PHONETIC_DIGITS = listOf(
        Triple("1", "One", "Единица"), Triple("2", "Two", "Двойка"), Triple("3", "Three", "Тройка"),
        Triple("4", "Four", "Четвёрка"), Triple("5", "Five", "Пятёрка"), Triple("6", "Six", "Шестёрка"),
        Triple("7", "Seven", "Семёрка"), Triple("8", "Eight", "Восьмёрка"), Triple("9", "Nine", "Девятка"),
        Triple("0", "Zero", "Ноль"),
    )

    // ---------- band plan and activity ----------

    /** One segment of a band plan: limits in kHz and what it is for (Russian text). */
    data class Segment(val from: Double, val to: Double, val use: String)

    /** Simplified IARU Region 1 HF band plan: where CW, narrow digital modes, beacons and all modes (SSB) go. */
    val BAND_PLAN = listOf(
        "160 м" to listOf(Segment(1810.0, 1838.0, "CW"), Segment(1838.0, 1843.0, "Узкополосные цифровые"), Segment(1843.0, 2000.0, "Все виды (SSB)")),
        "80 м" to listOf(Segment(3500.0, 3570.0, "CW"), Segment(3570.0, 3600.0, "Узкополосные цифровые"), Segment(3600.0, 3800.0, "Все виды (SSB)")),
        "40 м" to listOf(Segment(7000.0, 7040.0, "CW"), Segment(7040.0, 7060.0, "Узкополосные цифровые"), Segment(7060.0, 7200.0, "Все виды (SSB)")),
        "30 м" to listOf(Segment(10100.0, 10130.0, "CW"), Segment(10130.0, 10150.0, "Узкополосные цифровые")),
        "20 м" to listOf(Segment(14000.0, 14070.0, "CW"), Segment(14070.0, 14099.0, "Узкополосные цифровые"), Segment(14099.0, 14101.0, "Маяки"), Segment(14101.0, 14350.0, "Все виды (SSB)")),
        "17 м" to listOf(Segment(18068.0, 18095.0, "CW"), Segment(18095.0, 18109.0, "Узкополосные цифровые"), Segment(18109.0, 18111.0, "Маяки"), Segment(18111.0, 18168.0, "Все виды (SSB)")),
        "15 м" to listOf(Segment(21000.0, 21070.0, "CW"), Segment(21070.0, 21149.0, "Узкополосные цифровые"), Segment(21149.0, 21151.0, "Маяки"), Segment(21151.0, 21450.0, "Все виды (SSB)")),
        "12 м" to listOf(Segment(24890.0, 24915.0, "CW"), Segment(24915.0, 24929.0, "Узкополосные цифровые"), Segment(24929.0, 24931.0, "Маяки"), Segment(24931.0, 24990.0, "Все виды (SSB)")),
        "10 м" to listOf(Segment(28000.0, 28070.0, "CW"), Segment(28070.0, 28190.0, "Узкополосные цифровые"), Segment(28190.0, 28225.0, "Маяки"), Segment(28225.0, 29200.0, "Все виды (SSB)"), Segment(29200.0, 29700.0, "FM, спутники, репитеры")),
    )

    /** Frequencies where a mode lives (MHz, dial frequency for digital modes). */
    val ACTIVITY = listOf(
        "FT8" to "1.840 · 3.573 · 5.357 · 7.074 · 10.136 · 14.074 · 18.100 · 21.074 · 24.915 · 28.074 · 50.313 · 144.174",
        "FT4" to "3.575 · 7.0475 · 10.140 · 14.080 · 18.104 · 21.140 · 24.919 · 28.180 · 50.318 · 144.170",
        "JS8Call" to "3.578 · 7.078 · 10.130 · 14.078 · 18.104 · 21.078 · 24.922 · 28.078 · 50.318",
        "WSPR" to "1.8366 · 3.5686 · 7.0386 · 10.1387 · 14.0956 · 18.1046 · 21.0946 · 24.9246 · 28.1246 · 50.293 · 144.489",
        "PSK31" to "3.580 · 7.040 · 10.142 · 14.070 · 18.100 · 21.070 · 24.920 · 28.120",
        "SSTV" to "3.735 · 7.165 · 14.230 · 21.340 · 28.680",
        "QRP CW" to "1.836 · 3.560 · 7.030 · 10.116 · 14.060 · 18.086 · 21.060 · 24.906 · 28.060",
        "QRP SSB" to "3.690 · 7.090 · 14.285 · 21.285 · 28.360",
        "Центры аварийной связи" to "3.760 · 7.110 · 14.300 · 18.160 · 21.360",
        "УКВ вызывные" to "144.050 CW · 144.300 SSB · 145.500 FM · 432.200 SSB · 433.500 FM",
        "APRS" to "144.800",
        "МКС (ISS)" to "145.800 FM (голос, SSTV) · 145.825 APRS · 437.800 репитер",
    )

    /** NCDXF/IARU beacons: each sends 10 s in a 3-minute cycle, moving up one band every 10 s. */
    val NCDXF_FREQS = listOf(14.100, 18.110, 21.150, 24.930, 28.200)
    val NCDXF = listOf(
        "4U1UN" to "ООН, Нью-Йорк", "VE8AT" to "Канада, Нунавут", "W6WX" to "США, Калифорния", "KH6RS" to "Гавайи",
        "ZL6B" to "Новая Зеландия", "VK6RBP" to "Австралия", "JA2IGY" to "Япония", "RR9O" to "Россия, Новосибирск",
        "VR2B" to "Гонконг", "4S7B" to "Шри-Ланка", "ZS6DN" to "ЮАР", "5Z4B" to "Кения", "4X6TU" to "Израиль",
        "OH2B" to "Финляндия", "CS3B" to "Мадейра", "LU4AA" to "Аргентина", "OA4B" to "Перу", "YV5B" to "Венесуэла",
    )

    /** Index of the beacon on band [band] (0 = 14.100) at [epochSeconds] UTC. */
    fun ncdxfBeacon(epochSeconds: Long, band: Int): Int {
        val slot = ((epochSeconds % 180) / 10).toInt()
        return ((slot - band) % 18 + 18) % 18
    }

    // ---------- codes ----------

    val Q_CODES = listOf(
        "QRA" to "Название (позывной) вашей станции", "QRG" to "Точная частота", "QRK" to "Разборчивость сигналов (1–5)",
        "QRL" to "Занято, прошу не мешать / Частота занята?", "QRM" to "Помехи от других станций", "QRN" to "Атмосферные (природные) помехи",
        "QRO" to "Увеличьте мощность / большая мощность", "QRP" to "Уменьшите мощность / малая мощность (до 5 Вт CW)",
        "QRQ" to "Передавайте быстрее", "QRS" to "Передавайте медленнее", "QRT" to "Прекращаю работу", "QRU" to "Для вас ничего нет",
        "QRV" to "Готов к работе", "QRX" to "Подождите, я вызову вас", "QRZ" to "Кто меня вызывает?", "QSA" to "Сила сигналов (1–5)",
        "QSB" to "Замирания сигнала", "QSK" to "Слышу вас между знаками (полный дуплекс CW)", "QSL" to "Подтверждаю приём / QSL-карточка",
        "QSO" to "Радиосвязь", "QSP" to "Передам (ретрансляция)", "QSY" to "Перейдите на другую частоту", "QTH" to "Местоположение станции",
        "QTR" to "Точное время",
    )

    val CW_ABBR = listOf(
        "CQ" to "Всем (общий вызов)", "DE" to "От (перед своим позывным)", "K" to "Приём, перехожу на приём", "KN" to "Приём, только вызванная станция",
        "AR" to "Конец передачи", "SK" to "Конец связи", "BK" to "Прерываю", "R" to "Принял", "RST" to "Оценка сигнала", "5NN" to "599 (сокращённо)",
        "TNX / TKS" to "Спасибо", "TU" to "Спасибо (в конце связи)", "FB" to "Отлично", "OM" to "Коротковолновик", "YL" to "Девушка-коротковолновик",
        "UR" to "Ваш", "HR" to "Здесь", "NAME / OP" to "Имя / оператор", "WX" to "Погода", "RIG" to "Трансивер", "ANT" to "Антенна", "PWR" to "Мощность",
        "PSE" to "Пожалуйста", "AGN" to "Повторите", "ES" to "И", "GM / GA / GE" to "Доброе утро / день / вечер", "GL" to "Удачи",
        "CUAGN" to "До встречи в эфире", "BURO" to "Через QSL-бюро", "73" to "Наилучшие пожелания", "88" to "Любовь и поцелуи (для YL)",
    )

    val RST_R = listOf("1" to "Неразборчиво", "2" to "Едва разборчиво", "3" to "Разборчиво с трудом", "4" to "Разборчиво почти без труда", "5" to "Совершенно разборчиво")
    val RST_S = listOf(
        "1" to "Едва различимые", "2" to "Очень слабые", "3" to "Слабые", "4" to "Удовлетворительные", "5" to "Довольно хорошие",
        "6" to "Хорошие", "7" to "Умеренно сильные", "8" to "Сильные", "9" to "Очень сильные",
    )
    val RST_T = listOf(
        "1" to "Очень грубый, переменный ток", "2" to "Очень грубый, резкий", "3" to "Грубый, выпрямленный без фильтра", "4" to "Грубый, следы фильтрации",
        "5" to "Выпрямленный, сильная пульсация", "6" to "Отфильтрованный, заметная пульсация", "7" to "Почти чистый, следы пульсации",
        "8" to "Почти идеальный, лёгкая модуляция", "9" to "Идеально чистый тон",
    )

    // ---------- cables ----------

    /** dB/100 m at 10, 14, 28, 50, 100, 144, 435, 1296 MHz and velocity factors: DD1US table (27 April 2024). */
    val CABLES = listOf(
        RfCalc.Cable("RG-174", 0.66, listOf(10.0 to 9.6, 14.0 to 11.8, 28.0 to 17.0, 50.0 to 22.0, 100.0 to 31.0, 144.0 to 38.0, 435.0 to 70.0)),
        RfCalc.Cable("RG-58", 0.66, listOf(14.0 to 6.2, 28.0 to 8.0, 50.0 to 11.0, 100.0 to 15.6, 144.0 to 17.8, 435.0 to 33.2, 1296.0 to 64.5)),
        RfCalc.Cable("H155", 0.81, listOf(10.0 to 3.0, 14.0 to 3.4, 28.0 to 4.9, 50.0 to 6.5, 100.0 to 9.3, 144.0 to 11.2, 435.0 to 19.8, 1296.0 to 34.9)),
        RfCalc.Cable("LMR-240", 0.84, listOf(10.0 to 2.5, 14.0 to 3.0, 28.0 to 4.2, 50.0 to 5.7, 100.0 to 8.1, 144.0 to 9.7, 435.0 to 17.1, 1296.0 to 30.0)),
        RfCalc.Cable("Aircell 7", 0.83, listOf(10.0 to 2.2, 14.0 to 3.4, 28.0 to 3.7, 50.0 to 4.5, 100.0 to 6.3, 144.0 to 7.6, 435.0 to 13.8, 1296.0 to 24.8)),
        RfCalc.Cable("RG-213", 0.66, listOf(10.0 to 2.2, 28.0 to 3.1, 50.0 to 4.4, 100.0 to 6.2, 144.0 to 7.9, 435.0 to 14.8, 1296.0 to 27.5)),
        RfCalc.Cable("Ecoflex 10", 0.86, listOf(10.0 to 1.2, 100.0 to 4.0, 144.0 to 4.8, 435.0 to 8.9, 1296.0 to 16.5)),
        RfCalc.Cable("LMR-400", 0.85, listOf(10.0 to 1.3, 14.0 to 1.5, 28.0 to 2.2, 50.0 to 2.9, 100.0 to 4.4, 144.0 to 4.9, 435.0 to 8.8, 1296.0 to 14.8)),
        RfCalc.Cable("Ecoflex 15", 0.86, listOf(10.0 to 0.9, 50.0 to 2.0, 100.0 to 2.8, 144.0 to 3.4, 435.0 to 6.1, 1296.0 to 11.4)),
    )

    /** Frequencies of the cable table columns, MHz. */
    val CABLE_FREQS = listOf(3.6, 14.0, 28.0, 50.0, 145.0, 435.0, 1296.0)

    val SWR_ROWS = listOf(1.1, 1.2, 1.3, 1.5, 1.7, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0)

    // ---------- propagation and time ----------

    val SFI = listOf(
        "< 70" to "Слабое прохождение, открыты в основном 160–40 м",
        "70–90" to "Удовлетворительно на 40–20 м, верхние диапазоны редко",
        "90–150" to "Хорошо до 15–10 м",
        "> 150" to "Отлично, открыты 10 м и выше (иногда 6 м)",
    )
    val A_INDEX = listOf(
        "0–7" to "Спокойно", "8–15" to "Неустойчиво", "16–29" to "Возмущённо", "30–49" to "Малая магнитная буря",
        "50–99" to "Сильная буря", "100–400" to "Очень сильная буря",
    )
    val K_INDEX = listOf(
        "0–1" to "Спокойно — лучшие условия", "2–3" to "Неустойчиво", "4" to "Возмущённо, хуже на высоких широтах",
        "5" to "Буря G1: ухудшение, полярные трассы закрыты", "6" to "Буря G2", "7" to "Буря G3: КВ почти закрыто на высоких широтах",
        "8–9" to "Буря G4–G5: КВ может пропасть полностью",
    )

    /** Time zones of Russia: UTC offset in hours and where it is used. */
    val RU_ZONES = listOf(
        2 to "Калининград", 3 to "Москва, Санкт-Петербург, большая часть европейской части", 4 to "Самара, Удмуртия, Саратов, Астрахань, Ульяновск",
        5 to "Екатеринбург, Челябинск, Пермь, Тюмень, Уфа, Оренбург", 6 to "Омск", 7 to "Новосибирск, Красноярск, Томск, Кемерово, Барнаул",
        8 to "Иркутск, Улан-Удэ", 9 to "Якутск, Чита, Благовещенск", 10 to "Владивосток, Хабаровск", 11 to "Магадан, Сахалин",
        12 to "Камчатка, Чукотка",
    )

}
