package ru.r3xed.qsolog.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The newest Android release on GitHub: version, what changed, where to download it. */
data class Release(val version: String, val notes: String, val apkUrl: String?, val pageUrl: String)

/**
 * Asks GitHub for the latest Android release of the project (tags "vX.Y.Z"; desktop ones are "desktop-v…").
 * First the REST API; it allows 60 requests an hour per IP without a token, so when it refuses (403/429,
 * common behind a shared mobile or office IP) the public Atom feed of releases is read instead.
 */
object UpdateChecker {
    private const val REPO = "https://github.com/vladkondratyev/qso-log"
    private const val API = "https://api.github.com/repos/vladkondratyev/qso-log/releases/latest"

    /** Blocking network calls; run it off the main thread. */
    fun latest(): Release = try {
        fromApi()
    } catch (e: RefusedException) {
        fromFeed()
    }

    private class RefusedException(code: Int) : Exception("GitHub ответил $code")

    private fun fromApi(): Release {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "QSO-LOG")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw RefusedException(conn.responseCode)
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) apk = a.optString("browser_download_url")
            }
            return Release(
                version = json.optString("tag_name").removePrefix("v"),
                notes = plainText(json.optString("body")),
                apkUrl = apk,
                pageUrl = json.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** The releases feed, newest first; the first entry with an Android tag ("v0.9.0") wins. */
    private fun fromFeed(): Release {
        val conn = URL("$REPO/releases.atom").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "QSO-LOG")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        val xml = try {
            if (conn.responseCode != 200) throw IllegalStateException("GitHub ответил ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        for (entry in Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val body = entry.groupValues[1]
            val tag = Regex("releases/tag/([^\"]+)\"").find(body)?.groupValues?.get(1) ?: continue
            if (!Regex("^v\\d").containsMatchIn(tag)) continue
            val version = tag.removePrefix("v")
            val html = unescape(Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty())
            return Release(
                version = version,
                notes = htmlToText(html),
                // Release APKs are always named QSO-LOG-<version>.apk.
                apkUrl = "$REPO/releases/download/$tag/QSO-LOG-$version.apk",
                pageUrl = "$REPO/releases/tag/$tag",
            )
        }
        throw IllegalStateException("в ленте релизов нет версии для Android")
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")

    private fun htmlToText(html: String): String = unescape(
        html.replace(Regex("<li>"), "• ")
            .replace(Regex("</(li|p|h\\d)>|<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
    ).lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /** True when [candidate] ("0.9.0") is newer than [current] ("0.8.1"). Missing parts count as 0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Release notes are Markdown; the dialog shows them as plain text. */
    private fun plainText(md: String): String = md.lines().joinToString("\n") { line ->
        line.replace(Regex("^#{1,6}\\s*"), "")
            .replace("**", "")
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("^- "), "• ")
    }.trim()
}
