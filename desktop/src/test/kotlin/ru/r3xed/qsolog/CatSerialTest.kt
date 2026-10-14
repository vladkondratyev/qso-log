package ru.r3xed.qsolog

import ru.r3xed.qsolog.cat.CatConfig
import ru.r3xed.qsolog.cat.CatIo
import ru.r3xed.qsolog.cat.CatLinkType
import ru.r3xed.qsolog.cat.RigDriver
import ru.r3xed.qsolog.cat.RigState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The COM port through jSerialComm, against a radio on a real port. Runs only with QSOLOG_CAT_PORT set: a serial port
 * (or a pseudo-terminal with an IC-7300 imitation at 94h tuned to 3.650 MHz LSB).
 */
class CatSerialTest {
    @Test
    fun icomOverSerialPort() {
        val port = System.getenv("QSOLOG_CAT_PORT") ?: return
        val c = CatConfig(enabled = true, rig = "IC-7300", civAddress = 0x94, baud = 19200, link = CatLinkType.SERIAL, device = port)
        val link = CatDesktop.open(c)
        try {
            val io = CatIo(link)
            val d = RigDriver.of(c)
            var s = RigState()
            repeat(3) { s = d.poll(io, s) }
            assertEquals(3_650_000, s.freqHz)
            assertEquals("LSB", s.radioMode)
        } finally {
            link.close()
        }
    }
}
