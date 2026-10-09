package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallCommandTest {
    @Test
    fun bandsModesAndFrequencies() {
        assertEquals(CallCommand.Band("20m"), CallCommand.parse("20m"))
        assertEquals(CallCommand.Band("40m"), CallCommand.parse("40"))
        assertEquals(CallCommand.Band("70cm"), CallCommand.parse("70"))
        assertEquals(CallCommand.Band("70cm"), CallCommand.parse("70CM"))
        assertEquals(CallCommand.Mode("CW"), CallCommand.parse("cw"))
        assertEquals(CallCommand.Mode("FT8"), CallCommand.parse("FT8"))
        assertEquals(CallCommand.Mode("SSB"), CallCommand.parse("USB"))
        assertEquals(CallCommand.Freq("14.195"), CallCommand.parse("14195"))
        assertEquals(CallCommand.Freq("7.074"), CallCommand.parse("7,074"))
    }

    @Test
    fun callsignsAreNotCommands() {
        listOf("R9DEMO", "UA3DEMO", "4K4DEMO", "", "  ", "25", "99999", "123ABC").forEach { assertNull(CallCommand.parse(it), it) }
    }
}
