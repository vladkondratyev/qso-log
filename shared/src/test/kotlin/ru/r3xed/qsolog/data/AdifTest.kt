package ru.r3xed.qsolog.data

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADIF import/export on a made-up log shaped like a LogHX export (Windows-1251, Cyrillic, extra fields). */
class AdifTest {
    private fun field(name: String, value: String) = "<$name:${value.toByteArray(Adif.WINDOWS_1251).size}>$value "

    /** Two contacts of a fictional station; field set and quirks follow a real LogHX file. */
    private val sample: ByteArray = buildString {
        append("ADIF Export from LogHX\r\n")
        append(field("ADIF_VER", "3.0.4"))
        append("<EOH>\r\n")
        append(field("CALL", "R9DEMO") + field("QSO_DATE", "20250108") + field("TIME_ON", "160100") + field("FREQ", "7.135"))
        append(field("BAND", "40M") + field("MODE", "SSB") + field("RST_RCVD", "59") + field("RST_SENT", "59"))
        append(field("NAME", "Демо") + field("QTH", "Городок ") + field("GRIDSQUARE", "R9DEMO"))
        append(field("OPERATOR", "R0TEST") + field("MY_GRIDSQUARE", "JO00aa") + field("A_INDEX", "12") + field("STATE", "MA"))
        append(field("QSO_DATE_OFF", "20250108") + field("TIME_OFF", "160100") + field("BAND_RX", "40M"))
        append(field("QSL_VIA", "L;E") + field("STATION_CALLSIGN", "R0TEST") + "<EOR>\r\n")
        append(field("CALL", "DL1DEMO") + field("QSO_DATE", "20250121") + field("TIME_ON", "1253") + field("FREQ", "14.1575"))
        append(field("BAND", "20M") + field("MODE", "CW") + field("NAME", "Test") + field("GRIDSQUARE", "JO53ao"))
        append(field("STATION_CALLSIGN", "R0TEST") + "<EOR>\r\n")
    }.toByteArray(Adif.WINDOWS_1251)

    @Test
    fun importsWindows1251Log() {
        val r = Adif.import(sample.inputStream())
        assertEquals(Adif.WINDOWS_1251, r.charset)
        assertEquals(2, r.rows.size)
        assertEquals(0, r.skipped)
        val first = r.rows.first { it.call == "R9DEMO" }
        assertEquals("40m", first.band)
        assertEquals("SSB", first.mode)
        assertEquals("7.135", first.freqMhz)
        assertEquals("Демо", first.name)
        assertEquals("Городок", first.qth) // trailing space trimmed
        assertEquals("R0TEST", first.myCall)
        assertEquals("JO00aa", first.myLocator)
        assertEquals("", first.locator) // GRIDSQUARE "R9DEMO" is not a locator…
        assertEquals("R9DEMO", first.adif["GRIDSQUARE"]) // …but it is kept
        assertEquals("12", first.adif["A_INDEX"])
        assertEquals("L;E", first.adif["QSL_VIA"])
        val second = r.rows.first { it.call == "DL1DEMO" }
        assertEquals("JO53ao", second.locator)
        assertEquals(12 * 60 + 53, (second.timeUtc / 60_000 % (24 * 60)).toInt()) // 4-digit TIME_ON
    }

    @Test
    fun roundTripKeepsEveryField() {
        val original = Adif.parseRecords(sample, Adif.WINDOWS_1251)
        val rows = Adif.import(sample.inputStream()).rows
        for (charset in listOf(Adif.WINDOWS_1251, Charsets.UTF_8)) {
            val out = ByteArrayOutputStream()
            Adif.export(rows, out, charset)
            val again = Adif.import(out.toByteArray().inputStream())
            assertEquals(charset, again.charset)
            assertEquals(rows.size, again.rows.size)
            val reparsed = Adif.parseRecords(out.toByteArray(), charset).associateBy { it["CALL"] + it["QSO_DATE"] }
            for (rec in original) {
                val back = reparsed.getValue(rec["CALL"] + rec["QSO_DATE"])
                for ((k, v) in rec) {
                    // Import trims values; bands are written in lower case; a 4-digit time comes back with seconds.
                    val expected = when (k) {
                        "BAND", "BAND_RX" -> v.trim().lowercase()
                        "TIME_ON" -> v.trim().padEnd(6, '0')
                        else -> v.trim()
                    }
                    val actual = if (k == "BAND_RX") back[k]?.lowercase() else back[k]
                    assertEquals(expected, actual, "${rec["CALL"]} $k in $charset")
                }
            }
        }
    }

    @Test
    fun parsesUtf8AndCoordinates() {
        val text = "<CALL:6>R9DEMO <QSO_DATE:8>20260925 <TIME_ON:4>1311 <MODE:4>MFSK <SUBMODE:3>FT4 <NAME:4>Демо <LAT:11>N056 36.978 <LON:11>E059 52.356 <EOR>"
        val q = Adif.import(text.toByteArray(Charsets.UTF_8).inputStream()).rows.single()
        assertEquals("FT4", q.mode)
        assertEquals("Демо", q.name)
        assertEquals(56.6163, q.lat!!, 1e-4)
        assertEquals(59.8726, q.lon!!, 1e-4)
        assertEquals(null, q.adif["SUBMODE"])
    }

    @Test
    fun extraFieldsSurviveTheDatabaseColumnFormat() {
        val m = linkedMapOf("QSL_VIA" to "L;E", "NOTES" to "a <b> c", "MY_CNTY" to "DEMO-DISTRICT")
        assertEquals(m, Adif.decodeFields(Adif.encodeFields(m)))
    }
}
