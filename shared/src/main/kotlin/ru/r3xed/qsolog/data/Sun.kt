package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Where the sun is overhead and the day/night line (grey line) on the map, by a short solar formula (±0.5°). */
object Sun {
    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    /** The point where the sun is in the zenith at [ms] (UTC). */
    fun subsolar(ms: Long): LatLon {
        val n = ms / 86_400_000.0 + 2440587.5 - 2451545.0
        val l = (280.460 + 0.9856474 * n).mod(360.0)
        val g = rad((357.528 + 0.9856003 * n).mod(360.0))
        val lambda = rad(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val eps = rad(23.439 - 0.0000004 * n)
        val decl = asin(sin(eps) * sin(lambda))
        val ra = deg(atan2(cos(eps) * sin(lambda), cos(lambda)))
        val gmst = (18.697374558 + 24.06570982441908 * n).mod(24.0) * 15
        val lon = ((ra - gmst) + 540.0).mod(360.0) - 180.0
        return LatLon(deg(decl), lon)
    }

    /** The grey line: for each longitude from −180 to 180, the latitude where the sun is on the horizon. */
    fun terminator(ms: Long, step: Int = 2): List<LatLon> {
        val s = subsolar(ms)
        val d = rad(if (kotlin.math.abs(s.lat) < 0.1) 0.1 else s.lat)
        return (-180..180 step step).map { lon ->
            LatLon(deg(atan(-cos(rad(lon - s.lon)) / tan(d))), lon.toDouble())
        }
    }

    /** The night side as a closed polygon: the terminator and the pole that is in darkness. */
    fun night(ms: Long, step: Int = 2): List<LatLon> {
        val line = terminator(ms, step)
        val pole = if (subsolar(ms).lat > 0) -85.0 else 85.0
        return line + LatLon(pole, 180.0) + LatLon(pole, -180.0)
    }

    /** The sun is below the horizon at [p]. */
    fun isNight(p: LatLon, ms: Long): Boolean {
        val s = subsolar(ms)
        val el = sin(rad(p.lat)) * sin(rad(s.lat)) + cos(rad(p.lat)) * cos(rad(s.lat)) * cos(rad(p.lon - s.lon))
        return el < 0
    }
}

/** Solar and geomagnetic indices for HF conditions (N0NBH, hamqsl.com): flux, sunspots, A and K, X-ray. */
data class SolarData(val flux: String, val sunspots: String, val aIndex: String, val kIndex: String, val xray: String, val updated: String) {
    /** "SFI 142 · SN 98 · A 8 · K 2" */
    val short get() = listOf("SFI" to flux, "SN" to sunspots, "A" to aIndex, "K" to kIndex).filter { it.second.isNotBlank() }
        .joinToString(" · ") { "${it.first} ${it.second}" }

    companion object {
        const val URL_XML = "https://www.hamqsl.com/solarxml.php"

        fun parse(xml: String): SolarData? {
            fun tag(n: String) = Regex("<$n>\\s*(.*?)\\s*</$n>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim().orEmpty()
            val d = SolarData(tag("solarflux"), tag("sunspots"), tag("aindex"), tag("kindex"), tag("xray"), tag("updated"))
            return d.takeIf { it.flux.isNotBlank() || it.kIndex.isNotBlank() }
        }

        suspend fun fetch(userAgent: String): SolarData? = withContext(Dispatchers.IO) {
            val c = URL(URL_XML).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", userAgent)
                if (c.responseCode != 200) null else parse(c.inputStream.bufferedReader().use { it.readText() })
            } finally {
                c.disconnect()
            }
        }
    }
}
