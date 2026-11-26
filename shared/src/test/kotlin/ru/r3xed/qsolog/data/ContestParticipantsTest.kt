package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ContestParticipantsTest {
    private fun q(call: String, t: Long, sent: String, rcvd: String, code: String = "RDAC", band: String = "40m", mode: String = "CW") =
        Qso(call = call, timeUtc = t, band = band, mode = mode, adif = mapOf(ContestMode.FIELD to "Y", ContestMode.SENT to sent, ContestMode.RCVD to rcvd, Contest.CODE to code))

    @Test
    fun constantPartWithoutSerials() {
        // My code is constant (my RDA): the station's code is kept whole.
        val log = listOf(q("UA3DEMO", 1, "KG-15", "MO-05"), q("R9DEMO", 2, "KG-15", "SV-30"))
        val list = ContestParticipants.of(log, emptyList(), "RDAC", "40m", "CW")
        assertEquals(listOf("R9DEMO" to "SV-30", "UA3DEMO" to "MO-05"), list.map { it.call to it.exchange })
    }

    @Test
    fun serialNumbersAreDropped() {
        val log = listOf(q("UA3DEMO", 1, "001", "015 MO"), q("R9DEMO", 2, "002", "044 45"), q("R9DEMO", 3, "003", "120 45"))
        val list = ContestParticipants.of(log, emptyList(), "RDAC", "40m", "CW").associate { it.call to it.exchange }
        assertEquals("MO", list["UA3DEMO"])
        // Years on the air, the same in both contacts, stays; the serial number goes.
        assertEquals("45", list["R9DEMO"])
    }

    @Test
    fun dupesAndOtherContests() {
        val old = listOf(q("UA3DEMO", 1, "KG-15", "MO-05"), q("DL1DEMO", 1, "1", "14", code = "WAE"))
        val now = listOf(q("UA3DEMO", 10, "KG-15", "MO-05"), q("R1DEMO", 11, "KG-15", "LO-02", band = "20m"))
        val list = ContestParticipants.of(old + now, now, "RDAC", "40m", "CW")
        assertEquals(listOf("R1DEMO", "UA3DEMO"), list.map { it.call })
        assertEquals(ContestParticipants.Worked.DUPE, list.first { it.call == "UA3DEMO" }.worked)
        assertEquals(ContestParticipants.Worked.OTHER_BAND, list.first { it.call == "R1DEMO" }.worked)
        assertEquals(listOf("R1DEMO"), ContestParticipants.filter(list, "r1").map { it.call })
    }
}
