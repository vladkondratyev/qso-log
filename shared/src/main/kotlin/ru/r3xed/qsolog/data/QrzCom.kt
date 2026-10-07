package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The callsign page of QRZ.com (https://www.qrz.com/db/CALL), read as HTML with the user's own QRZ.com account.
 * The free account is enough: the XML API without a paid subscription gives no locator, the page after logging in
 * does. Like [QrzSite], the page is not an interface and can change at any time.
 *
 * Without logging in the page shows only the country; after it — name, address, and in the "Detail" table
 * latitude, longitude, grid square and the zones.
 *
 * Logging in is the site's own two steps (POST /login-handshake: the username, then the password), then the form
 * itself is posted to /login. Two-factor accounts are not supported.
 */
class QrzCom(private val credentials: () -> Pair<String, String>) {
    private val gate = Mutex()
    private val web = WebSession("QRZ.com", language = "en")
    private var loggedIn = false

    /** Logs in; throws [QrzException] with a readable message on a wrong username or password. */
    suspend fun login() = gate.withLock { loginLocked() }

    /** Null when QRZ.com does not know the callsign. */
    suspend fun lookup(call: String): QrzInfo? = gate.withLock {
        if (!loggedIn) loginLocked()
        var html = web.get("$BASE/db/${WebSession.enc(call.uppercase())}")
        // Session expired: log in again once.
        if (needsLogin(html)) {
            loggedIn = false
            loginLocked()
            html = web.get("$BASE/db/${WebSession.enc(call.uppercase())}")
        }
        parse(html, call)
    }

    fun reset() {
        web.clear()
        loggedIn = false
    }

    private suspend fun loginLocked() {
        val (user, password) = credentials()
        if (user.isBlank() || password.isBlank()) throw QrzException(403, tr("Укажите логин и пароль QRZ.com"))
        web.clear()
        // The page sets this one from JavaScript to see that cookies work.
        web.cookies["QRZ_Cookie_Test"] = "CoK+JSoK"
        val page = web.get("$BASE/login")
        val ticket = Regex("loginTicket'\\s*:\\s*'([0-9a-f]+)'").find(page)?.groupValues?.get(1)
            ?: throw QrzException(0, tr("QRZ.com: не найдена форма входа"))
        val u = WebSession.enc(user.trim())
        val p = WebSession.enc(password)
        handshake(web.post("$BASE/login-handshake", "loginTicket=$ticket&username=$u&step=1"))
        val second = web.post("$BASE/login-handshake", "loginTicket=$ticket&username=$u&password=$p&step=2")
        handshake(second)
        if (Regex("\"twofactor\"\\s*:\\s*true").containsMatchIn(second)) {
            throw QrzException(403, tr("QRZ.com: двухфакторный вход не поддерживается"))
        }
        val done = web.post("$BASE/login", "login_ref=&username=$u&password=$p&2fcode=&target=%2F&flush=1")
        if (!done.contains("Login Successful", ignoreCase = true)) throw QrzException(403, tr("QRZ.com не принял логин или пароль"))
        loggedIn = true
    }

    /** A step of the site's login: JSON with "error": true and a "message" when the username or password is wrong. */
    private fun handshake(json: String) {
        if (!Regex("\"error\"\\s*:\\s*true").containsMatchIn(json)) return
        val msg = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.let(WebSession::text).orEmpty()
        throw QrzException(403, tr("QRZ.com не принял логин или пароль") + if (msg.isNotBlank()) ": $msg" else "")
    }

    companion object {
        const val BASE = "https://www.qrz.com"

        /** The page asks to log in for the details (anonymous or an expired session). */
        fun needsLogin(html: String) = html.contains("Login is required for additional detail")

        /** The callsign page as [QrzInfo] (plus region and zones), or null when QRZ.com does not know the callsign. */
        fun parse(html: String, call: String): QrzInfo? {
            if (!html.contains("id=\"csdata\"")) return null
            val data = html.substringAfter("id=\"csdata\"").substringBefore("</td>")
            val country = Regex("<span style=\"position:relative;top:-8px;\">(.*?)</span>", RegexOption.DOT_MATCHES_ALL).find(data)?.groupValues?.get(1)
                ?.let(WebSession::text)
                ?: Regex("<img id=\"flg\"[^>]*alt=\"([^\"]*?) flag\"").find(data)?.groupValues?.get(1).orEmpty()

            // <p …><span style="color: black; font-weight: bold">Name</span><span class="csgnl">, CALL</span><br />street<br/>postcode town<br/>Country</p>
            val name = Regex("<span style=\"color: black; font-weight: bold\">(.*?)</span>", RegexOption.DOT_MATCHES_ALL).find(data)?.groupValues?.get(1)
                ?.let(WebSession::text).orEmpty()
            val addressHtml = Regex("font-weight: bold\">.*?</span>(?:<span class=\"csgnl[^\"]*\">.*?</span>)?(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(data)?.groupValues?.get(1).orEmpty()
            val lines = WebSession.text(addressHtml).lines().map { it.trim() }.filter { it.isNotEmpty() }
                .let { l -> if (country.isNotEmpty() && l.lastOrNull().equals(country, ignoreCase = true)) l.dropLast(1) else l }
            // The last line is the town with its postcode ("37547 Kreiensen", "NEWINGTON, CT 06111"); a comma splits off the region.
            val town = lines.lastOrNull().orEmpty().replace(Regex("\\b[A-Z]{0,2}-?\\d[\\d -]{2,}\\b"), " ").replace(Regex("\\s+"), " ").trim().trim(',').trim()
            val city = town.substringBefore(',').trim()
            val region = town.substringAfter(',', "").trim()

            fun detail(label: String) = Regex("<td class=\"dh\">\\s*$label\\s*</td>\\s*<td class=\"d[iw]\">(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1)?.let(WebSession::text).orEmpty()
            val lat = Regex("^-?\\d{1,2}(?:\\.\\d+)?").find(detail("Latitude"))?.value?.toDoubleOrNull()
            val lon = Regex("^-?\\d{1,3}(?:\\.\\d+)?").find(detail("Longitude"))?.value?.toDoubleOrNull()
            val grid = Regex("^[A-R]{2}\\d{2}(?:[A-X]{2})?", RegexOption.IGNORE_CASE).find(detail("Grid Square"))?.value.orEmpty()

            return QrzInfo(
                call = call.uppercase(), name = name, surname = "", city = city, country = country,
                locator = grid.take(4).uppercase() + grid.drop(4).lowercase(),
                lat = lat?.takeIf { lon != null }, lon = lon?.takeIf { lat != null },
                region = region,
                cqZone = Regex("^\\d+").find(detail("CQ Zone"))?.value.orEmpty(),
                ituZone = Regex("^\\d+").find(detail("ITU Zone"))?.value.orEmpty(),
            )
        }
    }
}
