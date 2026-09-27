package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.tr
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Online logbooks the log is uploaded to. Which contacts were sent is kept in the contact itself, in the standard
 * ADIF fields ([statusField] = "Y", [dateField] = the day), so it travels with ADIF exports and only new ones go next time;
 * the exact UTC time of the upload goes into our own [timeField].
 */
enum class OnlineLog(val title: String, val statusField: String, val dateField: String) {
    LOTW("LoTW", "LOTW_QSL_SENT", "LOTW_QSLSDATE"),
    QRZCOM("QRZ.com", "QRZCOM_QSO_UPLOAD_STATUS", "QRZCOM_QSO_UPLOAD_DATE"),
    EQSL("eQSL", "EQSL_QSL_SENT", "EQSL_QSLSDATE"),
    CLUBLOG("Club Log", "CLUBLOG_QSO_UPLOAD_STATUS", "CLUBLOG_QSO_UPLOAD_DATE"),
    ;

    /** "2026-09-27 10:15" (UTC): when the contact was sent or exported. */
    val timeField: String get() = "APP_QSOLOG_${name}_SENT"

    /** Sent, or (LoTW on the phone) exported for TQSL and waiting to be signed ("Q"). */
    fun isSent(q: Qso): Boolean = q.adif[statusField]?.uppercase() in setOf("Y", "Q")

    /** Only exported to a file for TQSL, not signed and sent yet. */
    fun isQueued(adif: Map<String, String>): Boolean = adif[statusField].equals("Q", ignoreCase = true)

    fun mark(q: Qso, status: String = "Y", now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): Qso = q.copy(
        adif = markFields(q.adif, status, now),
        updatedAt = System.currentTimeMillis(),
    )

    fun markFields(adif: Map<String, String>, status: String = "Y", now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): Map<String, String> =
        adif + (statusField to status) + (dateField to now.format(DateTimeFormatter.BASIC_ISO_DATE)) + (timeField to now.format(TIME_FORMAT))

    /** The contact without this logbook's mark, so it goes again next time. */
    fun unmarkFields(adif: Map<String, String>): Map<String, String> = adif - statusField - dateField - timeField

    /** "27.09.2026 10:15 UTC", or only the day when the mark came without a time (from another program); null if not sent. */
    fun sentAt(adif: Map<String, String>): String? {
        if (adif[statusField]?.uppercase() !in setOf("Y", "Q")) return null
        adif[timeField]?.let { t ->
            runCatching { return LocalDateTime.parse(t, TIME_FORMAT).format(SHOW_TIME) + " UTC" }
        }
        return adif[dateField]?.let { d -> runCatching { LocalDate.parse(d, DateTimeFormatter.BASIC_ISO_DATE).format(SHOW_DATE) }.getOrNull() }.orEmpty()
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val SHOW_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        private val SHOW_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        /** Every field the upload marks use; the card shows them in their own block. */
        val FIELDS: Set<String> = entries.flatMap { listOf(it.statusField, it.dateField, it.timeField) }.toSet()
    }
}

/** Result of one upload: how many went, how many the service already had, and what failed (null = all fine). */
data class UploadResult(val sent: List<Qso>, val duplicates: Int, val error: String?)

object OnlineLogs {
    private val UPLOAD_FIELDS = OnlineLog.FIELDS

    /** One contact as an ADIF record for a service: our own APP_ fields and upload marks stay home. */
    fun record(q: Qso): String = buildString {
        for ((k, v) in Adif.fields(q)) {
            if (k.startsWith("APP_") || k in UPLOAD_FIELDS) continue
            append('<').append(k).append(':').append(v.toByteArray(Charsets.UTF_8).size).append('>').append(v)
        }
        append("<EOR>\n")
    }

    /** A whole ADIF file (UTF-8) for services that take a file. */
    fun file(list: List<Qso>, extra: (Qso) -> String = { "" }): String = buildString {
        append("QSO-LOG upload\n<ADIF_VER:5>3.1.4<PROGRAMID:7>QSO-LOG<EOH>\n")
        for (q in list.sortedBy { it.timeUtc }) append(record(q).removeSuffix("<EOR>\n")).append(extra(q)).append("<EOR>\n")
    }

    // ---------- QRZ.com Logbook: https://www.qrz.com/docs/logbook/QRZLogbookAPI.html ----------

    /** Parses "RESULT=OK&LOGID=…" style answers. */
    fun parseQrzCom(body: String): Map<String, String> = body.trim().split('&').mapNotNull {
        val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i).uppercase() to it.substring(i + 1)
    }.toMap()

    /** Inserts each contact; a duplicate counts as sent (the logbook already has it). Stops at a key problem. */
    suspend fun uploadQrzCom(apiKey: String, list: List<Qso>, agent: String): UploadResult = withContext(Dispatchers.IO) {
        val sent = mutableListOf<Qso>(); var dupes = 0
        for (q in list) {
            val answer = try {
                parseQrzCom(post("https://logbook.qrz.com/api", form("KEY" to apiKey.trim(), "ACTION" to "INSERT", "ADIF" to record(q)), agent).second)
            } catch (e: Exception) {
                return@withContext UploadResult(sent, dupes, netError("QRZ.com", e))
            }
            val result = answer["RESULT"]?.uppercase()
            val reason = answer["REASON"].orEmpty()
            when {
                result == "OK" || result == "REPLACE" -> sent += q
                reason.contains("duplicate", ignoreCase = true) -> { sent += q; dupes++ }
                result == "AUTH" -> return@withContext UploadResult(sent, dupes, tr("QRZ.com не принял API-ключ: %s", reason.ifBlank { "AUTH" }))
                else -> return@withContext UploadResult(sent, dupes, tr("QRZ.com: %s (%s)", reason.ifBlank { result ?: "?" }, q.call))
            }
        }
        UploadResult(sent, dupes, null)
    }

    // ---------- eQSL: https://www.eqsl.cc/qslcard/ADIFContentSpecs.cfm ----------

    /** "Result: 3 out of 4 records added" and "Error: …" lines of the ImportADIF answer. */
    fun parseEqsl(html: String): Pair<Int?, String?> {
        val text = html.replace(Regex("<[^>]+>"), "\n")
        val added = Regex("Result:\\s*(\\d+)\\s+out of\\s+(\\d+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        val error = Regex("Error:\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
        return added to error
    }

    suspend fun uploadEqsl(user: String, password: String, qthNickname: String, list: List<Qso>, agent: String): UploadResult = withContext(Dispatchers.IO) {
        val nick = qthNickname.trim()
        val adif = file(list) { if (nick.isEmpty()) "" else "<APP_EQSL_QTH_NICKNAME:${nick.toByteArray().size}>$nick" }
        val body = try {
            post("https://www.eqsl.cc/qslcard/ImportADIF.cfm", form("EQSL_USER" to user.trim(), "EQSL_PSWD" to password, "ADIFData" to adif), agent).second
        } catch (e: Exception) {
            return@withContext UploadResult(emptyList(), 0, netError("eQSL", e))
        }
        val (added, error) = parseEqsl(body)
        when {
            // A wrong login is an "Error:" with no "Result:"; per-record problems (duplicates) come with a result.
            added == null -> UploadResult(emptyList(), 0, tr("eQSL: %s", error ?: tr("неожиданный ответ сервера")))
            else -> UploadResult(list, (list.size - added).coerceAtLeast(0), null)
        }
    }

    // ---------- Club Log: https://clublog.freshdesk.com/support/solutions/articles/3000064883 ----------

    /** Club Log wants an application key per program; the user enters one (see the settings). */
    suspend fun uploadClubLog(email: String, password: String, callsign: String, appKey: String, list: List<Qso>, agent: String): UploadResult =
        withContext(Dispatchers.IO) {
            val boundary = "qsolog" + System.nanoTime()
            val out = ByteArrayOutputStream()
            fun part(name: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            part("email", email.trim()); part("password", password); part("callsign", callsign.trim().uppercase())
            part("api", appKey.trim()); part("clear", "0")
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"qsolog.adi\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            out.write(file(list).toByteArray(Charsets.UTF_8))
            out.write("\r\n--$boundary--\r\n".toByteArray())
            val (code, body) = try {
                post("https://clublog.org/putlogs.php", out.toByteArray(), agent, "multipart/form-data; boundary=$boundary")
            } catch (e: Exception) {
                return@withContext UploadResult(emptyList(), 0, netError("Club Log", e))
            }
            when (code) {
                200 -> UploadResult(list, 0, null)
                403 -> UploadResult(emptyList(), 0, tr("Club Log не принял e-mail, пароль, позывной или ключ приложения"))
                else -> UploadResult(emptyList(), 0, tr("Club Log ответил %s: %s", code, body.replace(Regex("<[^>]+>"), " ").trim().take(120)))
            }
        }

    // ---------- HTTP ----------

    private fun form(vararg p: Pair<String, String>): ByteArray =
        p.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }.toByteArray()

    private fun post(url: String, body: ByteArray, agent: String, type: String = "application/x-www-form-urlencoded"): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", agent)
            conn.setRequestProperty("Content-Type", type)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun netError(service: String, e: Exception) = when (e) {
        is java.net.UnknownHostException -> tr("Нет интернета: не удаётся связаться с %s", service)
        is java.net.SocketTimeoutException -> tr("%s не ответил вовремя", service)
        else -> tr("Нет связи с %s: %s", service, e.message ?: e.javaClass.simpleName)
    }
}
