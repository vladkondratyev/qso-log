package ru.r3xed.qsolog.digi

import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.tr
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** Contacts from WSJT-X, JTDX, MSHV over UDP (see [Wsjtx]). Same keys (wsjtx_*) on the phone and the computer. */
data class DigiConfig(
    val enabled: Boolean = false,
    val port: Int = Wsjtx.DEFAULT_PORT,
    /** A multicast group (224.0.0.1, 239.255.0.1) the program sends to, so several programs can listen; empty — unicast. */
    val group: String = "",
) {
    fun toMap(): Map<String, String> = mapOf("wsjtx_enabled" to enabled.toString(), "wsjtx_port" to port.toString(), "wsjtx_group" to group)

    val multicast: Boolean get() = group.isNotBlank()

    companion object {
        fun fromMap(m: Map<String, String?>): DigiConfig {
            val d = DigiConfig()
            return DigiConfig(
                enabled = m["wsjtx_enabled"]?.toBooleanStrictOrNull() ?: d.enabled,
                port = m["wsjtx_port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: d.port,
                group = m["wsjtx_group"]?.trim() ?: d.group,
            )
        }

        val KEYS = DigiConfig().toMap().keys
    }
}

sealed interface DigiStatus {
    data object Off : DigiStatus

    /** The port is open. [program] — the last program heard ("WSJT-X v2.7.0"), null until one is. */
    data class Listening(val port: Int, val program: String? = null, val heardAt: Long = 0) : DigiStatus
    data class Failed(val message: String) : DigiStatus
}

/**
 * Listens on its own thread and reports to [onStatus] and [onLogged] (from that thread). A port that cannot be opened
 * (another program has it) is tried again every few seconds until [stop].
 */
class DigiListener(
    private val config: DigiConfig,
    private val onStatus: (DigiStatus) -> Unit,
    private val onLogged: (List<Qso>, String) -> Unit,
    private val retryMs: Long = 5000,
) {
    @Volatile private var running = false
    @Volatile private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "WSJT-X").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        thread?.interrupt()
        thread = null
    }

    private fun open(): DatagramSocket {
        val c = config
        if (c.multicast) {
            val group = InetAddress.getByName(c.group.trim())
            if (!group.isMulticastAddress) throw IllegalArgumentException(tr("%s — не групповой адрес (нужен 224.0.0.0–239.255.255.255)", c.group.trim()))
            val s = MulticastSocket(null)
            s.reuseAddress = true
            s.bind(InetSocketAddress(c.port))
            // Every interface that can: WSJT-X sends on the loopback and on the network ones it is set to.
            var joined = 0
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && it.supportsMulticast() }.getOrDefault(false) }
                .forEach { ni -> if (runCatching { s.joinGroup(InetSocketAddress(group, 0), ni) }.isSuccess) joined++ }
            if (joined == 0) s.joinGroup(group)
            return s
        }
        // No address reuse: with another listener on the port only one of them would get the datagrams — better a clear "busy".
        return DatagramSocket(InetSocketAddress(c.port))
    }

    private fun loop() {
        val buf = ByteArray(65_536)
        var program: String? = null
        var heardAt = 0L
        while (running) {
            try {
                val s = open()
                socket = s
                if (!running) { s.close(); break }
                s.soTimeout = 1000
                onStatus(DigiStatus.Listening(config.port, program, heardAt))
                while (running) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val m = Wsjtx.parse(buf, p.length) ?: continue
                    val name = if (m is Wsjtx.Message.Heartbeat && m.version.isNotBlank()) "${m.id} v${m.version}"
                    else program?.takeIf { it.startsWith(m.id) } ?: m.id
                    if (name != program || m is Wsjtx.Message.Heartbeat) {
                        program = name.ifBlank { null }
                        heardAt = System.currentTimeMillis()
                        onStatus(DigiStatus.Listening(config.port, program, heardAt))
                    }
                    if (m is Wsjtx.Message.Logged) onLogged(m.qsos, m.id)
                }
                s.close()
            } catch (e: Exception) {
                runCatching { socket?.close() }
                if (!running) break
                val msg = if (e is BindException) tr("Порт %s занят другой программой", config.port)
                else e.message?.ifBlank { null } ?: e.javaClass.simpleName
                onStatus(DigiStatus.Failed(msg))
                try { Thread.sleep(retryMs) } catch (e: InterruptedException) { break }
            }
        }
        socket = null
        onStatus(DigiStatus.Off)
    }
}

fun DigiStatus.describe(): String = when (this) {
    DigiStatus.Off -> tr("выключено")
    is DigiStatus.Listening -> if (program == null) tr("жду связи на порту %s", port) else tr("порт %s · слышно %s", port, program)
    is DigiStatus.Failed -> message
}

/** This device's addresses in the local network: WSJT-X on another computer sends to one of them. */
fun localAddresses(): List<String> = System.getProperty("qsolog.addresses")?.split(',') ?: runCatching {
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<java.net.Inet4Address>()
        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        .map { it.hostAddress }
        // 198.18.0.0/15 is what VPN clients give their tunnel: no other device reaches it. Home network first.
        .filter { !it.startsWith("198.18.") && !it.startsWith("198.19.") }
        .sortedBy { if (it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.")) 0 else 1 }
        .distinct()
}.getOrDefault(emptyList())
