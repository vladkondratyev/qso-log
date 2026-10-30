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
    fun infoFromLogOnBothCompactLogOnComputer() {
        // 2.7.3: the data from the previous QSO everywhere, the compact log only on the computer.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).single().startsWith("Данные из прошлой связи"))
            assertTrue(WhatsNew.items(desktop = true).size == 2)
            assertTrue(WhatsNew.items(desktop = true).any { "Компактный журнал" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
