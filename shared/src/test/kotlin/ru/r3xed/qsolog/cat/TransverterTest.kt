package ru.r3xed.qsolog.cat

import kotlin.test.Test
import kotlin.test.assertEquals

class TransverterTest {
    @Test
    fun realAndRigFrequencies() {
        val c = CatConfig(transverters = listOf(Transverter(true, 28_000_000, 30_000_000, 404_000_000), Transverter(true, 144_000_000, 146_000_000, 1_152_000_000)))
        assertEquals(432_150_000, c.realHz(28_150_000))
        assertEquals(1_296_200_000, c.realHz(144_200_000))
        assertEquals(14_074_000, c.realHz(14_074_000))
        assertEquals(28_150_000, c.rigHz(432_150_000))
        assertEquals(144_200_000, c.rigHz(1_296_200_000))
        assertEquals(7_074_000, c.rigHz(7_074_000))
        // Switched off: the IF band is worked directly.
        val off = c.copy(transverters = c.transverters.map { it.copy(enabled = false) })
        assertEquals(28_150_000, off.realHz(28_150_000))
        assertEquals(c, CatConfig.fromMap(c.toMap(), CatLinkType.USB))
    }
}
