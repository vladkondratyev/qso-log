package ru.r3xed.qsolog.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EqslTest {
    private fun at(s: String) = LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val today = LocalDate.of(2026, 10, 18)

    private val log = listOf(
        Qso(id = 1, call = "R0DEMO/P", timeUtc = at("2026-10-01T10:15"), band = "20m", mode = "SSB"),
        Qso(id = 2, call = "OH2DEMO", timeUtc = at("2026-10-02T18:40"), band = "40m", mode = "FT8"),
        Qso(id = 3, call = "DL1DEMO", timeUtc = at("2026-10-03T07:00"), band = "15m", mode = "CW", adif = mapOf(Eqsl.RCVD to "Y")),
    )

    private fun card(call: String, date: String, time: String, band: String, mode: String, extra: Map<String, String> = emptyMap()) =
        mapOf("CALL" to call, "QSO_DATE" to date, "TIME_ON" to time, "BAND" to band, "MODE" to mode, "RST_SENT" to "57", "QSL_SENT" to "Y", "QSL_SENT_VIA" to "E") + extra

    @Test
    fun cardsConfirmTheirContacts() {
        val m = Eqsl.match(
            listOf(
                // USB is SSB; the home callsign matches R0DEMO/P; 20 minutes apart.
                card("R0DEMO", "20261001", "1035", "20M", "SSB", mapOf("SUBMODE" to "USB")),
                card("OH2DEMO", "20261002", "1841", "40m", "FT8", mapOf(Eqsl.RCVD_DATE to "20261005")),
                card("DL1DEMO", "20261003", "0702", "15m", "CW"),
                // Another band: not this contact.
                card("OH2DEMO", "20261002", "1845", "20m", "FT8"),
                card("UA9DEMO", "20261004", "1200", "10m", "FT4"),
            ),
            log, today,
        )
        assertEquals(listOf(1L, 2L), m.confirmed.map { it.id })
        assertEquals("Y", m.confirmed[0].adif[Eqsl.RCVD])
        assertEquals("20261018", m.confirmed[0].adif[Eqsl.RCVD_DATE])
        // The day eQSL gives the card wins.
        assertEquals("20261005", m.confirmed[1].adif[Eqsl.RCVD_DATE])
        assertEquals(1, m.already)
        assertEquals(listOf("OH2DEMO", "UA9DEMO"), m.missing.map { it.call })
    }

    @Test
    fun aCardOutsideTheWindowIsNotTheContact() {
        val m = Eqsl.match(listOf(card("R0DEMO", "20261001", "1100", "20m", "SSB")), log, today)
        assertTrue(m.confirmed.isEmpty())
        assertEquals(1, m.missing.size)
    }

    @Test
    fun aMissingCardBecomesOurContact() {
        val q = Eqsl.toQso(card("UA9DEMO", "20261004", "1200", "10m", "FT4", mapOf("QSLMSG" to "TNX")), "20261018")!!
        assertEquals("57", q.rstRcvd)
        assertEquals("", q.rstSent)
        // Their card is not our paper QSL.
        assertEquals(false, q.qslSent)
        assertEquals("Y", q.adif[Eqsl.RCVD])
        assertEquals("TNX", q.adif["QSLMSG"])
        assertNull(q.adif["QSL_SENT_VIA"])
        assertEquals("18.10.2026", Eqsl.receivedAt(q.adif))
    }

    @Test
    fun pages() {
        val ok = """<html>Your ADIF log file has been built. <A HREF="../downloadedfiles/abc123.adi">.ADI file</A> <a href="../downloadedfiles/abc123.txt">.TXT</a>"""
        assertEquals("../downloadedfiles/abc123.adi", Eqsl.fileLink(ok))
        val bad = "<html><body>Error: No such Username/Password found</body></html>"
        assertNull(Eqsl.fileLink(bad))
        assertEquals("No such Username/Password found", Eqsl.pageError(bad))
    }

    @Test
    fun uploadAnswer() {
        val a = OnlineLogs.parseEqsl("<!-- Reply form eQSL.cc ADIF Real-time Interface -->\nResult: 2 out of 3 records added<BR>Warning: Y=2026 M=10 D=01 Bad record: Duplicate<BR>")
        assertEquals(2, a.added)
        assertEquals(1, a.duplicates)
        assertNull(a.error)
        assertEquals("No match on eQSL_User/eQSL_Pswd", OnlineLogs.parseEqsl("Error: No match on eQSL_User/eQSL_Pswd<BR>").error)
    }
}
