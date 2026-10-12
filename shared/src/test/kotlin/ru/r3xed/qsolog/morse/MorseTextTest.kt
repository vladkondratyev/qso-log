package ru.r3xed.qsolog.morse

import kotlin.test.Test
import kotlin.test.assertEquals

class MorseTextTest {
    @Test
    fun latinAndCyrillicMixed() {
        val p = MorseText.plan("cq  Привет!\nёж")
        assertEquals(listOf('c', 'q', ' ', 'П', 'р', 'и', 'в', 'е', 'т', ' ', 'ё', 'ж'), p.map { it.char })
        assertEquals("-.-.", p[0].code)
        assertEquals(null, p[2].code)
        assertEquals(".--.", p[3].code)
        // Ё is sent as Е.
        assertEquals(".", p[10].code)
        // The index points into the text: the highlight follows it.
        assertEquals(4, p[3].index)
        assertEquals(12, p[10].index)
    }

    @Test
    fun noPauseAtTheEdges() {
        assertEquals(listOf('A'), MorseText.plan("  A  ").map { it.char })
        assertEquals(emptyList(), MorseText.plan(" \n "))
    }

    @Test
    fun parisIsFiftyDots() {
        // PARIS and the word gap: 50 dots, one minute at 1 WPM — so 1 second per word at 60.
        val p = MorseText.plan("PARIS PARIS").take(6)
        assertEquals(50, p.map { MorseText.dots(it) }.sum())
        assertEquals(1000L, MorseText.durationMs(p, 60))
    }
}
