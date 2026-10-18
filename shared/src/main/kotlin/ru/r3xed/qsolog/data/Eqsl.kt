package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.tr
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class EqslException(message: String) : Exception(message)

/**
 * The eQSL inbox: the cards other stations sent. A card that matches a contact of the log marks it as confirmed
 * ([RCVD] = "Y", [RCVD_DATE] = the day); the cards with no contact in the log are offered to be added.
 * https://www.eqsl.cc/qslcard/DownloadInBox.txt
 */
object Eqsl {
    const val RCVD = "EQSL_QSL_RCVD"
    const val RCVD_DATE = "EQSL_QSLRDATE"
    val RCVD_FIELDS = setOf(RCVD, RCVD_DATE)

    /** A card and a contact are the same QSO when their times differ by no more than this. */
    const val WINDOW_MS = 30 * 60_000L

    fun isReceived(adif: Map<String, String>) = adif[RCVD].equals("Y", ignoreCase = true)

    /** "18.10.2026", "" when the day is unknown; null when no eQSL came. */
    fun receivedAt(adif: Map<String, String>): String? {
        if (!isReceived(adif)) return null
        return adif[RCVD_DATE]?.let { d ->
            runCatching { LocalDate.parse(d, DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrNull()
        }.orEmpty()
    }

    /** What the inbox gave: the contacts newly confirmed (already marked), how many were confirmed before, the cards not in the log. */
    data class Match(val confirmed: List<Qso>, val already: Int, val missing: List<Qso>)

    /** The records of the inbox; throws [EqslException] with what to tell the user. */
    suspend fun downloadInbox(user: String, password: String, nickname: String, agent: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = "https://www.eqsl.cc/qslcard/DownloadInBox.cfm?UserName=${enc(user.trim())}&Password=${enc(password)}" +
            (if (nickname.isNotBlank()) "&QTHNickname=${enc(nickname.trim())}" else "") + "&HamOnly=1"
        val (code, page) = try {
            OnlineLogs.get(url, agent)
        } catch (e: Exception) {
            throw EqslException(OnlineLogs.netError("eQSL", e))
        }
        if (code != 200) throw EqslException(tr("eQSL ответил %s, попробуйте позже", code))
        val html = page.toString(Charsets.UTF_8)
        val link = fileLink(html) ?: throw EqslException(
            if (html.contains("no log entries", ignoreCase = true) || html.contains("no records", ignoreCase = true)) tr("В ящике eQSL нет карточек")
            else tr("eQSL: %s", pageError(html) ?: tr("неожиданный ответ сервера")),
        )
        val (fileCode, bytes) = try {
            OnlineLogs.get(URL(URL(url), link).toString(), agent)
        } catch (e: Exception) {
            throw EqslException(OnlineLogs.netError("eQSL", e))
        }
        if (fileCode != 200) throw EqslException(tr("eQSL ответил %s, попробуйте позже", fileCode))
        Adif.parseRecords(bytes, Adif.detectCharset(bytes))
    }

    /** The link to the built .adi file after "Your ADIF log file has been built"; null when the page has none. */
    fun fileLink(html: String): String? {
        val at = html.indexOf("Your ADIF log file has been built", ignoreCase = true)
        if (at < 0) return null
        return Regex("""href\s*=\s*["']?([^"' >]+\.adi)""", RegexOption.IGNORE_CASE).find(html, at)?.groupValues?.get(1)
    }

    /** The reason the page gives ("Error: No such Username/Password found"), without the markup. */
    fun pageError(html: String): String? {
        val text = html.replace(Regex("<[^>]+>"), "\n")
        return Regex("(?:Error|Warning):\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
            ?: text.lines().map { it.trim() }.firstOrNull { it.contains("password", ignoreCase = true) || it.contains("not found", ignoreCase = true) }
    }

    /** SSB, USB and LSB are one mode for the match; the rest must be equal. */
    private fun modeKey(m: String) = m.uppercase().let { if (it in setOf("USB", "LSB")) "SSB" else it }

    /**
     * Matches the inbox [records] with the [log]: same station (the home callsign: R0DEMO/P is R0DEMO), band and mode,
     * times within [WINDOW_MS]; the nearest contact wins, each is used once. The mark takes the day eQSL gives the card,
     * [today] when it gives none.
     */
    fun match(records: List<Map<String, String>>, log: List<Qso>, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Match {
        val byCall = log.groupBy { Callsign.home(it.call) }
        val used = mutableSetOf<Long>()
        val confirmed = mutableListOf<Qso>()
        val missing = mutableListOf<Qso>()
        var already = 0
        val day = today.format(DateTimeFormatter.BASIC_ISO_DATE)
        for (r in records) {
            val card = toQso(r, day) ?: continue
            val best = byCall[Callsign.home(card.call)].orEmpty()
                .filter { it.id !in used && it.band.equals(card.band, ignoreCase = true) && modeKey(it.mode) == modeKey(card.mode) && abs(it.timeUtc - card.timeUtc) <= WINDOW_MS }
                .minByOrNull { abs(it.timeUtc - card.timeUtc) }
            when {
                best == null -> missing += card
                isReceived(best.adif) -> { used += best.id; already++ }
                else -> {
                    used += best.id
                    confirmed += best.copy(adif = best.adif + (RCVD to "Y") + (RCVD_DATE to card.adif[RCVD_DATE].orEmpty()), updatedAt = System.currentTimeMillis())
                }
            }
        }
        return Match(confirmed, already, missing.distinctBy { listOf(it.call, it.band, it.mode, it.timeUtc) })
    }

    /**
     * A card as a contact of our log: the RST it gives is the one we received; its "QSL sent" is the other station's
     * card, here an eQSL received (on the day eQSL gives, else [day]). Null for a record without a callsign or date.
     */
    fun toQso(r: Map<String, String>, day: String = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)): Qso? {
        val mine = r - setOf("RST_SENT", "RST_RCVD", "QSL_SENT", "QSL_SENT_VIA", "QSL_RCVD") +
            listOfNotNull(r["RST_SENT"]?.let { "RST_RCVD" to it }) + (RCVD to "Y") +
            (RCVD_DATE to (r[RCVD_DATE]?.trim()?.takeIf { it.length == 8 && it.all(Char::isDigit) } ?: day))
        return Adif.toQso(mine)
    }
}

/** The "eQSL: полученные карточки" dialog. */
sealed interface EqslInbox {
    data object Loading : EqslInbox
    /** [settings]: the account is not set, the dialog offers to open the settings. */
    data class Failed(val message: String, val settings: Boolean = false) : EqslInbox
    data class Done(val match: Eqsl.Match) : EqslInbox
}
