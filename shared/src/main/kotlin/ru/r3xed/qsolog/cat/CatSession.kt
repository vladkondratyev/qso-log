package ru.r3xed.qsolog.cat

import ru.r3xed.qsolog.tr
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Talks to the radio on its own thread: opens the link with [open], polls every [pollMs] and reports to [onStatus]
 * (from that thread). A lost link is opened again every few seconds until [stop].
 *
 * With [openAt] (a serial port opened at a given speed) a Kenwood or Yaesu that stays silent is tried at the other
 * speeds in turn — a TS-590S on 9600 while the program says 115200 — and the speed it answers at goes to [onBaud].
 */
class CatSession(
    private val config: CatConfig,
    private val open: () -> CatLink,
    private val onStatus: (CatStatus) -> Unit,
    private val pollMs: Long = 250,
    private val retryMs: Long = 3000,
    private val openAt: ((Int) -> CatLink)? = null,
    private val onBaud: (Int) -> Unit = {},
) {
    @Volatile private var running = false
    @Volatile private var link: CatLink? = null
    private var thread: Thread? = null
    private val freq = AtomicReference<CatCommand.Freq?>(null)
    private val mode = AtomicReference<CatCommand.Mode?>(null)
    private val ptt = AtomicReference<CatCommand.Ptt?>(null)
    private val wake = Semaphore(0)

    /**
     * Asks the radio to change something, between two polls. While the dial is dragged only the last frequency is sent;
     * the frequency goes before the mode (LSB or USB depends on it).
     */
    fun send(c: CatCommand) {
        when (c) {
            is CatCommand.Freq -> freq.set(c)
            is CatCommand.Mode -> mode.set(c)
            is CatCommand.Ptt -> ptt.set(c)
        }
        wake.release()
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "CAT").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { link?.close() }
        thread?.interrupt()
        thread = null
    }

    /** The speeds to try, the set one first; one speed (no trying) when the radio's speed cannot be guessed so. */
    private val bauds: List<Int> =
        if (openAt != null && config.protocol in setOf(CatProtocol.KENWOOD, CatProtocol.YAESU, CatProtocol.YAESU_OLD))
            listOf(config.baud) + (Rigs.BAUDS.reversed() - config.baud)
        else listOf(config.baud)

    private fun loop() {
        val driver = RigDriver.of(config)
        var state = RigState(freqHz = null)
        var baudAt = 0
        // Once the radio has answered at a speed, the speed stays (a radio switched off is not a wrong speed).
        var baudFound = bauds.size == 1
        while (running) {
            try {
                val baud = bauds[baudAt]
                val label = if (baudFound) driver.what else driver.what + " · " + tr("%s бод", baud)
                onStatus(CatStatus.Connecting(label))
                val l = if (openAt != null && !baudFound) openAt.invoke(baud) else open()
                link = l
                if (!running) { l.close(); break }
                val io = CatIo(l)
                runCatching { io.clear() }
                var silentPolls = 0
                var heard = false
                onStatus(CatStatus.Connected(driver.what, null))
                // Asked while there was no link: the user has moved on.
                freq.set(null)
                mode.set(null)
                ptt.set(null)
                while (running) {
                    // Transmit on or off first: a program waits for it to start or stop its audio.
                    ptt.getAndSet(null)?.let { io.clear(); driver.setPtt(io, it.on) }
                    freq.getAndSet(null)?.let { io.clear(); driver.setFreq(io, it.hz) }
                    mode.getAndSet(null)?.let { io.clear(); driver.setMode(io, it.mode) }
                    state = driver.poll(io, state)
                    if (driver.answered) {
                        heard = true
                        silentPolls = 0
                        if (!baudFound) {
                            baudFound = true
                            if (baud != config.baud) onBaud(baud)
                        }
                    } else silentPolls++
                    if (!baudFound && !heard && silentPolls >= 6) {
                        // Not a word at this speed: the next one. After all of them, back to the set speed, and it stays.
                        baudAt++
                        if (baudAt >= bauds.size) { baudAt = 0; baudFound = true }
                        break
                    }
                    val silent = silentPolls >= 3
                    onStatus(CatStatus.Connected(if (baudFound) driver.what else label, if (heard) state else null, silent))
                    // A command wakes the loop at once; otherwise the next poll comes after the pause.
                    wake.tryAcquire(pollMs, TimeUnit.MILLISECONDS)
                    wake.drainPermits()
                }
                l.close()
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                runCatching { link?.close() }
                if (!running) break
                onStatus(CatStatus.Failed(e.message?.ifBlank { null } ?: e.javaClass.simpleName))
                try { Thread.sleep(retryMs) } catch (e: InterruptedException) { break }
            }
        }
        link = null
        onStatus(CatStatus.Off)
    }
}

/** A link over blocking streams (Bluetooth, TCP): a reader thread puts the bytes in a queue so reads can time out. */
open class StreamLink(private val input: InputStream, private val output: OutputStream, private val closer: () -> Unit) : CatLink {
    private val queue = ArrayBlockingQueue<ByteArray>(1024)
    @Volatile private var closed = false
    @Volatile private var failure: IOException? = null

    private val reader = Thread({
        val b = ByteArray(1024)
        try {
            while (!closed) {
                val n = input.read(b)
                if (n < 0) break
                if (n > 0) queue.put(b.copyOf(n))
            }
            failure = IOException(tr("Соединение закрыто"))
        } catch (e: IOException) {
            failure = e
        } catch (e: InterruptedException) {
            Unit
        }
    }, "CAT-read").apply { isDaemon = true; start() }

    override fun write(b: ByteArray) {
        output.write(b)
        output.flush()
    }

    private var leftover: ByteArray? = null

    override fun read(buf: ByteArray, timeoutMs: Int): Int {
        val chunk = leftover ?: queue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            ?: return if (failure != null && queue.isEmpty()) throw failure!! else 0
        // A chunk bigger than buf is cut; the rest is read next time.
        val n = minOf(chunk.size, buf.size)
        System.arraycopy(chunk, 0, buf, 0, n)
        leftover = if (n < chunk.size) chunk.copyOfRange(n, chunk.size) else null
        return n
    }

    override fun close() {
        closed = true
        runCatching { closer() }
        reader.interrupt()
    }
}

/** rigctld, or a radio behind a serial-to-network server (ser2net, a Wi-Fi CAT adapter). */
fun tcpLink(host: String, port: Int, timeoutMs: Int = 4000): CatLink {
    val s = Socket()
    try {
        s.connect(InetSocketAddress(host.trim(), port), timeoutMs)
        s.tcpNoDelay = true
    } catch (e: IOException) {
        runCatching { s.close() }
        throw IOException(tr("Нет соединения с %s:%s", host, port) + " (" + (e.message ?: e.javaClass.simpleName) + ")")
    }
    return StreamLink(s.getInputStream(), s.getOutputStream()) { s.close() }
}
