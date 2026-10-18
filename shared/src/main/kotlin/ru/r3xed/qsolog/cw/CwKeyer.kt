package ru.r3xed.qsolog.cw

import ru.r3xed.qsolog.morse.MorseCode
import ru.r3xed.qsolog.tr
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

/** The modem line of a USB serial adapter (CP2102, CH340, FT232) that closes the key, as N1MM does. */
enum class CwLine { DTR, RTS }

/** How the key is closed: a line of a USB adapter right here, or cwdaemon on another computer over the network. */
enum class CwVia { SERIAL, CWDAEMON }

/**
 * Telegraph from the log: the messages F1–F8 sent by switching [line] of the adapter [device]. On a CP2102 the TTL pins
 * of DTR and RTS go low when the line is on — what the CW jack of a Xiegu G90 wants (key down = low level).
 * [ptt] — the other line goes on for the whole message (radios without break-in).
 * With [via] = [CwVia.CWDAEMON] the texts go over UDP to cwdaemon at [daemon] ("192.168.1.20" or "host:6789"),
 * which keys the radio wired to that computer.
 */
data class CwConfig(
    val enabled: Boolean = false,
    val via: CwVia = CwVia.SERIAL,
    val daemon: String = "",
    /** USB device name (Android) or the computer's serial port. */
    val device: String = "",
    val line: CwLine = CwLine.DTR,
    val invert: Boolean = false,
    val ptt: Boolean = false,
    val wpm: Int = 24,
    val messages: List<String> = DEFAULT_MESSAGES,
) {
    fun toMap(): Map<String, String> = mapOf(
        "cw_enabled" to enabled.toString(), "cw_via" to via.name, "cw_daemon" to daemon, "cw_device" to device, "cw_line" to line.name, "cw_invert" to invert.toString(),
        "cw_ptt" to ptt.toString(), "cw_wpm" to wpm.toString(),
    ) + messages.mapIndexed { i, m -> "cw_msg${i + 1}" to m }

    companion object {
        /** As N1MM's: CQ, exchange, thanks, my call, his call, the exchange again, "?", "AGN?". */
        val DEFAULT_MESSAGES = listOf(
            "CQ {MYCALL} {MYCALL} TEST", "{CALL} {RST} {NR}", "TU {MYCALL}", "{MYCALL}",
            "{CALL}", "{RST} {NR} {NR}", "?", "AGN?",
        )
        const val MIN_WPM = 10
        const val MAX_WPM = 50

        fun fromMap(m: Map<String, String?>): CwConfig {
            val d = CwConfig()
            return CwConfig(
                enabled = m["cw_enabled"]?.toBooleanStrictOrNull() ?: d.enabled,
                via = m["cw_via"]?.let { v -> CwVia.entries.firstOrNull { it.name == v } } ?: d.via,
                daemon = m["cw_daemon"] ?: d.daemon,
                device = m["cw_device"] ?: d.device,
                line = m["cw_line"]?.let { v -> CwLine.entries.firstOrNull { it.name == v } } ?: d.line,
                invert = m["cw_invert"]?.toBooleanStrictOrNull() ?: d.invert,
                ptt = m["cw_ptt"]?.toBooleanStrictOrNull() ?: d.ptt,
                wpm = m["cw_wpm"]?.toIntOrNull()?.coerceIn(MIN_WPM, MAX_WPM) ?: d.wpm,
                messages = DEFAULT_MESSAGES.indices.map { i -> m["cw_msg${i + 1}"] ?: DEFAULT_MESSAGES[i] },
            )
        }

        val KEYS = CwConfig().toMap().keys
    }
}

/** The fields of the card a message takes its words from. */
object CwMacros {
    // Both braces escaped: Android's regular expressions (ICU) refuse a lone "}".
    private val FIELD = Regex("""\{([A-Za-z]+)\}""")

    /** "{CALL}" and the like: what they mean, for the settings. */
    val HELP get() = tr("{MYCALL} — мой позывной, {CALL} — позывной корреспондента, {RST} — RST отправленный, {NR} — контрольный номер отправленный, {NAME} — имя корреспондента, {MYLOC} — мой локатор")

    /** [template] with the fields filled; unknown or empty ones vanish, spaces are squeezed. Latin capitals for Morse. */
    fun expand(template: String, myCall: String, call: String, rst: String, nr: String, name: String = "", myLoc: String = ""): String {
        val values = mapOf("MYCALL" to myCall, "CALL" to call, "RST" to rst, "NR" to nr, "EXCH" to nr, "NAME" to name, "MYLOC" to myLoc)
        return FIELD.replace(template) { m -> values[m.groupValues[1].uppercase()].orEmpty() }
            .uppercase().trim().replace(Regex("""\s+"""), " ")
    }
}

/** The adapter's lines; opened once and kept open while the key line is on. */
interface KeyLine : Closeable {
    fun key(down: Boolean)
    fun ptt(on: Boolean)
}

sealed interface CwState {
    data object Idle : CwState
    /** [text] is being sent; [done] characters of it are out. */
    data class Sending(val text: String, val done: Int) : CwState
    data class Failed(val message: String) : CwState
}

/** What sends the messages: [CwKeyer] on a line of an adapter, or [CwDaemon] over the network. */
interface CwSender {
    var wpm: Int
    fun send(text: String)
    /** Stops the message being sent and drops the ones waiting. */
    fun stop()
    fun close()
}

/**
 * Sends texts in Morse on its own thread, one after another. Element times are counted from the start of the message,
 * so a late wake-up does not pile up: at 24 WPM a dot is 50 ms, and a millisecond or two of USB delay does not matter.
 */
class CwKeyer(
    private val open: () -> KeyLine,
    private val onState: (CwState) -> Unit,
    @Volatile override var wpm: Int = 24,
    private val usePtt: Boolean = false,
) : CwSender {
    private val queue = LinkedBlockingQueue<String>()
    @Volatile private var abort = false
    @Volatile private var running = true
    private var line: KeyLine? = null
    private val thread = Thread(::loop, "CW").apply { isDaemon = true; start() }

    override fun send(text: String) {
        if (text.isBlank()) return
        abort = false
        queue.add(text)
    }

    override fun stop() {
        queue.clear()
        abort = true
    }

    override fun close() {
        running = false
        stop()
        thread.interrupt()
    }

    private fun loop() {
        try {
            while (running) {
                val text = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                abort = false
                try {
                    val l = line ?: open().also { line = it }
                    play(l, text)
                    onState(CwState.Idle)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    runCatching { line?.close() }
                    line = null
                    queue.clear()
                    onState(CwState.Failed(e.message?.ifBlank { null } ?: e.javaClass.simpleName))
                }
            }
        } catch (e: InterruptedException) {
            Unit
        } finally {
            runCatching { line?.key(false); line?.ptt(false) }
            runCatching { line?.close() }
            line = null
        }
    }

    private fun play(l: KeyLine, text: String) {
        var t = System.nanoTime()
        fun dot() = 1_200_000_000L / wpm.coerceIn(CwConfig.MIN_WPM, CwConfig.MAX_WPM)
        fun waitUntil(at: Long) {
            while (!abort) {
                val left = at - System.nanoTime()
                if (left <= 0) return
                LockSupport.parkNanos(left)
                if (Thread.interrupted()) throw InterruptedException()
            }
        }
        if (usePtt) {
            l.ptt(true)
            t += 30_000_000 // the radio switches to transmit
        }
        try {
            text.forEachIndexed { i, ch ->
                if (abort) return
                onState(CwState.Sending(text, i))
                if (ch.isWhitespace()) {
                    t += 4 * dot() // with the 3 after the last character: 7 between words
                    return@forEachIndexed
                }
                val code = MorseCode.code(ch.uppercaseChar()) ?: return@forEachIndexed
                code.forEachIndexed { j, e ->
                    waitUntil(t)
                    if (abort) return
                    l.key(true)
                    t += (if (e == '-') 3 else 1) * dot()
                    waitUntil(t)
                    l.key(false)
                    if (j < code.length - 1) t += dot()
                }
                t += 3 * dot()
            }
            waitUntil(t)
        } finally {
            l.key(false)
            if (usePtt) l.ptt(false)
        }
    }
}
