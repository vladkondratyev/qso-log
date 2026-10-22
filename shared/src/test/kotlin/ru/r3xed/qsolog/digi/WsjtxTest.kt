package ru.r3xed.qsolog.digi

import ru.r3xed.qsolog.data.Qso
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WsjtxTest {
    /** A datagram as WSJT-X writes it (Qt QDataStream, big-endian). */
    private class Msg(type: Int, id: String = "WSJT-X") {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)

        init {
            out.writeInt(Wsjtx.MAGIC); out.writeInt(3); out.writeInt(type); utf8(id)
        }

        fun utf8(s: String?) = apply {
            if (s == null) out.writeInt(-1) else s.toByteArray().let { out.writeInt(it.size); out.write(it) }
        }

        fun dateTime(t: LocalDateTime) = apply {
            out.writeLong(t.toLocalDate().toEpochDay() + 2_440_588L)
            out.writeInt((t.toLocalTime().toNanoOfDay() / 1_000_000).toInt())
            out.writeByte(1)
        }

        fun long(v: Long) = apply { out.writeLong(v) }
        fun int(v: Int) = apply { out.writeInt(v) }
        fun bool(v: Boolean) = apply { out.writeBoolean(v) }
        fun build(): ByteArray = bytes.toByteArray()
    }

    private val on = LocalDateTime.of(2026, 10, 15, 18, 30, 15)

    private fun loggedMsg(id: String = "WSJT-X", full: Boolean = true) = Msg(5, id)
        .dateTime(on.plusSeconds(45)).utf8("oh2demo").utf8("KP20").long(14_074_000)
        .utf8("FT4").utf8("-08").utf8("-12").utf8("50").utf8("").utf8("Mikko").dateTime(on)
        .apply { if (full) utf8("").utf8("UA3DEMO").utf8("KO85").utf8(null).utf8(null).utf8("") }
        .build()

    @Test
    fun qsoLogged() {
        val m = assertIs<Wsjtx.Message.Logged>(Wsjtx.parse(loggedMsg()))
        val q = m.qsos.single()
        assertEquals("OH2DEMO", q.call)
        assertEquals(on.toInstant(ZoneOffset.UTC).toEpochMilli(), q.timeUtc)
        assertEquals("20m", q.band)
        assertEquals("14.074", q.freqMhz)
        assertEquals("FT4", q.mode)
        assertEquals("-08", q.rstSent)
        assertEquals("-12", q.rstRcvd)
        assertEquals("KP20", q.locator)
        assertEquals("Mikko", q.name)
        assertEquals("50", q.power)
        assertEquals("UA3DEMO", q.myCall)
        assertEquals("KO85", q.myLocator)
        assertEquals("183100", q.adif["TIME_OFF"])
    }

    @Test
    fun olderProgramsStopEarlier() {
        // JTDX and old WSJT-X end after "Date/Time On".
        val q = assertIs<Wsjtx.Message.Logged>(Wsjtx.parse(loggedMsg("JTDX", full = false))).qsos.single()
        assertEquals("OH2DEMO", q.call)
        assertEquals("", q.myCall)
    }

    @Test
    fun loggedAdif() {
        val adif = "<adif_ver:5>3.1.0 <programid:6>WSJT-X <EOH> <call:6>W1DEMO <gridsquare:4>FN42 <mode:3>FT8 " +
            "<rst_sent:3>-15 <rst_rcvd:3>+02 <qso_date:8>20261015 <time_on:6>183015 <band:3>20m <freq:9>14.075123 " +
            "<station_callsign:7>UA3DEMO <my_gridsquare:6>KO85ts <EOR>"
        val q = assertIs<Wsjtx.Message.Logged>(Wsjtx.parse(Msg(12).utf8(adif).build())).qsos.single()
        assertEquals("W1DEMO", q.call)
        assertEquals("FT8", q.mode)
        assertEquals("+02", q.rstRcvd)
        assertEquals("14.075123", q.freqMhz)
        assertEquals("KO85ts", q.myLocator)
    }

    @Test
    fun plainAdifAndOthers() {
        val text = "<call:6>R0DEMO<qso_date:8>20261015<time_on:4>1830<mode:4>MFSK<submode:3>FT4<band:3>40m<eor>"
        assertEquals("FT4", assertIs<Wsjtx.Message.Logged>(Wsjtx.parse(text.toByteArray())).qsos.single().mode)
        val hb = assertIs<Wsjtx.Message.Heartbeat>(Wsjtx.parse(Msg(0).int(3).utf8("2.7.0").utf8("abc").build()))
        assertEquals("2.7.0", hb.version)
        assertIs<Wsjtx.Message.Other>(Wsjtx.parse(Msg(2).build()))
        assertNull(Wsjtx.parse("hello".toByteArray()))
        assertNull(Wsjtx.parse(Msg(5).utf8("cut").build()))
    }

    @Test
    fun configKeys() {
        val c = DigiConfig(enabled = true, port = 2238, group = "224.0.0.1")
        assertEquals(c, DigiConfig.fromMap(c.toMap()))
        assertEquals(DigiConfig(), DigiConfig.fromMap(mapOf("wsjtx_port" to "0")))
    }

    @Test
    fun listensOnUdp() {
        val port = ServerSocket(0).use { it.localPort }
        val got = LinkedBlockingQueue<List<Qso>>()
        val status = LinkedBlockingQueue<DigiStatus>()
        val l = DigiListener(DigiConfig(enabled = true, port = port), { status.put(it) }, { q, _ -> got.put(q) })
        l.start()
        try {
            assertIs<DigiStatus.Listening>(status.poll(5, TimeUnit.SECONDS))
            DatagramSocket().use { s ->
                val hb = Msg(0).int(3).utf8("2.7.0").utf8("abc").build()
                s.send(DatagramPacket(hb, hb.size, InetAddress.getLoopbackAddress(), port))
                val b = loggedMsg()
                s.send(DatagramPacket(b, b.size, InetAddress.getLoopbackAddress(), port))
            }
            assertEquals("OH2DEMO", assertNotNull(got.poll(5, TimeUnit.SECONDS)).single().call)
            val heard = generateSequence { status.poll(1, TimeUnit.SECONDS) }.filterIsInstance<DigiStatus.Listening>().first { it.program != null }
            assertEquals("WSJT-X v2.7.0", heard.program)
            // A second listener on the same port says it is busy.
            val busy = LinkedBlockingQueue<DigiStatus>()
            val l2 = DigiListener(DigiConfig(enabled = true, port = port), { busy.put(it) }, { _, _ -> })
            l2.start()
            assertTrue(busy.poll(5, TimeUnit.SECONDS) is DigiStatus.Failed)
            l2.stop()
        } finally {
            l.stop()
        }
    }

    @Test
    fun statusGivesTheDialAndMode() {
        val m = Msg(1, "JTDX").long(14_074_000).utf8("FT8").utf8("UA9DEMO").utf8("-12").utf8("FT8")
            .bool(true).bool(false).bool(true).int(1500).int(1500).utf8("UA3DEMO").utf8("KO85").utf8("").build()
        val s = Wsjtx.parse(m) as Wsjtx.Message.Status
        assertEquals(14_074_000, s.dialHz)
        assertEquals("FT8", s.mode)
        assertEquals("UA9DEMO", s.dxCall)
        assertEquals(false, s.transmitting)
    }

    @Test
    fun utcMarkedAsLocalStaysUtc() {
        // JTDX on Windows: 12:17 UTC sent with the "local" mark, logged a few seconds later, the computer in Moscow.
        val moscow = java.time.ZoneId.of("Europe/Moscow")
        val t = LocalDateTime.of(2026, 10, 20, 12, 17, 0)
        assertEquals(t, Wsjtx.localOrUtc(t, t.plusMinutes(2), moscow))
        // A real local time (15:17 in Moscow) is turned into UTC.
        assertEquals(t, Wsjtx.localOrUtc(t.plusHours(3), t.plusMinutes(2), moscow))
        // Logged long after: taken as UTC.
        assertEquals(t, Wsjtx.localOrUtc(t, t.plusDays(3), moscow))
    }
}
