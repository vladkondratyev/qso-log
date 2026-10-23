package ru.r3xed.qsolog.cw

import ru.r3xed.qsolog.morse.MorseCode
import ru.r3xed.qsolog.tr
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

/**
 * Waits until the [System.nanoTime] [at], to a fraction of a millisecond. Windows wakes a sleeping thread only every
 * 1–16 ms, which made dots of a different length ("not crisp" keying): the thread sleeps until the last 3 ms and spins
 * through them. [stop] ends the wait early.
 */
fun preciseWait(at: Long, stop: () -> Boolean = { false }) {
    while (!stop()) {
        val left = at - System.nanoTime()
        if (left <= 0) return
        if (left > SPIN_NS) LockSupport.parkNanos(left - SPIN_NS) else Thread.onSpinWait()
        if (Thread.interrupted()) throw InterruptedException()
    }
}

/** How long before the moment a waiting thread stops sleeping and spins: Windows' timer is coarse, the others' fine. */
private val SPIN_NS: Long = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) 3_000_000 else 300_000

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
    /** Keys of an external keyboard as a straight key or paddles in the CW card: see [CwKeyMode]. */
    val keyMode: CwKeyMode = CwKeyMode.OFF,
    /** The device's speaker beeps along with what is sent (self-monitoring), when the radio's own sidetone is not heard. */
    val sidetone: Boolean = true,
    /** The key on the app's own keypad (the phone): a straight key or paddles under F1–F8; OFF — none. */
    val screenKey: CwKeyMode = CwKeyMode.OFF,
) {
    fun toMap(): Map<String, String> = mapOf(
        "cw_enabled" to enabled.toString(), "cw_via" to via.name, "cw_daemon" to daemon, "cw_device" to device, "cw_line" to line.name, "cw_invert" to invert.toString(),
        "cw_ptt" to ptt.toString(), "cw_wpm" to wpm.toString(), "cw_key_mode" to keyMode.name, "cw_sidetone" to sidetone.toString(),
        "cw_screen_key" to screenKey.name,
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
                keyMode = m["cw_key_mode"]?.let { v -> CwKeyMode.entries.firstOrNull { it.name == v } } ?: d.keyMode,
                sidetone = m["cw_sidetone"]?.toBooleanStrictOrNull() ?: d.sidetone,
                screenKey = m["cw_screen_key"]?.let { v -> CwKeyMode.entries.firstOrNull { it.name == v } } ?: d.screenKey,
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

/**
 * Keying by hand from an external keyboard while the QSO card is in CW. [OFF]: the keys type as usual. [STRAIGHT]: a
 * key held down keys the radio, as a straight key. [IAMBIC_A], [IAMBIC_B]: two keys are the dot and dash paddles of an
 * electronic keyer. Which keys (left and right Ctrl, [ and ] by default) is set as for the Morse trainer.
 */
enum class CwKeyMode {
    OFF, STRAIGHT, IAMBIC_A, IAMBIC_B;

    val title: String
        get() = when (this) {
            OFF -> tr("Выкл.")
            STRAIGHT -> tr("Прямой ключ")
            IAMBIC_A -> tr("Ямбический A")
            IAMBIC_B -> tr("Ямбический B")
        }

    val keyType: ru.r3xed.qsolog.morse.KeyType
        get() = when (this) {
            IAMBIC_A -> ru.r3xed.qsolog.morse.KeyType.IAMBIC_A
            IAMBIC_B -> ru.r3xed.qsolog.morse.KeyType.IAMBIC_B
            else -> ru.r3xed.qsolog.morse.KeyType.STRAIGHT
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

    /** Keys the radio by hand (a straight key or a paddle keyer here); false when this sender cannot — it takes only text. */
    fun hand(down: Boolean): Boolean = false
}

/**
 * cwdaemon keys the radio far away; [mirror] plays the same texts on a line of nothing, so the speaker here beeps along
 * (see [CwKeyer.onKey]). The state is the daemon's.
 */
class MirroredSender(val main: CwSender, private val mirror: CwSender) : CwSender {
    override var wpm: Int
        get() = main.wpm
        set(v) { main.wpm = v; mirror.wpm = v }

    override fun send(text: String) { main.send(text); mirror.send(text) }
    override fun stop() { main.stop(); mirror.stop() }
    override fun close() { main.close(); mirror.close() }
}

/** A key line of nothing: for [MirroredSender]'s speaker-only keyer. */
object NoLine : KeyLine {
    override fun key(down: Boolean) = Unit
    override fun ptt(on: Boolean) = Unit
    override fun close() = Unit
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
    /** Every key down and up, for the speaker's self-monitoring tone. */
    private val onKey: (Boolean) -> Unit = {},
) : CwSender {
    private val queue = LinkedBlockingQueue<String>()
    @Volatile private var abort = false
    @Volatile private var running = true
    private var line: KeyLine? = null
    private val lineLock = Any()
    /** PTT is on: it stays on between the letters typed one by one, and goes off after [PTT_HANG_MS] of silence. */
    @Volatile private var pttOn = false
    @Volatile private var lastKeyAt = 0L
    @Volatile private var handDown = false
    private val thread = Thread(::loop, "CW").apply { isDaemon = true; start() }

    private fun line(): KeyLine = synchronized(lineLock) { line ?: open().also { line = it } }

    private fun key(l: KeyLine, down: Boolean) {
        l.key(down)
        onKey(down)
        lastKeyAt = System.currentTimeMillis()
    }

    override fun hand(down: Boolean): Boolean {
        // A key pressed by hand cuts the message being sent.
        if (down && (sending || queue.isNotEmpty())) stop()
        try {
            val l = line()
            if (down && usePtt && !pttOn) {
                l.ptt(true)
                pttOn = true
            }
            handDown = down
            key(l, down)
        } catch (e: Exception) {
            handDown = false
            onState(CwState.Failed(e.message?.ifBlank { null } ?: e.javaClass.simpleName))
        }
        return true
    }

    @Volatile private var sending = false

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
                val text = queue.poll(if (pttOn) 100 else 500, TimeUnit.MILLISECONDS)
                if (text == null) {
                    // Nothing more to send for a while (and no key held): the radio goes back to receive.
                    if (pttOn && !handDown && System.currentTimeMillis() - lastKeyAt > PTT_HANG_MS) {
                        runCatching { synchronized(lineLock) { line }?.ptt(false) }
                        pttOn = false
                    }
                    continue
                }
                abort = false
                try {
                    sending = true
                    play(line(), text)
                    sending = false
                    onState(CwState.Idle)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    sending = false
                    synchronized(lineLock) {
                        runCatching { line?.close() }
                        line = null
                    }
                    pttOn = false
                    queue.clear()
                    onState(CwState.Failed(e.message?.ifBlank { null } ?: e.javaClass.simpleName))
                }
            }
        } catch (e: InterruptedException) {
            Unit
        } finally {
            runCatching { line?.key(false); line?.ptt(false) }
            onKey(false)
            runCatching { line?.close() }
            line = null
        }
    }

    private fun play(l: KeyLine, text: String) {
        var t = System.nanoTime()
        fun dot() = 1_200_000_000L / wpm.coerceIn(CwConfig.MIN_WPM, CwConfig.MAX_WPM)
        fun waitUntil(at: Long) = preciseWait(at) { abort }
        if (usePtt && !pttOn) {
            l.ptt(true)
            pttOn = true
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
                    key(l, true)
                    t += (if (e == '-') 3 else 1) * dot()
                    waitUntil(t)
                    key(l, false)
                    if (j < code.length - 1) t += dot()
                }
                t += 3 * dot()
            }
            waitUntil(t)
        } finally {
            key(l, false)
            // A stopped message lets the radio go at once; otherwise PTT hangs a moment for the next letters.
            if (usePtt && abort) {
                l.ptt(false)
                pttOn = false
            }
        }
    }
}

/** PTT stays on this long after the last element, so letters typed one by one do not switch the radio each time. */
private const val PTT_HANG_MS = 700L

/**
 * Keying by hand from the card: the keyboard (or the phone's screen) is a straight key or the paddles of an iambic
 * keyer ([type]). A thread looks at the paddles every millisecond and keys through [keyDown]; [decoder] turns what was
 * keyed into letters for the tape. A sender that cannot key by hand (cwdaemon) gets the letters as text instead.
 */
class CwHand(
    type: ru.r3xed.qsolog.morse.KeyType,
    wpm: Int,
    private val keyDown: (Boolean) -> Boolean,
    private val onText: (String) -> Unit,
    private val onTone: (Boolean) -> Unit = {},
) {
    val session = ru.r3xed.qsolog.morse.MorseKeySession(wpm, type, cyrillic = false)
    @Volatile private var running = true
    private var tone = false
    private var direct = true
    private var told = 0

    /** What has been keyed so far, as letters. */
    fun text(): String = session.read(System.nanoTime() / 1_000_000) { it.text }

    fun clear() {
        session.clear()
        told = 0
    }

    private val thread = Thread({
        try {
            while (running) {
                val now = System.nanoTime()
                val on = session.toneAt(now / 1_000_000)
                if (on != tone) {
                    tone = on
                    onTone(on)
                    if (direct && !keyDown(on)) direct = false
                }
                // Letters as text for a sender that cannot be keyed by hand (the tape reads [text] itself).
                if (!direct) {
                    val t = text()
                    if (t.length > told) onText(t.substring(told))
                    told = t.length
                }
                val busy = on || session.dit || session.dah || session.straight
                if (busy) preciseWait(now + 1_000_000) else LockSupport.parkNanos(4_000_000)
            }
        } catch (e: InterruptedException) {
            Unit
        } finally {
            if (tone) { onTone(false); if (direct) keyDown(false) }
        }
    }, "CW-hand").apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }

    /** A paddle or the straight key: 0 straight, 1 dot, 2 dash (see [ru.r3xed.qsolog.morse.MorseKeySession.press]). */
    fun press(which: Int, down: Boolean) = session.press(which, down)

    fun close() {
        running = false
        thread.interrupt()
    }
}
