package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CallsignTest {
    @Test
    fun portableFormsSearchTheHomeCall() {
        assertEquals("R0DEMO", Callsign.home("r0demo/p"))
        assertEquals("R0DEMO", Callsign.home("R0DEMO/M"))
        assertEquals("R0DEMO", Callsign.home("R0DEMO/QRP"))
        assertEquals("R0DEMO", Callsign.home("R0DEMO/3"))
        assertEquals("R0DEMO", Callsign.home("DL/R0DEMO"))
        assertEquals("R0DEMO", Callsign.home("DL/R0DEMO/P"))
        assertEquals("K1DEMO", Callsign.home("VP2E/K1DEMO"))
        // Whatever follows the slash: one letter, digits, several letters, letters and digits.
        for (s in listOf("A", "1", "73", "MM", "AM", "LH", "9A", "QRPP", "ABC1"))
            assertEquals("R0DEMO", Callsign.home("R0DEMO/$s"), "R0DEMO/$s")
        assertEquals("R0DEMO", Callsign.home("9A/R0DEMO"))
        assertEquals("UA3DEMO", Callsign.home(" UA3DEMO "))
        assertEquals("", Callsign.home(""))
    }

    @Test
    fun logMatchesEveryFormOfTheStation() {
        assertEquals(listOf("R0DEMO", "R0DEMO/%", "%/R0DEMO", "%/R0DEMO/%"), Callsign.likeArgs("R0DEMO/P"))
    }
}
