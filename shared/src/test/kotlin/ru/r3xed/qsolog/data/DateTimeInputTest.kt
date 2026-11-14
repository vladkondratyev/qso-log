package ru.r3xed.qsolog.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeInputTest {
    private val today = LocalDate.of(2026, 11, 14)

    /** Typed one key at a time, as the field sees it. */
    private fun typeTime(keys: String) = keys.fold("") { acc, c -> DateTimeInput.liveTime(acc + c) }
    private fun typeDate(keys: String) = keys.fold("") { acc, c -> DateTimeInput.liveDate(acc + c) }

    @Test
    fun timeAsDigits() {
        assertEquals("12:34", typeTime("1234"))
        assertEquals("09:31", typeTime("931"))
        assertEquals("02:45", typeTime("245"))
        assertEquals("23:59", typeTime("23599"))
        assertEquals("11:21", DateTimeInput.liveTime("11:21"))
    }

    @Test
    fun timeCompletedWhenLeft() {
        assertEquals("12:34", DateTimeInput.finishTime("1234"))
        assertEquals("09:31", DateTimeInput.finishTime(typeTime("931")))
        assertEquals("01:23", DateTimeInput.finishTime("123"))
        assertEquals("09:30", DateTimeInput.finishTime("09:3"))
        assertEquals("09:00", DateTimeInput.finishTime("9"))
        assertEquals("18:00", DateTimeInput.finishTime("18"))
        assertEquals("12:77", DateTimeInput.finishTime("12:77"))
        assertEquals("", DateTimeInput.finishTime(""))
    }

    @Test
    fun dateAsDigits() {
        assertEquals("14.11.2026", typeDate("14112026"))
        assertEquals("05.11", typeDate("511"))
        assertEquals("05.01.2026", typeDate("5012026"))
        assertEquals("14.03", typeDate("143"))
        assertEquals("13.11.2026", DateTimeInput.liveDate("2026-11-13"))
    }

    @Test
    fun dateCompletedWhenLeft() {
        assertEquals("14.11.2026", DateTimeInput.finishDate("1411", today))
        assertEquals("14.11.2026", DateTimeInput.finishDate("141126", today))
        assertEquals("05.11.2026", DateTimeInput.finishDate(typeDate("511"), today))
        assertEquals("07.11.2026", DateTimeInput.finishDate("7", today))
        assertEquals("01.12.2025", DateTimeInput.finishDate("01122025", today))
        assertEquals("31.02.2026", DateTimeInput.finishDate("31.02.2026", today))
    }

    @Test
    fun arrowsStep() {
        assertEquals("00:00", DateTimeInput.stepTime("23:59", 1))
        assertEquals("09:30", DateTimeInput.stepTime("931", -1))
        assertEquals("01.12.2026", DateTimeInput.stepDate("30.11.2026", 1, today))
    }

    @Test
    fun cursorStaysAfterTheSameDigits() {
        // "9" typed into an empty field: shown "09", the cursor at the end.
        assertEquals(2, DateTimeInput.cursor("9", 1, "09", DateTimeInput::liveTime))
        // "12:3|" + 4 → "12:34", at the end.
        assertEquals(5, DateTimeInput.cursor("12:34", 5, "12:34", DateTimeInput::liveTime))
        // A digit typed in the middle: "1|2:34" → "152:34" → "15:23", after the 5.
        assertEquals(2, DateTimeInput.cursor("152:34", 2, "15:23", DateTimeInput::liveTime))
        // The separator rubbed out at the end: "09:" → "09".
        assertEquals(2, DateTimeInput.cursor("09", 2, "09", DateTimeInput::liveTime))
        // "5" in an empty date → "05", after it.
        assertEquals(2, DateTimeInput.cursor("5", 1, "05", DateTimeInput::liveDate))
        assertEquals(5, DateTimeInput.cursor("05.11", 5, "05.11", DateTimeInput::liveDate))
    }
}
