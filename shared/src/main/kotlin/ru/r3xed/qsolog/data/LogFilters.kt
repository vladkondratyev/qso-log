package ru.r3xed.qsolog.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * What the log shows besides the search and the station: contacts not yet in an online logbook ([unsent]), the paper
 * QSL queue ([qsl]), one band or mode. Saved under a [name] as "Мои фильтры" (search and station included).
 */
data class LogFilter(
    val name: String = "",
    val query: String = "",
    val station: String? = null,
    val unsent: OnlineLog? = null,
    val qsl: QslQueue? = null,
    val band: String? = null,
    val mode: String? = null,
) {
    /** The parts beyond search and station: nothing set — the log as usual. */
    val active get() = unsent != null || qsl != null || band != null || mode != null

    fun matches(q: Qso): Boolean =
        (unsent == null || !unsent.isSent(q)) &&
            (qsl == null || qsl.matches(q)) &&
            (band == null || q.band.equals(band, ignoreCase = true)) &&
            (mode == null || q.mode.equals(mode, ignoreCase = true))

    /** "LoTW: не отправлено · 20m" — the chip's text. */
    fun describe(): String = listOfNotNull(
        unsent?.let { ru.r3xed.qsolog.tr("%s: не отправлено", it.title) },
        qsl?.title, band, mode,
    ).joinToString(" · ")
}

/** Paper QSL: the cards still to send, and the ones that came. */
enum class QslQueue {
    TO_SEND, RECEIVED;

    val title: String get() = if (this == TO_SEND) ru.r3xed.qsolog.tr("QSL: к отправке") else ru.r3xed.qsolog.tr("QSL: получены")

    fun matches(q: Qso) = if (this == TO_SEND) !q.qslSent else q.qslRcvd
}

object LogFilters {
    /** One filter per line, its fields URL-encoded: name=…&query=…&station=…&unsent=LOTW&qsl=TO_SEND&band=20m&mode=CW. */
    fun encode(list: List<LogFilter>): String = list.joinToString("\n") { f ->
        listOfNotNull(
            "name" to f.name, "query" to f.query, f.station?.let { "station" to it }, f.unsent?.let { "unsent" to it.name },
            f.qsl?.let { "qsl" to it.name }, f.band?.let { "band" to it }, f.mode?.let { "mode" to it },
        ).joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    }

    fun decode(text: String): List<LogFilter> = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val m = line.split('&').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i < 0) null else p.substring(0, i) to URLDecoder.decode(p.substring(i + 1), "UTF-8")
        }.toMap()
        val name = m["name"]?.ifBlank { null } ?: return@mapNotNull null
        LogFilter(
            name = name, query = m["query"].orEmpty(), station = m["station"],
            unsent = m["unsent"]?.let { n -> OnlineLog.entries.firstOrNull { it.name == n } },
            qsl = m["qsl"]?.let { n -> QslQueue.entries.firstOrNull { it.name == n } },
            band = m["band"], mode = m["mode"],
        )
    }
}
