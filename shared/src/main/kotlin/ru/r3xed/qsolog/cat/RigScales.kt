package ru.r3xed.qsolog.cat

import ru.r3xed.qsolog.data.Reference

/** What a part of a band is for (IARU Region 1): the colour of that part of the band scale. */
enum class BandUse { CW, DIGITAL, BEACON, PHONE, FM }

/** A band as the transceiver panel draws it: its edges and its parts, in Hz. */
data class BandScale(val band: String, val fromHz: Long, val toHz: Long, val parts: List<Part>, val marks: List<Mark>) {
    data class Part(val fromHz: Long, val toHz: Long, val use: BandUse)

    /** A frequency worth a label on the scale: FT8, FT4. */
    data class Mark(val hz: Long, val label: String)

    val spanHz: Long get() = toHz - fromHz

    /** Where [hz] is on the scale, 0–1 (outside the band — clamped). */
    fun position(hz: Long): Float = ((hz - fromHz).toDouble() / spanHz).toFloat().coerceIn(0f, 1f)

    /** The frequency at [position] 0–1, rounded to [stepHz]. */
    fun freqAt(position: Float, stepHz: Long = 1000): Long {
        val hz = fromHz + (spanHz * position.coerceIn(0f, 1f)).toLong()
        return (hz + stepHz / 2) / stepHz * stepHz
    }

    /** Round frequencies for the labels: at least [count] of them, every 5, 10, 25, 50, 100… kHz. */
    fun ticks(count: Int = 6): List<Long> {
        val raw = spanHz / count
        val step = TICK_STEPS.lastOrNull { it <= raw } ?: TICK_STEPS.first()
        val first = (fromHz + step - 1) / step * step
        return generateSequence(first) { it + step }.takeWhile { it <= toHz }.toList()
    }

    companion object {
        private val TICK_STEPS = listOf(5_000L, 10_000, 20_000, 25_000, 50_000, 100_000, 200_000, 250_000, 500_000, 1_000_000, 2_000_000)

        /** The scale of the band [hz] is on, or null outside the amateur bands the panel knows. */
        fun of(hz: Long): BandScale? = ALL.firstOrNull { hz in it.fromHz..it.toHz }

        fun forBand(band: String): BandScale? = ALL.firstOrNull { it.band == band }

        private fun khz(v: Double) = Math.round(v * 1000)

        private fun use(s: String): BandUse = when {
            s == "CW" -> BandUse.CW
            s.contains("цифров", ignoreCase = true) -> BandUse.DIGITAL
            s.contains("Маяк", ignoreCase = true) -> BandUse.BEACON
            s.startsWith("FM") -> BandUse.FM
            else -> BandUse.PHONE
        }

        /** HF from the reference's band plan, the rest from the IARU Region 1 plan in short. */
        val ALL: List<BandScale> by lazy {
            val hf = Reference.BAND_PLAN.mapNotNull { (name, segs) ->
                val band = name.replace(" м", "m")
                val parts = segs.map { Part(khz(it.from), khz(it.to), use(it.use)) }
                BandScale(band, parts.first().fromHz, parts.last().toHz, parts, emptyList())
            }
            val more = listOf(
                BandScale("60m", 5_351_500, 5_366_500, listOf(Part(5_351_500, 5_354_000, BandUse.CW), Part(5_354_000, 5_366_000, BandUse.PHONE)), emptyList()),
                BandScale("6m", 50_000_000, 52_000_000, listOf(
                    Part(50_000_000, 50_100_000, BandUse.CW), Part(50_100_000, 50_300_000, BandUse.PHONE),
                    Part(50_300_000, 50_400_000, BandUse.DIGITAL), Part(50_400_000, 50_500_000, BandUse.BEACON),
                    Part(50_500_000, 52_000_000, BandUse.FM),
                ), emptyList()),
                BandScale("2m", 144_000_000, 146_000_000, listOf(
                    Part(144_000_000, 144_150_000, BandUse.CW), Part(144_150_000, 144_400_000, BandUse.PHONE),
                    Part(144_400_000, 144_490_000, BandUse.BEACON), Part(144_500_000, 144_800_000, BandUse.DIGITAL),
                    Part(144_800_000, 145_000_000, BandUse.DIGITAL), Part(145_000_000, 146_000_000, BandUse.FM),
                ), emptyList()),
                BandScale("70cm", 430_000_000, 440_000_000, listOf(
                    Part(432_000_000, 432_150_000, BandUse.CW), Part(432_150_000, 432_500_000, BandUse.PHONE),
                    Part(432_500_000, 433_000_000, BandUse.DIGITAL), Part(433_000_000, 435_000_000, BandUse.FM),
                    Part(435_000_000, 438_000_000, BandUse.DIGITAL), Part(438_000_000, 440_000_000, BandUse.FM),
                ), emptyList()),
            )
            (hf + more).map { s ->
                // FT8 and FT4 calling frequencies of the band, from the same table the panel tunes to.
                val ft8 = BandPlan.freq(s.band, "FT8")?.takeIf { it in s.fromHz..s.toHz }
                val ft4 = BandPlan.freq(s.band, "FT4")?.takeIf { it in s.fromHz..s.toHz && it != ft8 }
                s.copy(marks = listOfNotNull(ft8?.let { Mark(it, "FT8") }, ft4?.let { Mark(it, "FT4") }))
            }.sortedBy { it.fromHz }
        }
    }
}

/** The panel's meters: where a reading is on its scale (0–1), and the scale's labels. */
object RigMeterScale {
    /** S-meter: S0–S9 is the first 54 dB (60% of the scale), then up to S9+60. */
    fun sPosition(db: Int): Float = if (db <= 54) db / 54f * 0.6f else (0.6f + (db - 54) / 60f * 0.4f).coerceAtMost(1f)
    val S_LABELS = listOf(6 to "1", 18 to "3", 30 to "5", 42 to "7", 54 to "9", 74 to "+20", 94 to "+40", 114 to "+60")

    /** SWR 1.0–3.0 over most of the scale; ∞ at the end. */
    fun swrPosition(swr: Double): Float = when {
        swr <= 1.0 -> 0f
        swr <= 3.0 -> ((swr - 1.0) / 2.0 * 0.85).toFloat()
        else -> (0.85 + (swr - 3.0) / 7.0 * 0.15).toFloat().coerceAtMost(1f)
    }
    val SWR_LABELS = listOf(1.0 to "1", 1.5 to "1.5", 2.0 to "2", 3.0 to "3", 10.0 to "∞")
}
