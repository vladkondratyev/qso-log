package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class StartAppsTest {
    @Test
    fun roundTrip() {
        val list = listOf(
            StartApp("C:\\Program Files\\JTDX\\bin\\jtdx.exe", "jtdx"),
            StartApps.rigctld(3073, "COM3", 19200),
            StartApp("com.example.ft8", "FT8 app", enabled = false),
        )
        assertEquals(list, StartApps.decode(StartApps.encode(list)))
        assertEquals(emptyList(), StartApps.decode(""))
    }

    @Test
    fun arguments() {
        assertEquals(listOf("-m", "3073", "-r", "COM3"), StartApps.splitArgs("-m 3073  -r COM3"))
        assertEquals(listOf("--config", "C:\\My Files\\a.ini", ""), StartApps.splitArgs("--config \"C:\\My Files\\a.ini\" ''"))
    }
}
