package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.I18n
import ru.r3xed.qsolog.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwlTest {
    @Test
    fun stationMarkGoesIntoContacts() {
        val st = Swl.station(mapOf("MY_RIG" to "IC-7300"), on = true)
        assertTrue(Swl.isSwl(st))
        assertEquals("IC-7300", st["MY_RIG"])
        assertFalse(Swl.isSwl(Swl.station(st, on = false)))
        // A new contact takes the station's fields: it is a report by itself.
        val q = Qso(call = "UA3DEMO", timeUtc = 0, rstSent = "57", adif = st + (Swl.WITH to "R0DEMO"))
        assertTrue(Swl.isSwl(q))
        assertEquals("R0DEMO", Swl.with(q.adif))
    }

    @Test
    fun listenerNumbers() {
        assertTrue(Swl.isListenerNumber("R3A-1234"))
        assertTrue(Swl.isListenerNumber("ua3-170-1234"))
        assertFalse(Swl.isListenerNumber("UA3DEMO"))
        assertFalse(Swl.isListenerNumber("R3A--1"))
    }

    @Test
    fun summaryAndLabel() {
        I18n.chosen = Lang.RU
        try {
            val q = Qso(call = "UA3DEMO", timeUtc = 0, rstSent = "57", adif = mapOf(Swl.FIELD to "Y", Swl.WITH to "R0DEMO"))
            assertEquals("57 · с R0DEMO", Swl.summary(q))
            val html = QslLabels.html(listOf(q), "R3A-1234")
            assertTrue("SWL report · heard working <b>R0DEMO</b>" in html)
            // An ordinary contact has no such line.
            assertFalse("SWL report" in QslLabels.html(listOf(q.copy(adif = emptyMap())), "UA3DEMO"))
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
