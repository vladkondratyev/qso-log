package ru.r3xed.qsolog.digi

import ru.r3xed.qsolog.Form
import ru.r3xed.qsolog.data.Qso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigiCardTest {
    private val logged = Qso(call = "R0DEMO", timeUtc = 0, mode = "FT8", rstSent = "-10", rstRcvd = "-12", locator = "KO85", comment = "FT8 Sent: -10")

    @Test
    fun `the card adds the station and the note, the program keeps its reports`() {
        val card = Form(
            call = "R0DEMO", name = "Ivan", qth = "Moscow", locator = "KO85ts", lat = 55.8, lon = 37.6,
            comment = "73 tnx", audio = "a.m4a", rstSent = "599", adif = mapOf("STATE" to "MA", "MY_RIG" to "x"),
        )
        val q = logged.withCard(card)
        assertEquals("Ivan", q.name)
        assertEquals("Moscow", q.qth)
        assertEquals("KO85ts", q.locator)
        assertEquals(55.8, q.lat)
        assertEquals("-10", q.rstSent)
        assertEquals("FT8 Sent: -10 73 tnx", q.comment)
        assertEquals("a.m4a", q.audio)
        assertEquals("MA", q.adif["STATE"])
        assertFalse("MY_RIG" in q.adif)
    }

    @Test
    fun `a locator the program disagrees with is the program's`() {
        val q = logged.withCard(Form(call = "R0DEMO", lat = 48.5, lon = 132.5, adif = mapOf(ru.r3xed.qsolog.data.HamQth.POSITION_FIELD to ru.r3xed.qsolog.data.HamQth.POSITION_REGION)))
        assertEquals("KO85", q.locator)
        assertEquals(null, q.lat)
        assertFalse(ru.r3xed.qsolog.data.HamQth.POSITION_FIELD in q.adif)
    }

    @Test
    fun `the setting is kept`() {
        val c = DigiConfig(enabled = true, openCard = true)
        assertTrue(DigiConfig.fromMap(c.toMap()).openCard)
        assertFalse(DigiConfig.fromMap(emptyMap()).openCard)
    }
}
