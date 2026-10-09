package ru.r3xed.qsolog.morse

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MorseTrainerTest {
    /** Keys [code] on a straight key at [dot] ms, returns the time after the last element. */
    private fun key(d: MorseDecoder, start: Long, code: String, dot: Long): Long {
        var t = start
        code.forEach { c ->
            d.down(t); t += if (c == '-') 3 * dot else dot
            d.up(t); t += dot
        }
        return t
    }

    @Test
    fun straightKeyDecodesLettersAndWords() {
        val d = MorseDecoder(18, cyrillic = false, adaptive = true)
        var t = key(d, 0, ".-", 67) + 2 * 67 // A, then a letter gap
        t = key(d, t, "-...", 67) + 6 * 67   // B, then a word gap
        d.settle(t)
        t = key(d, t, "...", 67)
        d.settle(t + 3 * 67)
        assertEquals("AB S", d.text)
    }

    @Test
    fun straightKeyFollowsASlowerOperator() {
        val d = MorseDecoder(25, cyrillic = true, adaptive = true)
        var t = 0L
        // 10 WPM (120 ms dots) on a decoder set to 25: after a few letters it follows.
        repeat(3) { t = key(d, t, "-.-", 120) + 3 * 120 }
        d.settle(t)
        assertTrue(d.text.endsWith("ККК") || d.text.endsWith("КК"), d.text)
        assertTrue(d.wpm in 8..14, "${d.wpm}")
    }

    @Test
    fun iambicKeyerAlternatesWhenSqueezed() {
        val s = MorseKeySession(20, KeyType.IAMBIC_A, cyrillic = false)
        // Dah paddle first, then the dit one too: −·−· (C); released during the fourth element (10…11 dots).
        var t = 0L
        val dot = 60L
        s.dah = true
        while (t < dot / 2) { s.toneAt(t); t += 2 }
        s.dit = true
        while (t < 21 * dot / 2) { s.toneAt(t); t += 2 }
        s.dah = false; s.dit = false
        while (t < 30 * dot) { s.toneAt(t); t += 2 }
        val text = s.read(t) { it.text.trim() }
        assertEquals("C", text)
    }

    @Test
    fun kochOrderAndGroups() {
        assertEquals("KMRSUAPT", MorseCode.KOCH_LATIN.take(8))
        assertEquals("КМРСУАПТ", MorseCode.KOCH_CYRILLIC.take(8))
        assertTrue("ЧШЭЮЯЪ".all { it in MorseCode.KOCH_CYRILLIC })
        val g = MorseLesson.group(cyrillic = false, level = 3, rnd = Random(1))
        assertEquals(5, g.length)
        assertTrue(g.all { it in "KMR" } && 'R' in g)
        assertEquals(4 to listOf('M'), MorseLesson.check("KMRKS", "kkrks"))
        // The same signal typed in the other alphabet counts: K for К, W for В.
        assertEquals(5 to emptyList(), MorseLesson.check("КМВКМ", "kmwkm", cyrillic = true))
        assertEquals("КМВ", MorseLesson.normalize("kmw", cyrillic = true))
    }

    @Test
    fun farnsworthStretchesOnlyTheGaps() {
        val fast = MorseLesson.pcm("EE", 20, 20).size
        val slow = MorseLesson.pcm("EE", 20, 10).size
        assertTrue(slow > fast)
    }
}
