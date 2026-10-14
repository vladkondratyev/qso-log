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
    fun digitalModesOnBoth() {
        // 2.1.0: contacts from WSJT-X on both; the phone says where to send them, the computer how to set up WSJT-X.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Цифровые виды"))
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Цифровые виды"))
            assertTrue(WhatsNew.items(desktop = false).any { "Wi-Fi" in it })
            assertTrue(WhatsNew.items(desktop = true).any { "127.0.0.1" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
