package ru.r3xed.qsolog.data

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Which part of the log to pick for an export: dates (UTC, both ends included), bands and modes.
 * An empty set or a missing date means "any".
 */
data class QsoPick(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val bands: Set<String> = emptySet(),
    val modes: Set<String> = emptySet(),
) {
    private val fromMs = from?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
    private val untilMs = to?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    fun matches(q: Qso): Boolean =
        (fromMs == null || q.timeUtc >= fromMs) && (untilMs == null || q.timeUtc < untilMs) &&
            (bands.isEmpty() || bands.any { it.equals(q.band, ignoreCase = true) }) &&
            (modes.isEmpty() || modes.any { it.equals(q.mode, ignoreCase = true) })

    fun ids(log: List<Qso>): Set<Long> = log.filter(::matches).map { it.id }.toSet()

    companion object {
        private val FORMATS = listOf("d.M.yyyy", "d.M.yy", "yyyy-M-d").map { DateTimeFormatter.ofPattern(it) }

        /** "05.10.2026", "5.10.26", "2026-10-05"; null for anything else (an empty field included). */
        fun parseDate(text: String): LocalDate? {
            val t = text.trim().replace('/', '.').replace(',', '.')
            if (t.isEmpty()) return null
            for (f in FORMATS) try {
                return LocalDate.parse(t, f)
            } catch (_: DateTimeParseException) {
            }
            return null
        }

        /** Bands, then modes, that occur in [log]: the bands in the order of [BANDS], the modes by how often they occur. */
        fun bandsIn(log: List<Qso>): List<String> =
            log.map { it.band.lowercase() }.filter { it.isNotBlank() }.distinct()
                .sortedBy { b -> BANDS.indexOf(b).let { if (it < 0) Int.MAX_VALUE else it } }

        fun modesIn(log: List<Qso>): List<String> =
            log.map { it.mode.uppercase() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
    }
}
