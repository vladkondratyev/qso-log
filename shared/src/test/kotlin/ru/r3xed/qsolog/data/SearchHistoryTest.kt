package ru.r3xed.qsolog.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchHistoryTest {
    @Test
    fun putUpdateDeleteAndReadBack() {
        val file = Files.createTempDirectory("sh").resolve("search_history.json").toFile()
        val h = SearchHistory(file)
        h.put(SearchEntry("r9demo", name = "Иван", qth = "Демоград", lat = 56.8, lon = 60.6, source = SearchSource.QRZ_RU, searchedAt = 2))
        h.put(SearchEntry("DL1DEMO", country = "Germany", locator = "JO62qm", source = SearchSource.QRZ_COM, searchedAt = 3, adif = mapOf("CQZ" to "14")))
        // The same station again: one entry, the note kept.
        h.update(listOf("R9DEMO")) { it.copy(note = "QSL via bureau") }
        h.put(SearchEntry("R9DEMO", name = "Иван", source = SearchSource.HAMQTH, searchedAt = 5))

        val again = SearchHistory(file).all()
        assertEquals(listOf("R9DEMO", "DL1DEMO"), again.map { it.call })
        assertEquals("QSL via bureau", again[0].note)
        assertEquals(SearchSource.HAMQTH, again[0].source)
        assertEquals("14", again[1].adif["CQZ"])
        assertEquals(52.5, again[1].position!!.lat, 0.1)

        assertEquals(2, h.update(listOf("r9demo", "dl1demo")) { it.copy(qth = "Москва") })
        h.delete(listOf("dl1demo"))
        assertNull(SearchHistory(file).get("DL1DEMO"))
        assertEquals("Москва", SearchHistory(file).get("r9demo")!!.qth)
    }

    @Test
    fun brokenFileIsEmpty() {
        val file = Files.createTempDirectory("sh").resolve("search_history.json").toFile()
        file.writeText("{not json")
        assertEquals(emptyList(), SearchHistory(file).all())
    }

    @Test
    fun starredFirstThenSortAndFilter() {
        val me = LatLon(55.77, 37.62)
        val list = listOf(
            SearchEntry("R9DEMO", qth = "Демоград", lat = 56.8, lon = 60.6, source = SearchSource.QRZ_RU, searchedAt = 3),
            SearchEntry("DL1DEMO", locator = "JO62qm", source = SearchSource.QRZ_COM, searchedAt = 2, favorite = true),
            SearchEntry("R3DEMO", locator = "KO85ts", source = SearchSource.QRZ_RU, searchedAt = 1),
            SearchEntry("JA1DEMO", source = SearchSource.HAMQTH, searchedAt = 4),
        )
        assertEquals(listOf("DL1DEMO", "JA1DEMO", "R9DEMO", "R3DEMO"), HistoryView.of(list, HistoryFilter(), HistorySort.DATE, me).map { it.call })
        assertEquals(listOf("DL1DEMO", "R3DEMO", "R9DEMO", "JA1DEMO"), HistoryView.of(list, HistoryFilter(), HistorySort.DISTANCE, me).map { it.call })
        assertEquals(listOf("R9DEMO", "R3DEMO"), HistoryView.of(list, HistoryFilter(source = SearchSource.QRZ_RU), HistorySort.DATE, me).map { it.call })
        assertEquals(listOf("DL1DEMO"), HistoryView.of(list, HistoryFilter(favoritesOnly = true), HistorySort.CALL, me).map { it.call })
        assertEquals(3, HistoryView.of(list, HistoryFilter(withPosition = true), HistorySort.CALL, me).size)
        assertEquals(listOf("R9DEMO"), HistoryView.of(list, HistoryFilter(text = "демо"), HistorySort.CALL, me).map { it.call })
    }
}
