package ru.r3xed.qsolog

import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertTrue

class WhatsNewTest {
    @Test
    fun reportCarriesVersionAndDeviceOnly() {
        val u = ProblemReport.url("1.18.0", "macOS 15", "arm64")
        assertTrue(u.startsWith("https://github.com/vladkondratyev/qso-log/issues/new?body="))
        val body = URLDecoder.decode(u.substringAfter("body="), "UTF-8")
        assertTrue("QSO-LOG 1.18.0 · macOS 15 · arm64" in body)
        assertTrue(' ' !in u)
    }

    @Test
    fun catOnBothTheComputerCatchesUp() {
        // 2.0.0: CAT on both; the computer also lists what the phone got in 0.39–0.40 (the keyboard of classic loggers).
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("CAT"))
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("CAT"))
            assertTrue(WhatsNew.items(desktop = false).none { "Alt+W" in it })
            assertTrue(WhatsNew.items(desktop = true).any { "Alt+W" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "Bluetooth" in it })
            assertTrue(WhatsNew.items(desktop = true).any { "COM" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
