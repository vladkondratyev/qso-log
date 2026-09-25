package ru.r3xed.qsolog.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_KM = 6371.0

    private val LOCATOR = Regex("^[A-R]{2}[0-9]{2}([A-X]{2}([0-9]{2})?)?$", RegexOption.IGNORE_CASE)

    fun isLocator(s: String) = LOCATOR.matches(s.trim())

    /** Centre of a Maidenhead square (4, 6 or 8 characters). */
    fun locatorToLatLon(locator: String): LatLon? {
        val l = locator.trim().uppercase()
        if (!LOCATOR.matches(l)) return null
        var lon = (l[0] - 'A') * 20.0 - 180
        var lat = (l[1] - 'A') * 10.0 - 90
        lon += (l[2] - '0') * 2.0
        lat += (l[3] - '0') * 1.0
        var w = 2.0
        var h = 1.0
        if (l.length >= 6) {
            lon += (l[4] - 'A') * (2.0 / 24)
            lat += (l[5] - 'A') * (1.0 / 24)
            w = 2.0 / 24; h = 1.0 / 24
        }
        if (l.length >= 8) {
            lon += (l[6] - '0') * (2.0 / 240)
            lat += (l[7] - '0') * (1.0 / 240)
            w = 2.0 / 240; h = 1.0 / 240
        }
        return LatLon(lat + h / 2, lon + w / 2)
    }

    fun latLonToLocator(p: LatLon): String {
        var lon = p.lon + 180
        var lat = p.lat + 90
        val sb = StringBuilder()
        sb.append('A' + floor(lon / 20).toInt()); sb.append('A' + floor(lat / 10).toInt())
        lon %= 20; lat %= 10
        sb.append('0' + floor(lon / 2).toInt()); sb.append('0' + floor(lat).toInt())
        lon %= 2; lat %= 1
        sb.append('a' + floor(lon * 12).toInt()); sb.append('a' + floor(lat * 24).toInt())
        return sb.toString()
    }

    fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_KM * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial great-circle bearing from a to b, degrees 0..360. */
    fun bearing(a: LatLon, b: LatLon): Double {
        val f1 = Math.toRadians(a.lat)
        val f2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Points along the great circle, for drawing the path on a map. */
    fun greatCircle(a: LatLon, b: LatLon, steps: Int = 64): List<LatLon> {
        val f1 = Math.toRadians(a.lat); val l1 = Math.toRadians(a.lon)
        val f2 = Math.toRadians(b.lat); val l2 = Math.toRadians(b.lon)
        val d = distanceKm(a, b) / EARTH_KM
        if (d < 1e-9) return listOf(a, b)
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            val A = sin((1 - t) * d) / sin(d)
            val B = sin(t * d) / sin(d)
            val x = A * cos(f1) * cos(l1) + B * cos(f2) * cos(l2)
            val y = A * cos(f1) * sin(l1) + B * cos(f2) * sin(l2)
            val z = A * sin(f1) + B * sin(f2)
            LatLon(Math.toDegrees(atan2(z, sqrt(x * x + y * y))), Math.toDegrees(atan2(y, x)))
        }
    }
}
