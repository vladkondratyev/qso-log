package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HamQthTest {
    @Test
    fun regionAndPositionAreParsed() {
        val json = """{"callsign":"R9DEMO", "name":"Russia (Asiatic)", "details":"Russia (Asiatic), Sverdlovskaya oblast' (SV), 2nd Class", "continent":"AS", "utc":"-6", "waz":"17", "itu":"30", "lat":"58.7", "lng":"61.33", "adif":"15"}"""
        val d = HamQth.parse(json)!!
        assertEquals("Russia (Asiatic)", d.country)
        assertEquals("Sverdlovskaya oblast' (SV)", d.region)
        assertEquals("17", d.cqZone)
        assertEquals("30", d.ituZone)
        assertEquals("15", d.dxcc)
        assertEquals(LatLon(58.7, 61.33), d.position)
    }

    @Test
    fun countryWithoutRegion() {
        val d = HamQth.parse("""{"callsign":"DL1DEMO", "name":"Germany", "details":"Germany", "continent":"EU", "waz":"14", "itu":"28", "lat":"51", "lng":"10", "adif":"230"}""")!!
        assertEquals("", d.region)
        assertEquals("Germany", d.country)
    }

    @Test
    fun unknownPrefix() {
        assertNull(HamQth.parse("""{"callsign":"ZZ9DEMO", "name":"", "details":""}"""))
    }
}
