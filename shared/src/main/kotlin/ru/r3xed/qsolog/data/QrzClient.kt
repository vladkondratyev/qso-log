package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

data class QrzInfo(
    val call: String,
    val name: String,
    val surname: String,
    val city: String,
    val country: String,
    val locator: String,
    val lat: Double?,
    val lon: Double?,
) {
    val fullName get() = listOf(name, surname).filter { it.isNotBlank() }.joinToString(" ")
    val position: LatLon?
        get() = if (lat != null && lon != null) LatLon(lat, lon) else Geo.locatorToLatLon(locator)
}

class QrzException(val code: Int, message: String) : Exception(message)

/**
 * Client for the QRZ.ru XML API (https://www.qrz.ru/help/api/xml).
 *
 * Server rules: one connection per IP, no parallel requests, and after the first three requests
 * no more than one request every 3 seconds. A session key lives for one hour.
 * Errors come back with HTTP 403/404 and an XML body, so the body is read for any status.
 */
class QrzClient(private val credentials: () -> Pair<String, String>) {
    private var sessionId: String? = null
    private var sessionAt = 0L
    private val cache = mutableMapOf<String, QrzInfo?>()

    // Serialises all requests and spaces them out to respect the server's rate limit.
    private val gate = Mutex()
    private var lastRequestAt = 0L
    private var burst = 0

    suspend fun login(): String = gate.withLock { loginLocked() }

    /**
     * Returns null when the callsign is not in the QRZ.ru database.
     * [stillWanted] is checked after waiting for the rate limit, so outdated lookups are skipped.
     */
    suspend fun lookup(call: String, stillWanted: () -> Boolean = { true }): QrzInfo? {
        val key = call.uppercase()
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        return gate.withLock {
            synchronized(cache) { if (cache.containsKey(key)) return@withLock cache[key] }
            if (!stillWanted()) throw kotlinx.coroutines.CancellationException("outdated")
            val sid = sessionId?.takeIf { System.currentTimeMillis() - sessionAt < SESSION_TTL } ?: loginLocked()
            val info = try {
                query(key, sid)
            } catch (e: QrzException) {
                if (e.code != 403) throw e
                query(key, loginLocked()) // session expired on the server: log in again once
            }
            synchronized(cache) { cache[key] = info }
            info
        }
    }

    fun reset() {
        sessionId = null
        sessionAt = 0
        synchronized(cache) { cache.clear() }
    }

    private suspend fun loginLocked(): String {
        val (user, pass) = credentials()
        if (user.isBlank() || pass.isBlank()) throw QrzException(0, "Укажите логин и пароль QRZ.ru в настройках")
        // POST keeps the password out of URLs, as the API docs recommend.
        val r = parse(request("https://api.qrz.ru/login", "u=${enc(user)}&p=${enc(pass)}&agent=QSOLog"))
        r.error?.let { throw QrzException(r.errorCode, translate(it)) }
        val id = r.session["session_id"] ?: throw QrzException(0, "QRZ.ru не вернул ключ сессии")
        sessionId = id
        sessionAt = System.currentTimeMillis()
        return id
    }

    private suspend fun query(call: String, sid: String): QrzInfo? {
        val r = parse(request("https://api.qrz.ru/callsign?id=${enc(sid)}&callsign=${enc(call)}"))
        if (r.errorCode == 404) return null
        r.error?.let { throw QrzException(r.errorCode, translate(it)) }
        val c = r.callsign
        return QrzInfo(
            call = c["call"] ?: call,
            name = c["name"].orEmpty(),
            surname = c["surname"].orEmpty(),
            city = c["city"].orEmpty(),
            country = c["country"].orEmpty(),
            locator = c["qthloc"].orEmpty(),
            lat = c["latitude"]?.toDoubleOrNull(),
            lon = c["longitude"]?.toDoubleOrNull(),
        )
    }

    private class Response(
        val callsign: Map<String, String>,
        val session: Map<String, String>,
    ) {
        val error get() = session["error"]
        val errorCode get() = session["errorcode"]?.toIntOrNull() ?: if (error != null) -1 else 0
    }

    private fun parse(xml: String): Response {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val callsign = mutableMapOf<String, String>()
        val session = mutableMapOf<String, String>()
        val sections = doc.documentElement.childNodes
        for (i in 0 until sections.length) {
            val section = sections.item(i) as? Element ?: continue
            val target = when (section.tagName.lowercase()) {
                "callsign" -> callsign
                "session" -> session
                else -> continue
            }
            val fields = section.childNodes
            for (j in 0 until fields.length) {
                val field = fields.item(j) as? Element ?: continue
                val text = field.textContent.trim()
                if (text.isNotEmpty()) target[field.tagName.lowercase()] = text
            }
        }
        return Response(callsign, session)
    }

    private suspend fun request(url: String, postBody: String? = null): String {
        val wait = if (burst < FREE_REQUESTS) 0L else lastRequestAt + MIN_INTERVAL - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        return withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "QSOLog/0.1")
            try {
                if (postBody != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.outputStream.use { it.write(postBody.toByteArray()) }
                }
                val code = conn.responseCode
                // 403 and 404 carry an XML error description in the body.
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (!body.contains("<QRZDatabase")) throw QrzException(code, "QRZ.ru ответил кодом HTTP $code")
                body
            } finally {
                conn.disconnect()
                lastRequestAt = System.currentTimeMillis()
                burst++
            }
        }
    }

    private companion object {
        const val SESSION_TTL = 55 * 60 * 1000L
        const val MIN_INTERVAL = 3_000L
        const val FREE_REQUESTS = 3
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun translate(error: String) = when {
        error.contains("password", true) -> "Неверный логин или пароль QRZ.ru"
        error.contains("expired", true) -> "Сессия QRZ.ru истекла"
        else -> "QRZ.ru: $error"
    }
}
