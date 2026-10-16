package ru.r3xed.qsolog.cw

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CwKeyerTest {
    @Test
    fun macros() {
        assertEquals("UA9DEMO 599 007", CwMacros.expand("{CALL} {RST} {NR}", "UA3DEMO", "ua9demo", "599", "007"))
        assertEquals("TU UA3DEMO", CwMacros.expand("tu {MYCALL} {NAME}", "UA3DEMO", "", "", ""))
        assertEquals("CQ UA3DEMO", CwMacros.expand("CQ {MYCALL} {UNKNOWN}", "UA3DEMO", "", "", ""))
    }

    @Test
    fun configRoundTrip() {
        val c = CwConfig(enabled = true, device = "/dev/ttyUSB0", line = CwLine.RTS, wpm = 30, messages = CwConfig.DEFAULT_MESSAGES.map { "$it K" })
        assertEquals(c, CwConfig.fromMap(c.toMap()))
        assertEquals(CwConfig.MAX_WPM, CwConfig.fromMap(mapOf("cw_wpm" to "99")).wpm)
    }

    /** "E E" at 60 WPM (a dot is 20 ms): two key-downs of one dot, 7 dots apart from the start of one to the end of the gap. */
    @Test
    fun timing() {
        val events = CopyOnWriteArrayList<Pair<Boolean, Long>>()
        val line = object : KeyLine {
            override fun key(down: Boolean) { events += down to System.nanoTime() }
            override fun ptt(on: Boolean) = Unit
            override fun close() = Unit
        }
        val states = CopyOnWriteArrayList<CwState>()
        val k = CwKeyer({ line }, { states += it }, wpm = 50)
        k.send("E E")
        val deadline = System.currentTimeMillis() + 3000
        while (states.lastOrNull() != CwState.Idle && System.currentTimeMillis() < deadline) Thread.sleep(5)
        k.close()
        val downs = events.filter { it.first }.map { it.second }
        assertEquals(2, downs.size)
        val dotMs = 1200.0 / 50
        val gapMs = (downs[1] - downs[0]) / 1e6
        // E (1 dot) + 3 after the character + 4 more for the word space = 8 dots between the two key-downs.
        assertTrue(gapMs in 8 * dotMs - 5..8 * dotMs + 15, "gap $gapMs ms")
        assertEquals(false, events.last().first)
    }

    @Test
    fun stopLiftsTheKey() {
        val events = CopyOnWriteArrayList<Boolean>()
        val line = object : KeyLine {
            override fun key(down: Boolean) { events += down }
            override fun ptt(on: Boolean) = Unit
            override fun close() = Unit
        }
        val k = CwKeyer({ line }, {}, wpm = 10)
        k.send("TTTTTTTTTT")
        Thread.sleep(300)
        k.stop()
        Thread.sleep(200)
        val n = events.size
        Thread.sleep(400)
        assertEquals(n, events.size) // nothing more after the stop
        assertEquals(false, events.last())
        k.close()
    }
}
