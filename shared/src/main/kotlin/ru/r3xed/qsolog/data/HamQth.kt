package ru.r3xed.qsolog.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Country and region of a callsign by its prefix, from HamQTH's free DXCC search (no account needed).
 * [lat]/[lon] are the centre of the region (for Russia) or of the country, so distances from them are approximate.
 */
data class DxccInfo(
    val country: String,
    /** Region name without the country and the licence class, e.g. "Kaluzhskaya oblast' (KG)"; empty if unknown. */
    val region: String,
    val continent: String,
    val cqZone: String,
    val ituZone: String,
    val dxcc: String,
    val lat: Double?,
    val lon: Double?,
) {
    val position: LatLon? get() = if (lat != null && lon != null) LatLon(lat, lon) else null
}

/** https://www.hamqth.com/developers.php — "DXCC search in JSON format". */
object HamQth {
    /** Marks a record whose position came from the region centre, in [Qso.adif]. Exported as an application field. */
    const val POSITION_FIELD = "APP_QSOLOG_POS"
    const val POSITION_REGION = "REGION"

    /** Blocking call; null when HamQTH does not know the prefix. Throws on network errors. */
    fun dxcc(call: String, agent: String = "QSO-LOG"): DxccInfo? {
        val url = URL("https://www.hamqth.com/dxcc_json.php?callsign=" + URLEncoder.encode(call.trim(), "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", agent)
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val body = try {
            if (conn.responseCode != 200) throw IllegalStateException("HamQTH ответил ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return parse(body)
    }

    /** The answer is one flat JSON object of strings: {"callsign":"R3XEB", "name":"Russia (European)", …}. */
    fun parse(json: String): DxccInfo? {
        val v = Regex("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(json)
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\/", "/").replace("\\\"", "\"") }
        val country = v["name"].orEmpty()
        if (country.isBlank()) return null
        // "Russia (European), Kaluzhskaya oblast' (KG), 2nd Class" → "Kaluzhskaya oblast' (KG)"
        val region = v["details"].orEmpty().split(", ")
            .drop(1).filterNot { it.endsWith("Class", ignoreCase = true) || it.equals("Special Station", ignoreCase = true) }
            .joinToString(", ")
        return DxccInfo(
            country = country,
            region = region,
            continent = v["continent"].orEmpty(),
            cqZone = v["waz"].orEmpty(),
            ituZone = v["itu"].orEmpty(),
            dxcc = v["adif"].orEmpty(),
            lat = v["lat"]?.toDoubleOrNull(),
            lon = v["lng"]?.toDoubleOrNull(),
        )
    }
}
