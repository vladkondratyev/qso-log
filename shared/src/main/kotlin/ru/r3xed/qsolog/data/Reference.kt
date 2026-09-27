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
}
