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
    fun eqslCwdaemonCommanderOnBoth() {
        // 2.6.0: eQSL, cwdaemon and Commander on both; the Xiaomi note of "Запускать вместе" only on the phone.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("eQSL"))
            assertTrue(WhatsNew.items(desktop = true).any { "cwdaemon" in it } && WhatsNew.items(desktop = true).any { "Commander" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "Poco" in it })
            assertTrue(WhatsNew.items(desktop = true).none { "Poco" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
