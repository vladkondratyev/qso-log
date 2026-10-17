package ru.r3xed.qsolog.cat

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RigServerTest {
    private fun server(state: RigState?, sent: MutableList<CatCommand>, port: Int = 0, status: (RigServerStatus) -> Unit = {}) =
        RigServer(port, lan = false, state = { state }, send = { sent += it }, onStatus = status)

    @Test
    fun answers() {
        val sent = CopyOnWriteArrayList<CatCommand>()
        val s = server(RigState(freqHz = 14_074_000, radioMode = "USB-D", sDb = 48), sent)
        assertEquals("14074000\n", s.answer("f"))
        assertEquals("PKTUSB\n3000\n", s.answer("m"))
        assertEquals("RPRT 0\n", s.answer("F 7074000.000000"))
        // Right after the set the new frequency is answered, before the radio's next poll shows it.
        assertEquals("7074000\n", s.answer("f"))
        assertEquals("RPRT 0\n", s.answer("M PKTUSB 3000"))
        assertEquals("RPRT 0\n", s.answer("T 1"))
        assertEquals("1\n", s.answer("t"))
        assertEquals("-6\n", s.answer("l STRENGTH"))
        assertEquals("0\nVFOA\n", s.answer("s"))
        assertEquals("RPRT -11\n", s.answer("\\no_such"))
        assertEquals(listOf(CatCommand.Freq(7_074_000), CatCommand.Mode(RadioMode.DATA), CatCommand.Ptt(true)), sent.toList())
        assertTrue(s.answer("\\dump_state").endsWith("done\n"))
        // No radio: Hamlib's timeout, so the program says the radio does not answer.
        assertEquals("RPRT -5\n", server(null, sent).answer("f"))
    }

    @Test
    fun modeNames() {
        assertEquals("CWR", RigServer.hamlibMode("CW-R"))
        assertEquals("PKTUSB", RigServer.hamlibMode("DATA-U"))
        assertEquals("RTTY", RigServer.hamlibMode("FSK"))
        assertEquals(RadioMode.CW, RigServer.radioMode("CWR", null))
    }

    /** Hamlib's own network client — the one inside JTDX and WSJT-X — opens, reads, tunes and keys through the server. */
    @Test
    fun hamlibClient() {
        val rigctl = listOf("/opt/homebrew/bin/rigctl", "/usr/local/bin/rigctl", "/usr/bin/rigctl").firstOrNull { File(it).canExecute() } ?: return
        val port = java.net.ServerSocket(0).use { it.localPort }
        val sent = CopyOnWriteArrayList<CatCommand>()
        val statuses = CopyOnWriteArrayList<RigServerStatus>()
        val s = server(RigState(freqHz = 14_074_000, radioMode = "USB", sDb = 54), sent, port) { statuses += it }
        s.start()
        try {
            val deadline = System.currentTimeMillis() + 3000
            while (statuses.none { it is RigServerStatus.Listening } && System.currentTimeMillis() < deadline) Thread.sleep(20)
            val p = ProcessBuilder(rigctl, "-m", "2", "-r", "127.0.0.1:$port", "f", "m", "F", "7074000", "M", "PKTUSB", "3000", "T", "1", "T", "0", "s")
                .redirectErrorStream(true).start()
            assertTrue(p.waitFor(20, TimeUnit.SECONDS), "rigctl hung")
            val out = p.inputStream.bufferedReader().readText()
            assertEquals(0, p.exitValue(), out)
            assertTrue(out.lines().first().trim() == "14074000", out)
            assertTrue(CatCommand.Freq(7_074_000) in sent, "$sent\n$out")
            assertTrue(CatCommand.Mode(RadioMode.DATA) in sent, "$sent\n$out")
            assertTrue(CatCommand.Ptt(true) in sent && CatCommand.Ptt(false) in sent, "$sent\n$out")
        } finally {
            s.stop()
        }
    }
}
