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
    fun itemsDifferForTheComputer() {
        // The faster start is about the Android build only.
        assertTrue(WhatsNew.items(desktop = false).size == WhatsNew.items(desktop = true).size + 1)
        assertTrue(WhatsNew.items(desktop = true).none { "быстрее" in it })
    }
}
