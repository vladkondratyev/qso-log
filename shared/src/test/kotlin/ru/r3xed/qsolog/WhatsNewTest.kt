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
    fun scalesAndOnlineLogsOnBoth() {
        // 2.3.0: the meters on both; LoTW through TQSL on the computer, a file for TQSL on the phone.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Шкалы"))
            assertTrue(WhatsNew.items(desktop = true).any { "QSO-LOG запускает его сам" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "файл, который подписывает TQSL" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
