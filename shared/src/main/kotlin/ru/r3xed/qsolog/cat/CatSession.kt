package ru.r3xed.qsolog.cat

import ru.r3xed.qsolog.tr
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Talks to the radio on its own thread: opens the link with [open], polls every [pollMs] and reports to [onStatus]
 * (from that thread). A lost link is opened again every few seconds until [stop].
 */
class CatSession(
    private val config: CatConfig,
    private val open: () -> CatLink,
    private val onStatus: (CatStatus) -> Unit,
    private val pollMs: Long = 250,
    private val retryMs: Long = 3000,
) {
    @Volatile private var running = false
    @Volatile private var link: CatLink? = null
    private var thread: Thread? = null

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

    private fun loop() {
        val driver = RigDriver.of(config)
        var state = RigState(freqHz = null)
        while (running) {
            try {
                onStatus(CatStatus.Connecting(driver.what))
                val l = open()
                link = l
                if (!running) { l.close(); break }
                val io = CatIo(l)
                runCatching { io.clear() }
                var silentPolls = 0
                var heard = false
                onStatus(CatStatus.Connected(driver.what, null))
                while (running) {
                    state = driver.poll(io, state)
                    if (driver.answered) {
                        heard = true
                        silentPolls = 0
                    } else silentPolls++
                    val silent = silentPolls >= 3
                    onStatus(CatStatus.Connected(driver.what, if (heard) state else null, silent))
                    Thread.sleep(pollMs)
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
