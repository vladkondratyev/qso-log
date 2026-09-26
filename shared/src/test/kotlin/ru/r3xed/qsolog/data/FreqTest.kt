package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class FreqTest {
    @Test
    fun kilohertzAreRecognised() {
        assertEquals(14.195, freqMhz("14195"))
        assertEquals("14.195", normalizeFreq("14195"))
        assertEquals("7.0745", normalizeFreq("7074.5"))
        assertEquals("1.84", normalizeFreq("1840"))
        assertEquals("144.3", normalizeFreq("144300"))
        assertEquals("20m", bandForFreq(freqMhz("14195")!!))
    }

    @Test
    fun megahertzStay() {
        assertEquals("14.195", normalizeFreq("14.195"))
        assertEquals("14.195", normalizeFreq("14,195"))
        assertEquals("1296.2", normalizeFreq("1296.2"))
        assertEquals(1296.2, freqMhz("1296.2"))
        // Not a band either way (typing in progress): left as typed.
        assertEquals("1419", normalizeFreq("1419"))
        assertEquals("", normalizeFreq(""))
    }
}
