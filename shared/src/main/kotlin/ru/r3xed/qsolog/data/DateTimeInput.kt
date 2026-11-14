package ru.r3xed.qsolog.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Typing the date and the time of a contact as bare digits: the dots and the colon come by themselves.
 *
 * While typing ([liveTime], [liveDate]) a first digit that cannot start an hour, a day or a month gets its zero at once:
 * 9 → 09, so 931 reads 09:31 and 511 reads 05.11. When the field is left or the card saved ([finishTime], [finishDate])
 * what is short is completed: 1234 → 12:34, 123 → 01:23, 9 → 09:00; 1411 → 14.11 of this year, 141126 → 14.11.2026.
 * Something that is no date or time stays as typed, and the card says what is wrong.
 */
object DateTimeInput {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val ISO = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")

    private fun digits(s: String) = s.filter { it in '0'..'9' }

    /** The hour digits with the zeros a first digit needs: 9 → 09, 25 → 02 5. */
    private fun timeDigits(raw: String): String {
        val d = StringBuilder(digits(raw))
        if (d.isNotEmpty() && d[0] in '3'..'9') d.insert(0, '0')
        if (d.length >= 2 && d.substring(0, 2).toInt() > 23) d.insert(0, '0')
        return d.toString().take(4)
    }

    private fun dateDigits(raw: String): String {
        // A pasted ISO date (2026-11-14) is turned round first.
        ISO.matchEntire(raw.trim())?.let { m ->
            val (y, mo, da) = m.destructured
            return da.padStart(2, '0') + mo.padStart(2, '0') + y
        }
        val d = StringBuilder(digits(raw))
        if (d.isNotEmpty() && d[0] in '4'..'9') d.insert(0, '0')
        if (d.length >= 2 && d.substring(0, 2).toInt() > 31) d.insert(0, '0')
        if (d.length >= 3 && d[2] in '2'..'9') d.insert(2, '0')
        if (d.length >= 4 && d.substring(2, 4).toInt() > 12) d.insert(2, '0')
        return d.toString().take(8)
    }

    private fun formatTime(d: String) = if (d.length <= 2) d else d.take(2) + ":" + d.drop(2)
    private fun formatDate(d: String) = when {
        d.length <= 2 -> d
        d.length <= 4 -> d.take(2) + "." + d.drop(2)
        else -> d.take(2) + "." + d.substring(2, 4) + "." + d.drop(4)
    }

    /** What the time field shows for what was typed into it. */
    fun liveTime(raw: String): String = formatTime(timeDigits(raw))

    /** What the date field shows for what was typed into it. */
    fun liveDate(raw: String): String = formatDate(dateDigits(raw))

    /**
     * Where the cursor goes in [shown] (made by [live] — [liveTime] or [liveDate] — from [raw]) when it stood at [cursor]
     * in [raw]: after as many digits as the text before it makes, so the inserted zeros and separators do not throw it back.
     */
    fun cursor(raw: String, cursor: Int, shown: String, live: (String) -> String): Int {
        var left = digits(live(raw.take(cursor.coerceIn(0, raw.length)))).length
        if (left == 0) return 0
        shown.forEachIndexed { i, c -> if (c in '0'..'9' && --left == 0) return i + 1 }
        return shown.length
    }

    /** The time completed: 1234 → 12:34, 123 → 01:23, 093 → 09:30, 9 → 09:00; null when it is no time. */
    fun parseTime(text: String): LocalTime? {
        val d = timeDigits(text)
        val full = when (d.length) {
            0 -> return null
            1, 2 -> d.padStart(2, '0') + "00"
            3 -> if (d[0] != '0' && d.substring(1).toInt() < 60) "0$d" else d + "0"
            else -> d
        }
        val h = full.take(2).toInt()
        val m = full.drop(2).toInt()
        return if (h < 24 && m < 60) LocalTime.of(h, m) else null
    }

    /** The date completed: the month and the year of [today] when left out, 26 → 2026; null when it is no date. */
    fun parseDate(text: String, today: LocalDate): LocalDate? {
        val d = dateDigits(text)
        val day = d.take(2).toIntOrNull() ?: return null
        val month = if (d.length > 2) d.substring(2, minOf(4, d.length)).toInt() else today.monthValue
        val year = when (d.length) {
            in 1..4 -> today.year
            6 -> 2000 + d.drop(4).toInt()
            8 -> d.drop(4).toInt()
            else -> return null
        }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** The time field as it should stay once left: completed, or as typed when it is no time. */
    fun finishTime(text: String): String = parseTime(text)?.format(TIME) ?: text

    fun finishDate(text: String, today: LocalDate): String = parseDate(text, today)?.format(DATE) ?: text

    /** ↑ and ↓ in the time field: [minutes] later or earlier, round the clock. */
    fun stepTime(text: String, minutes: Long): String = parseTime(text)?.plusMinutes(minutes)?.format(TIME) ?: text

    /** ↑ and ↓ in the date field. */
    fun stepDate(text: String, days: Long, today: LocalDate): String = parseDate(text, today)?.plusDays(days)?.format(DATE) ?: text
}
