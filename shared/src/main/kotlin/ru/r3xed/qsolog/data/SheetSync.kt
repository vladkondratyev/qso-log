package ru.r3xed.qsolog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.tr
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The local log as the table sync sees it. */
interface SyncStore {
    /** Records created or changed after [t] (this device's clock). */
    fun changedSince(t: Long): List<Qso>
    /** Records deleted on this device after [t]: UUID and when. */
    fun deletedSince(t: Long): List<Pair<String, Long>>
    fun byUid(uid: String): Qso?
    fun insert(q: Qso)
    /** Replaces the record with [Qso.id]. */
    fun update(q: Qso)
    /** Deletes without a deletion mark: the deletion came from the table. */
    fun deleteByUid(uid: String)
    fun changeUid(old: String, new: String)
}

/** What a device remembers between syncs: the table's time of the last answer and when it last sent its changes. */
data class SyncState(val since: String = "", val lastPush: Long = 0)

data class SyncResult(val sent: Int, val received: Int, val state: SyncState)

class SyncException(message: String) : Exception(message)

/**
 * Two-way sync of the log through a Google Sheet. The sheet holds a small Apps Script ([SCRIPT]) deployed as a web
 * app: the devices POST their changes to its address and get back the rows changed since their last sync. The script
 * keeps one row per contact (by UUID; a contact sent by two devices under different UUIDs is merged into one row and
 * the device told to take the table's UUID) and the newer change wins (`updated_utc`). Deletions travel as rows marked
 * `deleted`. Everything is text, so "001" stays "001".
 */
object SheetSync {
    /** The table's columns, in the script's order. */
    val COLUMNS = listOf(
        "uid", "updated_utc", "synced_utc", "deleted",
        "call", "date_utc", "time_utc", "band", "mode", "freq_mhz", "rst_sent", "rst_rcvd",
        "name", "qth", "country", "locator", "lat", "lon", "distance_km", "bearing",
        "power", "qsl_sent", "qsl_rcvd", "comment", "my_call", "my_locator", "created_utc", "adif_extra",
    )

    /** The Apps Script the user pastes into the table. */
    val SCRIPT: String by lazy {
        SheetSync::class.java.getResourceAsStream("/qsolog-sheet-sync.gs")!!.use { it.readBytes().decodeToString() }
    }

    /** A web app address of Apps Script: https://script.google.com/macros/s/…/exec */
    fun isScriptUrl(url: String): Boolean {
        val u = url.trim()
        return u.startsWith("https://script.google.com/") && u.substringBefore('?').endsWith("/exec")
    }

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** The script's own time format (JavaScript toISOString), so the two compare as plain strings. */
    fun stamp(ms: Long): String = STAMP.format(Instant.ofEpochMilli(ms))

    fun parseStamp(s: String): Long? = runCatching { Instant.parse(s.trim()).toEpochMilli() }.getOrNull()

    fun toRow(q: Qso): List<String> {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(q.timeUtc), ZoneOffset.UTC)
        return listOf(
            q.uid, stamp(q.updatedAt), "", "",
            q.call, DATE.format(t), TIME.format(t), q.band, q.mode, q.freqMhz, q.rstSent, q.rstRcvd,
            q.name, q.qth, q.country, q.locator, q.lat.fmt(6), q.lon.fmt(6), q.distanceKm.fmt(1), q.bearing.fmt(0),
            q.power, if (q.qslSent) "Y" else "", if (q.qslRcvd) "Y" else "", q.comment, q.myCall, q.myLocator,
            stamp(q.createdAt), Adif.encodeFields(q.adif),
        )
    }

    fun deletedRow(uid: String, at: Long): List<String> =
        COLUMNS.map { when (it) { "uid" -> uid; "updated_utc" -> stamp(at); "deleted" -> "Y"; else -> "" } }

    /** A table row as a record; null for a row without a callsign or a readable date. [deleted] rows carry only the UUID. */
    fun fromRow(r: Map<String, String>): Pair<Qso, Boolean>? {
        fun col(k: String) = r[k]?.trim().orEmpty()
        val uid = col("uid")
        if (uid.isEmpty()) return null
        val deleted = col("deleted").equals("Y", ignoreCase = true)
        if (deleted) return Qso(uid = uid, call = col("call"), timeUtc = 0) to true
        val call = col("call").uppercase()
        val date = runCatching { LocalDate.parse(col("date_utc"), DATE) }.getOrNull()
        if (call.isEmpty() || date == null) return null
        val time = runCatching { LocalTime.parse(col("time_utc").let { if (it.length == 5) "$it:00" else it }, TIME) }.getOrDefault(LocalTime.MIDNIGHT)
        val now = System.currentTimeMillis()
        return Qso(
            uid = uid,
            call = call,
            timeUtc = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli(),
            band = col("band").lowercase(), mode = col("mode").uppercase(), freqMhz = col("freq_mhz"),
            rstSent = col("rst_sent"), rstRcvd = col("rst_rcvd"),
            name = col("name"), qth = col("qth"), country = col("country"), locator = col("locator"),
            lat = col("lat").num(), lon = col("lon").num(), distanceKm = col("distance_km").num(), bearing = col("bearing").num(),
            power = col("power"), qslSent = col("qsl_sent").isYes(), qslRcvd = col("qsl_rcvd").isYes(),
            comment = col("comment"), myCall = col("my_call").uppercase(), myLocator = col("my_locator"),
            adif = Adif.decodeFields(col("adif_extra")),
            createdAt = parseStamp(col("created_utc")) ?: now,
            updatedAt = parseStamp(col("updated_utc")) ?: now,
        ) to false
    }

    /** The table's answer: its time, the rows changed since the device's last sync, and UUIDs to take over. */
    class Reply(val now: String, val rows: List<Map<String, String>>, val remap: Map<String, String>)

    /** One round trip: the device's changes go to the script, the table's changes come back. */
    suspend fun exchange(url: String, since: String, rows: List<List<String>>): Reply {
        val body = MiniJson.encode(mapOf("version" to 1, "since" to since, "columns" to COLUMNS, "rows" to rows))
        val text = post(url.trim(), body)
        val json = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *>
            ?: throw SyncException(
                if (text.contains("<html", ignoreCase = true)) tr("Скрипт таблицы закрыт: в развёртывании выберите «У кого есть доступ: все»")
                else tr("Таблица ответила непонятно")
            )
        if (json["ok"] != true) throw SyncException(tr("Ошибка в таблице: %s", json["error"]?.toString().orEmpty()))
        val cols = (json["columns"] as? List<*>)?.map { it.toString() }.orEmpty()
        val out = (json["rows"] as? List<*>).orEmpty().mapNotNull { row ->
            (row as? List<*>)?.let { v -> cols.indices.associate { i -> cols[i] to (v.getOrNull(i)?.toString() ?: "") } }
        }
        val remap = (json["remap"] as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value.toString() }
        return Reply(json["now"]?.toString().orEmpty(), out, remap)
    }

    /** Takes the table's answer into the local log; returns how many local records it changed. */
    fun apply(store: SyncStore, reply: Reply): Int {
        var changed = 0
        for ((old, new) in reply.remap) {
            if (old == new) continue
            // Both already here (one came from the table earlier): the device's copy goes, the table's stays.
            if (store.byUid(new) != null) store.deleteByUid(old) else store.changeUid(old, new)
            changed++
        }
        // A merged contact: the table's copy replaces the device's one, whichever is newer.
        val merged = reply.remap.values.toSet()
        for (row in reply.rows) {
            val (remote, deleted) = fromRow(row) ?: continue
            val local = store.byUid(remote.uid)
            when {
                deleted -> if (local != null) { store.deleteByUid(remote.uid); changed++ }
                local == null -> { store.insert(remote); changed++ }
                remote.updatedAt > local.updatedAt || remote.uid in merged -> {
                    // The voice note stays on the device that recorded it.
                    store.update(remote.copy(id = local.id, audio = local.audio, pendingLookup = false))
                    changed++
                }
            }
        }
        return changed
    }

    /** Sends what changed here since the last sync, takes what changed in the table. */
    suspend fun sync(url: String, store: SyncStore, state: SyncState): SyncResult {
        val start = System.currentTimeMillis()
        val rows = store.changedSince(state.lastPush).filter { it.uid.isNotBlank() }.map { toRow(it) } +
            store.deletedSince(state.lastPush).map { (uid, at) -> deletedRow(uid, at) }
        val reply = exchange(url, state.since, rows)
        val received = apply(store, reply)
        return SyncResult(rows.size, received, SyncState(reply.now.ifBlank { state.since }, start))
    }

    /** Checks the address: the script answers and says it is ours. */
    suspend fun check(url: String): Int {
        val text = get(url.trim())
        val json = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *>
            ?: throw SyncException(
                if (text.contains("<html", ignoreCase = true)) tr("Скрипт таблицы закрыт: в развёртывании выберите «У кого есть доступ: все»")
                else tr("По этому адресу не скрипт QSO-LOG")
            )
        if (json["app"] != "QSO-LOG") throw SyncException(tr("По этому адресу не скрипт QSO-LOG"))
        return (json["rows"] as? Double)?.toInt() ?: 0
    }

    private suspend fun get(url: String): String = request(url, null)

    private suspend fun post(url: String, body: String): String = request(url, body)

    /** Apps Script answers a POST with a redirect to the result, which is then fetched with GET. */
    private suspend fun request(start: String, body: String?): String = withContext(Dispatchers.IO) {
        var url = start
        var postBody = body
        repeat(5) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 120_000 // the first sync of a big log takes the script a while
                if (postBody != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    conn.outputStream.use { it.write(postBody!!.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), conn.getHeaderField("Location") ?: throw SyncException(tr("Таблица ответила %s", code))).toString()
                    postBody = null
                    return@repeat
                }
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
                if (code == 404) throw SyncException(tr("Скрипт не найден: проверьте адрес …/exec"))
                if (code !in 200..299 && !text.trimStart().startsWith("{")) throw SyncException(tr("Таблица ответила %s", code))
                return@withContext text
            } finally {
                conn.disconnect()
            }
        }
        throw SyncException(tr("Таблица ответила %s", "redirect"))
    }

    private fun Double?.fmt(digits: Int) = this?.let { String.format(Locale.ROOT, "%.${digits}f", it) }.orEmpty()
    private fun String.num() = replace(',', '.').toDoubleOrNull()
    private fun String.isYes() = trim().uppercase() in setOf("Y", "YES", "1", "TRUE", "ДА")
}
