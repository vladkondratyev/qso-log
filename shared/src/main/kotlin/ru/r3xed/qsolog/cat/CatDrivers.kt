package ru.r3xed.qsolog.cat

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

    companion object {
        fun of(c: CatConfig, sMeter: Boolean = true): RigDriver = when (c.protocol) {
            CatProtocol.ICOM -> CivDriver(c.civAddress, true, c.model.name, sMeter)
            CatProtocol.KENWOOD -> KenwoodDriver(c.model.name, c.model.elecraft, sMeter)
            CatProtocol.YAESU -> YaesuDriver(c.model.name, sMeter)
            CatProtocol.YAESU_OLD -> YaesuOldDriver(c.model.name, sMeter)
            CatProtocol.RIGCTLD -> RigctldDriver(sMeter)
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

    override fun poll(io: CatIo, last: RigState): RigState {
        state = last
        answered = false
        val gotFreq = ask(io, 0x03)
        if (gotFreq) {
            misses = 0
            ask(io, 0x04)
            if (dataSupported && cycle % 2 == 0) ask(io, 0x1A, 0x06)
            if (sMeter) ask(io, 0x15, 0x02)
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
                return cmd != 0x03
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
            0x15 -> if (body.size >= 3 && body[0].u == 0x02) {
                val v = bcd(body[1]) * 100 + bcd(body[2])
                state = state.copy(sDb = CatFormat.scale(v, 120, 241))
            }
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
    }
}

private val Byte.u get() = toInt() and 0xFF

/** Kenwood, Elecraft, FlexRadio: "FA;" → "FA00014074000;", "MD;" → "MD2;", "SM0;" → "SM00015;". */
class KenwoodDriver(private val name: String, private val elecraft: Boolean, private val sMeter: Boolean = true) : RigDriver {
    override var answered = false; private set
    override val what get() = name
    private var smCmd = if (elecraft) "SM;" else "SM0;"

    override fun poll(io: CatIo, last: RigState): RigState {
        var s = last
        answered = false
        ask(io, "FA;")?.let { r -> digits(r.removePrefix("FA"))?.let { s = s.copy(freqHz = it, at = System.currentTimeMillis()) } }
        if (!answered) return s
        ask(io, "MD;")?.let { r -> r.getOrNull(2)?.let { c -> s = s.copy(radioMode = (if (elecraft) ELECRAFT_MODES else MODES)[c] ?: s.radioMode) } }
        if (sMeter) {
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
        if (sMeter) ask(io, "SM0;")?.removePrefix("SM0")?.toIntOrNull()?.let { v -> s = s.copy(sDb = CatFormat.scale(v, 130, 255)) }
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
        if (sMeter) ask(io, "l STRENGTH", 1)?.firstOrNull()?.trim()?.toIntOrNull()?.let { s = s.copy(sDb = (54 + it).coerceAtLeast(0)) }
        return s
    }

    override fun setFreq(io: CatIo, hz: Long): Boolean = report(io, "F $hz")

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

    private fun ask(io: CatIo, cmd: String, lines: Int): List<String>? {
        io.send("$cmd\n".toByteArray(Charsets.US_ASCII))
        val out = mutableListOf<String>()
        while (out.size < lines) {
            val l = io.line(TIMEOUT, '\n')?.trimEnd('\r', '\n') ?: return null
            answered = true
            if (l.startsWith("RPRT")) return null
            out += l
        }
        return out
    }
}

private fun digits(s: String): Long? = s.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
