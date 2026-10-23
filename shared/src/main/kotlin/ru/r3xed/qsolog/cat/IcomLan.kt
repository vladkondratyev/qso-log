package ru.r3xed.qsolog.cat

import ru.r3xed.qsolog.tr
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Icom's network remote control (IC-705, IC-7610, IC-7760, IC-9700, IC-905, IC-7300MK2…): what RS-BA1, wfview and
 * SDR-Control speak. Three UDP streams: control (port 50001: login, token), CI-V (50002) and audio (50003); the radio
 * names the last two after the login. Every stream starts with "are you there" / "I am here" / "are you ready" /
 * "I am ready", then both sides ping each other and send small "idle" packets so the radio keeps the stream.
 * The CI-V stream carries the same frames as the USB cable, so [CivDriver] works through this link unchanged.
 *
 * Packets are little-endian (len, type, seq, sender id, receiver id — 16 bytes), the login's inner fields big-endian.
 * The user name and password are scrambled with Icom's fixed table ([passcode]). The protocol was learned from
 * kappanhang and wfview (their descriptions of the packets); this is an implementation of its own.
 */
class IcomLanLink(
    host: String,
    private val controlPort: Int = CatConfig.ICOM_LAN_PORT,
    private val user: String,
    private val password: String,
    private val computer: String = "QSO-LOG",
    timeoutMs: Long = 10_000,
) : CatLink {
    private val radio: InetAddress = try {
        InetAddress.getByName(host.trim())
    } catch (e: Exception) {
        throw IOException(tr("Не найден адрес %s", host))
    }
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "IcomLAN").apply { isDaemon = true } }
    private val control = Stream(controlPort, ::onControl)
    private val civSocket = DatagramSocket()
    private val audioSocket = DatagramSocket()
    @Volatile private var civ: Stream? = null
    @Volatile private var audio: Stream? = null
    private val ready = CountDownLatch(1)
    @Volatile private var failure: String? = null
    @Volatile private var closed = false
    private val incoming = ArrayBlockingQueue<ByteArray>(512)
    private var leftover: ByteArray? = null

    // Login state, kept as the radio sent it.
    private val tokRequest = Random.nextInt(1, 0xFFFF)
    private var token = 0
    private var authSeq = 0x30
    private var authenticated = false
    private var loginSent = false
    private var radioCap: ByteArray? = null
    private var radioFree = false
    private var streamRequested = false
    private var busyBy: String? = null
    @Volatile private var civHeardAt = 0L

    /** The radio's name and CI-V address from its capabilities ("IC-705", 0xA4), once the login is through. */
    @Volatile var radioName: String? = null; private set
    @Volatile var radioCiv: Int? = null; private set

    init {
        try {
            control.start()
            if (!ready.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                failure = failure ?: busyBy?.let { tr("Трансивер занят: к нему подключена программа на компьютере %s. Закройте её или подождите.", it) }
                    ?: when {
                        !control.connected -> tr("Трансивер не отвечает по адресу %s, порт UDP %s. Проверьте IP, что в меню трансивера включено Network Control, и порт (Network → Control Port, обычно 50001).", host, controlPort)
                        !authenticated -> tr("Трансивер не ответил на вход. Проверьте имя и пароль Network User в меню трансивера.")
                        else -> tr("Трансивер не открыл поток CI-V. Перезагрузите трансивер или подождите пару минут и переподключитесь.")
                    }
            }
            failure?.let { throw IOException(it) }
        } catch (e: Exception) {
            shutdown(polite = false)
            throw if (e is IOException) e else IOException(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun write(b: ByteArray) {
        failure?.let { throw IOException(it) }
        val s = civ ?: throw IOException(tr("Соединение закрыто"))
        s.sendCiv(b)
    }

    override fun read(buf: ByteArray, timeoutMs: Int): Int {
        failure?.let { if (incoming.isEmpty() && leftover == null) throw IOException(it) }
        val chunk = leftover ?: incoming.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        val n = minOf(chunk.size, buf.size)
        System.arraycopy(chunk, 0, buf, 0, n)
        leftover = if (n < chunk.size) chunk.copyOfRange(n, chunk.size) else null
        return n
    }

    override fun close() = shutdown(polite = true)

    private fun shutdown(polite: Boolean) {
        if (closed) return
        closed = true
        if (polite && control.connected) runCatching {
            civ?.let { it.sendOpenClose(open = false); it.sendControl(0x05) }
            audio?.sendControl(0x05)
            if (authenticated) control.sendTracked(tokenPacket(0x01))
            control.sendControl(0x05)
        }
        timer.shutdownNow()
        listOfNotNull(control, civ, audio).forEach { it.stop() }
        runCatching { civSocket.close() }
        runCatching { audioSocket.close() }
    }

    private fun fail(message: String) {
        if (failure == null) failure = message
        ready.countDown()
    }

    // ---------- the control stream: login, token, the radio's capabilities, the request for CI-V and audio ----------

    private fun onControl(p: ByteArray) {
        val type = p.u16(4)
        when (p.size) {
            0x10 -> if (type == 0x06 && !loginSent) {
                loginSent = true
                control.sendTracked(loginPacket())
            }
            0x60 -> if (type != 0x01) {
                if (p.i32(0x30) == LOGIN_REFUSED) {
                    fail(tr("Трансивер отклонил имя или пароль. В меню трансивера: Network → Network User1 — имя (ID) и пароль, права Administrator; те же имя и пароль впишите здесь."))
                } else if (!authenticated && p.u16(0x1a) == tokRequest) {
                    token = p.i32(0x1c)
                    authenticated = true
                    control.sendTracked(tokenPacket(0x02))
                    timer.scheduleAtFixedRate({ runCatching { control.sendTracked(tokenPacket(0x05)) } }, 60, 60, TimeUnit.SECONDS)
                }
            }
            0x40 -> if (type != 0x01 && p.u8(0x15) == 0x05 && p.u8(0x14) == 0x02 && p.i32(0x30) == -1) {
                fail(tr("Трансивер прервал сеанс. Переподключаюсь…"))
            }
            0x50 -> if (type != 0x01) onStatus(p)
            0x90 -> {
                // The radio's state: who holds it now.
                val busy = p.i32(0x60) != 0
                val who = p.cString(0x64, 16)
                busyBy = if (busy && who.isNotEmpty() && who != computer) who else null
                radioFree = !busy || who == computer
                requestStream()
            }
            else -> if (type != 0x01 && p.size > CAPS_HEAD && (p.size - CAPS_HEAD) % RADIO_CAP == 0) {
                val cap = p.copyOfRange(CAPS_HEAD, CAPS_HEAD + RADIO_CAP)
                radioCap = cap
                radioName = cap.cString(0x10, 32)
                radioCiv = cap.u8(0x52)
                requestStream()
            }
        }
    }

    /** Asks for the CI-V and audio streams once the radio is known and free. */
    private fun requestStream() {
        val cap = radioCap ?: return
        if (streamRequested || !radioFree || !authenticated) return
        streamRequested = true
        val p = ByteArray(0x90)
        head(p, control)
        p.putBe32(0x10, 0x90 - 0x10)
        p[0x14] = 0x01
        p[0x15] = 0x03
        p.putBe16(0x16, authSeq++)
        p.putLe16(0x1a, tokRequest)
        p.putLe32(0x1c, token)
        if (cap.u16(0x07) == 0x8010) {
            p.putLe16(0x27, 0x8010)
            System.arraycopy(cap, 0x0a, p, 0x2a, 6)
        } else {
            System.arraycopy(cap, 0, p, 0x20, 16)
        }
        System.arraycopy(cap, 0x10, p, 0x40, 32) // the radio's name
        passcode(user).copyInto(p, 0x60)
        p[0x70] = 1 // receive audio: the radio wants an audio stream; the smallest one is taken and dropped
        p[0x71] = 0 // no transmit audio
        p[0x72] = 0x01 // µ-law, 1 channel
        p[0x73] = 0
        p.putBe32(0x74, 8000)
        p.putBe32(0x78, 0)
        p.putBe32(0x7c, civSocket.localPort)
        p.putBe32(0x80, audioSocket.localPort)
        p.putBe32(0x84, 150)
        p[0x88] = 1
        control.sendTracked(p)
    }

    private fun onStatus(p: ByteArray) {
        val error = p.i32(0x30)
        when {
            error == -1 && civ == null -> fail(tr("Трансивер отказал в подключении CI-V. Перезагрузите трансивер или подождите пару минут: он ещё держит прошлое подключение."))
            error == 0 && p.u8(0x40) == 0x01 -> fail(tr("Трансивер закрыл подключение"))
            civ == null -> {
                val civPort = p.be16(0x42)
                val audioPort = p.be16(0x46)
                if (civPort == 0) return
                // Kept before it starts: its "I am ready" may come at once, and writing needs it then.
                val c = Stream(civPort, ::onCiv, civSocket, isCiv = true)
                civ = c
                c.start()
                if (audioPort != 0) Stream(audioPort, {}, audioSocket).also { audio = it }.start()
            }
        }
    }

    private fun onCiv(p: ByteArray) {
        if (p.size <= 0x15 || p.u16(4) == 0x01) return
        if (p.u16(0x11) + 0x15 != p.u32(0).toInt()) return
        civHeardAt = System.currentTimeMillis()
        incoming.offer(p.copyOfRange(0x15, p.size))
    }

    private fun loginPacket(): ByteArray {
        val p = ByteArray(0x80)
        head(p, control)
        p.putBe32(0x10, 0x80 - 0x10)
        p[0x14] = 0x01
        p[0x15] = 0x00
        p.putBe16(0x16, authSeq++)
        p.putLe16(0x1a, tokRequest)
        passcode(user).copyInto(p, 0x40)
        passcode(password).copyInto(p, 0x50)
        computer.toByteArray(Charsets.US_ASCII).take(16).toByteArray().copyInto(p, 0x60)
        return p
    }

    /** 0x02 — confirm the token after the login, 0x05 — renew it (every minute), 0x01 — give it back (leaving). */
    private fun tokenPacket(kind: Int): ByteArray {
        val p = ByteArray(0x40)
        head(p, control)
        p.putBe32(0x10, 0x40 - 0x10)
        p[0x14] = 0x01
        p[0x15] = kind.toByte()
        p.putBe16(0x16, authSeq++)
        p.putLe16(0x1a, tokRequest)
        p.putLe32(0x1c, token)
        p.putBe16(0x24, 0x0798)
        return p
    }

    private fun head(p: ByteArray, s: Stream) {
        p.putLe32(0, p.size)
        p.putLe32(8, s.myId)
        p.putLe32(0x0c, s.remoteId)
    }

    /**
     * One UDP stream: its handshake, pings, idle packets and the radio's requests to send a packet again. [onPacket]
     * gets every packet after the stream's own handling.
     */
    private inner class Stream(
        private val port: Int,
        private val onPacket: (ByteArray) -> Unit,
        private val socket: DatagramSocket = DatagramSocket(),
        private val isCiv: Boolean = false,
    ) {
        var myId = 0; private set
        @Volatile var remoteId = 0; private set
        @Volatile var connected = false; private set
        private var sendSeq = 1
        private var pingSeq = 0
        private var civSeq = 0
        private val sent = LinkedHashMap<Int, ByteArray>()
        @Volatile private var lastSent = 0L
        @Volatile private var running = true
        private val reader = Thread(::receive, "IcomLAN-$port")
        private val tasks = mutableListOf<java.util.concurrent.ScheduledFuture<*>>()

        fun start() {
            socket.connect(InetSocketAddress(radio, port))
            socket.soTimeout = 250
            val ip = socket.localAddress?.address?.takeIf { it.size == 4 } ?: byteArrayOf(0, 0, 0, 0)
            myId = ((ip[2].toInt() and 0xFF) shl 24) or ((ip[3].toInt() and 0xFF) shl 16) or (socket.localPort and 0xFFFF)
            reader.isDaemon = true
            reader.start()
            // "Are you there?" until the radio answers.
            tasks += timer.scheduleAtFixedRate({ if (!connected) runCatching { sendControl(0x03, seq = 0) } }, 0, 500, TimeUnit.MILLISECONDS)
            tasks += timer.scheduleAtFixedRate({ if (connected) runCatching { ping() } }, 500, 500, TimeUnit.MILLISECONDS)
            tasks += timer.scheduleAtFixedRate({
                if (connected && System.currentTimeMillis() - lastSent >= 100) runCatching { sendTracked(controlPacket(0x00)) }
            }, 100, 100, TimeUnit.MILLISECONDS)
            if (isCiv) tasks += timer.scheduleAtFixedRate({
                // No CI-V for a while (or not yet): ask the radio to open the data again.
                if (remoteId != 0 && System.currentTimeMillis() - civHeardAt > 2000) runCatching { sendOpenClose(open = true) }
            }, 2000, 1000, TimeUnit.MILLISECONDS)
        }

        fun stop() {
            running = false
            tasks.forEach { it.cancel(false) }
            runCatching { socket.close() }
        }

        private fun receive() {
            val buf = ByteArray(2048)
            val packet = DatagramPacket(buf, buf.size)
            while (running) {
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: java.net.PortUnreachableException) {
                    // Nobody at that port yet (ICMP): the same as silence, the questions go on.
                    continue
                } catch (e: Exception) {
                    if (running) fail(tr("Нет связи с трансивером: %s", e.message ?: e.javaClass.simpleName))
                    return
                }
                val p = buf.copyOf(packet.length)
                if (p.size < 0x10) continue
                try {
                    handle(p)
                    onPacket(p)
                } catch (e: Exception) {
                    // A packet of a kind not understood: skipped.
                }
            }
        }

        private fun handle(p: ByteArray) {
            val type = p.u16(4)
            if (p.size == 0x10) when (type) {
                0x04 -> {
                    // "I am here": the radio's id; then "are you ready?"
                    remoteId = p.i32(8)
                    connected = true
                    sendControl(0x06, seq = 1)
                }
                0x06 -> {
                    remoteId = p.i32(8)
                    if (isCiv) {
                        sendOpenClose(open = true)
                        ready.countDown()
                    }
                }
                0x01 -> resend(p.u16(6))
            } else if (p.size == 0x15 && type == 0x07) {
                if (p.u8(0x10) == 0x00) {
                    // The radio's ping: answered with its own number and time.
                    val r = ByteArray(0x15)
                    r.putLe32(0, 0x15)
                    r.putLe16(4, 0x07)
                    r.putLe16(6, p.u16(6))
                    r.putLe32(8, myId)
                    r.putLe32(0x0c, remoteId)
                    r[0x10] = 0x01
                    System.arraycopy(p, 0x11, r, 0x11, 4)
                    raw(r)
                } else if (p.u16(6) == pingSeq) {
                    pingSeq = (pingSeq + 1) and 0xFFFF
                }
            } else if (type == 0x01) {
                // Several packets asked again: their numbers, two bytes each.
                var i = 0x10
                while (i + 1 < p.size) {
                    resend(p.u16(i))
                    i += 2
                }
            }
        }

        private fun resend(seq: Int) {
            val again = synchronized(sent) { sent[seq] }
            if (again != null) raw(again) else sendControl(0x00, seq = seq)
        }

        private fun ping() {
            val p = ByteArray(0x15)
            p.putLe32(0, 0x15)
            p.putLe16(4, 0x07)
            p.putLe16(6, pingSeq)
            p.putLe32(8, myId)
            p.putLe32(0x0c, remoteId)
            p.putLe32(0x11, (System.currentTimeMillis() % 86_400_000L).toInt())
            raw(p)
        }

        fun controlPacket(type: Int): ByteArray {
            val p = ByteArray(0x10)
            p.putLe32(0, 0x10)
            p.putLe16(4, type)
            p.putLe32(8, myId)
            p.putLe32(0x0c, remoteId)
            return p
        }

        /** An untracked control packet ("are you there", "are you ready", "goodbye"). */
        fun sendControl(type: Int, seq: Int = 0) {
            val p = controlPacket(type)
            p.putLe16(6, seq)
            raw(p)
        }

        /** A packet with the next sequence number, kept so the radio can ask for it again. */
        fun sendTracked(p: ByteArray) {
            synchronized(sent) {
                if (sendSeq == 0) sent.clear()
                p.putLe16(6, sendSeq)
                // The id the radio gave this stream (it may come after the packet was built).
                p.putLe32(8, myId)
                p.putLe32(0x0c, remoteId)
                sent[sendSeq] = p
                if (sent.size > 300) sent.remove(sent.keys.first())
                sendSeq = (sendSeq + 1) and 0xFFFF
            }
            raw(p)
        }

        fun sendCiv(b: ByteArray) {
            val p = ByteArray(0x15 + b.size)
            p.putLe32(0, p.size)
            p[0x10] = 0xC1.toByte()
            p.putLe16(0x11, b.size)
            synchronized(sent) { p.putBe16(0x13, civSeq); civSeq = (civSeq + 1) and 0xFFFF }
            System.arraycopy(b, 0, p, 0x15, b.size)
            sendTracked(p)
        }

        fun sendOpenClose(open: Boolean) {
            val p = ByteArray(0x16)
            p.putLe32(0, 0x16)
            p.putLe16(0x10, 0x01C0)
            synchronized(sent) { p.putBe16(0x13, civSeq); civSeq = (civSeq + 1) and 0xFFFF }
            p[0x15] = if (open) 0x04 else 0x00
            sendTracked(p)
        }

        private fun raw(p: ByteArray) {
            if (!running) return
            lastSent = System.currentTimeMillis()
            socket.send(DatagramPacket(p, p.size))
        }
    }

    companion object {
        private const val CAPS_HEAD = 0x42
        private const val RADIO_CAP = 0x66
        /** The login answer's error field when the name or the password is wrong. */
        private const val LOGIN_REFUSED = 0xFEFFFFFF.toInt()

        /** Icom's table for the user name and password: a character and its position pick a byte. */
        private val SEQUENCE = intArrayOf(
            0x47, 0x5d, 0x4c, 0x42, 0x66, 0x20, 0x23, 0x46, 0x4e, 0x57, 0x45, 0x3d, 0x67, 0x76, 0x60, 0x41, 0x62, 0x39, 0x59, 0x2d, 0x68, 0x7e,
            0x7c, 0x65, 0x7d, 0x49, 0x29, 0x72, 0x73, 0x78, 0x21, 0x6e, 0x5a, 0x5e, 0x4a, 0x3e, 0x71, 0x2c, 0x2a, 0x54, 0x3c, 0x3a, 0x63, 0x4f,
            0x43, 0x75, 0x27, 0x79, 0x5b, 0x35, 0x70, 0x48, 0x6b, 0x56, 0x6f, 0x34, 0x32, 0x6c, 0x30, 0x61, 0x6d, 0x7b, 0x2f, 0x4b, 0x64, 0x38,
            0x2b, 0x2e, 0x50, 0x40, 0x3f, 0x55, 0x33, 0x37, 0x25, 0x77, 0x24, 0x26, 0x74, 0x6a, 0x28, 0x53, 0x4d, 0x69, 0x22, 0x5c, 0x44, 0x31,
            0x36, 0x58, 0x3b, 0x7a, 0x51, 0x5f, 0x52,
        )

        /** The scrambled form of a user name or password (up to 16 characters) as the radio expects it. */
        fun passcode(s: String): ByteArray {
            val b = s.toByteArray(Charsets.ISO_8859_1)
            return ByteArray(minOf(b.size, 16)) { i ->
                var p = (b[i].toInt() and 0xFF) + i
                if (p > 126) p = 32 + p % 127
                (if (p in 32..126) SEQUENCE[p - 32] else 0).toByte()
            }
        }
    }
}

private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
private fun ByteArray.u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
private fun ByteArray.i32(i: Int) = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16) or (u8(i + 3) shl 24)
private fun ByteArray.u32(i: Int) = i32(i).toLong() and 0xFFFFFFFFL
private fun ByteArray.be16(i: Int) = (u8(i) shl 8) or u8(i + 1)
private fun ByteArray.cString(at: Int, max: Int): String {
    val end = (at until minOf(at + max, size)).firstOrNull { this[it] == 0.toByte() } ?: minOf(at + max, size)
    return String(this, at, end - at, Charsets.ISO_8859_1).trim()
}
private fun ByteArray.putLe16(i: Int, v: Int) { this[i] = v.toByte(); this[i + 1] = (v shr 8).toByte() }
private fun ByteArray.putLe32(i: Int, v: Int) { putLe16(i, v); putLe16(i + 2, v shr 16) }
private fun ByteArray.putBe16(i: Int, v: Int) { this[i] = (v shr 8).toByte(); this[i + 1] = v.toByte() }
private fun ByteArray.putBe32(i: Int, v: Int) { putBe16(i, v shr 16); putBe16(i + 2, v) }
