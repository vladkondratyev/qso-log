package ru.r3xed.qsolog.data

/**
 * One copy of each repeated text while a whole log is read: bands, modes, my callsign, countries, ADIF field names and
 * values repeat in almost every row, and a log of 100 000 contacts kept every copy (about 3 KB a contact; the Windows
 * build closed with OutOfMemoryError). Not thread-safe: one pool per read.
 */
class StringPool {
    private val map = HashMap<String, String>(4096)

    /** [s], or the equal string seen before. Long texts (comments) are rarely repeated and are kept as they are. */
    fun of(s: String): String {
        if (s.isEmpty()) return ""
        if (s.length > 64) return s
        return map.putIfAbsent(s, s) ?: s
    }
}

/**
 * A read-only map in two arrays, in the order the fields were added. A contact's ADIF extras are a dozen short fields;
 * a LinkedHashMap spends about 50 bytes of bookkeeping on each, these arrays 8. Lookups are a short linear scan.
 */
class CompactMap private constructor(private val ks: Array<String>, private val vs: Array<String>) : AbstractMap<String, String>() {
    override val size: Int get() = ks.size
    override fun isEmpty(): Boolean = ks.isEmpty()
    override fun containsKey(key: String): Boolean = ks.indexOf(key) >= 0
    override fun get(key: String): String? = ks.indexOf(key).let { if (it < 0) null else vs[it] }

    override val entries: Set<Map.Entry<String, String>>
        get() = object : AbstractSet<Map.Entry<String, String>>() {
            override val size: Int get() = ks.size
            override fun iterator(): Iterator<Map.Entry<String, String>> = object : Iterator<Map.Entry<String, String>> {
                private var i = 0
                override fun hasNext() = i < ks.size
                override fun next(): Map.Entry<String, String> {
                    if (i >= ks.size) throw NoSuchElementException()
                    val k = ks[i]; val v = vs[i]; i++
                    return java.util.AbstractMap.SimpleImmutableEntry(k, v)
                }
            }
        }

    companion object {
        /** [m] in arrays (texts through [pool] when given); an empty map stays the shared empty one. */
        fun of(m: Map<String, String>, pool: StringPool? = null): Map<String, String> {
            if (m.isEmpty()) return emptyMap()
            val k = arrayOfNulls<String>(m.size)
            val v = arrayOfNulls<String>(m.size)
            var i = 0
            for ((key, value) in m) {
                k[i] = pool?.of(key) ?: key
                v[i] = pool?.of(value) ?: value
                i++
            }
            @Suppress("UNCHECKED_CAST")
            return CompactMap(k as Array<String>, v as Array<String>)
        }
    }
}

/** [q] with its repeated texts shared through [pool] and its ADIF extras in a [CompactMap]. */
fun compactQso(q: Qso, pool: StringPool): Qso = q.copy(
    call = pool.of(q.call), band = pool.of(q.band), mode = pool.of(q.mode), freqMhz = pool.of(q.freqMhz),
    rstSent = pool.of(q.rstSent), rstRcvd = pool.of(q.rstRcvd), name = pool.of(q.name), qth = pool.of(q.qth),
    country = pool.of(q.country), locator = pool.of(q.locator), power = pool.of(q.power), comment = pool.of(q.comment),
    myCall = pool.of(q.myCall), myLocator = pool.of(q.myLocator), audio = pool.of(q.audio),
    adif = CompactMap.of(q.adif, pool),
)

/** The contacts of [list] matching [query] (callsign, name, QTH, country, locator, comment, band, mode, frequency). */
fun searchQsos(list: List<Qso>, query: String): List<Qso> {
    // Matched here: SQLite's LIKE ignores case only for Latin letters.
    val n = query.trim()
    if (n.isEmpty()) return list
    return list.filter { q ->
        q.call.contains(n, true) || q.name.contains(n, true) || q.qth.contains(n, true) ||
            q.country.contains(n, true) || q.locator.contains(n, true) || q.comment.contains(n, true) ||
            q.band.contains(n, true) || q.mode.contains(n, true) || q.freqMhz.contains(n, true)
    }
}
