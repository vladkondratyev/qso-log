package ru.r3xed.qsolog.data

import kotlin.test.Test
import kotlin.test.assertEquals

class MyStationsTest {
    @Test
    fun roundTripKeepsEveryField() {
        val list = listOf(
            MyStation("1", "Дом", "UA3DEMO", "KO85ts", "Москва, ул. Тверская & Ко", "100", mapOf("MY_RIG" to "IC-7300", "OPERATOR" to "UA3DEMO")),
            MyStation("2", "Выезд", "UA3DEMO/P", "", "", "10", autoLocator = true),
        )
        assertEquals(list, MyStations.decode(MyStations.encode(list)))
        assertEquals(emptyList(), MyStations.decode(""))
    }

    @Test
    fun applyAndOfMoveTheStationThroughTheSettings() {
        val s = StationSettings(myCall = "UA3DEMO", myLocator = "KO85ts", qrzLogin = "x")
        val club = MyStation("2", "Клуб", "RK3DEMO", "KO85uq", "Москва", "100", mapOf("MY_RIG" to "TS-590"))
        val t = MyStations.apply(s, club)
        assertEquals("RK3DEMO", t.myCall)
        assertEquals("x", t.qrzLogin)
        assertEquals(club, MyStations.of(t, "2", "Клуб"))
    }

    @Test
    fun callsAreDistinct() {
        assertEquals(listOf("UA3DEMO", "UA3DEMO/P"), MyStations.calls(listOf(MyStation("1", call = "ua3demo"), MyStation("2", call = "UA3DEMO"), MyStation("3", call = "UA3DEMO/P"), MyStation("4"))))
    }
}

class UploadCallsTest {
    private fun q(id: Long, my: String, loc: String = "") = Qso(id = id, call = "DL1DEMO", timeUtc = 0, myCall = my, myLocator = loc)

    @Test
    fun onlyTheAccountsCallsign() {
        val (ok, other) = UploadCalls.forAccount(listOf(q(1, "UA3DEMO"), q(2, "ua3demo"), q(3, ""), q(4, "UA3DEMO/P")), "UA3DEMO")
        assertEquals(listOf(1L, 2L, 3L), ok.map { it.id })
        assertEquals(1, other)
        assertEquals(4, UploadCalls.forAccount(listOf(q(1, "A"), q(2, "B"), q(3, "C"), q(4, "D")), "").first.size)
    }

    @Test
    fun lotwPerLocator() = kotlinx.coroutines.runBlocking {
        val seen = mutableListOf<String>()
        val r = UploadCalls.byLocator(listOf(q(1, "X", "KO85ts"), q(2, "X", ""), q(3, "X", "KO95ab")), "KO85ts") { loc, part ->
            seen += loc + ":" + part.size
            UploadResult(part, 1, null)
        }
        assertEquals(listOf("KO85ts:2", "KO95ab:1"), seen)
        assertEquals(3, r.sent.size)
        assertEquals(2, r.duplicates)
    }
}
