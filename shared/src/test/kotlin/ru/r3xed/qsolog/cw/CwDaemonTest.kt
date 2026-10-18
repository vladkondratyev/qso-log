package ru.r3xed.qsolog.cw

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CwDaemonTest {
    @Test
    fun addresses() {
        assertEquals("192.168.1.20" to 6789, CwDaemon.parse("192.168.1.20"))
        assertEquals("pi.local" to 6790, CwDaemon.parse(" pi.local:6790 "))
        assertEquals("fe80::1" to 6789, CwDaemon.parse("[fe80::1]"))
        assertEquals("fe80::1" to 6789, CwDaemon.parse("fe80::1"))
    }

    @Test
    fun speedSignsAreLeftOut() {
        assertEquals("UA3DEMO 5NN MA11", CwDaemon.clean("ua3demo  5nn  MA-11+~"))
        // PARIS is 50 units: 2 s at 30 WPM, with the gap after the word.
        assertEquals(50 * 1200L / 30, CwDaemon.durationMs("PARIS ", 30))
    }

    /** A cwdaemon of our own on localhost: what comes in, and the echo it answers after the message. */
    @Test
    fun speedTextAndEcho() {
        val server = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val got = LinkedBlockingQueue<String>()
        val states = LinkedBlockingQueue<CwState>()
        Thread {
            val buf = ByteArray(256)
            var echo: String? = null
            repeat(4) {
                val p = DatagramPacket(buf, buf.size)
                server.receive(p)
                val s = String(p.data, 0, p.length, Charsets.US_ASCII)
                got.add(s)
                if (s.startsWith("\u001Bh")) echo = "h" + s.substring(2)
                else if (!s.startsWith("\u001B")) echo?.let { e ->
                    val b = (e + "\r\n").toByteArray()
                    server.send(DatagramPacket(b, b.size, p.socketAddress))
                }
            }
        }.apply { isDaemon = true; start() }
        val d = CwDaemon("127.0.0.1:${server.localPort}", { states.add(it) }, wpm = 28)
        d.send("cq test {x}")
        assertEquals("\u001B228", got.poll(2, TimeUnit.SECONDS))
        assertEquals("\u001Bh1", got.poll(2, TimeUnit.SECONDS))
        assertEquals("CQ TEST X", got.poll(2, TimeUnit.SECONDS))
        assertTrue(states.poll(2, TimeUnit.SECONDS) is CwState.Sending)
        assertEquals(CwState.Idle, states.poll(2, TimeUnit.SECONDS))
        d.stop()
        assertEquals("\u001B4", got.poll(2, TimeUnit.SECONDS))
        d.close()
        server.close()
    }

    /** A real cwdaemon, when one is given: CWDAEMON=host:port (e.g. in Docker: cwdaemon -n -x n -d null). */
    @Test
    fun realCwdaemon() {
        val addr = System.getenv("CWDAEMON") ?: return
        val states = LinkedBlockingQueue<CwState>()
        val d = CwDaemon(addr, { states.add(it) }, wpm = 40)
        val started = System.currentTimeMillis()
        d.send("TEST")
        assertTrue(states.poll(3, TimeUnit.SECONDS) is CwState.Sending)
        // Its own echo, well before the fallback (length + 3 s): TEST at 40 WPM is about 1 s.
        assertEquals(CwState.Idle, states.poll(5, TimeUnit.SECONDS))
        assertTrue(System.currentTimeMillis() - started < CwDaemon.durationMs("TEST", 40) + 2500)
        d.close()
    }
}
