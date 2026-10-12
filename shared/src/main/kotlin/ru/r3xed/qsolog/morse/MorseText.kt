package ru.r3xed.qsolog.morse

/** One step of a text played in Morse: a character at [index] of the text with its [code], or a pause between words (code null). */
data class MorseTextItem(val index: Int, val char: Char, val code: String?)

/**
 * Any text, Latin and Cyrillic mixed, as Morse: what is played, character by character, and how long it takes. Characters
 * without a signal are skipped; Ё is sent as Е; spaces and line breaks become one pause between words.
 */
object MorseText {
    fun plan(text: String): List<MorseTextItem> {
        val out = ArrayList<MorseTextItem>()
        text.forEachIndexed { i, c ->
            if (c.isWhitespace()) {
                // One pause for any run of spaces, none at the start.
                if (out.isNotEmpty() && out.last().code != null) out += MorseTextItem(i, ' ', null)
                return@forEachIndexed
            }
            val code = MorseCode.code(if (c == 'ё' || c == 'Ё') 'Е' else c) ?: return@forEachIndexed
            out += MorseTextItem(i, c, code)
        }
        if (out.lastOrNull()?.code == null) out.removeLastOrNull()
        return out
    }

    /**
     * Length of a step in dots: the elements (dash = 3) with a dot between them and the three-dot gap after the character;
     * a pause adds four more, seven in all between words.
     */
    fun dots(item: MorseTextItem): Int {
        val code = item.code ?: return 4
        return 3 * code.count { it == '-' } + code.count { it == '.' } + (code.length - 1) + 3
    }

    /** How long [items] take at [wpm], ms (PARIS: a dot is 1200 / WPM ms). */
    fun durationMs(items: List<MorseTextItem>, wpm: Int): Long = items.sumOf { dots(it) } * 1200L / wpm
}
