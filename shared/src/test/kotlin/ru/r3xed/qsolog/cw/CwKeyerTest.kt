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

    @Test
    fun twoSetsOfMessages() {
        // Before 2.9.3: one untouched set (the contest one) → contest = it, everyday = the new defaults.
        val untouched = CwConfig.DEFAULT_CONTEST_MESSAGES.mapIndexed { i, m -> "cw_msg${i + 1}" to m }.toMap()
        CwConfig.fromMap(untouched).let {
            assertEquals(CwConfig.DEFAULT_MESSAGES, it.messages)
            assertEquals(CwConfig.DEFAULT_CONTEST_MESSAGES, it.contestMessages)
        }
        // A changed set stays in both.
        val own = untouched + ("cw_msg1" to "CQ DE UA3DEMO")
        CwConfig.fromMap(own).let {
            assertEquals("CQ DE UA3DEMO", it.messages[0])
            assertEquals("CQ DE UA3DEMO", it.contestMessages[0])
        }
        val c = CwConfig().withMessage(true, 2, "TU")
        assertEquals("TU", c.messagesFor(true)[2])
        assertEquals(CwConfig.DEFAULT_MESSAGES[2], c.messagesFor(false)[2])
        val back = CwConfig.fromMap(c.copy(pttTailMs = 120, pttLeadMs = 0, pttDevice = "COM5", pttLine = CwLine.DTR).toMap())
        assertEquals(c.copy(pttTailMs = 120, pttLeadMs = 0, pttDevice = "COM5", pttLine = CwLine.DTR), back)
    }

    /** PTT goes off [CwConfig.pttTailMs] after the message, not 0.7 s later. */
    @Test
    fun pttTail() {
        val ptt = CopyOnWriteArrayList<Pair<Boolean, Long>>()
        var lastKey = 0L
        val line = object : KeyLine {
            override fun key(down: Boolean) { if (!down) lastKey = System.nanoTime() }
            override fun ptt(on: Boolean) { ptt += on to System.nanoTime() }
            override fun close() = Unit
        }
        val k = CwKeyer({ line }, {}, wpm = 50, usePtt = true, pttLeadMs = 10, pttTailMs = 50)
        k.send("E")
        val deadline = System.currentTimeMillis() + 3000
        while (ptt.none { !it.first } && System.currentTimeMillis() < deadline) Thread.sleep(5)
        k.close()
        val off = ptt.first { !it.first }.second
        val tailMs = (off - lastKey) / 1e6
        assertTrue(tailMs in 30.0..300.0, "tail $tailMs ms")
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

class CwHandTest {
    @org.junit.jupiter.api.Test
    fun paddleKeysTheLineAndDecodes() {
        val edges = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        val hand = CwHand(ru.r3xed.qsolog.morse.KeyType.IAMBIC_B, 30, keyDown = { edges += it; true }, onText = {})
        // 30 WPM: a dot is 40 ms. The dot paddle held for three dots and released: "S".
        hand.press(1, true)
        Thread.sleep(200)
        hand.press(1, false)
        Thread.sleep(400)
        kotlin.test.assertEquals("S", hand.text().trim())
        kotlin.test.assertEquals(listOf(true, false, true, false, true, false), edges.toList())
        hand.close()
    }

    @org.junit.jupiter.api.Test
    fun aSenderWithoutHandKeyingGetsLetters() {
        val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
        val hand = CwHand(ru.r3xed.qsolog.morse.KeyType.STRAIGHT, 20, keyDown = { false }, onText = { sent += it })
        // 20 WPM: dash 180 ms, gap 60 ms: "T" then "E".
        hand.press(0, true); Thread.sleep(180); hand.press(0, false)
        Thread.sleep(400)
        hand.press(0, true); Thread.sleep(60); hand.press(0, false)
        Thread.sleep(400)
        kotlin.test.assertEquals("TE", sent.joinToString("").replace(" ", ""))
        hand.close()
    }
}
