package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Whatever is typed into the callsign field, letter by letter, the country lookup answers and does not throw. */
class CallsignTypingTest {
    @Test
    fun onlySuffixesTypedDoNotCrash() {
        // Every part a portable suffix: "A/P" crashed Cty.effective (2.9.6, a tablet closed while typing).
        for (s in listOf("A/P", "P/P", "QRP/P", "M/MM", "/P/A", "A//P", "LH/A/")) {
            Cty.effective(s)
            Cty.lookup(s)
            Callsign.home(s)
        }
        assertEquals("A", Cty.effective("A/P"))
    }

    @Test
    fun randomTypingDoesNotCrash() {
        val alphabet = "AR0UK9/3PMLQМРЁ1"
        val rnd = java.util.Random(7)
        repeat(20_000) {
            val s = String(CharArray(1 + rnd.nextInt(9)) { alphabet[rnd.nextInt(alphabet.length)] })
            for (i in 1..s.length) s.substring(0, i).let { Cty.lookup(it); Callsign.home(it); RussianRegions.of(it) }
        }
    }
}
