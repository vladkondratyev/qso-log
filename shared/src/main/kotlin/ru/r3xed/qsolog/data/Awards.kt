package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr

/**
 * Award tracking from the log: which countries, zones, states, districts were worked and confirmed (paper QSL, LoTW
 * or eQSL), on which bands, and what is still missing. Counted offline, from the log alone.
 */
enum class Award(val total: Int?) {
    /** DXCC entities by the callsign (cty.dat). */
    DXCC(340),
    /** CQ zones: the contact's CQZ, else the one cty.dat gives the callsign. */
    WAZ(40),
    /** US states: STATE of contacts with the United States, Alaska and Hawaii. */
    WAS(50),
    /** Regions of Russia by the callsign's letter after the digit (Р-100-О style). */
    REGIONS(RussianRegions.ALL.size),
    /** Russian District Award: the contact's district (CNTY "KG-15"). */
    RDA(null);

    val title: String
        get() = when (this) {
            DXCC -> "DXCC"
            WAZ -> "WAZ — " + tr("зоны CQ")
            WAS -> "WAS — " + tr("штаты США")
            REGIONS -> tr("Регионы России")
            RDA -> "RDA — " + tr("районы России")
        }
}

/** One award: worked and confirmed keys, per band (worked, confirmed), and what is missing when the list is finite. */
data class AwardStat(
    val award: Award,
    val worked: Set<String>,
    val confirmed: Set<String>,
    val byBand: List<Triple<String, Int, Int>>,
    val missing: List<String>,
)

object Awards {
    val US_STATES = listOf(
        "AK", "AL", "AR", "AZ", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "IA", "ID", "IL", "IN", "KS", "KY", "LA", "MA", "MD",
        "ME", "MI", "MN", "MO", "MS", "MT", "NC", "ND", "NE", "NH", "NJ", "NM", "NV", "NY", "OH", "OK", "OR", "PA", "RI", "SC",
        "SD", "TN", "TX", "UT", "VA", "VT", "WA", "WI", "WV", "WY",
    )
    private val US = setOf("United States", "Alaska", "Hawaii")
    private val RDA_RE = Regex("^[A-Z]{2}-\\d{2}$")

    fun confirmed(q: Qso) = q.qslRcvd || Lotw.isReceived(q.adif) || Eqsl.isReceived(q.adif)

    /** What [q] counts for [award], or null. */
    fun keyOf(award: Award, q: Qso): String? = when (award) {
        Award.DXCC -> Cty.lookup(q.call)?.entity?.name
        Award.WAZ -> (q.adif["CQZ"]?.trim()?.toIntOrNull() ?: Cty.lookup(q.call)?.cqZone)?.takeIf { it in 1..40 }?.let { "%02d".format(it) }
        Award.WAS -> q.adif["STATE"]?.trim()?.uppercase()?.takeIf { it in US_STATES && Cty.lookup(q.call)?.entity?.name in US }
        Award.REGIONS -> RussianRegions.of(q.call)?.rda
        Award.RDA -> q.adif["CNTY"]?.trim()?.uppercase()?.takeIf { RDA_RE.matches(it) }
    }

    /** The name to show for a [key] of [award]: "Калужская область (KG)", "Zone 16", the state code. */
    fun label(award: Award, key: String): String = when (award) {
        Award.REGIONS -> RussianRegions.ALL.firstOrNull { it.rda == key }?.let { "${it.name} · $key" } ?: key
        Award.WAZ -> tr("Зона %s", key.trimStart('0'))
        else -> key
    }

    fun all(award: Award): List<String> = when (award) {
        Award.DXCC -> { Cty.load(); Cty.entities.map { it.name }.distinct().sorted() }
        Award.WAZ -> (1..40).map { "%02d".format(it) }
        Award.WAS -> US_STATES
        Award.REGIONS -> RussianRegions.ALL.map { it.rda }.distinct()
        Award.RDA -> emptyList()
    }

    fun stat(award: Award, list: List<Qso>, bands: List<String> = BANDS): AwardStat {
        val worked = HashSet<String>()
        val confirmed = HashSet<String>()
        val bandWorked = HashMap<String, HashSet<String>>()
        val bandConfirmed = HashMap<String, HashSet<String>>()
        for (q in list) {
            val k = keyOf(award, q) ?: continue
            val c = confirmed(q)
            worked += k
            if (c) confirmed += k
            val b = q.band.lowercase()
            if (b.isNotBlank()) {
                bandWorked.getOrPut(b) { HashSet() } += k
                if (c) bandConfirmed.getOrPut(b) { HashSet() } += k
            }
        }
        val order = bands.map { it.lowercase() }
        val byBand = bandWorked.keys.sortedBy { order.indexOf(it).let { i -> if (i < 0) 999 else i } }
            .map { Triple(it, bandWorked[it]!!.size, bandConfirmed[it]?.size ?: 0) }
        val missing = all(award).filter { it !in worked }
        return AwardStat(award, worked, confirmed, byBand, missing)
    }
}
