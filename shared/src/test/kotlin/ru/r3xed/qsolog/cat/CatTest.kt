package ru.r3xed.qsolog.cat

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A radio on the other end of the link: [answer] turns each command into the bytes the radio sends back. */
private class FakeRadio(private val split: (ByteArray) -> List<ByteArray>, private val answer: (ByteArray) -> ByteArray?) : CatLink {
    private val out = LinkedBlockingQueue<Byte>()
    val heard = CopyOnWriteArrayList<ByteArray>()

    override fun write(b: ByteArray) {
        for (cmd in split(b)) {
            heard += cmd
            answer(cmd)?.forEach { out.put(it) }
        }
    }

    fun push(b: ByteArray) = b.forEach { out.put(it) }

    override fun read(buf: ByteArray, timeoutMs: Int): Int {
        val first = out.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        buf[0] = first
        var n = 1
        while (n < buf.size) {
            buf[n] = out.poll() ?: break
            n++
        }
        return n
    }

    override fun close() = Unit
}

private fun whole(b: ByteArray) = listOf(b)
private fun text(b: ByteArray) = b.toString(Charsets.US_ASCII).split(';', '\n').filter { it.isNotEmpty() }.map { it.toByteArray() }

/** An Icom at [addr] tuned to [hz] in [mode]; with [echo] it also repeats every frame, as a one-wire CI-V cable does. */
private fun icom(addr: Int, hz: () -> Long, mode: Int = 0x01, data: Int = 0, s: Int = 0x0120, echo: Boolean = false) = FakeRadio(::whole) { f ->
    val to = f[2].toInt() and 0xFF
    val cmd = f[4].toInt() and 0xFF
    val reply = if (to != addr) null else when (cmd) {
        0x03 -> CivDriver.frame(0xE0, addr, 0x03, data = CivDriver.civFreqBytes(hz()))
        0x04 -> CivDriver.frame(0xE0, addr, 0x04, data = byteArrayOf(mode.toByte(), 0x01))
        0x1A -> CivDriver.frame(0xE0, addr, 0x1A, 0x06, byteArrayOf(data.toByte(), if (data != 0) 1 else 0))
        0x15 -> CivDriver.frame(0xE0, addr, 0x15, 0x02, byteArrayOf((s shr 8).toByte(), (s and 0xFF).toByte()))
        else -> CivDriver.frame(0xE0, addr, 0xFA)
    }
    (if (echo) f else ByteArray(0)) + (reply ?: ByteArray(0))
}

class CatTest {
    @Test
    fun civFrequencyBcd() {
        val b = CivDriver.civFreqBytes(14_074_000)
        assertEquals(listOf(0x00, 0x40, 0x07, 0x14, 0x00), b.map { it.toInt() and 0xFF })
        assertEquals(14_074_000, CivDriver.civFreq(b))
        assertEquals(1_296_200_000, CivDriver.civFreq(CivDriver.civFreqBytes(1_296_200_000)))
    }

    @Test
    fun icomWithEchoOnOneWire() {
        val radio = icom(0x94, { 7_012_340 }, mode = 0x03, s = 0x0060, echo = true)
        val d = CivDriver(0x94, name = "IC-7300")
        val s = d.poll(CatIo(radio), RigState())
        assertEquals(7_012_340, s.freqHz)
        assertEquals("CW", s.radioMode)
        assertEquals("7.01234", s.freqMhz)
        assertEquals(27, s.sDb) // 60 of 120 to S9: S4–S5
        assertEquals("S5", CatFormat.sMeter(s.sDb!!))
    }

    @Test
    fun icomDataModeIsFt8OrTheCardsDigitalMode() {
        val d = CivDriver(0x94)
        val s = d.poll(CatIo(icom(0x94, { 14_074_000 }, mode = 0x01, data = 1)), RigState())
        assertEquals("USB-D", s.radioMode)
        assertEquals("FT8", CatModes.toLog(s.radioMode!!, "SSB"))
        assertEquals("FT4", CatModes.toLog(s.radioMode!!, "FT4"))
        assertEquals("SSB", CatModes.toLog("USB", "FT8"))
    }

    @Test
    fun icomAddressIsFoundWhenTheSetOneIsWrong() {
        // Set to the IC-705's A4, the radio is an IC-7300 at 94.
        val radio = icom(0x94, { 21_200_000 })
        val d = CivDriver(0xA4, name = "IC-705")
        val io = CatIo(radio)
        var s = RigState()
        repeat(12) { if (s.freqHz == null) s = d.poll(io, s) }
        assertEquals(21_200_000, s.freqHz)
        assertEquals(0x94, d.address)
        assertTrue(d.answered)
    }

    @Test
    fun icomTransceiveFramesUpdateTheState() {
        var hz = 14_195_000L
        val radio = icom(0x94, { hz })
        val d = CivDriver(0x94)
        val io = CatIo(radio)
        d.poll(io, RigState())
        // The dial turned: the radio announces it to everyone (00) before the next poll's answer.
        radio.push(CivDriver.frame(0x00, 0x94, 0x00, data = CivDriver.civFreqBytes(14_200_000)))
        hz = 14_201_000
        val s = d.poll(io, RigState())
        assertEquals(14_201_000, s.freqHz)
    }

    @Test
    fun civFramingSkipsJunkAndCollisions() {
        val good = CivDriver.frame(0xE0, 0x94, 0x03, data = CivDriver.civFreqBytes(3_650_000))
        val radio = FakeRadio(::whole) { byteArrayOf(0x00, 0x13, 0xFE.toByte(), 0xFE.toByte(), 0x94.toByte(), 0xFC.toByte()) + byteArrayOf(0xFE.toByte()) + good }
        val s = CivDriver(0x94, probe = false, sMeter = false).poll(CatIo(radio), RigState())
        assertEquals(3_650_000, s.freqHz)
    }

    @Test
    fun kenwood() {
        val radio = FakeRadio(::text) { c ->
            when (c.toString(Charsets.US_ASCII)) {
                "FA" -> "FA00014025500;"
                "MD" -> "MD3;"
                "SM0" -> "SM00015;"
                else -> "?;"
            }.toByteArray()
        }
        val s = KenwoodDriver("TS-590S", elecraft = false).poll(CatIo(radio), RigState())
        assertEquals(14_025_500, s.freqHz)
        assertEquals("CW", s.radioMode)
        assertEquals("S9", CatFormat.sMeter(s.sDb!!))
    }

    @Test
    fun elecraftAnswersSmWithoutTheZero() {
        val radio = FakeRadio(::text) { c ->
            when (c.toString(Charsets.US_ASCII)) {
                "FA" -> "FA00007074000;"
                "MD" -> "MD6;"
                "SM" -> "SM0013;"
                else -> "?;"
            }.toByteArray()
        }
        val s = KenwoodDriver("Elecraft K3 / K3S", elecraft = true).poll(CatIo(radio), RigState())
        assertEquals(7_074_000, s.freqHz)
        assertEquals("FT8", CatModes.toLog(s.radioMode!!, "SSB"))
        assertEquals(74, s.sDb) // 13 of 21 bars: S9+20
    }

    @Test
    fun yaesuNewCat() {
        val radio = FakeRadio(::text) { c ->
            when (c.toString(Charsets.US_ASCII)) {
                "FA" -> "FA028450000;"
                "MD0" -> "MD02;"
                "SM0" -> "SM0130;"
                else -> "?;"
            }.toByteArray()
        }
        val s = YaesuDriver("FT-991 / FT-991A").poll(CatIo(radio), RigState())
        assertEquals(28_450_000, s.freqHz)
        assertEquals("SSB", CatModes.toLog(s.radioMode!!, "CW"))
        assertEquals("S9", CatFormat.sMeter(s.sDb!!))
    }

    @Test
    fun yaesuFt817Binary() {
        val radio = FakeRadio(::whole) { c ->
            when (c[4].toInt() and 0xFF) {
                0x03 -> byteArrayOf(0x01, 0x40, 0x74, 0x00, 0x01) // 14.07400 MHz USB
                0xE7 -> byteArrayOf(0x07)
                else -> null
            }
        }
        val s = YaesuOldDriver("FT-817 / FT-818").poll(CatIo(radio), RigState())
        assertEquals(14_074_000, s.freqHz)
        assertEquals("USB", s.radioMode)
        assertEquals("S7", CatFormat.sMeter(s.sDb!!))
    }

    @Test
    fun rigctld() {
        val radio = FakeRadio(::text) { c ->
            when (c.toString(Charsets.US_ASCII)) {
                "f" -> "3700000\n"
                "m" -> "LSB\n2400\n"
                "l STRENGTH" -> "-12\n"
                else -> "RPRT -1\n"
            }.toByteArray()
        }
        val s = RigctldDriver().poll(CatIo(radio), RigState())
        assertEquals(3_700_000, s.freqHz)
        assertEquals("SSB", CatModes.toLog(s.radioMode!!, "CW"))
        assertEquals("S7", CatFormat.sMeter(s.sDb!!))
    }

    @Test
    fun silentRadio() {
        val d = CivDriver(0x94, probe = false)
        val s = d.poll(CatIo(FakeRadio(::whole) { null }), RigState())
        assertNull(s.freqHz)
        assertTrue(!d.answered)
    }

    @Test
    fun reportsAndFormats() {
        assertEquals("57", CatFormat.rstWithS("59", 42))
        assertEquals("589", CatFormat.rstWithS("599", 48))
        assertEquals("51", CatFormat.rstWithS("59", 0))
        assertEquals("-10", CatFormat.rstWithS("-10", 54))
        assertEquals("S9+20", CatFormat.sMeter(74))
        assertEquals("14 074.000", CatFormat.khz(14_074_000))
        assertEquals("144.3", CatFormat.mhz(144_300_000))
        assertEquals("10.0", CatFormat.mhz(10_000_000))
    }

    @Test
    fun configRoundTrip() {
        val c = CatConfig(enabled = true, rig = "IC-705", civAddress = 0xA4, baud = 19200, link = CatLinkType.BLUETOOTH, device = "00:11:22:33:44:55", sMeterToRst = true)
        assertEquals(c, CatConfig.fromMap(c.toMap(), CatLinkType.USB))
        assertEquals(CatConfig(link = CatLinkType.SERIAL), CatConfig.fromMap(emptyMap(), CatLinkType.SERIAL))
    }

    @Test
    fun sessionReportsTheRadio() {
        val statuses = LinkedBlockingQueue<CatStatus>()
        val session = CatSession(CatConfig(rig = "IC-7300", civAddress = 0x94), { icom(0x94, { 50_313_000 }, data = 1) }, { statuses.put(it) }, pollMs = 20)
        session.start()
        var got: RigState? = null
        val deadline = System.currentTimeMillis() + 5000
        while (got == null && System.currentTimeMillis() < deadline) {
            val st = statuses.poll(100, TimeUnit.MILLISECONDS)
            if (st is CatStatus.Connected && st.state?.freqHz != null && st.state?.radioMode != null) got = st.state
        }
        session.stop()
        assertEquals(50_313_000, got?.freqHz)
        assertEquals("USB-D", got?.radioMode)
    }

    @Test
    fun overTcp() {
        val server = java.net.ServerSocket(0)
        val t = Thread {
            server.accept().use { s ->
                val r = s.getInputStream().bufferedReader()
                val w = s.getOutputStream()
                while (true) {
                    val l = r.readLine() ?: break
                    w.write(when (l) { "f" -> "7100000\n"; "m" -> "CW\n500\n"; else -> "RPRT -11\n" }.toByteArray())
                    w.flush()
                }
            }
        }.apply { isDaemon = true; start() }
        val link = tcpLink("127.0.0.1", server.localPort)
        val s = RigctldDriver(sMeter = false).poll(CatIo(link), RigState())
        link.close()
        server.close()
        t.join(1000)
        assertEquals(7_100_000, s.freqHz)
        assertEquals("CW", s.radioMode)
    }
}
