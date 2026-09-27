package ru.r3xed.qsolog.data

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceCalcTest {
    private fun near(expected: Double, actual: Double, eps: Double = 0.01) = assertTrue(abs(expected - actual) < eps, "$expected ≈ $actual")

    @Test
    fun levels() {
        near(30.0, RfCalc.wattsToDbm(1.0)); near(50.0, RfCalc.wattsToDbm(100.0)); near(0.0, RfCalc.wattsToDbw(1.0))
        near(100.0, RfCalc.dbmToWatts(50.0))
        near(-73.0, RfCalc.sMeterDbm(9, vhf = false)); near(-121.0, RfCalc.sMeterDbm(1, vhf = false)); near(-93.0, RfCalc.sMeterDbm(9, vhf = true))
        // S9 on HF is about 50 µV into 50 Ω.
        near(50.0, RfCalc.dbmToMicrovolts(-73.0), 0.2)
        // 100 W, 6 dBi, 2 dB of cable: +4 dB ≈ 251 W.
        near(251.19, RfCalc.eirpWatts(100.0, 6.0, 2.0), 0.1)
    }

    @Test
    fun cablesAndSwr() {
        val lmr = Reference.CABLES.first { it.name == "LMR-400" }
        near(4.9, RfCalc.attenuationPer100m(lmr, 144.0))
        val at145 = RfCalc.attenuationPer100m(lmr, 145.0)
        assertTrue(at145 > 4.9 && at145 < 5.0)
        near(50.0, RfCalc.powerAfterLoss(100.0, 3.0103), 0.01)
        // Below the table: √f from the first point (Aircell 7: 2.2 dB at 10 MHz → 1.32 at 3.6).
        near(1.32, RfCalc.attenuationPer100m(Reference.CABLES.first { it.name == "Aircell 7" }, 3.6), 0.01)
        val s2 = RfCalc.swr(2.0)
        near(0.3333, s2.gamma, 0.001); near(0.1111, s2.reflected, 0.001); near(9.54, s2.returnLossDb); near(0.51, s2.mismatchLossDb)
    }

    @Test
    fun antennas() {
        val a = RfCalc.antenna(14.2, 0.95)
        near(21.112, a.wavelength, 0.01)
        near(10.028, a.dipole, 0.01)
        near(3.48, RfCalc.coaxQuarter(14.2, 0.66), 0.01)
    }

    @Test
    fun beacons() {
        // 00:00:00 UTC: 4U1UN on 14.100, YV5B (last) on 18.110; 10 s later VE8AT on 14.100.
        assertEquals(0, Reference.ncdxfBeacon(0, 0))
        assertEquals(17, Reference.ncdxfBeacon(0, 1))
        assertEquals(1, Reference.ncdxfBeacon(10, 0))
        assertEquals(0, Reference.ncdxfBeacon(40, 4))
    }

    @Test
    fun countryFile() {
        assertEquals("European Russia", Cty.lookup("RA3XDEMO")!!.entity.name)
        assertEquals("Asiatic Russia", Cty.lookup("R9DEMO")!!.entity.name)
        assertEquals("Kaliningrad", Cty.lookup("UA2FDEMO")!!.entity.name)
        assertEquals("Fed. Rep. of Germany", Cty.lookup("DL1DEMO")!!.entity.name)
        assertEquals("Fed. Rep. of Germany", Cty.lookup("DL/RA3XDEMO")!!.entity.name)
        assertEquals("European Russia", Cty.lookup("RA3XDEMO/P")!!.entity.name)
        assertEquals("EU", Cty.lookup("OH2B")!!.entity.continent)
        assertTrue(Cty.entities.size > 300)
    }

    @Test
    fun russianRegions() {
        assertEquals("KG", RussianRegions.of("RA3XDEMO")!!.rda)
        assertEquals("KG", RussianRegions.of("UA3XYZ")!!.rda)
        assertEquals("MA", RussianRegions.of("R2A")?.rda ?: RussianRegions.of("RA2ADEMO")!!.rda)
        assertEquals("KA", RussianRegions.of("UA2FDEMO")!!.rda)
        assertEquals("BA", RussianRegions.of("RW9WDEMO")!!.rda)
        assertEquals("GA", RussianRegions.of("RA9ZDEMO")!!.rda)
        assertNull(RussianRegions.of("DL1DEMO"))
        assertEquals(89, RussianRegions.ALL.size)
    }
}
