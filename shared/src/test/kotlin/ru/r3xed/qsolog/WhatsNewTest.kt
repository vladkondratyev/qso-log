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
    fun qrzComApiOnBoth() {
        // 2.7.2: the same points on the phone and the computer.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("QRZ.com по API"))
            assertTrue(WhatsNew.items(desktop = true).any { "одновременно" in it })
            assertTrue(WhatsNew.items(desktop = false) == WhatsNew.items(desktop = true))
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
