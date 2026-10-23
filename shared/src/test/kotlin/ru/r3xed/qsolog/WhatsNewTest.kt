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
    fun wsjtxCardOnBothScreenKeyOnPhone() {
        // 2.7.1: the card from WSJT-X on both; the on-screen key and the keypad row only on the phone.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Карточка QSO по двойному щелчку"))
            assertTrue(WhatsNew.items(desktop = false).any { "Ключ на экране" in it } && WhatsNew.items(desktop = false).any { "F1–F8" in it })
            assertTrue(WhatsNew.items(desktop = true).size == 1)
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
