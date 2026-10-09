package ru.r3xed.qsolog.morse

import ru.r3xed.qsolog.data.Reference

/** Signal ↔ character, for the trainer: Latin or Cyrillic letters, plus digits and signs. */
object MorseCode {
    private val digitsAndSigns = (Reference.MORSE_DIGITS + Reference.MORSE_SIGNS.filter { it.first.length == 1 })

    private val latin = (Reference.MORSE_LATIN + digitsAndSigns).associate { (c, code) -> code to c[0] }
    private val cyrillic = (Reference.MORSE_CYRILLIC + digitsAndSigns).associate { (c, code) -> code to c[0] }
    private val toCode = (Reference.MORSE_LATIN + Reference.MORSE_CYRILLIC + digitsAndSigns).associate { (c, code) -> c[0] to code }

    /** The character of a signal (".-" → A or А), or null when there is no such signal. */
    fun char(code: String, cyrillic: Boolean): Char? = (if (cyrillic) this.cyrillic else latin)[code]

    fun code(c: Char): String? = toCode[c.uppercaseChar()]

    /**
     * The order of the Koch method: two characters first, one more each time 90% are right. The Latin order is
     * the classic one; the Cyrillic one follows it through the letters with the same signals (K → К, W → В …),
     * the Russian letters without a Latin twin come last.
     */
    val KOCH_LATIN = "KMRSUAPTLOWI.NJEF0Y,VG5/Q9ZH38B?427C1D6X"
    val KOCH_CYRILLIC: String = KOCH_LATIN.mapNotNull { c ->
        val code = code(c) ?: return@mapNotNull null
        if (c.isLetter()) this.cyrillic[code]?.takeIf { it in 'А'..'Я' } else c
    }.joinToString("") + "ЧШЭЮЯЪ"

    fun koch(cyrillic: Boolean) = if (cyrillic) KOCH_CYRILLIC else KOCH_LATIN
}
