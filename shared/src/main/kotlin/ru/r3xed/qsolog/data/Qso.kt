package ru.r3xed.qsolog.data

/** One radio contact. Time is stored as epoch milliseconds in UTC. */
data class Qso(
    val id: Long = 0,
    /**
     * The contact's identity across devices (a random UUID), unlike [id], which is the row number in this device's
     * database. Kept in ADIF and CSV (APP_QSOLOG_UID) so a contact moved by file stays the same contact.
     */
    val uid: String = "",
    val call: String,
    val timeUtc: Long,
    val band: String = "",
    val mode: String = "",
    val freqMhz: String = "",
    val rstSent: String = "",
    val rstRcvd: String = "",
    val name: String = "",
    val qth: String = "",
    val country: String = "",
    val locator: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val distanceKm: Double? = null,
    val bearing: Double? = null,
    val power: String = "",
    val qslSent: Boolean = false,
    val qslRcvd: Boolean = false,
    val comment: String = "",
    val myCall: String = "",
    val myLocator: String = "",
    /** File name of the voice note in the app's audio folder, or empty. Stays on the device; not exported to CSV. */
    val audio: String = "",
    /** ADIF fields without a dedicated property (STATE, CQZ, MY_RIG, …), kept so nothing is lost on import/export. */
    val adif: Map<String, String> = emptyMap(),
    /** Saved while QRZ.ru could not be reached; the log offers a button to fetch the station data again. */
    val pendingLookup: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Distance of this record is from the region centre (HamQTH), not from the station's QTH. */
val Qso.approxPosition get() = adif[HamQth.POSITION_FIELD] == HamQth.POSITION_REGION

/**
 * How many times we worked a station, the most recent contact, and the most recent one on each band
 * ([byBand], low to high frequency).
 */
data class CallHistory(val count: Int, val last: Qso?, val byBand: List<Qso> = emptyList()) {
    companion object {
        /** From the station's contacts, newest first. */
        fun of(newestFirst: List<Qso>) = CallHistory(
            newestFirst.size,
            newestFirst.firstOrNull(),
            newestFirst.filter { it.band.isNotBlank() }.distinctBy { it.band.lowercase() }
                .sortedBy { q -> BANDS.indexOf(q.band.lowercase()).let { if (it < 0) Int.MAX_VALUE else it } },
        )
    }
}

/** Every band the app knows, low to high frequency. Which ones show as buttons is chosen in the settings. */
val BANDS = listOf(
    "2200m", "630m", "160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m",
    "6m", "4m", "2m", "70cm", "23cm",
)
/** Bands switched on out of the box. */
val DEFAULT_BANDS = listOf("160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m", "2m", "70cm")

/** Every mode the app knows (ADIF names; FT4, PSK31, JS8, Q65, C4FM, DMR, DSTAR are written as SUBMODE). */
val MODES = listOf(
    "SSB", "CW", "FT8", "FT4", "RTTY", "PSK31", "AM", "FM", "DIGI",
    "JT65", "JS8", "Q65", "OLIVIA", "SSTV", "C4FM", "DMR", "DSTAR",
)
val DEFAULT_MODES = listOf("SSB", "CW", "FT8", "FT4", "RTTY", "PSK31", "AM", "FM", "DIGI")

/** Band for a frequency in MHz, or null when outside the amateur allocations. */
fun bandForFreq(mhz: Double): String? = when (mhz) {
    in 0.1357..0.1378 -> "2200m"
    in 0.472..0.479 -> "630m"
    in 1.8..2.0 -> "160m"
    in 3.5..4.0 -> "80m"
    in 5.25..5.45 -> "60m"
    in 7.0..7.3 -> "40m"
    in 10.1..10.15 -> "30m"
    in 14.0..14.35 -> "20m"
    in 18.068..18.168 -> "17m"
    in 21.0..21.45 -> "15m"
    in 24.89..24.99 -> "12m"
    in 28.0..29.7 -> "10m"
    in 50.0..54.0 -> "6m"
    in 70.0..70.5 -> "4m"
    in 144.0..148.0 -> "2m"
    in 430.0..440.0 -> "70cm"
    in 1240.0..1300.0 -> "23cm"
    else -> null
}

/**
 * The typed frequency in MHz. A number that is no amateur band in MHz but is one in kHz ("14195", "7074.5", "1840")
 * is taken as kHz, as most loggers do; "1296.2" stays MHz (23 cm).
 */
fun freqMhz(typed: String): Double? {
    val v = typed.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return if (v >= 1000 && bandForFreq(v) == null && bandForFreq(v / 1000) != null) v / 1000 else v
}

/** The frequency as stored, always MHz: "14195" → "14.195", "14,195" → "14.195"; anything else as typed. */
fun normalizeFreq(typed: String): String {
    val t = typed.trim().replace(',', '.')
    val v = t.toDoubleOrNull() ?: return t
    val mhz = freqMhz(t) ?: return t
    return if (mhz == v) t else java.math.BigDecimal(t).movePointLeft(3).stripTrailingZeros().toPlainString()
}

fun defaultRst(mode: String): String = when (mode) {
    "CW", "RTTY", "PSK31", "OLIVIA" -> "599"
    "FT8", "FT4", "DIGI", "JT65", "JS8", "Q65" -> "-10"
    else -> "59"
}

/**
 * Fills coordinates, distance and bearing that a row (e.g. from CSV) is missing but can be derived from locators.
 * My position is taken from the row's own my_locator, falling back to [myPosition].
 */
fun Qso.withDistance(myPosition: LatLon?): Qso {
    val la = lat
    val lo = lon
    val them = if (la != null && lo != null) LatLon(la, lo) else Geo.locatorToLatLon(locator) ?: return this
    val me = Geo.locatorToLatLon(myLocator) ?: myPosition ?: return copy(lat = them.lat, lon = them.lon)
    return copy(
        lat = them.lat, lon = them.lon,
        distanceKm = distanceKm ?: Geo.distanceKm(me, them),
        bearing = bearing ?: Geo.bearing(me, them),
    )
}
