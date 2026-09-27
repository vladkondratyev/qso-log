package ru.r3xed.qsolog.data

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Radio arithmetic for the reference calculators: levels, cable loss, SWR, antenna lengths. */
object RfCalc {
    const val C_MHZ_M = 299.792458 // speed of light: wavelength in m = C_MHZ_M / f in MHz
    const val DBD_TO_DBI = 2.15

    fun wattsToDbm(w: Double): Double = 10 * log10(w * 1000)
    fun dbmToWatts(dbm: Double): Double = 10.0.pow(dbm / 10) / 1000
    fun wattsToDbw(w: Double): Double = 10 * log10(w)

    /** Voltage across 50 Ω for a power in dBm, in µV. */
    fun dbmToMicrovolts(dbm: Double): Double = sqrt(dbmToWatts(dbm) * 50) * 1e6

    /** EIRP from transmitter power, antenna gain (dBi) and feeder loss (dB): watts. */
    fun eirpWatts(powerW: Double, gainDbi: Double, lossDb: Double): Double = powerW * 10.0.pow((gainDbi - lossDb) / 10)

    /** S-meter reading (IARU R.1 recommendation): S9 is −73 dBm below 30 MHz and −93 dBm above, 6 dB per S unit. */
    fun sMeterDbm(s: Int, vhf: Boolean): Double = (if (vhf) -93.0 else -73.0) - (9 - s) * 6

    /** A coaxial cable: attenuation in dB/100 m at [freqs] MHz (from DD1US's table of manufacturers' data). */
    data class Cable(val name: String, val velocity: Double, val points: List<Pair<Double, Double>>)

    /** Loss in dB/100 m at [mhz]: log-log interpolation between the known points; below them √f, above them the last slope. */
    fun attenuationPer100m(cable: Cable, mhz: Double): Double {
        val p = cable.points.sortedBy { it.first }
        // Below the first known point conductor loss dominates: it grows as √f.
        if (p.size == 1 || mhz <= p[0].first) return p[0].second * sqrt(mhz / p[0].first)
        val i = p.indexOfFirst { it.first >= mhz }.let { if (it <= 0) 1 else it }.coerceAtMost(p.size - 1)
        val (f1, a1) = p[i - 1]; val (f2, a2) = p[i]
        val k = (ln(a2) - ln(a1)) / (ln(f2) - ln(f1))
        return exp(ln(a1) + k * (ln(mhz) - ln(f1)))
    }

    /** Power reaching the antenna through [lossDb] of cable. */
    fun powerAfterLoss(powerW: Double, lossDb: Double): Double = powerW * 10.0.pow(-lossDb / 10)

    /** SWR → reflection coefficient |Γ|, reflected power share, return loss and mismatch loss (dB). */
    data class Swr(val gamma: Double, val reflected: Double, val returnLossDb: Double, val mismatchLossDb: Double)

    fun swr(s: Double): Swr {
        val g = (s - 1) / (s + 1)
        val refl = g * g
        return Swr(g, refl, if (g > 0) -20 * log10(g) else Double.POSITIVE_INFINITY, -10 * log10(1 - refl))
    }

    /** Wire antenna lengths at [mhz] in metres; [k] is the shortening factor of the wire (≈0.95). */
    data class Antenna(val wavelength: Double, val dipole: Double, val dipoleLeg: Double, val quarter: Double, val loop: Double)

    fun antenna(mhz: Double, k: Double = 0.95): Antenna {
        val l = C_MHZ_M / mhz
        // A full-wave loop resonates a little longer than λ: the usual 1.02.
        return Antenna(l, l / 2 * k, l / 4 * k, l / 4 * k, l * 1.02)
    }

    /** A quarter-wave piece of coax at [mhz] with velocity factor [vf], metres. */
    fun coaxQuarter(mhz: Double, vf: Double): Double = C_MHZ_M / mhz / 4 * vf

    /** A number typed with a comma or a dot; null when it is not a positive number. */
    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
