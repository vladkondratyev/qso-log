package ru.r3xed.qsolog.data

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportMarkTest {
    private val q = Qso(id = 3, call = "R0DEMO", timeUtc = 1_790_000_000_000, band = "20m", mode = "SSB", adif = mapOf("STATE" to "KG"))

    @Test
    fun markKeepsTheTimeAndCanBeTakenOff() {
        val m = ExportFormat.ADIF.mark(q, LocalDateTime.of(2026, 9, 27, 10, 15))
        assertEquals("2026-09-27 10:15", m.adif["APP_QSOLOG_EXPORT_ADIF"])
        assertEquals("27.09.2026 10:15 UTC", ExportFormat.ADIF.exportedAt(m.adif))
        assertTrue(ExportFormat.ADIF.isExported(m.adif))
        // Each format has its own mark.
        assertFalse(ExportFormat.CSV.isExported(m.adif))
        assertNull(ExportFormat.CONTEST.exportedAt(m.adif))
        val off = ExportFormat.ADIF.unmarkFields(m.adif)
        assertFalse(ExportFormat.ADIF.isExported(off))
        assertEquals("KG", off["STATE"])
        assertEquals(3, ExportFormat.FIELDS.size)
    }

    @Test
    fun marksTravelThroughAdif() {
        val m = ExportFormat.CONTEST.mark(q, LocalDateTime.of(2026, 9, 1, 8, 0))
        val out = java.io.ByteArrayOutputStream()
        Adif.export(listOf(m), out)
        val back = Adif.import(out.toByteArray().inputStream()).rows.single()
        assertEquals("01.09.2026 08:00 UTC", ExportFormat.CONTEST.exportedAt(back.adif))
    }
}
