package ru.r3xed.qsolog.data

import java.io.File

/** ADIF fields about the station kept with a history entry (HamQTH's "approximate position" mark included). */
val HISTORY_FIELDS = setOf("STATE", "CNTY", "CQZ", "ITUZ", "DXCC", "CONT", HamQth.POSITION_FIELD)

/** Where the data of a looked-up station came from. Stored by [key]; shown by [title]. */
enum class SearchSource(val key: String, val title: String) {
    QRZ_RU("qrzru", "QRZ.ru"),
    QRZ_RU_SITE("qrzru-site", "QRZ.ru (сайт)"),
    QRZ_COM("qrzcom", "QRZ.com"),
    HAMQTH("hamqth", "HamQTH"),
    MANUAL("manual", "вручную");

    companion object {
        fun of(key: String?): SearchSource = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

/**
 * A station looked up but not logged: what the source said about it (and what the user corrected), kept so a card can
 * be filled without the internet later. One entry per callsign; [searchedAt] is the last time it was looked up.
 */
data class SearchEntry(
    val call: String,
    val name: String = "",
    val qth: String = "",
    val country: String = "",
    val locator: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    /** ADIF fields of the station: STATE, CNTY (RDA), CQZ, ITUZ, DXCC, CONT. */
    val adif: Map<String, String> = emptyMap(),
    val source: SearchSource = SearchSource.MANUAL,
    val searchedAt: Long = System.currentTimeMillis(),
    val note: String = "",
    /** Starred: always at the top of the list. */
    val favorite: Boolean = false,
) {
    val position: LatLon?
        get() = if (lat != null && lon != null) LatLon(lat, lon) else Geo.locatorToLatLon(locator)

    /** Nothing but the callsign: not worth keeping. */
    val isEmpty: Boolean
        get() = name.isBlank() && qth.isBlank() && country.isBlank() && locator.isBlank() && position == null
}

/**
 * The search history in one small JSON file next to the log. Every change is written at once; a damaged or missing
 * file reads as an empty history.
 */
class SearchHistory(private val file: File) {
    private var cache: List<SearchEntry>? = null

    @Synchronized
    fun all(): List<SearchEntry> = cache ?: read().also { cache = it }

    fun get(call: String): SearchEntry? = all().firstOrNull { it.call.equals(call.trim(), ignoreCase = true) }

    /** Adds the station or replaces its entry (the note typed before is kept unless [e] has its own). */
    @Synchronized
    fun put(e: SearchEntry) {
        val old = get(e.call)
        val entry = e.copy(call = e.call.trim().uppercase(), note = e.note.ifBlank { old?.note.orEmpty() }, favorite = e.favorite || old?.favorite == true)
        write(listOf(entry) + all().filter { !it.call.equals(entry.call, ignoreCase = true) })
    }

    /** Changes the given callsigns with [change]; returns how many entries changed. */
    @Synchronized
    fun update(calls: Collection<String>, change: (SearchEntry) -> SearchEntry): Int {
        val keys = calls.map { it.uppercase() }.toSet()
        var n = 0
        write(all().map { if (it.call in keys) { n++; change(it) } else it })
        return n
    }

    @Synchronized
    fun delete(calls: Collection<String>) {
        val keys = calls.map { it.uppercase() }.toSet()
        write(all().filter { it.call !in keys })
    }

    @Synchronized
    fun clear() = write(emptyList())

    private fun write(list: List<SearchEntry>) {
        val sorted = list.sortedByDescending { it.searchedAt }
        cache = sorted
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(MiniJson.encode(mapOf("version" to 1, "entries" to sorted.map(::toJson))))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun read(): List<SearchEntry> {
        if (!file.isFile) return emptyList()
        val json = runCatching { MiniJson.parse(file.readText()) }.getOrNull() as? Map<*, *> ?: return emptyList()
        return (json["entries"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::fromJson) }
            .sortedByDescending { it.searchedAt }
    }

    private fun toJson(e: SearchEntry): Map<String, Any?> = mapOf(
        "call" to e.call, "name" to e.name, "qth" to e.qth, "country" to e.country, "locator" to e.locator,
        "lat" to e.lat, "lon" to e.lon, "adif" to e.adif, "source" to e.source.key, "at" to e.searchedAt, "note" to e.note, "favorite" to e.favorite,
    )

    private fun fromJson(m: Map<*, *>): SearchEntry? {
        val call = (m["call"] as? String)?.ifBlank { null } ?: return null
        fun s(k: String) = m[k] as? String ?: ""
        return SearchEntry(
            call = call, name = s("name"), qth = s("qth"), country = s("country"), locator = s("locator"),
            lat = (m["lat"] as? Number)?.toDouble(), lon = (m["lon"] as? Number)?.toDouble(),
            adif = (m["adif"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap(),
            source = SearchSource.of(m["source"] as? String),
            searchedAt = (m["at"] as? Number)?.toLong() ?: 0L,
            note = s("note"),
            favorite = m["favorite"] == true,
        )
    }
}

/** How the history list is ordered (starred entries always come first). */
enum class HistorySort { DATE, CALL, DISTANCE, COUNTRY }

/** What the history list shows: a text to find, one source, only starred, only with a known position. */
data class HistoryFilter(
    val text: String = "",
    val source: SearchSource? = null,
    val favoritesOnly: Boolean = false,
    val withPosition: Boolean = false,
) {
    val active: Boolean get() = text.isNotBlank() || source != null || favoritesOnly || withPosition
}

object HistoryView {
    /** [all] filtered and sorted; starred first. Distance needs [me]; entries without a position go last. */
    fun of(all: List<SearchEntry>, f: HistoryFilter, sort: HistorySort, me: LatLon?): List<SearchEntry> {
        val q = f.text.trim().lowercase()
        val shown = all.filter { e ->
            (q.isEmpty() || listOf(e.call, e.name, e.qth, e.country, e.locator, e.note).any { it.lowercase().contains(q) }) &&
                (f.source == null || e.source == f.source) &&
                (!f.favoritesOnly || e.favorite) &&
                (!f.withPosition || e.position != null)
        }
        val order: Comparator<SearchEntry> = when (sort) {
            HistorySort.DATE -> compareByDescending { it.searchedAt }
            HistorySort.CALL -> compareBy { it.call }
            HistorySort.COUNTRY -> compareBy<SearchEntry> { it.country.ifBlank { "\uFFFF" } }.thenBy { it.call }
            HistorySort.DISTANCE -> compareBy { e -> e.position?.let { p -> me?.let { Geo.distanceKm(it, p) } } ?: Double.MAX_VALUE }
        }
        return shown.sortedWith(compareByDescending<SearchEntry> { it.favorite }.then(order))
    }
}
