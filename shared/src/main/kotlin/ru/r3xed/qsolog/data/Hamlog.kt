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
 * HAMLOG.online, API v2 (the account's «Разработчикам» page; summary in progress.md, «2.9.4 Hamlog»).
 *
 * The program is connected by the OAuth device flow: it asks for a code, the user opens hamlog.online/account/activate.php,
 * signs in with WWPass, picks the callsigns and confirms; the program meanwhile asks for the tokens every few seconds.
 * The access token lives an hour, the refresh token 90 days and is single-use: each refresh gives a new pair, which is
 * saved before the old one is let go, and an old one is never sent twice (that revokes the whole connection). So the
 * tokens are this device's own ([Link], kept apart from the other settings and never synced): each device connects itself.
 */
object Hamlog {
    const val BASE = "https://hamlog.online/api/v2"

    /** [BASE]; a test points it at a local server. */
    internal var base: String = BASE
    const val APP_NAME = "QSO-LOG"
    private const val SCOPE = "qsos:read qsos:write"

    /** Where Hamlog keeps a sent contact: its number there (to edit it later, if that is ever wanted). */
    const val ID_FIELD = "APP_QSOLOG_HAMLOG_QSO_ID"

    /** The fields the live upload takes (the others are not kept by Hamlog, so they do not go). */
    val FIELDS: Set<String> = setOf(
        "CALL", "QSO_DATE", "TIME_ON", "MODE", "BAND", "FREQ",
        "SUBMODE", "QSO_DATE_OFF", "TIME_OFF", "RST_SENT", "RST_RCVD",
        "NAME", "QTH", "GRIDSQUARE", "STATE", "CNTY", "CQZ", "ITUZ", "IOTA",
        "COMMENT", "QSLMSG", "QSL_VIA", "PROP_MODE", "SAT_NAME", "CONTEST_ID", "STX", "SRX",
        "MY_GRIDSQUARE", "MY_STATE", "MY_CNTY", "MY_NAME", "MY_CITY", "MY_COUNTRY", "MY_CQ_ZONE", "MY_ITU_ZONE",
    )

    /** At most this many records in one live upload. */
    const val BATCH = 50

    /** This device's connection: the tokens with their ends (epoch ms) and the callsigns the user allowed. */
    data class Link(
        val access: String,
        val accessUntil: Long,
        val refresh: String,
        val refreshUntil: Long,
        val calls: List<String> = emptyList(),
    ) {
        fun encode(): String = MiniJson.encode(
            mapOf("a" to access, "au" to accessUntil.toString(), "r" to refresh, "ru" to refreshUntil.toString(), "c" to calls.joinToString(",")),
        )

        companion object {
            fun decode(s: String): Link? {
                if (s.isBlank()) return null
                val m = runCatching { MiniJson.parse(s) as? Map<*, *> }.getOrNull() ?: return null
                val r = m["r"]?.toString().orEmpty().ifBlank { return null }
                return Link(
                    m["a"]?.toString().orEmpty(), m["au"]?.toString()?.toLongOrNull() ?: 0, r,
                    m["ru"]?.toString()?.toLongOrNull() ?: 0,
                    m["c"]?.toString().orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                )
            }
        }
    }

    /** The code the user types on hamlog.online ([userCode]) and the page; [deviceCode] stays in the program. */
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val uri: String,
        val uriComplete: String,
        val expiresAt: Long,
        val intervalSec: Int,
    )

    sealed interface Poll {
        /** Not confirmed yet: ask again in [intervalSec]. */
        data class Wait(val intervalSec: Int) : Poll
        data class Done(val link: Link) : Poll
        /** The user refused, the code ran out or is spent: start over. */
        data class Failed(val message: String) : Poll
    }

    class HamlogException(message: String, val reconnect: Boolean = false) : Exception(message)

    // ---------- connecting ----------

    suspend fun start(deviceName: String, agent: String): DeviceCode = withContext(Dispatchers.IO) {
        val (code, body) = try {
            request("POST", "$base/oauth/authorization", agent, form("application_name" to APP_NAME, "device_name" to deviceName.take(60), "scope" to SCOPE))
        } catch (e: Exception) {
            throw HamlogException(OnlineLogs.netError("HAMLOG", e))
        }
        val m = obj(body)
        if (code != 200 || m == null) throw HamlogException(error("HAMLOG", code, m, body))
        val interval = (m["interval"] as? Number)?.toInt() ?: 5
        val expires = (m["expires_in"] as? Number)?.toLong() ?: 600
        DeviceCode(
            deviceCode = m["device_code"]?.toString().orEmpty(),
            userCode = m["user_code"]?.toString().orEmpty(),
            uri = m["verification_uri"]?.toString() ?: "https://hamlog.online/account/activate.php",
            uriComplete = m["verification_uri_complete"]?.toString() ?: m["verification_uri"]?.toString() ?: "https://hamlog.online/account/activate.php",
            expiresAt = System.currentTimeMillis() + expires * 1000,
            intervalSec = interval,
        )
    }

    /** One ask for the tokens; a lost answer is just asked again (the device code is spent only by a success). */
    suspend fun poll(d: DeviceCode, intervalSec: Int, agent: String): Poll = withContext(Dispatchers.IO) {
        val (code, body, retryAfter) = try {
            requestFull("POST", "$base/oauth/token", agent, form("grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "device_code" to d.deviceCode))
        } catch (e: Exception) {
            return@withContext Poll.Wait(intervalSec)
        }
        val m = obj(body)
        if (code == 200 && m != null) {
            val link = tokens(m) ?: return@withContext Poll.Failed(tr("HAMLOG: неожиданный ответ сервера"))
            // Which callsigns the user allowed: shown in the settings (a failure here does not undo the connection).
            val calls = runCatching { stations(link.access, agent) }.getOrDefault(emptyList())
            return@withContext Poll.Done(link.copy(calls = calls))
        }
        when (m?.get("error")?.toString()) {
            "authorization_pending" -> Poll.Wait(intervalSec)
            "slow_down" -> Poll.Wait(maxOf(intervalSec + 5, retryAfter ?: 0))
            "access_denied" -> Poll.Failed(tr("Подключение HAMLOG отклонено на сайте"))
            "expired_token" -> Poll.Failed(tr("Код HAMLOG устарел: подключите заново"))
            "invalid_grant" -> Poll.Failed(tr("Код HAMLOG уже использован или недействителен: подключите заново"))
            else -> if (code == 429 || code >= 500) Poll.Wait(maxOf(intervalSec, retryAfter ?: 0)) else Poll.Failed(error("HAMLOG", code, m, body))
        }
    }

    /** The callsigns this connection may log to (GET /connection). */
    suspend fun stations(access: String, agent: String): List<String> = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", "$base/connection", agent, null, access)
        val m = obj(body)
        if (code != 200 || m == null) throw HamlogException(error("HAMLOG", code, m, body), reconnect = code == 401)
        (m["station_callsigns"] as? List<*>).orEmpty().map { it.toString().uppercase() }
    }

    /** Disconnects this device on Hamlog's side (the tokens stop working). */
    suspend fun revoke(access: String, agent: String) = withContext(Dispatchers.IO) {
        runCatching { request("DELETE", "$base/connection", agent, null, access) }
    }

    private fun tokens(m: Map<*, *>, calls: List<String> = emptyList()): Link? {
        val access = m["access_token"]?.toString().orEmpty()
        val refresh = m["refresh_token"]?.toString().orEmpty()
        if (access.isEmpty() || refresh.isEmpty()) return null
        val now = System.currentTimeMillis()
        val accessSec = (m["expires_in"] as? Number)?.toLong() ?: 3600
        val refreshSec = (m["refresh_expires_in"] as? Number)?.toLong() ?: 7_776_000
        return Link(access, now + accessSec * 1000, refresh, now + refreshSec * 1000, calls)
    }

    // ---------- the session: a fresh access token whenever it is needed ----------

    /**
     * The tokens of this device: [load] reads the stored [Link] (encoded), [save] stores a new one at once and for
     * sure (it must be on disk before the old refresh token is forgotten), null when the connection is gone.
     */
    class Session(private val load: () -> String, private val save: (Link?) -> Unit, private val agent: String) {
        private val lock = Mutex()

        val link: Link? get() = Link.decode(load())

        /** A working access token: refreshed (one at a time) when it ends within a minute or [force]. */
        suspend fun access(force: Boolean = false): String = lock.withLock {
            val l = link ?: throw HamlogException(tr("Подключите HAMLOG в настройках: Онлайн-журналы"), reconnect = true)
            if (!force && l.access.isNotEmpty() && l.accessUntil - 60_000 > System.currentTimeMillis()) return@withLock l.access
            if (l.refreshUntil in 1 until System.currentTimeMillis()) {
                save(null)
                throw HamlogException(tr("Подключение HAMLOG истекло (90 дней без работы): подключите заново"), reconnect = true)
            }
            val (code, body) = try {
                withContext(Dispatchers.IO) { request("POST", "$base/oauth/token", agent, form("grant_type" to "refresh_token", "refresh_token" to l.refresh)) }
            } catch (e: java.net.UnknownHostException) {
                throw HamlogException(OnlineLogs.netError("HAMLOG", e)) // never reached the server: the token is unused
            } catch (e: java.net.ConnectException) {
                throw HamlogException(OnlineLogs.netError("HAMLOG", e))
            } catch (e: Exception) {
                // The request may have reached the server and spent the token: sending it again would revoke the connection.
                save(null)
                throw HamlogException(tr("Обновление ключа HAMLOG прервалось (%s): подключите HAMLOG заново", e.message ?: e.javaClass.simpleName), reconnect = true)
            }
            val m = obj(body)
            if (code == 200 && m != null) {
                val fresh = tokens(m, l.calls) ?: throw HamlogException(tr("HAMLOG: неожиданный ответ сервера"))
                save(fresh)
                return@withLock fresh.access
            }
            if (code == 400 || code == 401) {
                save(null)
                throw HamlogException(tr("HAMLOG отозвал подключение: подключите заново"), reconnect = true)
            }
            // 429, 5xx: the server did not take the token; it is kept for the next time.
            throw HamlogException(error("HAMLOG", code, m, body))
        }

        /**
         * Sends [list] by [BATCH]: each contact under its own callsign ([Qso.myCall], else [fallbackCall]). A contact
         * Hamlog already has counts as sent (it fills in what was missing there). The sent ones come back with
         * Hamlog's number in [ID_FIELD].
         */
        suspend fun upload(list: List<Qso>, fallbackCall: String): UploadResult {
            val sent = mutableListOf<Qso>()
            val rejected = mutableListOf<Qso>()
            val problems = mutableListOf<String>()
            val groups = list.sortedBy { it.timeUtc }.groupBy { it.myCall.trim().uppercase().ifEmpty { fallbackCall.trim().uppercase() } }
            for ((call, all) in groups) {
                if (call.isEmpty()) { problems += tr("укажите свой позывной"); continue }
                for (part in all.chunked(BATCH)) {
                    val r = try {
                        send(call, part)
                    } catch (e: HamlogException) {
                        return UploadResult(sent, 0, (problems + e.message.orEmpty()).joinToString("; "), rejected)
                    }
                    sent += r.sent
                    rejected += r.rejected
                    problems += r.problems
                    if (r.stop) return UploadResult(sent, 0, problems.distinct().joinToString("; "), rejected)
                }
            }
            return UploadResult(sent, 0, problems.distinct().take(3).joinToString("; ").ifBlank { null }?.let { tr("HAMLOG: %s", it) }, rejected)
        }

        private class Batch(val sent: List<Qso>, val rejected: List<Qso>, val problems: List<String>, val stop: Boolean)

        /** One batch: the sent and refused contacts, the problems, and whether to stop (the next batches would fail the same way). */
        private suspend fun send(call: String, part: List<Qso>): Batch {
            val url = "$base/qsos?station_callsign=" + URLEncoder.encode(call, "UTF-8")
            val body = MiniJson.encode(mapOf("adif" to part.joinToString("") { record(it) })).toByteArray(Charsets.UTF_8)
            var (code, text) = post(url, body, access())
            if (code == 401) { val t = post(url, body, access(force = true)); code = t.first; text = t.second }
            val m = obj(text)
            val results = (m?.get("results") as? List<*>)?.mapNotNull { it as? Map<*, *> }
            if (results == null) {
                val stop = code == 401 || code == 403 || code == 429 || code >= 500
                // 422 and the like: the batch itself is wrong, so its contacts are refused, not waiting.
                return Batch(emptyList(), if (stop) emptyList() else part, listOf(error(call, code, m, text)), stop)
            }
            val sent = mutableListOf<Qso>()
            val rejected = mutableListOf<Qso>()
            val problems = mutableListOf<String>()
            for (r in results) {
                val i = ((r["record"] as? Number)?.toInt() ?: continue) - 1
                val q = part.getOrNull(i) ?: continue
                when (r["status"]?.toString()) {
                    "saved" -> sent += (r["qso_id"] as? Number)?.toLong()?.let { id -> q.copy(adif = q.adif + (ID_FIELD to id.toString())) } ?: q
                    else -> {
                        problems += q.call + ": " + (r["message"]?.toString() ?: r["error"]?.toString() ?: "HTTP " + (r["http_status"] ?: code))
                        // "failed" with retryable: a passing trouble, the contact waits; otherwise Hamlog refuses it.
                        if (!(r["status"] == "failed" && r["retryable"] == true)) rejected += q
                    }
                }
            }
            return Batch(sent, rejected, problems, false)
        }

        private suspend fun post(url: String, body: ByteArray, access: String): Pair<Int, String> = try {
            withContext(Dispatchers.IO) { request("POST", url, agent, body, access, "application/json") }
        } catch (e: Exception) {
            throw HamlogException(OnlineLogs.netError("HAMLOG", e))
        }
    }

    /**
     * The automatic sending: the contacts waiting by number ([ids]) go, the sent ones get the HAMLOG mark ([save]),
     * those Hamlog refuses leave the queue; the rest (no internet, a passing error) stay for the next time.
     */
    class QueueResult(val left: List<Long>, val sent: Int, val rejected: List<Qso>, val error: String?)

    suspend fun sendQueue(session: Session, ids: List<Long>, get: (Long) -> Qso?, save: (Qso) -> Unit, fallbackCall: String): QueueResult {
        val list = withContext(Dispatchers.IO) { ids.mapNotNull(get) }.filter { !OnlineLog.HAMLOG.isSent(it) }
        if (list.isEmpty()) return QueueResult(emptyList(), 0, emptyList(), null)
        val r = session.upload(list, fallbackCall)
        withContext(Dispatchers.IO) { r.sent.forEach { save(OnlineLog.HAMLOG.mark(it)) } }
        val done = (r.sent + r.rejected).map { it.id }.toSet()
        return QueueResult(list.map { it.id }.filter { it !in done }, r.sent.size, r.rejected, r.error)
    }

    fun parseQueue(s: String): List<Long> = s.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()

    /** One contact for the live upload: only the fields Hamlog keeps; the callsign goes in the address, not the record. */
    fun record(q: Qso): String = buildString {
        for ((k0, v0) in Adif.fields(q)) {
            val k = k0.uppercase()
            if (k !in FIELDS) continue
            val v = when (k) {
                "BAND" -> v0.uppercase()
                "GRIDSQUARE", "MY_GRIDSQUARE" -> v0.take(10)
                else -> v0
            }
            append('<').append(k).append(':').append(v.toByteArray(Charsets.UTF_8).size).append('>').append(v)
        }
        append("<EOR>\n")
    }

    // ---------- HTTP ----------

    private fun obj(text: String): Map<*, *>? = runCatching { MiniJson.parse(text.trim()) as? Map<*, *> }.getOrNull()

    private fun error(who: String, code: Int, m: Map<*, *>?, body: String): String {
        val what = m?.let { it["message"] ?: it["error_description"] ?: it["error"] }?.toString()
            ?: body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(120).ifBlank { null }
        return when (code) {
            401 -> tr("HAMLOG не принял ключ: подключите заново")
            403 -> tr("%s: нет разрешения HAMLOG для этого позывного%s", who, what?.let { " ($it)" }.orEmpty())
            429 -> tr("HAMLOG: слишком много запросов, попробуйте позже")
            in 500..599 -> tr("HAMLOG временно недоступен (%s), попробуйте позже", code)
            else -> tr("%s: HAMLOG ответил %s%s", who, code, what?.let { ": $it" }.orEmpty())
        }
    }

    private fun form(vararg p: Pair<String, String>): ByteArray =
        p.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }.toByteArray()

    private fun request(method: String, url: String, agent: String, body: ByteArray?, bearer: String? = null, type: String = "application/x-www-form-urlencoded"): Pair<Int, String> =
        requestFull(method, url, agent, body, bearer, type).let { it.first to it.second }

    private fun requestFull(method: String, url: String, agent: String, body: ByteArray?, bearer: String? = null, type: String = "application/x-www-form-urlencoded"): Triple<Int, String, Int?> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", agent)
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", type)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return Triple(code, text, conn.getHeaderField("Retry-After")?.trim()?.toIntOrNull())
        } finally {
            conn.disconnect()
        }
    }
}
