package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LogMarksTest {
    @Test
    fun firstContactWithEachCountry() {
        val log = listOf(
            Qso(id = 1, call = "UA3DEMO", timeUtc = 300, country = "Россия"),
            Qso(id = 2, call = "R9DEMO", timeUtc = 100, country = "россия"),
            Qso(id = 3, call = "DL1DEMO", timeUtc = 200, country = "Germany"),
            Qso(id = 4, call = "X1DEMO", timeUtc = 50, country = ""),
        )
        assertEquals(setOf(2L, 3L), LogMarks.firstWithCountry(log))
    }

    @Test
    fun jumpToTheDayOrTheOneBefore() {
        val newest = listOf(30L, 20L, 10L)
        assertEquals(1, LogMarks.dayIndex(newest, 20))
        assertEquals(1, LogMarks.dayIndex(newest, 25))
        assertEquals(2, LogMarks.dayIndex(newest, 5))
        assertEquals(0, LogMarks.dayIndex(newest, 99))
        assertEquals(-1, LogMarks.dayIndex(emptyList(), 5))
    }
}
