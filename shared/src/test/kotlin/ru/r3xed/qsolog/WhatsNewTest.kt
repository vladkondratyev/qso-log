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
    fun locatorsAndServerOnBoth() {
        // 2.4.0: VHF locators on both; the rigctld server — JTDX on this computer, or on another one for the phone.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Обмен QTH-локаторами"))
            assertTrue(WhatsNew.items(desktop = true).any { "127.0.0.1:4532" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "Wi-Fi" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
