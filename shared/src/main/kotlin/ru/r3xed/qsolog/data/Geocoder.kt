package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A place found by name: what to write into QTH, the full name to choose by, and where it is. */
data class Place(val name: String, val full: String, val point: LatLon) {
    val locator get() = Geo.latLonToLocator(point)
}

/**
 * Search of a town or village by name through OpenStreetMap Nominatim (https://nominatim.org/release-docs/latest/api/Search/).
 * Used only when the operator asks for it in the card ("QTH на карте"): only the typed name goes out.
 */
object Geocoder {
    const val URL_BASE = "https://nominatim.openstreetmap.org/search"

    suspend fun search(query: String, language: String, userAgent: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val url = "$URL_BASE?format=jsonv2&addressdetails=1&limit=10&accept-language=${enc(language)}&q=${enc(q)}"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            if (code != 200) throw java.io.IOException("OpenStreetMap: HTTP $code")
            parse(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** The answer's array: the settlement's own name for QTH ("Калуга"), the whole address line to tell them apart. */
    fun parse(json: String): List<Place> {
        val list = MiniJson.parse(json) as? List<*> ?: return emptyList()
        return list.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val lat = (m["lat"] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = (m["lon"] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
            val full = m["display_name"] as? String ?: return@mapNotNull null
            val a = m["address"] as? Map<*, *>
            val town = listOf("city", "town", "village", "hamlet", "municipality", "locality", "suburb")
                .firstNotNullOfOrNull { (a?.get(it) as? String)?.ifBlank { null } }
            val name = (m["name"] as? String)?.ifBlank { null } ?: town ?: full.substringBefore(',')
            Place(name.trim(), full.trim(), LatLon(lat, lon))
        }.distinctBy { it.full }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
