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
    fun settingsAndPartOfTheLogOnBoth() {
        // 2.5.0: the settings pages on both; picking part of the log — from "Файл" on the computer, from the export window on the phone.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Настройки стали короче"))
            assertTrue(WhatsNew.items(desktop = true).any { "«Файл»" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "TRX" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "/P" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
