package ru.r3xed.qsolog.data

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CabrilloTest {
    private fun at(s: String) = LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val log = listOf(
        Qso(call = "UA2DEMO", timeUtc = at("2008-09-23T07:11"), band = "10m", mode = "CW", freqMhz = "28.009",
            rstSent = "599", rstRcvd = "599", myCall = "UA1DEMO", adif = mapOf("SRX_STRING" to "001")),
        Qso(call = "R9DEMO", timeUtc = at("2008-09-23T07:05"), band = "40m", mode = "SSB", freqMhz = "",
            rstSent = "59", rstRcvd = "57", myCall = "UA1DEMO", name = "Сергей"),
    )

    @Test
    fun ermakQsoLineUsesTheSpecColumns() {
        val text = Cabrillo.export(log, Cabrillo.Header(Cabrillo.Format.ERMAK, "r3x-champ", "UA1DEMO", location = "KG03"))
        val lines = text.split("\r\n")
        assertEquals("START-OF-LOG: 3.0", lines.first())
        assertTrue("CONTEST: R3X-CHAMP" in lines)
        assertTrue("LOCATION: KG03" in lines)
        assertTrue("CATEGORY-MODE: MIXED" in lines)
        // Chronological: the 07:05 contact first, sent serial numbers follow that order.
        val qso = lines.filter { it.startsWith("QSO:") }
        assertEquals("QSO:  7000 PH 2008-09-23 0705 UA1DEMO       59  001    R9DEMO        57", qso[0])
        assertEquals("QSO: 28009 CW 2008-09-23 0711 UA1DEMO       599 002    UA2DEMO       599 001", qso[1])
        // Field starts from the spec: frequency 6, mode 12, date 15, time 26, calls 31 and 56.
        assertEquals('2', qso[1][5]); assertEquals('C', qso[1][11]); assertEquals('2', qso[1][14])
        assertEquals('0', qso[1][25]); assertEquals('U', qso[1][30]); assertEquals('U', qso[1][55])
        assertEquals("END-OF-LOG:", lines[lines.size - 2])
    }

    @Test
    fun cabrilloIsAsciiAndUsesItsOwnModes() {
        val psk = log + Qso(call = "OH2DEMO", timeUtc = at("2008-09-23T08:00"), band = "20m", mode = "PSK31", myCall = "UA1DEMO")
        val h = Cabrillo.Header(Cabrillo.Format.CABRILLO, "cq-ww-rtty", "UA1DEMO", operators = "Иванов")
        val text = Cabrillo.export(psk, h)
        assertTrue(text.all { it.code < 128 })
        assertTrue("OPERATORS: Ivanov" in text)
        assertTrue(text.lines().any { it.startsWith("QSO: 14000 DG ") })
        assertEquals("PS", Cabrillo.modeCode("PSK31", Cabrillo.Format.ERMAK))
    }

    @Test
    fun specExamplesAreRead() {
        val report = """
            START-OF-LOG: 3.0
            CONTEST: R3X-CHAMP
            CALLSIGN: UA1XYZ
            QSO: 28009 CW 2008-09-23 0711 UA1XYZ        599 001    UA2XYZ        599 001    0
            QSO: 28009 CW 2008-09-23 0712 UA1XYZ        599  AC123 UA2XYZ        599  001
            QSO: 28009 CW 2008-09-23 0713 UA1XYZ        KR 10 001  UA2XYZ        KR 2  005
            QSO: 28009 CW 2008-09-23 0714 UA1XYZ        599 27  UA0KAA/U 33  UA2XYZ        599  45 UA3VCS   33
            QSO:  3559 RY 2007-10-19 1611 UA8AA         599 KO85MM 50 UA3XYZ        599 LO65AA 35
            QSO: 144   PH 2008-09-23 0715 UA1XYZ        59  001    UA4XYZ        59  002
            END-OF-LOG:
        """.trimIndent()
        val r = Cabrillo.import(report.byteInputStream())
        assertEquals(0, r.skipped)
        assertEquals(listOf("UA2XYZ", "UA2XYZ", "UA2XYZ", "UA2XYZ", "UA3XYZ", "UA4XYZ"), r.rows.map { it.call })
        val first = r.rows[0]
        assertEquals("10m", first.band); assertEquals("28.009", first.freqMhz); assertEquals("CW", first.mode)
        assertEquals("599", first.rstSent); assertEquals("001", first.adif["STX_STRING"]); assertEquals("001", first.adif["SRX_STRING"])
        assertEquals("R3X-CHAMP", first.adif["CONTEST_ID"])
        assertEquals("AC123", r.rows[1].adif["STX_STRING"])
        assertEquals("KR 2 005", r.rows[2].adif["SRX_STRING"])
        assertEquals("RTTY", r.rows[4].mode); assertEquals("80m", r.rows[4].band)
        assertEquals("2m", r.rows[5].band); assertEquals("SSB", r.rows[5].mode)
    }

    @Test
    fun exportThenImportKeepsTheContacts() {
        val text = Cabrillo.export(log, Cabrillo.Header(Cabrillo.Format.ERMAK, "RDXC", "UA1DEMO"))
        val back = Cabrillo.import(text.byteInputStream()).rows
        assertEquals(listOf("R9DEMO", "UA2DEMO"), back.map { it.call })
        assertEquals(listOf("40m", "10m"), back.map { it.band })
        assertEquals(listOf("SSB", "CW"), back.map { it.mode })
        assertEquals(log.sortedBy { it.timeUtc }.map { it.timeUtc }, back.map { it.timeUtc })
    }
}
