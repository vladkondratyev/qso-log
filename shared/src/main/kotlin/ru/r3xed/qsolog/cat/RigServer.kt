package ru.r3xed.qsolog.cat

import ru.r3xed.qsolog.tr
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.Locale

sealed interface RigServerStatus {
    data object Off : RigServerStatus
    /** Listening on [port]; [clients] programs connected now. */
    data class Listening(val port: Int, val clients: Int) : RigServerStatus
    data class Failed(val message: String) : RigServerStatus
}

/**
 * QSO-LOG shares its transceiver: a server that speaks Hamlib rigctld's network protocol, so JTDX, WSJT-X, MSHV, GridTracker
 * (Rig "Hamlib NET rigctl", 127.0.0.1:4532) read and set the frequency and mode and key the radio through QSO-LOG, which
 * keeps the radio's port. The answers follow rigctld 4.7 as its own client (rigctl -m 2) talks to it: \get_powerstat,
 * \chk_vfo, \dump_state on connecting, then f/F, m/M, t/T, v, s, l.
 *
 * [state] is the radio's latest state (null — the radio does not answer); [send] passes a command to the CAT session.
 */
class RigServer(
    private val port: Int,
    private val lan: Boolean,
    private val state: () -> RigState?,
    private val send: (CatCommand) -> Unit,
    private val onStatus: (RigServerStatus) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var server: ServerSocket? = null
    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())

    /** What was set a moment ago: the radio's answer to the next poll may still have the old value. */
    @Volatile private var setFreq: Pair<Long, Long>? = null
    @Volatile private var setMode: Pair<String, Long>? = null
    @Volatile private var setPtt: Pair<Boolean, Long>? = null

    fun start() {
        if (running) return
        running = true
        Thread(::accept, "rigctld-server").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        synchronized(clients) { clients.toList() }.forEach { runCatching { it.close() } }
        // A program keyed the radio and the server goes: the radio must not stay on transmit.
        if (setPtt?.first == true) send(CatCommand.Ptt(false))
    }

    private fun accept() {
        try {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(if (lan) InetAddress.getByName("0.0.0.0") else InetAddress.getLoopbackAddress(), port))
            server = s
            if (!running) { s.close(); return }
            report()
            while (running) {
                val c = s.accept()
                c.tcpNoDelay = true
                clients += c
                report()
                Thread({ serve(c) }, "rigctld-client").apply { isDaemon = true; start() }
            }
        } catch (e: BindException) {
            if (running) onStatus(RigServerStatus.Failed(tr("Порт %s занят: его уже слушает rigctld или другая программа", port)))
        } catch (e: IOException) {
            if (running) onStatus(RigServerStatus.Failed(e.message ?: e.javaClass.simpleName))
        } finally {
            if (!running) onStatus(RigServerStatus.Off)
        }
    }

    private fun report() = onStatus(RigServerStatus.Listening(port, clients.size))

    private fun serve(c: Socket) {
        var keyed = false
        try {
            val r = c.getInputStream().bufferedReader(Charsets.US_ASCII)
            val w = c.getOutputStream()
            while (running) {
                val line = r.readLine() ?: break
                val cmd = line.trim().trimStart('+', ';', '|', ',')
                if (cmd.isEmpty()) continue
                if (cmd == "q" || cmd == "Q" || cmd == "\\quit") break
                val answer = answer(cmd)
                if (cmd.startsWith("T ") || cmd.startsWith("\\set_ptt")) keyed = cmd.substringAfter(' ').trim().let { it != "0" && it.isNotEmpty() }
                w.write(answer.toByteArray(Charsets.US_ASCII))
                w.flush()
            }
        } catch (e: SocketException) {
            Unit
        } catch (e: IOException) {
            Unit
        } finally {
            clients -= c
            runCatching { c.close() }
            // The program went away while transmitting (closed, crashed): back to receive.
            if (keyed) {
                send(CatCommand.Ptt(false))
                setPtt = false to System.currentTimeMillis()
            }
            if (running) report()
        }
    }

    /** The answer to one command line, with its line ends. */
    fun answer(cmd: String): String {
        val parts = cmd.split(Regex("\\s+"))
        val name = parts[0]
        val args = parts.drop(1)
        val now = System.currentTimeMillis()
        val s = state()
        return when (name) {
            "\\get_powerstat" -> "1\n"
            "\\chk_vfo" -> "0\n"
            "\\dump_state" -> DUMP_STATE
            "\\get_lock_mode" -> "0\nRPRT 0\n"
            "_", "\\get_info" -> "QSO-LOG\n"
            "v", "\\get_vfo" -> "VFOA\n"
            "V", "\\set_vfo" -> OK
            "f", "\\get_freq" -> {
                val hz = setFreq?.takeIf { now - it.second < HOLD_MS }?.first ?: s?.freqHz ?: return NO_RIG
                "$hz\n"
            }
            "F", "\\set_freq" -> {
                val hz = args.firstOrNull()?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 } ?: return BAD
                if (s == null) return NO_RIG
                setFreq = hz to now
                send(CatCommand.Freq(hz))
                OK
            }
            "m", "\\get_mode" -> {
                val m = setMode?.takeIf { now - it.second < HOLD_MS }?.first ?: s?.radioMode?.let { hamlibMode(it) } ?: return NO_RIG
                "$m\n${passband(m)}\n"
            }
            "M", "\\set_mode" -> {
                val m = args.firstOrNull()?.uppercase() ?: return BAD
                if (m == "?") return "USB LSB CW CWR RTTY RTTYR AM FM WFM PKTUSB PKTLSB PKTFM\n"
                val rm = radioMode(m, s?.freqHz) ?: return BAD
                if (s == null) return NO_RIG
                setMode = m to now
                send(CatCommand.Mode(rm))
                OK
            }
            "t", "\\get_ptt" -> {
                val on = setPtt?.takeIf { now - it.second < HOLD_MS }?.first ?: (s?.tx == true)
                if (on) "1\n" else "0\n"
            }
            "T", "\\set_ptt" -> {
                val on = args.firstOrNull()?.let { it != "0" } ?: return BAD
                if (s == null) return NO_RIG
                setPtt = on to now
                send(CatCommand.Ptt(on))
                OK
            }
            // No split: QSO-LOG tunes one VFO. WSJT-X / JTDX: Split Operation "None" or "Fake It".
            "s", "\\get_split_vfo" -> "0\nVFOA\n"
            "S", "\\set_split_vfo" -> if (args.firstOrNull() == "1") NOT_AVAILABLE else OK
            "i", "\\get_split_freq" -> "${s?.freqHz ?: 0}\n"
            "l", "\\get_level" -> level(args.firstOrNull()?.uppercase(), s)
            "L", "\\set_level" -> OK
            "u", "\\get_func" -> "0\n"
            "U", "\\set_func" -> OK
            "j", "\\get_rit", "z", "\\get_xit" -> "0\n"
            else -> NOT_AVAILABLE
        }
    }

    private fun level(name: String?, s: RigState?): String = when (name) {
        // rigctld's STRENGTH: dB over or under S9.
        "STRENGTH" -> "${(s?.sDb ?: 0) - 54}\n"
        "RFPOWER_METER" -> String.format(Locale.ROOT, "%.2f\n", (s?.powerPct ?: 0) / 100.0)
        "SWR" -> String.format(Locale.ROOT, "%.2f\n", s?.swr ?: 1.0)
        "ALC" -> String.format(Locale.ROOT, "%.2f\n", (s?.alcPct ?: 0) / 100.0)
        null -> BAD
        else -> "0\n"
    }

    companion object {
        const val DEFAULT_PORT = 4532
        private const val HOLD_MS = 1500L
        private const val OK = "RPRT 0\n"
        private const val BAD = "RPRT -1\n"
        private const val NOT_AVAILABLE = "RPRT -11\n"
        /** The radio does not answer (CAT off or the radio switched off): Hamlib's "communication timed out". */
        private const val NO_RIG = "RPRT -5\n"

        /** The radio's own mode name ("USB-D", "DATA-U", "CW-R", "FSK") as Hamlib calls it ("PKTUSB", "CWR", "RTTY"). */
        fun hamlibMode(radio: String): String {
            val r = radio.uppercase()
            return when {
                r == "USB-D" || r == "DATA-U" || r == "DATA" || r == "PKTUSB" || r == "DIG" || r == "DD" || r == "PKT" -> "PKTUSB"
                r == "LSB-D" || r == "DATA-L" || r == "PKTLSB" -> "PKTLSB"
                r == "FM-D" || r == "DATA-FM" || r == "PKTFM" -> "PKTFM"
                r == "CW-R" || r == "CWR" -> "CWR"
                r == "FSK-R" || r == "RTTY-R" || r == "RTTYR" -> "RTTYR"
                r.startsWith("FSK") || r.startsWith("RTTY") -> "RTTY"
                r == "FM-N" || r == "NFM" -> "FM"
                r == "AM-N" || r == "AM-D" -> "AM"
                r in setOf("USB", "LSB", "CW", "AM", "FM", "WFM") -> r
                else -> "USB"
            }
        }

        /** Hamlib's mode name → what the app can switch the radio to; null for a mode it cannot. */
        fun radioMode(hamlib: String, hz: Long?): RadioMode? = when (hamlib) {
            "USB" -> RadioMode.USB
            "LSB" -> RadioMode.LSB
            "CW", "CWR" -> RadioMode.CW
            "RTTY", "RTTYR" -> RadioMode.RTTY
            "AM", "SAM", "AMS" -> RadioMode.AM
            "FM", "WFM", "FMN" -> RadioMode.FM
            "PKTUSB", "PKTLSB", "PKTFM" -> RadioMode.DATA
            else -> null
        }

        private fun passband(m: String): Int = when (m) {
            "CW", "CWR" -> 500
            "AM" -> 6000
            "FM", "PKTFM" -> 12000
            "WFM" -> 230000
            "PKTUSB", "PKTLSB" -> 3000
            else -> 2400
        }

        /**
         * The radio's capabilities as rigctld lists them (protocol 1): what its client builds its own idea of the radio
         * from. HF to 70 cm, every common mode, CAT PTT; the end is "done".
         */
        val DUMP_STATE: String = buildString {
            val modes = "0x1fff" // AM CW USB LSB RTTY FM WFM CWR RTTYR … PKTLSB PKTUSB PKTFM
            append("1\n") // protocol version
            append("1\n") // rig model (the dummy: a radio with everything)
            append("0\n") // ITU region
            append("100000.000000 1500000000.000000 $modes -1 -1 0x3 0x1\n")
            append("0 0 0 0 0 0 0\n")
            append("1800000.000000 450000000.000000 $modes 5000 100000 0x3 0x1\n")
            append("0 0 0 0 0 0 0\n")
            append("$modes 1\n$modes 10\n$modes 100\n$modes 1000\n0 0\n")
            append("0xc 2400\n0xc 1800\n0xc 3000\n0x82 500\n0x82 2400\n0x10 300\n0x10 2400\n0x1 6000\n0x20 12000\n0x40 230000\n0xe00 3000\n0 0\n")
            append("0\n0\n0\n") // max RIT, XIT, IF shift
            append("0\n") // announces
            append("\n\n") // preamps, attenuators
            append("0x0\n0x0\n0x40000000\n0x0\n0x0\n0x0\n") // functions, levels (get STRENGTH), parms
            append("vfo_ops=0x0\nptt_type=0x1\ntargetable_vfo=0x0\nhas_set_vfo=1\nhas_get_vfo=1\nhas_set_freq=1\nhas_get_freq=1\n")
            append("has_set_conf=0\nhas_get_conf=0\nhas_power2mW=0\nhas_mW2power=0\nhas_get_ant=0\nhas_set_ant=0\ntimeout=0\nrig_model=1\n")
            append("rigctld_version=QSO-LOG\ndone\n")
        }
    }
}
