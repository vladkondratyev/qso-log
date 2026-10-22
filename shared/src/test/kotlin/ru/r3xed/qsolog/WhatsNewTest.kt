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
    fun keyboardCwIcomLanLotwOnBoth() {
        // 2.7.0: the key on an external keyboard first; the keypad row only on the phone, OmniRig only on the computer.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Ключ на внешней клавиатуре"))
            assertTrue(WhatsNew.items(desktop = true).any { "Icom" in it } && WhatsNew.items(desktop = true).any { "LoTW" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "F1–F8" in it })
            assertTrue(WhatsNew.items(desktop = true).any { "OmniRig" in it })
            assertTrue(WhatsNew.items(desktop = false).none { "OmniRig" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
