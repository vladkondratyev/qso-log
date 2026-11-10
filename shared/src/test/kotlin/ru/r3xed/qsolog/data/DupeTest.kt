package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DupeTest {
    private fun q(band: String, mode: String, t: Long) = Qso(call = "R0DEMO", timeUtc = t, band = band, mode = mode)

    @Test
    fun levels() {
        val h = CallHistory.of(listOf(q("20m", "CW", 3), q("20m", "FT8", 2), q("40m", "SSB", 1)))
        assertEquals(Dupe.DUPE, h.dupe("20m", "CW"))
        assertEquals(Dupe.DUPE, h.dupe("20m", "FT4")) // FT4 and FT8 are both digital
        assertEquals(Dupe.BAND, h.dupe("40m", "CW"))
        assertEquals(Dupe.WORKED, h.dupe("15m", "CW"))
        assertEquals(Dupe.NEW, CallHistory.of(emptyList()).dupe("20m", "CW"))
    }

    @Test
    fun grid() {
        val h = CallHistory.of(listOf(q("20m", "CW", 3), q("20m", "CW", 2), q("40m", "SSB", 1)))
        val g = h.grid("15m")
        assertEquals(listOf("40m", "20m", "15m"), g.map { it.first })
        assertEquals(2, g[1].second[ModeGroup.CW])
        assertEquals(1, g[0].second[ModeGroup.PHONE])
        assertEquals(emptyMap(), g[2].second)
    }
}
