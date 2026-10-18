package ru.r3xed.qsolog.cw

import ru.r3xed.qsolog.morse.MorseCode
import ru.r3xed.qsolog.tr
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * cwdaemon (https://github.com/acerion/cwdaemon) on another computer keys the radio wired to it; the texts go there
 * over UDP, as from tlf, N1MM-like loggers and Xlog. Plain text is sent in Morse; Esc commands: "2" + WPM — the speed,
 * "4" — stop, "h" + text — answer with "h" + text when the next message has been sent (here: the message is over).
 */
class CwDaemon(
    address: String,
    private val onState: (CwState) -> Unit,
    @Volatile override var wpm: Int = 24,
) : CwSender {
    private val host: String
    private val port: Int

    init {
        val (h, p) = parse(address)
        host = h
        port = p
    }

    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cwdaemon").apply { isDaemon = true } }
    @Volatile private var running = true
    private var socket: DatagramSocket? = null
    private var reader: Thread? = null
    private var sentWpm = 0
    /** The number of the last message: its echo means the sending is over. */
    @Volatile private var last = 0
    private var fallback: java.util.concurrent.ScheduledFuture<*>? = null

    override fun send(text: String) {
        val t = clean(text)
        if (t.isBlank()) return
        worker.execute {
            try {
                val s = open()
                val w = wpm.coerceIn(CwConfig.MIN_WPM, CwConfig.MAX_WPM)
                if (w != sentWpm) {
                    command(s, "2$w")
                    sentWpm = w
                }
                val id = ++last
                command(s, "h$id")
                s.send(packet(t))
                onState(CwState.Sending(t, 0))
                // An old cwdaemon without the echo, or a lost answer: the message is over by its length.
                fallback?.cancel(false)
                fallback = worker.schedule({ if (last == id) onState(CwState.Idle) }, durationMs(t, w) + 3000, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    override fun stop() {
        worker.execute {
            last++
            fallback?.cancel(false)
            runCatching { socket?.let { command(it, "4") } }
            onState(CwState.Idle)
        }
    }

    override fun close() {
        stop()
        worker.execute {
            running = false
            socket?.close()
            socket = null
        }
        worker.shutdown()
    }

    private fun open(): DatagramSocket = socket ?: DatagramSocket().also { s ->
        if (host.isBlank()) throw IllegalStateException(tr("Укажите адрес cwdaemon в настройках CW"))
        val to = InetSocketAddress(host, port)
        if (to.isUnresolved) throw IllegalStateException(tr("cwdaemon: не найден адрес %s", host))
        // Connected: the "port unreachable" of a computer without cwdaemon comes back as an error.
        s.connect(to)
        s.soTimeout = 1000
        socket = s
        reader = Thread({ read(s) }, "cwdaemon-reply").apply { isDaemon = true; start() }
    }

    private fun read(s: DatagramSocket) {
        val buf = ByteArray(256)
        while (running && !s.isClosed) {
            try {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val answer = String(p.data, 0, p.length, Charsets.US_ASCII).trim()
                if (answer == "h$last") worker.execute { fallback?.cancel(false); onState(CwState.Idle) }
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: PortUnreachableException) {
                worker.execute { fail(e) }
                return
            } catch (e: Exception) {
                return
            }
        }
    }

    private fun fail(e: Exception) {
        fallback?.cancel(false)
        runCatching { socket?.close() }
        socket = null
        sentWpm = 0
        onState(CwState.Failed(
            if (e is PortUnreachableException) tr("cwdaemon не отвечает на %s:%s — он запущен?", host, port)
            else e.message?.ifBlank { null } ?: e.javaClass.simpleName,
        ))
    }

    private fun command(s: DatagramSocket, c: String) = s.send(packet("\u001B" + c))

    private fun packet(text: String): DatagramPacket = text.toByteArray(Charsets.US_ASCII).let { DatagramPacket(it, it.size) }

    companion object {
        const val DEFAULT_PORT = 6789

        /** "192.168.1.20", "pi.local:6790", "[fe80::1]:6789" → host and port. */
        fun parse(address: String): Pair<String, Int> {
            val a = address.trim()
            if (a.startsWith("[")) {
                val end = a.indexOf(']')
                if (end > 0) return a.substring(1, end) to (a.substring(end + 1).removePrefix(":").toIntOrNull() ?: DEFAULT_PORT)
            }
            val i = a.lastIndexOf(':')
            // One colon is a port; more is a bare IPv6 address.
            return if (i > 0 && a.indexOf(':') == i) a.substring(0, i) to (a.substring(i + 1).toIntOrNull() ?: DEFAULT_PORT) else a to DEFAULT_PORT
        }

        /**
         * For cwdaemon "+" and "-" change the speed and "~" is a short pause; they are left out, as are the characters
         * it cannot send. Spaces are squeezed.
         */
        fun clean(text: String): String = text.uppercase()
            .filter { it == ' ' || (it !in "+-~" && MorseCode.code(it) != null) }
            .trim().replace(Regex(" +"), " ")

        /** How long [text] takes at [wpm]: a dot is 1200/WPM ms, a dash 3 dots, 3 between characters, 7 between words. */
        fun durationMs(text: String, wpm: Int): Long {
            var units = 0
            for (ch in text) {
                if (ch == ' ') { units += 4; continue }
                val code = MorseCode.code(ch) ?: continue
                units += code.count { it == '-' } * 2 + code.length + code.length - 1 + 3
            }
            return units * 1200L / wpm.coerceAtLeast(1)
        }
    }
}
