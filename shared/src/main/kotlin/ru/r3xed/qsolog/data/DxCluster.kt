package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.Executors

/** A station on the air reported to the DX cluster: [khz] as spotted, [timeUtc] of the spot, ms. */
data class DxSpot(val khz: Double, val call: String, val spotter: String, val comment: String, val timeUtc: Long) {
    val hz: Long get() = Math.round(khz * 1000)
    val band: String? get() = bandForFreq(khz / 1000)
    /** The log's mode: named in the comment, otherwise by where on the band the spot is. */
    val mode: String get() = DxCluster.mode(khz, comment)
}

/** How a spot stands against the log, the most interesting first. */
enum class DxMark { NEW_COUNTRY, NEW_REGION, NEW_BAND, NEW_MODE, WORKED, NONE }

/** The DX cluster settings: the same keys (dxc_*) on the phone and the computer. */
data class DxClusterConfig(
    /** host:port of a DXSpider, AR-Cluster or CC Cluster node, or of the Reverse Beacon Network. */
    val server: String = DxCluster.SERVERS.first().first,
    /** The callsign to log in with; empty — the station's own. */
    val login: String = "",
    /** A tap on a spot also tunes the radio (when CAT is on). */
    val tune: Boolean = true,
    /** Spots older than this leave the list, minutes. */
    val keepMinutes: Int = 30,
) {
    val host: String get() = server.substringBeforeLast(':').trim()
    val port: Int get() = server.substringAfterLast(':', "").trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 23

    fun toMap(): Map<String, String> = mapOf(
        "dxc_server" to server, "dxc_login" to login, "dxc_tune" to tune.toString(), "dxc_keep" to keepMinutes.toString(),
    )

    companion object {
        fun fromMap(m: Map<String, String?>): DxClusterConfig {
            val d = DxClusterConfig()
            return DxClusterConfig(
                server = m["dxc_server"]?.trim()?.ifBlank { null } ?: d.server,
                login = m["dxc_login"]?.trim()?.uppercase() ?: d.login,
                tune = m["dxc_tune"]?.toBooleanStrictOrNull() ?: d.tune,
                keepMinutes = m["dxc_keep"]?.toIntOrNull()?.takeIf { it in 5..720 } ?: d.keepMinutes,
            )
        }

        val KEYS = DxClusterConfig().toMap().keys
    }
}

sealed interface DxStatus {
    data object Off : DxStatus
    data class Connecting(val server: String) : DxStatus
    data class Connected(val server: String) : DxStatus
    data class Failed(val message: String) : DxStatus
}

object DxCluster {
    /** Nodes offered in the settings: host:port and what it is. The first is the default. */
    val SERVERS = listOf(
        "db0sue.de:8000" to "DB0SUE, DXSpider (Европа)",
        "dxspider.co.uk:7300" to "GB7DXC, DXSpider (Европа)",
        "dxc.nc7j.com:7373" to "NC7J, AR-Cluster (США)",
        "telnet.reversebeacon.net:7000" to "Reverse Beacon Network (скиммеры CW и RTTY)",
    )

    // "DX de ND4X:      18100.0  6W1SU        FT8 -12 dB                     2058Z" (RBN: "DX de DL8LAS-#: …", a locator may follow the time)
    private val LIVE = Regex("""^DX de ([A-Z0-9/#\-]+):?\s+(\d+(?:\.\d+)?)\s+([A-Z0-9/]+)\s*(.*?)\s*(\d{4})Z(?:\s+\S+)?\s*$""", RegexOption.IGNORE_CASE)
    // sh/dx: " 18100.0  6W1SU       30-Oct-2026 2058Z  FT8                          <ND4X>"
    private val LISTED = Regex("""^\s*(\d+\.\d+)\s+([A-Z0-9/]+)\s+(\d{1,2})-([A-Za-z]{3})-(\d{4})\s+(\d{4})Z\s*(.*?)\s*<([^>]+)>\s*$""", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    /** A spot from a line the node sent, or null for anything else (greetings, prompts, announcements). */
    fun parse(line: String, now: Long = System.currentTimeMillis()): DxSpot? {
        val text = line.trimEnd()
        LIVE.find(text)?.let { m ->
            val (spotter, khz, call, comment, hhmm) = m.destructured
            val k = khz.toDoubleOrNull()?.takeIf { it > 100 } ?: return null
            return DxSpot(k, call.uppercase(), spotter.uppercase().removeSuffix("-#"), comment.trim(), timeToday(hhmm, now))
        }
        LISTED.find(text)?.let { m ->
            val (khz, call, day, mon, year, hhmm, comment, spotter) = m.destructured
            val k = khz.toDoubleOrNull()?.takeIf { it > 100 } ?: return null
            val month = MONTHS.indexOf(mon.uppercase()) + 1
            if (month == 0) return null
            val t = runCatching {
                LocalDate.of(year.toInt(), month, day.toInt()).atTime(LocalTime.of(hhmm.take(2).toInt(), hhmm.drop(2).toInt()))
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull() ?: return null
            return DxSpot(k, call.uppercase(), spotter.uppercase().removeSuffix("-#"), comment.trim(), t)
        }
        return null
    }

    /** HHMM of today, UTC; a time a little ahead of the clock is yesterday's (spots around midnight). */
    private fun timeToday(hhmm: String, now: Long): Long {
        val h = hhmm.take(2).toIntOrNull() ?: return now
        val m = hhmm.drop(2).toIntOrNull() ?: return now
        if (h > 23 || m > 59) return now
        val today = java.time.Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate()
        val t = today.atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
        return if (t > now + 10 * 60_000) t - 86_400_000 else t
    }

    private val COMMENT_MODES = listOf(
        "FT8" to "FT8", "FT4" to "FT4", "CW" to "CW", "SSB" to "SSB", "USB" to "SSB", "LSB" to "SSB", "RTTY" to "RTTY",
        "PSK31" to "PSK31", "PSK" to "PSK31", "JS8" to "JS8", "Q65" to "Q65", "SSTV" to "SSTV", "FM" to "FM", "AM" to "AM",
    )

    /** CW below, then the data segment, then phone; kHz. */
    private class Plan(val cwTop: Double, val phone: Double)

    private val PLANS = mapOf(
        "160m" to Plan(1838.0, 1843.0), "80m" to Plan(3570.0, 3600.0), "60m" to Plan(5354.0, 5354.0),
        "40m" to Plan(7040.0, 7060.0), "30m" to Plan(10_140.0, 10_150.0), "20m" to Plan(14_070.0, 14_101.0),
        "17m" to Plan(18_095.0, 18_111.0), "15m" to Plan(21_070.0, 21_151.0), "12m" to Plan(24_915.0, 24_931.0),
        "10m" to Plan(28_070.0, 28_300.0), "6m" to Plan(50_100.0, 50_300.0), "2m" to Plan(144_150.0, 144_150.0),
    )

    fun mode(khz: Double, comment: String): String {
        val words = comment.uppercase().split(Regex("[^A-Z0-9]+"))
        COMMENT_MODES.firstOrNull { (w, _) -> w in words }?.let { return it.second }
        val band = bandForFreq(khz / 1000) ?: return "SSB"
        // A digital spot is the dial frequency plus the audio offset: 0–3 kHz above the calling frequency.
        for (m in listOf("FT8", "FT4")) {
            val f = ru.r3xed.qsolog.cat.BandPlan.freq(band, m)?.let { it / 1000.0 } ?: continue
            if (khz >= f - 0.1 && khz <= f + 3.2) return m
        }
        val p = PLANS[band] ?: return if (band == "70cm" && khz >= 433_000) "FM" else "SSB"
        return when {
            khz < p.cwTop -> "CW"
            khz < p.phone -> "DIGI"
            band == "2m" && khz >= 145_200 -> "FM"
            band == "10m" && khz >= 29_500 -> "FM"
            else -> "SSB"
        }
    }

    /** CW, phone or data: the "new mode" mark and the chips of the list go by these. */
    fun group(mode: String): String = when (mode.uppercase()) {
        "CW" -> "CW"
        "SSB", "AM", "FM", "USB", "LSB" -> "SSB"
        else -> "DIGI"
    }

    /**
     * [spot] added to [list] (newest first): the same station on the same frequency (±1 kHz) is replaced, spots older
     * than [keepMs] go.
     */
    fun merge(list: List<DxSpot>, spot: DxSpot, now: Long = System.currentTimeMillis(), keepMs: Long = 30 * 60_000L): List<DxSpot> {
        if (spot.timeUtc < now - keepMs) return list
        val rest = list.filter { it.timeUtc >= now - keepMs && !(it.call == spot.call && kotlin.math.abs(it.khz - spot.khz) <= 1.0) }
        return (rest + spot).sortedByDescending { it.timeUtc }.take(400)
    }

    /** The last characters of what the node has sent ask for the callsign. */
    fun isLoginPrompt(tail: String): Boolean {
        val t = tail.trimEnd().lowercase()
        return t.endsWith("login:") || t.endsWith("call:") || t.endsWith("callsign:") || t.endsWith("your callsign") ||
            t.endsWith("enter your call")
    }
}

/** What the log has: countries, countries on bands and in modes, and callsigns on bands. */
class DxLogIndex(qsos: List<Qso>) {
    private val entities = HashSet<String>()
    private val entityBand = HashSet<String>()
    private val entityGroup = HashSet<String>()
    private val callBand = HashSet<String>()
    /** Regions of Russia worked (by the callsign's letter after the digit): a spot from another one is "новая область". */
    private val regions = HashSet<String>()

    init {
        for (q in qsos) {
            RussianRegions.of(q.call)?.let { regions += it.rda }
            val e = Cty.lookup(q.call)?.entity?.name
            val band = q.band.lowercase()
            callBand += q.call.uppercase() + "|" + band
            if (e != null) {
                entities += e
                entityBand += "$e|$band"
                entityGroup += e + "|" + DxCluster.group(q.mode)
            }
        }
    }

    fun mark(s: DxSpot): DxMark {
        val band = s.band ?: return DxMark.NONE
        if (s.call.uppercase() + "|" + band in callBand) return DxMark.WORKED
        val e = Cty.lookup(s.call)?.entity?.name ?: return DxMark.NONE
        return when {
            e !in entities -> DxMark.NEW_COUNTRY
            RussianRegions.of(s.call)?.rda?.let { it !in regions } == true -> DxMark.NEW_REGION
            "$e|$band" !in entityBand -> DxMark.NEW_BAND
            e + "|" + DxCluster.group(s.mode) !in entityGroup -> DxMark.NEW_MODE
            else -> DxMark.NONE
        }
    }
}

/**
 * The telnet connection to a node, on its own thread: logs in as [login], asks for the last spots, then reports each
 * spot to [onSpot] and every other line to [onLine] (from that thread). A dropped connection is made again every
 * [retryMs] until [stop].
 */
class DxClusterClient(
    private val config: DxClusterConfig,
    private val login: String,
    private val onStatus: (DxStatus) -> Unit,
    private val onSpot: (DxSpot) -> Unit,
    private val onLine: (String) -> Unit = {},
    private val retryMs: Long = 15_000,
) {
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    @Volatile private var out: OutputStream? = null
    private var thread: Thread? = null
    /** Writes go through one background thread: the caller may be the UI thread. */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "DX cluster out").apply { isDaemon = true } }

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "DX cluster").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        thread?.interrupt()
        writer.shutdownNow()
        onStatus(DxStatus.Off)
    }

    /** A command or a spot ("DX 14025 UA3DEMO tnx"); false when not connected. */
    fun send(line: String): Boolean {
        val o = out ?: return false
        writer.execute { runCatching { o.write((line.trim() + "\r\n").toByteArray(Charsets.ISO_8859_1)); o.flush() } }
        return true
    }

    private fun loop() {
        while (running) {
            val name = config.server
            onStatus(DxStatus.Connecting(name))
            try {
                Socket().use { s ->
                    socket = s
                    // The screenshot generator puts a made-up node in place of the configured one (qsolog.dxcluster.connect).
                    val stand = System.getProperty("qsolog.dxcluster.connect")
                    val addr = if (stand != null) InetSocketAddress(stand.substringBeforeLast(':'), stand.substringAfterLast(':').toInt())
                    else InetSocketAddress(config.host, config.port)
                    s.connect(addr, 10_000)
                    s.soTimeout = 0
                    out = s.getOutputStream()
                    session(s.getInputStream(), name)
                }
                if (running) onStatus(DxStatus.Failed(tr("Сервер %s закрыл соединение", name)))
            } catch (e: Exception) {
                if (running) onStatus(DxStatus.Failed(error(e, name)))
            } finally {
                out = null
                socket = null
            }
            if (!running) break
            try {
                Thread.sleep(retryMs)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun session(input: InputStream, name: String) {
        val line = StringBuilder()
        var loggedIn = false
        var asked = false
        // Some nodes greet only after a key: the callsign is sent anyway if no prompt has come in a few seconds.
        val started = System.currentTimeMillis()
        while (running) {
            if (!loggedIn && input.available() == 0 && System.currentTimeMillis() - started > 6000) {
                send(login); loggedIn = true
            }
            if (input.available() == 0 && !loggedIn) {
                Thread.sleep(100)
                continue
            }
            val b = input.read()
            if (b < 0) return
            val c = b.toChar()
            when {
                // Telnet negotiation (IAC …): skipped, the nodes work without it.
                b == 255 -> { input.read(); input.read() }
                c == '\n' -> {
                    val text = line.toString().trimEnd('\r')
                    line.setLength(0)
                    val spot = DxCluster.parse(text)
                    if (spot != null) {
                        onSpot(spot)
                        if (!asked) { asked = true; onStatus(DxStatus.Connected(name)) }
                    } else if (text.isNotBlank()) onLine(text)
                    // The node's prompt after the login ("UA3DEMO de DB0SUE 30-Oct 2101Z dxspider >"): ask for the recent spots.
                    if (loggedIn && !asked && text.contains(" de ", ignoreCase = true) && text.trimEnd().endsWith(">")) {
                        asked = true
                        onStatus(DxStatus.Connected(name))
                        send("sh/dx 30")
                    }
                }
                else -> {
                    if (b >= 32 || c == '\t') line.append(c)
                    if (!loggedIn && DxCluster.isLoginPrompt(line.toString())) {
                        onLine(line.toString().trim())
                        line.setLength(0)
                        send(login)
                        loggedIn = true
                    } else if (loggedIn && !asked && line.endsWith(">") && line.contains(" de ", ignoreCase = true)) {
                        // The prompt comes without a line end.
                        onLine(line.toString().trim())
                        line.setLength(0)
                        asked = true
                        onStatus(DxStatus.Connected(name))
                        send("sh/dx 30")
                    }
                }
            }
        }
    }

    private fun error(e: Exception, name: String) = when (e) {
        is java.net.UnknownHostException -> tr("Нет интернета или сервер %s не найден", name)
        is java.net.SocketTimeoutException -> tr("Сервер %s не отвечает", name)
        is java.net.ConnectException -> tr("Сервер %s не принимает подключение", name)
        else -> tr("Связь с %s прервалась: %s", name, e.message ?: e.javaClass.simpleName)
    }
}
