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
    fun scaleStepsDifferAndOmniRigOnlyOnComputer() {
        // 2.9.0: 100/85/70 on the computer, 100/70/50 on the phone; OmniRig is a Windows program.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Масштаб интерфейса"))
            assertTrue("85" in WhatsNew.items(desktop = true).first() && "50%" in WhatsNew.items(desktop = false).first())
            assertTrue(WhatsNew.items(desktop = true).any { "OmniRig" in it })
            assertTrue(WhatsNew.items(desktop = false).none { "OmniRig" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
