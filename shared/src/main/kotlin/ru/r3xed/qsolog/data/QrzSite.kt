package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The callsign page of the QRZ.ru site (https://www.qrz.ru/db/CALL), read as HTML with the site's own account
 * (e-mail and password). A fallback for users without XML API access: the page layout is not an interface and can
 * change at any time, and every request adds a "Просмотр" to the station's counter.
 *
 * Without logging in the page shows the name with patronymic and the surname's initial (the full name is in <title>),
 * city, region, country and RDA; the details (address, locator…) need an account.
 */
class QrzSite(private val credentials: () -> Pair<String, String>) {
    private val gate = Mutex()
    private val web = WebSession("QRZ.ru")
    private var loggedIn = false

    /** Logs in; throws [QrzException] with a readable message on a wrong e-mail or password. */
    suspend fun login() = gate.withLock { loginLocked() }

    /** Null when the site does not know the callsign. */
    suspend fun lookup(call: String): QrzInfo? = gate.withLock {
        val (email, _) = credentials()
        if (email.isNotBlank() && !loggedIn) loginLocked()
        var html = get("$BASE/db/${enc(call.uppercase())}")
        // Session expired: log in again once.
        if (email.isNotBlank() && needsLogin(html)) {
            loggedIn = false
            loginLocked()
            html = get("$BASE/db/${enc(call.uppercase())}")
        }
        parse(html, call)
    }

    fun reset() {
        web.clear()
        loggedIn = false
    }

    private suspend fun loginLocked() {
        val (email, password) = credentials()
        if (email.isBlank() || password.isBlank()) throw QrzException(403, tr("Укажите e-mail и пароль сайта QRZ.ru"))
        web.clear()
        get("$BASE/passport/login") // session cookie first, as a browser would
        val form = "Form%5Bemail%5D=${enc(email.trim())}&Form%5Bpassword%5D=${enc(password)}&Form%5BrememberMe%5D=1"
        val page = post("$BASE/passport/login", form)
        if (page.contains("id=\"login-form\"")) throw QrzException(403, tr("Сайт QRZ.ru не принял e-mail или пароль"))
        loggedIn = true
    }

    private suspend fun get(url: String) = web.get(url)
    private suspend fun post(url: String, body: String) = web.post(url, body)

    companion object {
        const val BASE = "https://www.qrz.ru"
        private val LOCATOR = Regex("\\b([A-R]{2}\\d{2}(?:[A-X]{2})?)\\b", RegexOption.IGNORE_CASE)

        private fun enc(s: String) = WebSession.enc(s)

        /** The page offers "авторизоваться" instead of the details. */
        fun needsLogin(html: String) = html.contains("id=\"detailInfo\"") && html.substringAfter("id=\"detailInfo\"").take(600).contains("/passport/login")

        /** The callsign page as [QrzInfo] (plus region and RDA), or null when the site says the callsign is unknown. */
        fun parse(html: String, call: String): QrzInfo? {
            if (!html.contains("id=\"infoBlock\"")) return null
            val info = html.substringAfter("id=\"infoBlock\"").substringBefore("id=\"detailInfo\"")
            val details = if (html.contains("id=\"detailInfo\"")) html.substringAfter("id=\"detailInfo\"").substringBefore("<script") else ""

            // The data block: <b>name</b>, <div color:gray><b>Latin name</b></div>, the address line, RDA.
            // (A photo, "ex CALL(s)" or "позывной устарел" may come before it inside #infoBlock.)
            val block = info.substringAfter("font-family:Verdana", info)
            val shown = text(Regex("<b>(.*?)</b>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1).orEmpty())
            val title = text(Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1).orEmpty())
                .substringAfter(" - ", "").substringBefore("::").trim()
            val titleWords = title.split(' ').filter { it.isNotBlank() }
            val shownWords = shown.split(' ').filter { it.isNotBlank() }
            val surname = if (titleWords.size >= 2) titleWords.last() else ""
            // Anonymous: "Имя Отчество Ч" — the initial gives way to the surname from the title.
            // Logged in the block may already hold the whole "Имя Отчество Фамилия": the surname is not repeated.
            val nameWords = when {
                shownWords.isEmpty() -> titleWords.dropLast(1)
                surname.isNotEmpty() && shownWords.last().equals(surname, ignoreCase = true) -> shownWords.dropLast(1)
                surname.isNotEmpty() && shownWords.size >= 2 && shownWords.last().length <= 2 &&
                    surname.startsWith(shownWords.last().trimEnd('.'), ignoreCase = true) -> shownWords.dropLast(1)
                else -> shownWords
            }
            val name = nameWords.joinToString(" ")

            val country = Regex("<img[^>]*/flags/[^>]*title=\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let(::text).orEmpty()
            // The address: after the grey Latin name (or after the name), up to RDA or the end of the block.
            val afterName = if (block.contains("color:gray")) block.substringAfter("color:gray").substringAfter("</div>")
            else block.substringAfter("</b>", "")
            val addressHtml = afterName.substringBefore("RDA").substringBefore("Просмотров").substringBefore("</div>")
            val lines = text(addressHtml).lines().map { it.trim() }.filter { it.isNotEmpty() }
            // "Город, Область Страна"; logged in there may be more lines (street, postcode): the one with the country wins.
            val address = lines.firstOrNull { country.isNotEmpty() && it.endsWith(country) } ?: lines.firstOrNull().orEmpty()
            val parts = address.removeSuffix(country).trim().trimEnd(',').split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val city = parts.firstOrNull().orEmpty()
            val region = parts.drop(1).joinToString(", ")
            val rda = Regex("RDA(?:/URDA)?\\s*#?\\s*([A-Z]{2}-\\d{2})").find(info + details)?.groupValues?.get(1).orEmpty()

            // Details for a logged-in user: a locator and coordinates if the page has them.
            val detailText = text(details)
            val locator = Regex("(?iu)(?:локатор|qth[- ]?loc\\w*|grid)[^A-Za-z]{0,20}([A-R]{2}\\d{2}(?:[A-X]{2})?)").find(detailText)?.groupValues?.get(1)
                ?: LOCATOR.find(detailText)?.groupValues?.get(1)
            val lat = Regex("(?iu)(?:широта|latitude|lat)\\s*[:=]?\\s*(-?\\d{1,2}[.,]\\d+)").find(detailText)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            val lon = Regex("(?iu)(?:долгота|longitude|lon)\\s*[:=]?\\s*(-?\\d{1,3}[.,]\\d+)").find(detailText)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

            return QrzInfo(
                call = call.uppercase(), name = name, surname = surname, city = city, country = country,
                locator = locator?.let { it.take(4).uppercase() + it.drop(4).lowercase() }.orEmpty(),
                lat = lat?.takeIf { lon != null }, lon = lon?.takeIf { lat != null },
                region = region, rda = rda,
            )
        }

        private fun text(html: String) = WebSession.text(html)
    }
}
