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
    fun reportCarriesTheCrash() {
        val e = IllegalStateException("boom", IndexOutOfBoundsException("Index 0"))
        val text = ProblemReport.crashText(e)
        assertTrue(text.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue("Caused by: java.lang.IndexOutOfBoundsException" in text)
        val body = URLDecoder.decode(ProblemReport.url("2.9.7", "Android 10", "Tablet", crash = text).substringAfter("body="), "UTF-8")
        assertTrue("```" in body && "boom" in body)
    }

    @Test
    fun computerAndPhonePoints() {
        // 3.1: the divider on the computer, the tablet and the dense log on the phone.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Фото в старой связи"))
            assertTrue(WhatsNew.items(desktop = true).any { "двойной щелчок" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "Планшет" in it })
            assertTrue(WhatsNew.items(desktop = true).none { "Плотный журнал" in it })
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
