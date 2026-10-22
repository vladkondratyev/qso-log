package ru.r3xed.qsolog.data

import java.security.Signature
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LotwTest {
    /** A certificate made like TQSL's (made-up R0DEMO, DXCC 54, QSOs from 2020-01-01), with keytool. */
    private val p12 = javaClass.getResourceAsStream("/lotw-test.p12")!!.readBytes()

    private fun qso(id: Long, call: String, band: String, mode: String, freq: String, t: LocalDateTime, myCall: String = "") =
        Qso(id = id, call = call, timeUtc = t.toInstant(ZoneOffset.UTC).toEpochMilli(), band = band, mode = mode, freqMhz = freq, myCall = myCall)

    @Test
    fun readsTheCertificate() {
        val c = Lotw.loadP12(p12, "demo123")
        assertEquals("R0DEMO", c.callsign)
        assertEquals(54, c.dxcc)
        assertEquals(LocalDate.of(2020, 1, 1), c.qsoFrom)
        assertFailsWith<LotwException> { Lotw.loadP12(p12, "wrong") }
        assertFailsWith<LotwException> { Lotw.loadP12("not a p12".toByteArray(), "") }
    }

    @Test
    fun signsAsTqslDoes() {
        val c = Lotw.loadP12(p12, "demo123")
        val t = LocalDateTime.of(2026, 10, 20, 12, 17, 5)
        val list = listOf(
            qso(1, "dl9demo", "20m", "FT8", "14.074", t),
            qso(2, "OH2DEMO", "40m", "SSB", "7.1", t.plusMinutes(3), myCall = "R0DEMO"),
            qso(3, "OK9DEMO", "20m", "CW", "", t, myCall = "R0DEMO/P"), // another callsign: not this certificate's
            qso(4, "OK9DEMO", "20m", "CW", "", LocalDateTime.of(2019, 5, 1, 10, 0)), // before the certificate's dates
        )
        assertEquals(listOf(3L, 4L), Lotw.unsignable(c, list).map { it.id })
        val text = Lotw.signedLog(c, Lotw.Station("KO85ts", cqZone = "16", ituZone = "29"), list)
        assertTrue(text.contains("<Rec_Type:5>tCERT"))
        assertTrue(text.contains("<CALL:6>R0DEMO\n<DXCC:2>54\n<GRIDSQUARE:6>KO85ts\n<ITUZ:2>29\n<CQZ:2>16\n"))
        assertEquals(2, Regex("<Rec_Type:8>tCONTACT").findAll(text).count())
        // CQZ, GRIDSQUARE, ITUZ, then BAND, CALL, FREQ, MODE, QSO_DATE, QSO_TIME — upper case.
        val signData = "16KO85TS2920MDL9DEMO14.074FT82026-10-2012:17:05Z"
        assertTrue(text.contains("<SIGNDATA:${signData.length}>$signData\n"), text)
        // The signature (base64 in lines of 64, 1024-bit key: 175 characters with the new lines) checks with the certificate.
        val sig = Regex("<SIGN_LOTW_V2.0:(\\d+):6>([A-Za-z0-9+/=\\n]+?)<SIGNDATA:\\d+>" + Regex.escape(signData)).find(text)!!
        assertEquals("175", sig.groupValues[1])
        val v = Signature.getInstance("SHA1withRSA")
        v.initVerify(c.cert.publicKey)
        v.update(signData.toByteArray())
        assertTrue(v.verify(Base64.getMimeDecoder().decode(sig.groupValues[2])))
        // The .tq8 is the same text, gzipped.
        assertEquals(text, GZIPInputStream(Lotw.tq8(text).inputStream()).readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun uploadAnswer() {
        assertEquals(true to "File queued for processing", Lotw.parseUpload("<html><!-- .UPL. accepted --><!-- .UPLMESSAGE. File queued for processing --></html>"))
        assertEquals(false, Lotw.parseUpload("<!-- .UPL. rejected --><!-- .UPLMESSAGE. Signature invalid -->").first)
        assertEquals("FT4", Lotw.mode(Qso(call = "X", timeUtc = 0, band = "20m", mode = "MFSK", adif = mapOf("SUBMODE" to "FT4"))))
        assertEquals("SSB", Lotw.mode(Qso(call = "X", timeUtc = 0, band = "20m", mode = "USB")))
        assertEquals("DATA", Lotw.mode(Qso(call = "X", timeUtc = 0, band = "20m", mode = "JS8")))
    }

    @Test
    fun confirmationsMarkTheLog() {
        val t = LocalDateTime.of(2026, 10, 18, 4, 30, 14)
        val log = listOf(qso(1, "RT8DEMO", "20m", "FT8", "14.076", t), qso(2, "LU6DEMO", "15m", "FT8", "21.074", t.plusHours(14)))
        val records = listOf(
            mapOf("CALL" to "RT8DEMO", "BAND" to "20M", "MODE" to "FT8", "QSO_DATE" to "20261018", "TIME_ON" to "043000", "QSL_RCVD" to "Y", "QSLRDATE" to "20261019"),
            mapOf("CALL" to "N0DEMO", "BAND" to "20M", "MODE" to "FT8", "QSO_DATE" to "20261018", "TIME_ON" to "043000", "QSL_RCVD" to "Y"),
        )
        val m = Lotw.match(records, log)
        assertEquals(listOf(1L), m.confirmed.map { it.id })
        assertEquals("20261019", m.confirmed[0].adif[Lotw.RCVD_DATE])
        assertEquals(1, m.notFound)
        assertEquals(1, Lotw.match(records, m.confirmed + log[1]).already)
    }
}
