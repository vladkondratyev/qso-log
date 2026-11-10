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
    fun tableOnComputerKeypadOnPhone() {
        // 2.9.3: the table log and PTT are the computer's, the search keypad the phone's.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).any { "таблицей" in it })
            assertTrue(WhatsNew.items(desktop = true).any { "PTT" in it })
            assertTrue(WhatsNew.items(desktop = false).none { "PTT" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "клавиатура" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
