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
    fun itemsDifferForTheComputer() {
        // The phone is releases ahead: its list is about the keyboard and the Morse text, the computer's about the contest book.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).any { "Alt+W" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
        assertTrue(WhatsNew.items(desktop = true).none { "Alt+W" in it })
    }
}
