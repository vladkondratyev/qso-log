package ru.r3xed.qsolog.data

/**
 * Countries (DXCC entities) by callsign prefix from the country file cty.dat by Jim Reisert AD1C
 * (https://www.country-files.com), bundled as a resource. Works without the internet.
 */
object Cty {
    data class Entity(
        val name: String, val cqZone: Int, val ituZone: Int, val continent: String,
        val lat: Double, val lon: Double, val utcOffset: Double, val prefix: String,
    )

    /** A match: the entity plus zones that a prefix or an exact callsign may override. */
    data class Hit(val entity: Entity, val matched: String, val cqZone: Int, val ituZone: Int)

    private class Rule(val entity: Entity, val cq: Int?, val itu: Int?)

    private val exact = HashMap<String, Rule>()
    private val prefixes = HashMap<String, Rule>()
    @Volatile private var loaded = false

    val entities = mutableListOf<Entity>()

    private val CQ = Regex("\\((\\d+)\\)")
    private val ITU = Regex("\\[(\\d+)\\]")
    private val OVERRIDES = Regex("\\(\\d+\\)|\\[\\d+\\]|<[^>]*>|\\{[^}]*\\}|~[^~]*~")

    /** Reads the file from [text] (tests) or the bundled resource. */
    @Synchronized
    fun load(text: String? = null) {
        if (loaded && text == null) return
        exact.clear(); prefixes.clear(); entities.clear()
        val src = text ?: Cty::class.java.getResourceAsStream("/cty.dat")?.bufferedReader(Charsets.ISO_8859_1)?.readText() ?: ""
        // Records: 8 header fields separated by ':', then aliases up to ';'.
        for (record in src.split(';')) {
            val parts = record.split(':')
            if (parts.size < 9) continue
            val f = parts.map { it.trim() }
            val e = Entity(
                name = f[0], cqZone = f[1].toIntOrNull() ?: 0, ituZone = f[2].toIntOrNull() ?: 0, continent = f[3],
                // cty.dat stores west longitude as positive.
                lat = f[4].toDoubleOrNull() ?: 0.0, lon = -(f[5].toDoubleOrNull() ?: 0.0), utcOffset = -(f[6].toDoubleOrNull() ?: 0.0),
                prefix = f[7].removePrefix("*"),
            )
            entities += e
            for (alias in parts.drop(8).joinToString(":").split(',')) {
                var a = alias.trim()
                if (a.isEmpty()) continue
                // Most aliases are a bare prefix: the patterns run only on the ones with overrides.
                val cq = if ('(' in a) CQ.find(a)?.groupValues?.get(1)?.toIntOrNull() else null
                val itu = if ('[' in a) ITU.find(a)?.groupValues?.get(1)?.toIntOrNull() else null
                if (a.any { it in "([<{~" }) a = a.replace(OVERRIDES, "").trim()
                val rule = Rule(e, cq, itu)
                if (a.startsWith("=")) exact[a.substring(1)] = rule else prefixes.putIfAbsent(a, rule)
            }
        }
        loaded = true
    }

    /** Portable forms: "DL/R0DEMO" → DL, "R0DEMO/P" → R0DEMO, "R0DEMO/9" → R9…; the part that decides the country. */
    fun effective(call: String): String {
        val parts = call.uppercase().trim().split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return parts.firstOrNull().orEmpty()
        val skip = setOf("P", "M", "MM", "AM", "QRP", "A", "LH")
        val meaningful = parts.filter { it !in skip }
        if (meaningful.size == 1) return meaningful[0]
        val (a, b) = meaningful[0] to meaningful[1]
        // A single digit after the call moves it to that call area: R0DEMO/3 → R3DEMO.
        if (b.length == 1 && b[0].isDigit()) return a.replaceFirst(Regex("\\d"), b)
        return if (a.length <= b.length) a else b
    }

    fun lookup(call: String): Hit? {
        load()
        val raw = call.uppercase().trim()
        exact[raw]?.let { return Hit(it.entity, raw, it.cq ?: it.entity.cqZone, it.itu ?: it.entity.ituZone) }
        val c = effective(raw)
        if (c.isEmpty()) return null
        exact[c]?.let { return Hit(it.entity, c, it.cq ?: it.entity.cqZone, it.itu ?: it.entity.ituZone) }
        for (len in c.length downTo 1) {
            val p = c.substring(0, len)
            prefixes[p]?.let { return Hit(it.entity, p, it.cq ?: it.entity.cqZone, it.itu ?: it.entity.ituZone) }
        }
        return null
    }
}
