package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.tr
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * HamQTH.com's callbook with the user's free HamQTH account (API v2, https://www.hamqth.com/developers.php): name, QTH,
 * locator and coordinates, zones. The login gives a token for an hour; a 401 means log in again.
 */
class HamQthBook(private val credentials: () -> Pair<String, String>) {
    private val gate = Mutex()
    private var token: String? = null
    private var tokenUntil = 0L

    suspend fun lookup(call: String): QrzInfo? = gate.withLock {
        val t = token?.takeIf { System.currentTimeMillis() < tokenUntil } ?: login()
        val (code, body) = get(t, call)
        when (code) {
            200 -> parse(body, call)
            404 -> null
            401 -> {
                val (again, b2) = get(login(), call)
                if (again == 404) null else if (again == 200) parse(b2, call) else throw QrzException(again, tr("HamQTH ответил %s", again))
            }
            else -> throw QrzException(code, tr("HamQTH ответил %s", code))
        }
    }

    fun reset() {
        token = null
    }

    /** "Проверить вход": logs in, throws [QrzException] with a readable message when refused. */
    suspend fun check() {
        gate.withLock { login() }
    }

    private suspend fun login(): String {
        val (user, password) = credentials()
        if (user.isBlank() || password.isBlank()) throw QrzException(0, tr("Укажите логин и пароль HamQTH"))
        val (code, body) = http(
            "$BASE/login", "POST", body = MiniJson.encode(mapOf("callsign" to user.trim(), "password" to password)),
            headers = mapOf("Content-Type" to "application/json"),
        )
        if (code == 401 || code == 403) throw QrzException(403, tr("HamQTH не принял логин или пароль"))
        if (code == 429) throw QrzException(429, tr("HamQTH временно не пускает: слишком много попыток входа"))
        val m = MiniJson.parse(body) as? Map<*, *>
        val t = m?.get("token") as? String ?: throw QrzException(code, tr("HamQTH ответил %s", code))
        val life = ((m["expiresIn"] as? Double) ?: 3600.0).toLong()
        token = t
        tokenUntil = System.currentTimeMillis() + (life - 60).coerceAtLeast(60) * 1000
        return t
    }

    private suspend fun get(token: String, call: String) =
        http("$BASE/callbook/" + URLEncoder.encode(call.uppercase(), "UTF-8") + "?app=QSO-LOG", "GET", headers = mapOf("Authorization" to "Bearer $token"))

    companion object {
        const val BASE = "https://www.hamqth.com/api/v2"

        /** The callbook answer (JSON) as [QrzInfo]: name, QTH, locator and coordinates, zones, region. */
        fun parse(json: String, call: String): QrzInfo? {
            val m = MiniJson.parse(json) as? Map<*, *> ?: return null
            fun s(v: Any?): String = when (v) {
                null -> ""
                is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
                else -> v.toString().trim()
            }
            val loc = m["location"] as? Map<*, *>
            val address = m["address"] as? Map<*, *>
            val region = m["region"] as? Map<*, *>
            val name = s(m["name"]).ifBlank { s(address?.get("name")) }.ifBlank { s(m["nick"]) }
            val grid = s(loc?.get("grid"))
            return QrzInfo(
                call = s(m["callsign"]).uppercase().ifBlank { call.uppercase() },
                name = name, surname = "",
                city = s(m["qth"]).ifBlank { s(address?.get("city")) },
                country = s(m["country"]),
                locator = if (Geo.isLocator(grid)) grid.take(4).uppercase() + grid.drop(4).lowercase() else "",
                lat = (loc?.get("lat") as? Double)?.takeIf { loc["lon"] is Double },
                lon = (loc?.get("lon") as? Double)?.takeIf { loc["lat"] is Double },
                region = listOf("oblast", "usState", "district").map { s(region?.get(it)) }.firstOrNull { it.isNotBlank() }.orEmpty(),
                cqZone = s(m["cq"]), ituZone = s(m["itu"]),
            )
        }
    }
}

/**
 * QRZCQ.com's XML API (https://www.qrzcq.com/docs/api/xml/): like QRZ.com's — a session key from the login (valid for
 * days), then one request per callsign. The API is for QRZCQ's premium members; others get "Premium Required".
 */
class QrzCq(private val credentials: () -> Pair<String, String>) {
    private val gate = Mutex()
    private var key: String? = null

    suspend fun lookup(call: String): QrzInfo? = gate.withLock {
        val k = key ?: login()
        val r = query(k, call)
        if (r.error?.contains("Session", ignoreCase = true) == true || r.error?.contains("Invalid", ignoreCase = true) == true) {
            return@withLock answer(query(login(), call), call)
        }
        answer(r, call)
    }

    fun reset() {
        key = null
    }

    /** "Проверить вход": logs in, throws [QrzException] with a readable message when refused. */
    suspend fun check() {
        gate.withLock { login() }
    }

    private fun answer(r: Response, call: String): QrzInfo? {
        if (r.error?.startsWith("Not found", ignoreCase = true) == true) return null
        r.error?.let { throw QrzException(0, "QRZCQ: $it") }
        return toInfo(r.fields, call)
    }

    private suspend fun login(): String {
        val (user, password) = credentials()
        if (user.isBlank() || password.isBlank()) throw QrzException(0, tr("Укажите логин и пароль QRZCQ"))
        val (_, body) = http("$BASE?username=${enc(user.trim())}&password=${enc(password)}&agent=QSO-LOG", "GET")
        val r = parse(body)
        r.error?.let { e ->
            throw QrzException(
                403,
                when {
                    e.contains("Premium", ignoreCase = true) -> tr("QRZCQ: поиск через программы доступен только с премиум-подпиской QRZCQ")
                    e.contains("Password", ignoreCase = true) || e.contains("User", ignoreCase = true) -> tr("QRZCQ не принял логин или пароль")
                    else -> "QRZCQ: $e"
                },
            )
        }
        return r.session["Key"]?.also { key = it } ?: throw QrzException(0, tr("QRZCQ не вернул ключ сессии"))
    }

    private suspend fun query(key: String, call: String): Response =
        parse(http("$BASE?s=${enc(key)}&callsign=${enc(call.uppercase())}&agent=QSO-LOG", "GET").second)

    class Response(val fields: Map<String, String>, val session: Map<String, String>, val error: String?)

    companion object {
        const val BASE = "https://ssl.qrzcq.com/xml"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        /** The <Callsign> and <Session> blocks of an answer as flat maps. */
        fun parse(xml: String): Response {
            fun block(name: String): Map<String, String> {
                val body = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1) ?: return emptyMap()
                return Regex("<(\\w+)>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(body)
                    .associate { it.groupValues[1] to WebSession.text(it.groupValues[2]).trim() }
            }
            val session = block("Session")
            return Response(block("Callsign"), session, session["Error"]?.ifBlank { null })
        }

        fun toInfo(c: Map<String, String>, call: String): QrzInfo? {
            if (c.isEmpty()) return null
            val grid = c["locator"].orEmpty()
            val lat = c["latitude"]?.toDoubleOrNull()
            val lon = c["longitude"]?.toDoubleOrNull()
            fun zone(k: String) = c[k]?.takeIf { it != "0" }.orEmpty()
            return QrzInfo(
                call = c["call"]?.uppercase() ?: call.uppercase(),
                name = c["name"].orEmpty(), surname = "",
                city = c["city"].orEmpty().ifBlank { c["qth"].orEmpty() },
                country = c["country"].orEmpty(),
                locator = if (Geo.isLocator(grid)) grid.take(4).uppercase() + grid.drop(4).lowercase() else "",
                lat = lat?.takeIf { lon != null && !(lat == 0.0 && lon == 0.0) },
                lon = lon?.takeIf { lat != null && !(lat == 0.0 && lon == 0.0) },
                region = c["state"].orEmpty(),
                cqZone = zone("cq"), ituZone = zone("itu"),
            )
        }
    }
}

/** A request to a callbook: the status and the body (of an error too). */
private suspend fun http(url: String, method: String, body: String? = null, headers: Map<String, String> = emptyMap()): Pair<Int, String> =
    withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "QSO-LOG")
            c.setRequestProperty("Accept", "application/json, text/xml")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            c.disconnect()
        }
    }

/**
 * Fills what [first] lacks from [more] (the next callbook's answer): the QTH, the locator and coordinates, the name,
 * the zones and the region. A field [first] has stays.
 */
fun QrzInfo.fillFrom(more: QrzInfo): QrzInfo {
    val havePosition = (lat != null && lon != null) || locator.isNotBlank()
    return copy(
        name = name.ifBlank { more.name },
        surname = if (name.isBlank()) more.surname else surname,
        city = city.ifBlank { more.city },
        country = country.ifBlank { more.country },
        locator = if (havePosition) locator else more.locator,
        lat = if (havePosition) lat else more.lat,
        lon = if (havePosition) lon else more.lon,
        region = region.ifBlank { more.region },
        cqZone = cqZone.ifBlank { more.cqZone },
        ituZone = ituZone.ifBlank { more.ituZone },
    )
}

/** Has what the card needs most: the QTH and where the station is. */
val QrzInfo.complete: Boolean get() = city.isNotBlank() && ((lat != null && lon != null) || locator.isNotBlank())
