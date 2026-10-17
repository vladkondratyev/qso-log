package ru.r3xed.qsolog.data

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QsoPickTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDate.of(y, m, d).atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val log = listOf(
        Qso(id = 1, call = "R1DEMO", timeUtc = at(2026, 10, 4, 23), band = "40m", mode = "SSB"),
        Qso(id = 2, call = "R2DEMO", timeUtc = at(2026, 10, 5, 0), band = "20m", mode = "FT8"),
        Qso(id = 3, call = "R3DEMO", timeUtc = at(2026, 10, 6, 23), band = "20M", mode = "ft8"),
        Qso(id = 4, call = "R4DEMO", timeUtc = at(2026, 10, 7, 0), band = "2m", mode = "FM"),
    )

    @Test
    fun datesIncludeBothEndsInUtc() {
        assertEquals(setOf(2L, 3L), QsoPick(from = LocalDate.of(2026, 10, 5), to = LocalDate.of(2026, 10, 6)).ids(log))
        assertEquals(setOf(3L, 4L), QsoPick(from = LocalDate.of(2026, 10, 6)).ids(log))
        assertEquals(setOf(1L), QsoPick(to = LocalDate.of(2026, 10, 4)).ids(log))
        assertEquals(setOf(1L, 2L, 3L, 4L), QsoPick().ids(log))
    }

    @Test
    fun bandsAndModesIgnoreCase() {
        assertEquals(setOf(2L, 3L), QsoPick(bands = setOf("20m")).ids(log))
        assertEquals(setOf(2L, 3L, 4L), QsoPick(modes = setOf("FT8", "FM")).ids(log))
        assertEquals(setOf(3L), QsoPick(from = LocalDate.of(2026, 10, 6), bands = setOf("20m"), modes = setOf("FT8")).ids(log))
    }

    @Test
    fun typedDates() {
        assertEquals(LocalDate.of(2026, 10, 5), QsoPick.parseDate("05.10.2026"))
        assertEquals(LocalDate.of(2026, 10, 5), QsoPick.parseDate("5.10.26"))
        assertEquals(LocalDate.of(2026, 10, 5), QsoPick.parseDate("2026-10-05"))
        assertNull(QsoPick.parseDate(""))
        assertNull(QsoPick.parseDate("32.10.2026"))
    }

    @Test
    fun bandsAndModesOfTheLog() {
        assertEquals(listOf("40m", "20m", "2m"), QsoPick.bandsIn(log))
        assertEquals(listOf("FT8", "SSB", "FM"), QsoPick.modesIn(log))
    }
}
