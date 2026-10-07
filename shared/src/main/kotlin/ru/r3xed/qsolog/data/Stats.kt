package ru.r3xed.qsolog.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Period of the dashboard, counted back from today (UTC). */
enum class StatPeriod(val days: Int?) { WEEK(7), MONTH(30), YEAR(365), ALL(null) }

/** How the time chart groups contacts. AUTO picks days, weeks or months from the length of the period. */
enum class StatBucket { AUTO, DAY, WEEK, MONTH }

/** What the dashboard counts: a period, optionally one band, one mode, only contest-mode contacts. */
data class StatFilter(
    val period: StatPeriod = StatPeriod.ALL,
    val band: String? = null,
    val mode: String? = null,
    val contestOnly: Boolean = false,
)

/** The headline numbers. */
data class StatSummary(
    val total: Int,
    val calls: Int,
    val countries: Int,
    val continents: Int,
    val activeDays: Int,
    /** The busiest UTC day and its count. */
    val bestDay: Pair<LocalDate, Int>?,
    /** The farthest contact with a known distance. */
    val farthest: Qso?,
) {
    val perActiveDay: Double get() = if (activeDays == 0) 0.0 else total.toDouble() / activeDays
}

/** One column of the time chart: [start] of the day, week (Monday) or month, the count and its split by [byKey]. */
data class TimePoint(val start: LocalDate, val count: Int, val byKey: Map<String, Int> = emptyMap())

/** Counts for the dashboard. Pure functions over the log; the country comes from the callsign prefix (cty.dat). */
object Stats {
    const val OTHER = "\u0000other"

    fun day(q: Qso): LocalDate = Instant.ofEpochMilli(q.timeUtc).atOffset(ZoneOffset.UTC).toLocalDate()

    /** DXCC entity by prefix; null when the prefix is unknown. */
    fun country(q: Qso): String? = Cty.lookup(q.call)?.entity?.name

    fun continent(q: Qso): String? = Cty.lookup(q.call)?.entity?.continent?.ifBlank { null }

    fun filter(list: List<Qso>, f: StatFilter, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<Qso> {
        val from = f.period.days?.let { today.minusDays(it - 1L) }
        return list.filter { q ->
            (from == null || !day(q).isBefore(from)) &&
                (f.band == null || q.band.equals(f.band, ignoreCase = true)) &&
                (f.mode == null || q.mode.equals(f.mode, ignoreCase = true)) &&
                (!f.contestOnly || ContestMode.isContest(q.adif))
        }
    }

    fun summary(list: List<Qso>): StatSummary {
        val byDay = list.groupingBy { day(it) }.eachCount()
        return StatSummary(
            total = list.size,
            calls = list.map { it.call.uppercase() }.distinct().size,
            countries = list.mapNotNull { country(it) }.distinct().size,
            continents = list.mapNotNull { continent(it) }.distinct().size,
            activeDays = byDay.size,
            bestDay = byDay.entries.maxWithOrNull(compareBy<Map.Entry<LocalDate, Int>> { it.value }.thenBy { it.key })?.let { it.key to it.value },
            farthest = list.filter { it.distanceKm != null }.maxByOrNull { it.distanceKm!! },
        )
    }

    /** Count per key, biggest first (ties by key); records without a key are left out. */
    fun counts(list: List<Qso>, key: (Qso) -> String?): List<Pair<String, Int>> =
        list.mapNotNull { key(it)?.ifBlank { null } }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    /** The first [n] entries, the rest summed into one [OTHER] entry (if there is a rest). */
    fun top(counts: List<Pair<String, Int>>, n: Int): List<Pair<String, Int>> {
        if (counts.size <= n) return counts
        return counts.take(n) + (OTHER to counts.drop(n).sumOf { it.second })
    }

    /** Bands in frequency order rather than by count: the natural order for a band chart. */
    fun bandCounts(list: List<Qso>): List<Pair<String, Int>> =
        counts(list) { it.band.lowercase() }.sortedBy { (b, _) -> BANDS.indexOf(b).let { if (it < 0) Int.MAX_VALUE else it } }

    fun resolve(bucket: StatBucket, from: LocalDate, to: LocalDate): StatBucket {
        if (bucket != StatBucket.AUTO) return bucket
        val days = ChronoUnit.DAYS.between(from, to) + 1
        return when {
            days <= 62 -> StatBucket.DAY
            days <= 400 -> StatBucket.WEEK
            else -> StatBucket.MONTH
        }
    }

    fun bucketStart(d: LocalDate, b: StatBucket): LocalDate = when (b) {
        StatBucket.WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatBucket.MONTH -> d.withDayOfMonth(1)
        else -> d
    }

    private fun next(d: LocalDate, b: StatBucket): LocalDate = when (b) {
        StatBucket.WEEK -> d.plusWeeks(1)
        StatBucket.MONTH -> d.plusMonths(1)
        else -> d.plusDays(1)
    }

    /**
     * Contacts per day / week / month from [from] to [to], empty buckets included so gaps show. [split] gives each
     * column's parts (e.g. the band); keys outside [keep] go to [OTHER].
     */
    fun timeline(
        list: List<Qso>, bucket: StatBucket, from: LocalDate, to: LocalDate,
        split: ((Qso) -> String?)? = null, keep: Set<String> = emptySet(),
    ): List<TimePoint> {
        val b = resolve(bucket, from, to)
        val grouped = list.groupBy { bucketStart(day(it), b) }
        val out = mutableListOf<TimePoint>()
        var d = bucketStart(from, b)
        val end = bucketStart(to, b)
        while (!d.isAfter(end)) {
            val items = grouped[d].orEmpty()
            val parts = if (split == null) emptyMap() else items.groupingBy { q ->
                split(q)?.takeIf { it in keep } ?: OTHER
            }.eachCount()
            out += TimePoint(d, items.size, parts)
            d = next(d, b)
        }
        return out
    }

    /** The span the time chart covers: the period, or for "all" from the first contact to today. */
    fun span(list: List<Qso>, period: StatPeriod, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Pair<LocalDate, LocalDate> {
        val from = period.days?.let { today.minusDays(it - 1L) } ?: list.minOfOrNull { day(it) } ?: today
        val to = maxOf(today, list.maxOfOrNull { day(it) } ?: today)
        return from to to
    }

    /** Running total over the time chart's columns. */
    fun cumulative(points: List<TimePoint>): List<TimePoint> {
        var sum = 0
        return points.map { sum += it.count; it.copy(count = sum, byKey = emptyMap()) }
    }

    /** Contacts by weekday (0 = Monday) and UTC hour: when you are on the air. */
    fun weekHour(list: List<Qso>): Array<IntArray> {
        val grid = Array(7) { IntArray(24) }
        for (q in list) {
            val t = Instant.ofEpochMilli(q.timeUtc).atOffset(ZoneOffset.UTC)
            grid[t.dayOfWeek.value - 1][t.hour]++
        }
        return grid
    }

    /** Upper bounds of the distance bands, km; the last band is "more than the last bound". */
    val DISTANCE_BOUNDS = listOf(100, 500, 1000, 2000, 5000, 10000)

    /** Contacts per distance band (index into [DISTANCE_BOUNDS], the last index = beyond); unknown distances left out. */
    fun distanceBins(list: List<Qso>): IntArray {
        val bins = IntArray(DISTANCE_BOUNDS.size + 1)
        for (q in list) {
            val km = q.distanceKm ?: continue
            val i = DISTANCE_BOUNDS.indexOfFirst { km < it }
            bins[if (i < 0) DISTANCE_BOUNDS.size else i]++
        }
        return bins
    }
}
