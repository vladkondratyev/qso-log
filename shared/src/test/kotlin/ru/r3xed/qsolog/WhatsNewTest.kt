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
    fun computerAndPhonePoints() {
        // 2.9.6: the folder, the column widths and the scale error are the computer's; the keys are on both.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Горячие клавиши"))
            assertTrue(WhatsNew.items(desktop = true).any { "в папке" in it })
            assertTrue(WhatsNew.items(desktop = false).none { "в папке" in it || "колонок" in it || "«Error»" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "внешней клавиатурой" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
