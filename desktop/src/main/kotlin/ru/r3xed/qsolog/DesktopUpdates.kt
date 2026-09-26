package ru.r3xed.qsolog

import java.awt.Desktop
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** The newest desktop release on GitHub: version, what changed, the file for this system. */
data class DesktopRelease(val version: String, val notes: String, val downloadUrl: String, val pageUrl: String)

/**
 * Desktop releases are tagged "desktop-vX.Y.Z" and published as not-latest (the "latest" one is Android), so
 * the releases feed is read and the first desktop entry wins. The feed has no request limit, unlike the REST API.
 */
object DesktopUpdates {
    private const val REPO = "https://github.com/vladkondratyev/qso-log"

    fun latest(): DesktopRelease {
        val conn = URL("$REPO/releases.atom").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", USER_AGENT)
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
            if (!tag.startsWith("desktop-v")) continue
            val version = tag.removePrefix("desktop-v")
            val html = unescape(Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1).orEmpty())
            return DesktopRelease(version, htmlToText(html), "$REPO/releases/download/$tag/${fileName(version)}", "$REPO/releases/tag/$tag")
        }
        throw IllegalStateException("в ленте релизов нет версии для компьютера")
    }

    /** Release files are always named like this, see the build scripts. */
    private fun fileName(version: String) = when {
        IS_MAC -> "QSO-LOG-$version-macos-arm64.dmg"
        IS_WINDOWS -> "QSO-LOG-$version-windows-x64.zip"
        else -> "QSO-LOG-$version-linux-amd64.deb"
    }

    /** True when [candidate] ("1.3.0") is newer than [current] ("1.2.0"). Missing parts count as 0. */
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

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")

    private fun htmlToText(html: String): String = unescape(
        html.replace(Regex("<li>"), "• ")
            .replace(Regex("</(li|p|h\\d)>|<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
    ).lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

/** Opens a link in the default browser; on Linux desktops without AWT support falls back to xdg-open. */
fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            return
        }
    } catch (_: Exception) {
    }
    try {
        ProcessBuilder("xdg-open", url).start()
    } catch (_: Exception) {
    }
}
