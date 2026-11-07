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
    fun menusOnComputerTabsOnPhone() {
        // 2.9.1: the computer gets the menus, ⌘K and the status bar; the phone the bottom bar.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Меню"))
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Нижняя панель"))
            assertTrue(WhatsNew.items(desktop = false).any { "Дипломы" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
