package ru.r3xed.qsolog.cat

import ru.r3xed.qsolog.tr
import java.io.Closeable
import java.io.IOException

/** A byte pipe to the radio: a USB or COM serial port, a Bluetooth socket, a TCP connection. */
interface CatLink : Closeable {
    fun write(b: ByteArray)

    /** Reads what has come into [buf], waiting up to [timeoutMs]: 0 when nothing came; throws when the link is gone. */
    fun read(buf: ByteArray, timeoutMs: Int): Int
}

/** Collects the bytes from a [CatLink] and cuts them into messages. */
class CatIo(private val link: CatLink, private val clock: () -> Long = System::currentTimeMillis) {
    private var pending = ByteArray(0)
    private val chunk = ByteArray(512)

    fun send(b: ByteArray) = link.write(b)

    /** Drops whatever came earlier (answers to commands that timed out). */
    fun clear() {
        pending = ByteArray(0)
        while (link.read(chunk, 1) > 0) Unit
    }

    /**
     * The next message. [end] looks at the bytes so far: a positive number — a whole message of that length is at the
     * start; negative — that many bytes are junk; 0 — more bytes are needed. Null when [timeoutMs] runs out.
     */
    fun next(timeoutMs: Int, end: (ByteArray) -> Int): ByteArray? {
        val deadline = clock() + timeoutMs
        while (true) {
            while (pending.isNotEmpty()) {
                val n = end(pending)
                if (n > 0) {
                    val m = pending.copyOf(n)
                    pending = pending.copyOfRange(n, pending.size)
                    return m
                }
                if (n == 0) break
                pending = pending.copyOfRange(minOf(-n, pending.size), pending.size)
            }
            val left = deadline - clock()
            if (left <= 0) return null
            val r = link.read(chunk, left.toInt().coerceAtLeast(1))
            if (r < 0) throw IOException("closed")
            if (r > 0) pending += chunk.copyOf(r)
        }
    }

    /** A text answer up to and including [stop]. */
    fun line(timeoutMs: Int, stop: Char): String? =
        next(timeoutMs) { b -> b.indexOf(stop.code.toByte()).let { if (it < 0) (if (b.size > 256) -b.size else 0) else it + 1 } }
            ?.toString(Charsets.US_ASCII)
}

/** Polls one kind of radio. */
interface RigDriver {
    /** The radio's state now, building on [last]; fields the radio did not answer stay as they were. */
    fun poll(io: CatIo, last: RigState): RigState

    /** A short description for the status line ("IC-7300 · 94h"). */
    val what: String

    /** Whether the last [poll] got any answer. */
    val answered: Boolean

    /** Tunes the radio to [hz]. False when the radio refused (or this protocol cannot). */
    fun setFreq(io: CatIo, hz: Long): Boolean

    /** Switches the radio to [mode] (see [RadioMode]). */
    fun setMode(io: CatIo, mode: RadioMode): Boolean

    /** Transmit on or off (CAT PTT). */
    fun setPtt(io: CatIo, on: Boolean): Boolean

    companion object {
        fun of(c: CatConfig, sMeter: Boolean = true): RigDriver = when (c.protocol) {
            CatProtocol.ICOM -> CivDriver(c.civAddress, true, c.model.name, sMeter)
            CatProtocol.KENWOOD -> KenwoodDriver(c.model.name, c.model.elecraft, sMeter)
            CatProtocol.YAESU -> YaesuDriver(c.model.name, sMeter)
            CatProtocol.YAESU_OLD -> YaesuOldDriver(c.model.name, sMeter)
            CatProtocol.RIGCTLD -> RigctldDriver(sMeter)
            CatProtocol.COMMANDER -> CommanderDriver()
            CatProtocol.OMNIRIG -> OmniRigDriver(c.omniRig)
        }
    }
}

private const val TIMEOUT = 300

/**
 * Icom CI-V. Each command is a frame FE FE <radio> <E0> <cmd> [sub] FD; the radio answers FE FE E0 <radio> … FD.
 * On a one-wire CI-V cable (CT-17) the app hears its own frames back — those come from E0 and are skipped.
 * With "CI-V Transceive" on the radio also sends frames to 00 when the dial turns: those update the state too.
 * When the set address gets no answer the factory addresses are tried in turn, and the one that answers is kept.
 */
class CivDriver(
    address: Int,
    private val probe: Boolean = true,
    private val name: String = "ICOM",
    private val sMeter: Boolean = true,
    private val controller: Int = 0xE0,
) : RigDriver {
    var address = address; private set
    private var misses = 0
    private var probeAt = -1
    private var dataSupported = true
    private var cycle = 0
    override var answered = false; private set
    override val what get() = "$name · %02Xh".format(address)

    private var state = RigState()
    private var dataOn = false
    private var baseMode: String? = null

    /** The radio answers 1C 00 (transmitting or not); radios that refuse it once are not asked again. */
    private var txSupported = true

    override fun poll(io: CatIo, last: RigState): RigState {
        state = last
        answered = false
        val gotFreq = ask(io, 0x03)
        if (gotFreq) {
            misses = 0
            ask(io, 0x04)
            if (dataSupported && cycle % 2 == 0) ask(io, 0x1A, 0x06)
            if (txSupported && !ask(io, 0x1C, 0x00)) txSupported = false
            if (state.tx == true) {
                // On transmit the meters that matter: output power, SWR, ALC.
                ask(io, 0x15, 0x11)
                ask(io, 0x15, 0x12)
                ask(io, 0x15, 0x13)
            } else {
                if (state.powerPct != null) state = state.copy(powerPct = null, swr = null, alcPct = null)
                if (sMeter) ask(io, 0x15, 0x02)
            }
        } else if (probe && ++misses >= 3) {
            // Nobody at this address: next poll tries the next factory address.
            probeAt = (probeAt + 1) % Rigs.CIV_PROBE.size
            address = Rigs.CIV_PROBE[probeAt]
            misses = 0
        }
        cycle++
        return state
    }

    private fun ask(io: CatIo, cmd: Int, sub: Int? = null): Boolean {
        io.send(frame(address, controller, cmd, sub))
        while (true) {
            val f = io.next(TIMEOUT, ::civEnd) ?: return false
            if (f.size < 6) continue
            val to = f[2].u
            val from = f[3].u
            val c = f[4].u
            if (from == controller) continue // our own echo
            if (to != controller && to != 0x00) continue // talk to another device on the bus
            if (to == controller && from != address) address = from // the radio answered from another address
            answered = true
            take(f)
            if (c == 0xFA) {
                if (cmd == 0x1A) dataSupported = false
                return cmd != 0x03 && cmd != 0x1C
            }
            if (c == cmd && (sub == null || (f.size > 6 && f[5].u == sub))) return true
        }
    }

    private fun take(f: ByteArray) {
        val c = f[4].u
        val body = f.copyOfRange(5, f.size - 1)
        when (c) {
            0x00, 0x03 -> if (body.size >= 4) state = state.copy(freqHz = civFreq(body), at = System.currentTimeMillis())
            0x01, 0x04 -> if (body.isNotEmpty()) {
                baseMode = CIV_MODES[body[0].u]
                state = state.copy(radioMode = mode())
            }
            0x1A -> if (body.size >= 2 && body[0].u == 0x06) {
                dataOn = body[1].u != 0
                state = state.copy(radioMode = mode())
            }
            0x15 -> if (body.size >= 3) {
                val v = bcd(body[1]) * 100 + bcd(body[2])
                state = when (body[0].u) {
                    0x02 -> state.copy(sDb = CatFormat.scale(v, 120, 241))
                    // IC-7300 manual: 0143 = 50%, 0213 = 100% power; SWR 0048 = 1.5, 0080 = 2.0, 0120 = 3.0; ALC 0120 = top of the zone.
                    0x11 -> state.copy(powerPct = (if (v <= 143) v * 50 / 143 else 50 + (v - 143) * 50 / 70).coerceIn(0, 120))
                    0x12 -> state.copy(swr = civSwr(v))
                    0x13 -> state.copy(alcPct = (v * 100 / 120).coerceIn(0, 200))
                    else -> state
                }
            }
            0x1C -> if (body.size >= 2 && body[0].u == 0x00) state = state.copy(tx = body[1].u != 0)
        }
    }

    private fun mode(): String? = baseMode?.let { if (dataOn && it in setOf("LSB", "USB", "AM", "FM")) "$it-D" else it }

    override fun setFreq(io: CatIo, hz: Long): Boolean = command(io, 0x05, null, civFreqBytes(hz))

    override fun setMode(io: CatIo, mode: RadioMode): Boolean {
        val m = when (mode) {
            RadioMode.LSB -> 0x00
            RadioMode.USB, RadioMode.DATA -> 0x01
            RadioMode.AM -> 0x02
            RadioMode.CW -> 0x03
            RadioMode.RTTY -> 0x04
            RadioMode.FM -> 0x05
        }
        val ok = command(io, 0x06, null, byteArrayOf(m.toByte()))
        // Data mode (USB-D) is a switch of its own; radios without it (Xiegu, old Icoms) answer FA once and are not asked again.
        if (ok && dataSupported && (mode == RadioMode.DATA || dataOn)) {
            val on = if (mode == RadioMode.DATA) 1 else 0
            if (!command(io, 0x1A, 0x06, byteArrayOf(on.toByte(), on.toByte()))) dataSupported = false
        }
        return ok
    }

    /** 1C 00 01 — transmit, 1C 00 00 — receive. */
    override fun setPtt(io: CatIo, on: Boolean): Boolean = command(io, 0x1C, 0x00, byteArrayOf(if (on) 1 else 0))

    /** A command that changes something: the radio answers FB (done) or FA (refused). */
    private fun command(io: CatIo, cmd: Int, sub: Int?, data: ByteArray): Boolean {
        io.send(frame(address, controller, cmd, sub, data))
        while (true) {
            val f = io.next(TIMEOUT, ::civEnd) ?: return false
            if (f.size < 6) continue
            val to = f[2].u
            val from = f[3].u
            if (from == controller) continue
            if (to != controller && to != 0x00) continue
            answered = true
            when (f[4].u) {
                0xFB -> return true
                0xFA -> return false
                else -> take(f)
            }
        }
    }

    companion object {
        val CIV_MODES = mapOf(
            0x00 to "LSB", 0x01 to "USB", 0x02 to "AM", 0x03 to "CW", 0x04 to "RTTY", 0x05 to "FM", 0x06 to "WFM",
            0x07 to "CW-R", 0x08 to "RTTY-R", 0x17 to "DV", 0x22 to "DD",
        )

        fun frame(to: Int, from: Int, cmd: Int, sub: Int? = null, data: ByteArray = ByteArray(0)): ByteArray =
            byteArrayOf(0xFE.toByte(), 0xFE.toByte(), to.toByte(), from.toByte(), cmd.toByte()) +
                (if (sub != null) byteArrayOf(sub.toByte()) else ByteArray(0)) + data + byteArrayOf(0xFD.toByte())

        /** Frequency of a CI-V frame: BCD, the lowest two digits first. */
        fun civFreq(b: ByteArray): Long {
            var f = 0L
            var mul = 1L
            for (x in b) {
                f += bcd(x) * mul
                mul *= 100
            }
            return f
        }

        fun civFreqBytes(hz: Long, n: Int = 5): ByteArray {
            var v = hz
            return ByteArray(n) {
                val two = (v % 100).toInt()
                v /= 100
                ((two / 10) shl 4 or (two % 10)).toByte()
            }
        }

        /** Length of the CI-V frame at the start of [b] (see [CatIo.next]). */
        fun civEnd(b: ByteArray): Int {
            val fe = 0xFE.toByte()
            if (b[0] != fe) return -1
            if (b.size < 2) return 0
            if (b[1] != fe) return -1
            if (b.size > 2 && b[2] == fe) return -1 // a longer preamble
            val fd = b.indexOf(0xFD.toByte())
            if (fd < 0) return if (b.size > 64) -1 else 0
            val next = (2 until fd).firstOrNull { b[it] == fe }
            if (next != null) return -next // a frame broken by a collision, a new one starts inside
            return fd + 1
        }

        private fun bcd(b: Byte): Int = (b.u shr 4) * 10 + (b.u and 0x0F)

        /** The SWR meter's 0–255 as SWR: 0 → 1.0, 48 → 1.5, 80 → 2.0, 120 → 3.0, then up to ∞. */
        fun civSwr(v: Int): Double = when {
            v <= 48 -> 1.0 + v * 0.5 / 48
            v <= 80 -> 1.5 + (v - 48) * 0.5 / 32
            v <= 120 -> 2.0 + (v - 80) * 1.0 / 40
            else -> (3.0 + (v - 120) * 7.0 / 135).coerceAtMost(10.0)
        }
    }
}

private val Byte.u get() = toInt() and 0xFF

/** Kenwood, Elecraft, FlexRadio: "FA;" → "FA00014074000;", "MD;" → "MD2;", "SM0;" → "SM00015;". */
class KenwoodDriver(private val name: String, private val elecraft: Boolean, private val sMeter: Boolean = true) : RigDriver {
    override var answered = false; private set
    override val what get() = name
    private var smCmd = if (elecraft) "SM;" else "SM0;"
    private var ifSupported = true

    override fun poll(io: CatIo, last: RigState): RigState {
        var s = last
        answered = false
        ask(io, "FA;")?.let { r -> digits(r.removePrefix("FA"))?.let { s = s.copy(freqHz = it, at = System.currentTimeMillis()) } }
        if (!answered) return s
        ask(io, "MD;")?.let { r -> r.getOrNull(2)?.let { c -> s = s.copy(radioMode = (if (elecraft) ELECRAFT_MODES else MODES)[c] ?: s.radioMode) } }
        // IF: the 29th character (after "IF", 11 digits of frequency, 5 spaces, RIT, flags, memory) is 1 on transmit.
        if (ifSupported) ask(io, "IF;").let { r ->
            if (r == null || r.length < 29) ifSupported = r != null && r.length >= 29 else s = s.copy(tx = r[28] == '1')
        }
        if (sMeter && s.tx != true) {
            val r = ask(io, smCmd)
            if (r == null && smCmd == "SM0;") smCmd = "SM;"
            r?.removePrefix("SM")?.takeLast(4)?.toIntOrNull()?.let { v ->
                s = s.copy(sDb = if (elecraft) CatFormat.scale(v, 9, 21) else CatFormat.scale(v, 15, 30))
            }
        }
        return s
    }

    private fun ask(io: CatIo, cmd: String): String? {
        io.send(cmd.toByteArray(Charsets.US_ASCII))
        val head = cmd.take(2)
        while (true) {
            val r = io.line(TIMEOUT, ';')?.trim() ?: return null
            if (r == "?;" || r == "E;" || r == "O;") return null.also { answered = true }
            if (r.startsWith(head)) return r.removeSuffix(";").also { answered = true }
            // Auto-information from the radio ("IF…;") — not what was asked.
        }
    }

    // Kenwood radios do not answer a command that sets something; a refused one comes as "?;" and is dropped by the next poll.
    override fun setFreq(io: CatIo, hz: Long): Boolean {
        io.send("FA%011d;".format(hz).toByteArray(Charsets.US_ASCII))
        return true
    }

    override fun setMode(io: CatIo, mode: RadioMode): Boolean {
        val c = when (mode) {
            RadioMode.LSB -> '1'
            RadioMode.USB -> '2'
            RadioMode.CW -> '3'
            RadioMode.FM -> '4'
            RadioMode.AM -> '5'
            RadioMode.RTTY -> '6'
            RadioMode.DATA -> if (elecraft) '6' else '2'
        }
        io.send("MD$c;".toByteArray(Charsets.US_ASCII))
        // TS-590, TS-890, TS-990: USB with the data switch on (DA1); radios without it answer "?;", which is dropped.
        if (!elecraft && (c == '1' || c == '2')) io.send("DA${if (mode == RadioMode.DATA) 1 else 0};".toByteArray(Charsets.US_ASCII))
        return true
    }

    override fun setPtt(io: CatIo, on: Boolean): Boolean {
        io.send((if (on) "TX;" else "RX;").toByteArray(Charsets.US_ASCII))
        return true
    }

    companion object {
        val MODES = mapOf('1' to "LSB", '2' to "USB", '3' to "CW", '4' to "FM", '5' to "AM", '6' to "FSK", '7' to "CW-R", '9' to "FSK-R")
        val ELECRAFT_MODES = mapOf('1' to "LSB", '2' to "USB", '3' to "CW", '4' to "FM", '5' to "AM", '6' to "DATA", '7' to "CW-R", '9' to "DATA-R")
    }
}

/** Yaesu's text CAT: "FA;" → "FA014074000;", "MD0;" → "MD02;", "SM0;" → "SM0123;". */
class YaesuDriver(private val name: String, private val sMeter: Boolean = true) : RigDriver {
    override var answered = false; private set
    override val what get() = name

    override fun poll(io: CatIo, last: RigState): RigState {
        var s = last
        answered = false
        ask(io, "FA;")?.let { r ->
            val d = r.removePrefix("FA")
            digits(d)?.let { s = s.copy(freqHz = it, at = System.currentTimeMillis()); faDigits = d.length }
        }
        if (!answered) return s
        ask(io, "MD0;")?.let { r -> r.getOrNull(3)?.let { c -> s = s.copy(radioMode = MODES[c.uppercaseChar()] ?: s.radioMode) } }
        // "TX;" → TX0 receive, TX1 (PTT) or TX2 (CAT) transmit.
        if (txSupported) ask(io, "TX;").let { r ->
            val c = r?.getOrNull(2)
            if (c == null) txSupported = false else s = s.copy(tx = c != '0')
        }
        if (sMeter && s.tx != true) ask(io, "SM0;")?.removePrefix("SM0")?.toIntOrNull()?.let { v -> s = s.copy(sDb = CatFormat.scale(v, 130, 255)) }
        return s
    }

    private fun ask(io: CatIo, cmd: String): String? {
        io.send(cmd.toByteArray(Charsets.US_ASCII))
        val head = cmd.take(2)
        while (true) {
            val r = io.line(TIMEOUT, ';')?.trim() ?: return null
            if (r == "?;") return null.also { answered = true }
            if (r.startsWith(head)) return r.removeSuffix(";").also { answered = true }
        }
    }

    private var txSupported = true

    /** FT-991, FT-891, FT-710, FTDX10, FTDX101 send the frequency in 9 digits, FT-950, FT-2000, FT-450 in 8: set it the same way. */
    private var faDigits = 9

    override fun setFreq(io: CatIo, hz: Long): Boolean {
        io.send(("FA" + hz.toString().padStart(faDigits, '0') + ";").toByteArray(Charsets.US_ASCII))
        return true
    }

    override fun setMode(io: CatIo, mode: RadioMode): Boolean {
        val c = when (mode) {
            RadioMode.LSB -> '1'
            RadioMode.USB -> '2'
            RadioMode.CW -> '3'
            RadioMode.FM -> '4'
            RadioMode.AM -> '5'
            RadioMode.RTTY -> '6'
            RadioMode.DATA -> 'C'
        }
        io.send("MD0$c;".toByteArray(Charsets.US_ASCII))
        return true
    }

    /** TX1 — transmit by CAT, TX0 — receive. */
    override fun setPtt(io: CatIo, on: Boolean): Boolean {
        io.send((if (on) "TX1;" else "TX0;").toByteArray(Charsets.US_ASCII))
        return true
    }

    companion object {
        val MODES = mapOf(
            '1' to "LSB", '2' to "USB", '3' to "CW", '4' to "FM", '5' to "AM", '6' to "RTTY-L", '7' to "CW-R",
            '8' to "DATA-L", '9' to "RTTY", 'A' to "DATA-FM", 'B' to "FM-N", 'C' to "DATA-U", 'D' to "AM-N",
            'E' to "PSK", 'F' to "DATA-FM",
        )
    }
}

/**
 * FT-817/818/857/897: five-byte commands. 03 → 4 BCD bytes of the frequency in tens of hertz (highest first) and the
 * mode byte; E7 → the receiver status byte, its low 4 bits the S-meter (0–9 — S0–S9, 10–15 — S9+10…+60).
 */
class YaesuOldDriver(private val name: String, private val sMeter: Boolean = true) : RigDriver {
    override var answered = false; private set
    override val what get() = name

    override fun poll(io: CatIo, last: RigState): RigState {
        var s = last
        answered = false
        io.clear()
        io.send(byteArrayOf(0, 0, 0, 0, 0x03))
        val r = io.next(TIMEOUT) { b -> if (b.size >= 5) 5 else 0 } ?: return s
        answered = true
        var f = 0L
        for (i in 0 until 4) f = f * 100 + (r[i].u shr 4) * 10 + (r[i].u and 0x0F)
        s = s.copy(freqHz = f * 10, radioMode = MODES[r[4].u] ?: s.radioMode, at = System.currentTimeMillis())
        if (sMeter) {
            io.send(byteArrayOf(0, 0, 0, 0, 0xE7.toByte()))
            io.next(TIMEOUT) { b -> 1 }?.let { b ->
                val v = b[0].u and 0x0F
                s = s.copy(sDb = if (v <= 9) v * 6 else 54 + (v - 9) * 10)
            }
        }
        return s
    }

    /** 01: four BCD bytes of the frequency in tens of hertz, the highest first. */
    override fun setFreq(io: CatIo, hz: Long): Boolean {
        var v = hz / 10
        val b = ByteArray(4)
        for (i in 3 downTo 0) {
            val two = (v % 100).toInt()
            v /= 100
            b[i] = ((two / 10) shl 4 or (two % 10)).toByte()
        }
        io.send(b + byteArrayOf(0x01))
        // The radio answers some commands with one byte: dropped so that it is not taken for the next poll's answer.
        io.next(100) { 1 }
        return true
    }

    override fun setMode(io: CatIo, mode: RadioMode): Boolean {
        val m = when (mode) {
            RadioMode.LSB -> 0x00
            RadioMode.USB -> 0x01
            RadioMode.CW -> 0x02
            RadioMode.AM -> 0x04
            RadioMode.FM -> 0x08
            RadioMode.RTTY, RadioMode.DATA -> 0x0A
        }
        io.send(byteArrayOf(m.toByte(), 0, 0, 0, 0x07))
        io.next(100) { 1 }
        return true
    }

    /** 08 — PTT on, 88 — PTT off. */
    override fun setPtt(io: CatIo, on: Boolean): Boolean {
        io.send(byteArrayOf(0, 0, 0, 0, (if (on) 0x08 else 0x88).toByte()))
        io.next(100) { 1 }
        return true
    }

    companion object {
        val MODES = mapOf(
            0x00 to "LSB", 0x01 to "USB", 0x02 to "CW", 0x03 to "CW-R", 0x04 to "AM", 0x06 to "WFM", 0x08 to "FM",
            0x0A to "DIG", 0x0C to "PKT", 0x82 to "CW", 0x83 to "CW-R", 0x88 to "FM-N", 0x8A to "DIG",
        )
    }
}

/** Hamlib rigctld: "f" → "14074000", "m" → "USB" and the passband, "l STRENGTH" → dB over or under S9. */
class RigctldDriver(private val sMeter: Boolean = true) : RigDriver {
    override var answered = false; private set
    override val what get() = "rigctld"

    override fun poll(io: CatIo, last: RigState): RigState {
        var s = last
        answered = false
        // rigctld's answers carry no name: a late one would be taken for the answer to the next question, and every
        // poll after it would be one line behind. Whatever came late is dropped first.
        io.clear()
        ask(io, "f", 1)?.firstOrNull()?.trim()?.toDoubleOrNull()?.let { s = s.copy(freqHz = it.toLong(), at = System.currentTimeMillis()) }
        if (!answered) return s
        ask(io, "m", 2)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { s = s.copy(radioMode = it) }
        if ("t" in supported) ask(io, "t", 1)?.firstOrNull()?.trim()?.toIntOrNull().let { v ->
            if (v != null) s = s.copy(tx = v != 0) else if (refused) supported -= "t"
        }
        if (s.tx == true) {
            s = s.copy(
                powerPct = level(io, "RFPOWER_METER")?.let { (it * 100).toInt() },
                swr = level(io, "SWR")?.takeIf { it >= 1.0 },
                alcPct = level(io, "ALC")?.let { (it * 100).toInt() },
            )
        } else {
            if (s.powerPct != null || s.swr != null) s = s.copy(powerPct = null, swr = null, alcPct = null)
            if (sMeter) ask(io, "l STRENGTH", 1)?.firstOrNull()?.trim()?.toIntOrNull()?.let { s = s.copy(sDb = (54 + it).coerceAtLeast(0)) }
        }
        return s
    }

    /** What this rigctld answers; a command it refuses once is not sent again. */
    private val supported = mutableSetOf("t", "RFPOWER_METER", "SWR", "ALC")

    /** "l NAME": a level as a number, or null (and not asked again) when the radio has no such meter. */
    private fun level(io: CatIo, name: String): Double? {
        if (name !in supported) return null
        val v = ask(io, "l $name", 1)?.firstOrNull()?.trim()?.toDoubleOrNull()
        // Only a refusal ("RPRT -11") means the radio has no such meter; a late answer is just late.
        if (v == null && refused) supported -= name
        return v
    }

    override fun setFreq(io: CatIo, hz: Long): Boolean = report(io, "F $hz")

    override fun setPtt(io: CatIo, on: Boolean): Boolean = report(io, "T " + if (on) 1 else 0)

    override fun setMode(io: CatIo, mode: RadioMode): Boolean = report(io, "M " + when (mode) {
        RadioMode.DATA -> "PKTUSB"
        else -> mode.name
    } + " 0")

    /** A command that sets something: rigctld answers "RPRT 0" when done, "RPRT -n" when not. */
    private fun report(io: CatIo, cmd: String): Boolean {
        io.send("$cmd\n".toByteArray(Charsets.US_ASCII))
        val l = io.line(TIMEOUT, '\n')?.trim() ?: return false
        answered = true
        return l == "RPRT 0"
    }

    /** The last [ask] got "RPRT -n" instead of an answer. */
    private var refused = false

    private fun ask(io: CatIo, cmd: String, lines: Int): List<String>? {
        refused = false
        io.send("$cmd\n".toByteArray(Charsets.US_ASCII))
        val out = mutableListOf<String>()
        while (out.size < lines) {
            val l = io.line(TIMEOUT, '\n')?.trimEnd('\r', '\n') ?: return null
            answered = true
            if (l.startsWith("RPRT")) return null.also { refused = true }
            out += l
        }
        return out
    }
}

/**
 * DXLab Commander's TCP directives (port 52002): "<command:10>CmdGetFreq<parameters:0>" → "<CmdFreq:10>14,074.000"
 * (kHz), "CmdSendMode" → "<CmdMode:5>DATA-U", "CmdSendTX" → "<CmdTX:2>ON". The set directives get no answer. Commander
 * reads the frequency it is given with the decimal separator of its Windows: that is learned from "CmdSendFreq",
 * which answers in the same local format.
 */
class CommanderDriver : RigDriver {
    override var answered = false; private set
    override val what get() = "DXLab Commander"

    /** The decimal separator of Commander's computer; null until "CmdSendFreq" has answered. */
    private var decimal: Char? = null
    private var txSupported = true

    override fun poll(io: CatIo, last: RigState): RigState {
        var s = last
        answered = false
        io.clear()
        if (decimal == null) ask(io, "CmdSendFreq", "CmdFreq")?.let { v -> decimal = v.lastOrNull { it == ',' || it == '.' } ?: '.' }
        ask(io, "CmdGetFreq", "CmdFreq")?.let { khz(it) }?.takeIf { it > 0 }?.let { s = s.copy(freqHz = it, at = System.currentTimeMillis()) }
        if (!answered) return s
        ask(io, "CmdSendMode", "CmdMode")?.trim()?.takeIf { it.isNotEmpty() }?.let { s = s.copy(radioMode = it) }
        if (txSupported) ask(io, "CmdSendTX", "CmdTX")?.trim()?.uppercase().let { v ->
            when (v) {
                "ON" -> s = s.copy(tx = true)
                "OFF" -> s = s.copy(tx = false)
                // Not all radios tell Commander; it then stays silent and is not asked again.
                else -> txSupported = false
            }
        }
        return s
    }

    override fun setFreq(io: CatIo, hz: Long): Boolean {
        val d = decimal ?: '.'
        val khz = "%d%c%03d".format(hz / 1000, d, hz % 1000)
        send(io, "CmdSetFreq", field("xcvrfreq", khz))
        return true
    }

    override fun setMode(io: CatIo, mode: RadioMode): Boolean {
        send(io, "CmdSetMode", field("1", if (mode == RadioMode.DATA) "DATA-U" else mode.name))
        return true
    }

    override fun setPtt(io: CatIo, on: Boolean): Boolean {
        send(io, if (on) "CmdTX" else "CmdRX", "")
        return true
    }

    private fun send(io: CatIo, cmd: String, params: String) =
        io.send((field("command", cmd) + field("parameters", params)).toByteArray(Charsets.UTF_8))

    /** Sends [cmd] and waits for the field [answer]; its value, or null when none came. */
    private fun ask(io: CatIo, cmd: String, answer: String): String? {
        send(io, cmd, "")
        while (true) {
            val m = io.next(TIMEOUT, ::fieldEnd) ?: return null
            answered = true
            // Commander writes in its Windows code page: one byte a character (the no-break space of "14 074,000" too).
            val text = m.toString(Charsets.ISO_8859_1)
            val name = text.substring(1, text.indexOf(':'))
            if (name.equals(answer, ignoreCase = true)) return text.substring(text.indexOf('>') + 1)
        }
    }

    companion object {
        const val DEFAULT_PORT = 52002

        fun field(name: String, value: String) = "<$name:${value.length}>$value"

        /** "14,074.000", "14 074,000", "14.074,000" (kHz) → Hz: the last separator is the decimal one. */
        fun khz(v: String): Long? {
            val t = v.trim()
            val at = t.indexOfLast { it == ',' || it == '.' }
            val whole = (if (at < 0) t else t.substring(0, at)).filter(Char::isDigit)
            val frac = if (at < 0) "" else t.substring(at + 1).filter(Char::isDigit)
            if (whole.isEmpty() && frac.isEmpty()) return null
            return (whole.ifEmpty { "0" }.toLong() * 1000) + (frac + "000").take(3).toLong()
        }

        /** The length of one "<NAME:LEN>VALUE" at the start of [b]: 0 — more bytes needed, negative — junk to skip. */
        fun fieldEnd(b: ByteArray): Int {
            val lt = b.indexOf('<'.code.toByte())
            if (lt < 0) return -b.size
            if (lt > 0) return -lt
            val gt = b.indexOf('>'.code.toByte())
            if (gt < 0) return if (b.size > 64) -1 else 0
            val head = b.copyOfRange(1, gt).toString(Charsets.US_ASCII)
            val len = head.substringAfter(':', "").substringBefore(':').trim().toIntOrNull() ?: return -1
            val end = gt + 1 + len
            return if (b.size >= end) end else 0
        }
    }
}

private fun digits(s: String): Long? = s.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()

/**
 * OmniRig (Windows): the radio is held by OmniRig's COM server, and QSO-LOG reaches it through a PowerShell helper
 * ([SCRIPT]) speaking lines: "poll" → "S <status> <freq> <mode bits> <tx bits> <status text>", "freq N", "mode BITS",
 * "tx 1|0" → "OK". Status 4 is "on line"; the mode and transmit are OmniRig's PM_* bits.
 */
class OmniRigDriver(private val rig: Int = 1) : RigDriver {
    override var answered = false; private set
    private var status: String? = null
    override val what get() = "OmniRig · Rig $rig" + (status?.let { " · $it" } ?: "")

    override fun poll(io: CatIo, last: RigState): RigState {
        var s = last
        answered = false
        io.clear()
        val r = ask(io, "poll") ?: return s
        val p = r.split(' ', limit = 6)
        if (p.size < 5 || p[0] != "S") return s
        val online = p[1] == "4"
        status = if (online) null else p.getOrNull(5)?.trim()?.ifBlank { null } ?: tr("трансивер не отвечает")
        if (!online) return s
        answered = true
        p[2].toLongOrNull()?.takeIf { it > 0 }?.let { s = s.copy(freqHz = it, at = System.currentTimeMillis()) }
        p[3].toLongOrNull()?.let { m -> modeName(m)?.let { s = s.copy(radioMode = it) } }
        p[4].toLongOrNull()?.let { t -> if (t and (PM_TX or PM_RX) != 0L) s = s.copy(tx = t and PM_TX != 0L) }
        return s
    }

    override fun setFreq(io: CatIo, hz: Long): Boolean = ask(io, "freq $hz") == "OK"

    override fun setMode(io: CatIo, mode: RadioMode): Boolean = ask(io, "mode " + when (mode) {
        RadioMode.LSB -> PM_SSB_L
        RadioMode.USB -> PM_SSB_U
        RadioMode.CW -> PM_CW_U
        RadioMode.AM -> PM_AM
        RadioMode.FM -> PM_FM
        RadioMode.RTTY, RadioMode.DATA -> PM_DIG_U
    }) == "OK"

    override fun setPtt(io: CatIo, on: Boolean): Boolean = ask(io, "tx " + if (on) 1 else 0) == "OK"

    private fun ask(io: CatIo, cmd: String): String? {
        io.send("$cmd\n".toByteArray(Charsets.US_ASCII))
        // OmniRig answers from its own process: slower than a radio's port.
        return io.line(1500, '\n')?.trim()
    }

    companion object {
        const val PM_CW_U = 0x00800000L
        const val PM_CW_L = 0x01000000L
        const val PM_SSB_U = 0x02000000L
        const val PM_SSB_L = 0x04000000L
        const val PM_DIG_U = 0x08000000L
        const val PM_DIG_L = 0x10000000L
        const val PM_AM = 0x20000000L
        const val PM_FM = 0x40000000L
        const val PM_RX = 0x00200000L
        const val PM_TX = 0x00400000L

        fun modeName(bits: Long): String? = when {
            bits and PM_CW_U != 0L -> "CW"
            bits and PM_CW_L != 0L -> "CW-R"
            bits and PM_SSB_U != 0L -> "USB"
            bits and PM_SSB_L != 0L -> "LSB"
            bits and PM_DIG_U != 0L -> "DATA-U"
            bits and PM_DIG_L != 0L -> "DATA-L"
            bits and PM_AM != 0L -> "AM"
            bits and PM_FM != 0L -> "FM"
            else -> null
        }

        /**
         * The helper: PowerShell holds OmniRig's COM object (an out-of-process server, so the 64-bit PowerShell works
         * with the 32-bit OmniRig) and answers the driver's lines. The first line says "OK" or "ERR <why>".
         */
        val SCRIPT = """
${'$'}ErrorActionPreference = 'Stop'
try { ${'$'}o = New-Object -ComObject OmniRig.OmniRigX } catch { [Console]::Out.WriteLine('ERR ' + ${'$'}_.Exception.Message); exit 1 }
${'$'}r = if (${'$'}args[0] -eq '2') { ${'$'}o.Rig2 } else { ${'$'}o.Rig1 }
[Console]::Out.WriteLine('OK ' + ${'$'}o.SoftwareVersion)
while (${'$'}true) {
  ${'$'}l = [Console]::In.ReadLine()
  if (${'$'}l -eq ${'$'}null) { break }
  ${'$'}p = ${'$'}l.Trim().Split(' ')
  try {
    switch (${'$'}p[0]) {
      'poll' { [Console]::Out.WriteLine('S ' + [int]${'$'}r.Status + ' ' + ${'$'}r.Freq + ' ' + ${'$'}r.Mode + ' ' + ${'$'}r.Tx + ' ' + ${'$'}r.StatusStr) }
      'freq' { ${'$'}r.Freq = [int]${'$'}p[1]; [Console]::Out.WriteLine('OK') }
      'mode' { ${'$'}r.Mode = [int]${'$'}p[1]; [Console]::Out.WriteLine('OK') }
      'tx' { if (${'$'}p[1] -eq '1') { ${'$'}r.Tx = 0x00400000 } else { ${'$'}r.Tx = 0x00200000 }; [Console]::Out.WriteLine('OK') }
      default { [Console]::Out.WriteLine('?') }
    }
  } catch { [Console]::Out.WriteLine('ERR ' + ${'$'}_.Exception.Message) }
}
""".trimStart()
    }
}
