package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A browser-like session with a site read as HTML ([QrzSite], [QrzCom]): cookies kept by hand, redirects followed by
 * hand so that cookies set on the way (logins set them on a 302) are kept, and a polite pause between requests.
 * Not thread-safe: the callers serialize requests with their own mutex.
 */
internal class WebSession(private val site: String, private val minIntervalMs: Long = 1500L, private val language: String = "ru") {
    val cookies = linkedMapOf<String, String>()
    private var lastRequest = 0L

    fun clear() = cookies.clear()

    suspend fun get(url: String) = request(url, null)
    suspend fun post(url: String, body: String) = request(url, body)

    private suspend fun request(start: String, body: String?): String = withContext(Dispatchers.IO) {
        // Polite pace: the site is not an API.
        val wait = lastRequest + minIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        var url = start
        var postBody = body
        repeat(6) {
            lastRequest = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", BROWSER)
                conn.setRequestProperty("Accept-Language", language)
                // A kept-alive socket goes stale during the pause between requests (QRZ.com then gives no status line).
                conn.setRequestProperty("Connection", "close")
                if (cookies.isNotEmpty()) conn.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                if (postBody != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.setRequestProperty("Referer", url)
                    conn.outputStream.use { it.write(postBody!!.toByteArray()) }
                }
                val code = conn.responseCode
                conn.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }.forEach { c ->
                    val pair = c.substringBefore(';')
                    val name = pair.substringBefore('=').trim()
                    val value = pair.substringAfter('=', "").trim()
                    if (name.isNotEmpty()) if (value.isEmpty() || value == "deleted") cookies.remove(name) else cookies[name] = value
                }
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw QrzException(code, tr("Сайт %s ответил %s", site, code))
                    url = URL(URL(url), loc).toString()
                    postBody = null
                    return@repeat
                }
                if (code != 200) throw QrzException(code, tr("Сайт %s ответил %s", site, code))
                return@withContext (conn.inputStream).use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                conn.disconnect()
            }
        }
        throw QrzException(0, tr("Сайт %s: слишком много перенаправлений", site))
    }

    companion object {
        private const val BROWSER = "Mozilla/5.0 (QSO-LOG; +https://github.com/vladkondratyev/qso-log)"

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        /** Tags out, entities decoded, runs of spaces collapsed (line breaks kept). */
        fun text(html: String): String = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'")
            .replace("&deg;", "°").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .lines().joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
            .trim()
    }
}
