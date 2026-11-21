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
        // 2.9.7: the LoTW certificate and the crash report are the phone's; the rest is on both.
        I18n.chosen = Lang.RU
        try {
            assertTrue(WhatsNew.items(desktop = true).first().startsWith("Имя файла"))
            assertTrue(WhatsNew.items(desktop = true).none { "LoTW" in it || "отчёт" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "LoTW" in it })
            assertTrue(WhatsNew.items(desktop = false).any { "QSO-LOG Web" in it })
            assertTrue(WhatsNew.items(desktop = false).first().startsWith("Исправлено закрытие приложения на Android 8–12"))
        } finally {
            I18n.chosen = Lang.SYSTEM
        }
    }
}
