package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompactTest {
    @Test
    fun compactMapKeepsOrderAndEquality() {
        val m = linkedMapOf("STATE" to "MO", "CQZ" to "16", "CNTY" to "MO-05")
        val c = CompactMap.of(m)
        assertEquals(m, c)
        assertEquals(m.hashCode(), c.hashCode())
        assertEquals(listOf("STATE", "CQZ", "CNTY"), c.keys.toList())
        assertEquals("16", c["CQZ"])
        assertNull(c["IOTA"])
        assertTrue(CompactMap.of(emptyMap()).isEmpty())
        assertEquals(m + ("IOTA" to "EU-001"), c + ("IOTA" to "EU-001"))
        assertEquals(m, Adif.decodeFields(Adif.encodeFields(c)))
    }

    @Test
    fun poolSharesRepeatedTexts() {
        val pool = StringPool()
        val a = compactQso(Qso(call = "UA3DEMO", timeUtc = 0, band = String(charArrayOf('2', '0', 'm')), adif = mapOf("CQZ" to "16")), pool)
        val b = compactQso(Qso(call = "UA3DEMO", timeUtc = 1, band = String(charArrayOf('2', '0', 'm')), adif = mapOf("CQZ" to "16")), pool)
        assertSame(a.band, b.band)
        assertSame(a.adif["CQZ"], b.adif["CQZ"])
        assertEquals("20m", a.band)
    }

    @Test
    fun searchIgnoresCaseInCyrillic() {
        val list = listOf(Qso(call = "UA3DEMO", timeUtc = 0, name = "Иван"), Qso(call = "R1DEMO", timeUtc = 0, qth = "Вологда"))
        assertEquals(listOf("R1DEMO"), searchQsos(list, "вологда").map { it.call })
        assertEquals(listOf("UA3DEMO"), searchQsos(list, " ua3 ").map { it.call })
        assertEquals(2, searchQsos(list, "").size)
    }
}
