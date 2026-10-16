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
    fun transceiverPanelOnBoth() {
        // 2.2.0: the panel and CW on both; the computer also starts JTDX and rigctld.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Пульт трансивера"))
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Пульт трансивера"))
            assertTrue(WhatsNew.items(desktop = true).any { "rigctld" in it })
            assertTrue(WhatsNew.items(desktop = false).none { "rigctld" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
