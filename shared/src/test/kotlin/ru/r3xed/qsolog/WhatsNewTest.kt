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
    fun digitsAndCwOnComputerOnly() {
        // 2.9.5: the digits of the rig panel and the CW timing file are the computer's, the long press the phone's.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Наблюдатели (SWL)"))
            assertTrue(WhatsNew.items(desktop = true).any { "щелчок по цифре" in it })
            assertTrue(WhatsNew.items(desktop = true).any { "cw-timing.txt" in it })
            assertTrue(WhatsNew.items(desktop = false).none { "cw-timing.txt" in it || "щелчок по цифре" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "долгим нажатием" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
