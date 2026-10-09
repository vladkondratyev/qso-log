package ru.r3xed.qsolog.data

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MorseToneTest {
    private val dot = (MorseTone.RATE * 1.2 / 18).toInt()

    @Test
    fun lengthsFollowTheTiming() {
        // А ·−: dot, gap, dash, tail of two dots.
        assertEquals(dot + dot + 3 * dot + 2 * dot, MorseTone.pcm(".-").size, absoluteTolerance = 3)
        assertEquals(0, MorseTone.pcm("").size)
    }

    @Test
    fun noClickAtTheEdgesAndSilenceBetween() {
        val p = MorseTone.pcm(".-")
        assertTrue(abs(p[0].toInt()) < 200)
        assertTrue((dot + 10 until 2 * dot - 10).all { p[it].toInt() == 0 })
        assertTrue(p.maxOf { it.toInt() } > 15_000)
    }

    @Test
    fun everyCyrillicLetterHasAChantOfMatchingLength() {
        Reference.MORSE_CYRILLIC.forEach { (ch, code) ->
            val chant = Reference.MORSE_CHANTS[ch] ?: error("no chant for $ch")
            val syll = chant.split('-')
            assertEquals(code.length, syll.size, "$ch $chant")
            // Long syllables (capitals) where the dashes are.
            syll.zip(code.toList()).forEach { (s, c) -> assertEquals(c == '-', s == s.uppercase() && s != s.lowercase(), "$ch $chant") }
        }
    }

    private fun assertEquals(expected: Int, actual: Int, absoluteTolerance: Int) = assertTrue(abs(expected - actual) <= absoluteTolerance, "$expected vs $actual")

    @Test
    fun everyCyrillicLetterHasItsLatinTwin() {
        assertTrue(Reference.MORSE_CYRILLIC.all { Reference.MORSE_LATIN_TWIN[it.first].orEmpty().isNotEmpty() })
        kotlin.test.assertEquals("W", Reference.MORSE_LATIN_TWIN["В"])
        kotlin.test.assertEquals("Q", Reference.MORSE_LATIN_TWIN["Щ"])
        kotlin.test.assertEquals("CH", Reference.MORSE_LATIN_TWIN["Ш"])
    }
}
