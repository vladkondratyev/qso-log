package ru.r3xed.qsolog.data

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StatsTest {
    private fun at(date: String, hour: Int = 12) = LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun q(call: String, date: String, band: String = "20m", mode: String = "SSB", km: Double? = null, hour: Int = 12, contest: Boolean = false) =
        Qso(call = call, timeUtc = at(date, hour), band = band, mode = mode, distanceKm = km,
            adif = if (contest) mapOf(ContestMode.FIELD to "Y") else emptyMap())

    private val today = LocalDate.parse("2026-10-07")
    private val log = listOf(
        q("R3DEMO", "2026-10-07", km = 41.0, hour = 9, contest = true),
        q("R3DEMO", "2026-10-07", band = "40m", km = 41.0, hour = 10, contest = true),
        q("DL0DEMO", "2026-10-06", band = "40m", mode = "CW", km = 1800.0),
        q("UA9DEMO", "2026-09-20", km = 1454.0),
        q("W1DEMO", "2025-01-15", band = "15m", mode = "FT8", km = 7600.0),
    )

    @Test
    fun filterByPeriodBandModeAndContest() {
        assertEquals(2, Stats.filter(log, StatFilter(StatPeriod.WEEK), today).count { it.call == "R3DEMO" })
        assertEquals(3, Stats.filter(log, StatFilter(StatPeriod.WEEK), today).size)
        assertEquals(4, Stats.filter(log, StatFilter(StatPeriod.MONTH), today).size)
        assertEquals(5, Stats.filter(log, StatFilter(StatPeriod.ALL), today).size)
        assertEquals(2, Stats.filter(log, StatFilter(band = "40m"), today).size)
        assertEquals(1, Stats.filter(log, StatFilter(mode = "cw"), today).size)
        assertEquals(2, Stats.filter(log, StatFilter(contestOnly = true), today).size)
    }

    @Test
    fun summary() {
        val s = Stats.summary(log)
        assertEquals(5, s.total)
        assertEquals(4, s.calls)
        assertEquals(4, s.activeDays)
        assertEquals(LocalDate.parse("2026-10-07") to 2, s.bestDay)
        assertEquals("W1DEMO", s.farthest?.call)
        assertEquals(1.25, s.perActiveDay)
        // R, DL, UA9, W: European Russia, Germany, Asiatic Russia, United States; continents EU, AS, NA.
        assertEquals(4, s.countries)
        assertEquals(3, s.continents)
    }

    @Test
    fun countsTopAndBands() {
        assertEquals(listOf("SSB" to 3, "CW" to 1, "FT8" to 1), Stats.counts(log) { it.mode })
        assertEquals(listOf("R3DEMO" to 2, "DL0DEMO" to 1, Stats.OTHER to 2), Stats.top(Stats.counts(log) { it.call }, 2))
        // Bands in frequency order, not by count.
        assertEquals(listOf("40m" to 2, "20m" to 2, "15m" to 1), Stats.bandCounts(log))
    }

    @Test
    fun timelineFillsGapsAndSplits() {
        val days = Stats.timeline(log, StatBucket.AUTO, LocalDate.parse("2026-10-01"), today, split = { it.band }, keep = setOf("20m"))
        assertEquals(7, days.size)
        assertEquals(LocalDate.parse("2026-10-01"), days.first().start)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 2), days.map { it.count })
        assertEquals(mapOf("20m" to 1, Stats.OTHER to 1), days.last().byKey)
        val weeks = Stats.timeline(log, StatBucket.WEEK, LocalDate.parse("2026-09-14"), today)
        assertEquals(LocalDate.parse("2026-09-14"), weeks.first().start) // a Monday
        assertEquals(listOf(1, 0, 0, 3), weeks.map { it.count }) // 06.10 and 07.10 share the week of Monday 05.10
        assertEquals(StatBucket.MONTH, Stats.resolve(StatBucket.AUTO, LocalDate.parse("2025-01-15"), today))
        assertEquals(listOf(1, 1, 3), Stats.cumulative(listOf(TimePoint(today, 1), TimePoint(today, 0), TimePoint(today, 2))).map { it.count })
    }

    @Test
    fun weekHourAndDistance() {
        val g = Stats.weekHour(log)
        assertEquals(1, g[2][9]) // 2026-10-07 is a Wednesday
        assertEquals(1, g[2][10])
        assertEquals(5, g.sumOf { it.sum() })
        // <100, <500, <1000, <2000, <5000, <10000, beyond
        assertContentEquals(intArrayOf(2, 0, 0, 2, 0, 1, 0), Stats.distanceBins(log))
    }
}

class CallHistoryTest {
    @Test
    fun lastContactOnEachBandInFrequencyOrder() {
        fun q(band: String, day: Int, mode: String = "SSB") = Qso(call = "R9DEMO", timeUtc = 1_790_000_000_000L + day * 86_400_000L, band = band, mode = mode)
        // Newest first, as the database gives them.
        val h = CallHistory.of(listOf(q("20m", 9), q("40m", 7, "CW"), q("20m", 5), q("80m", 3), q("", 2)))
        assertEquals(5, h.count)
        assertEquals(9, ((h.last!!.timeUtc - 1_790_000_000_000L) / 86_400_000L).toInt())
        assertEquals(listOf("80m" to 3, "40m" to 7, "20m" to 9), h.byBand.map { it.band to ((it.timeUtc - 1_790_000_000_000L) / 86_400_000L).toInt() })
        assertEquals("CW", h.byBand[1].mode)
    }
}
