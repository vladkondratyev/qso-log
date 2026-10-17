package ru.r3xed.qsolog.data

/**
 * Contacts entered in contest mode: flagged with an app field, the exchanged numbers in the standard ADIF fields
 * that ЕРМАК / Cabrillo reports read ([SENT], [RCVD]).
 */
object ContestMode {
    const val FIELD = "APP_QSOLOG_CONTEST"
    const val SENT = "STX_STRING"
    const val RCVD = "SRX_STRING"

    /** VHF contests: the QTH locators were exchanged too; the report puts them after the numbers. */
    const val GRIDS = "APP_QSOLOG_GRID_EXCH"

    fun isContest(adif: Map<String, String>) = adif[FIELD] == "Y"

    fun hasGrids(adif: Map<String, String>) = adif[GRIDS] == "Y"

    /**
     * The locator [call] gave last: in this contest ([contestRef]) first, then anywhere in the log. A station in a VHF
     * contest works from the same square all tours, so the next tour's card fills it in. Null — none known.
     */
    fun previousLocator(call: String, log: List<Qso>, contestRef: String?): String? {
        if (call.length < 3) return null
        val mine = log.filter { it.call.equals(call, ignoreCase = true) && Geo.isLocator(it.locator) }
        val pick = mine.filter { contestRef != null && it.adif[Contest.REF] == contestRef }.maxByOrNull { it.timeUtc }
            ?: mine.maxByOrNull { it.timeUtc }
        return pick?.locator?.let { gridCase(it) }
    }

    /** "lo65FR" → "LO65fr": the usual way a locator is written. */
    fun gridCase(s: String): String {
        val t = s.trim()
        return if (t.length > 4) t.substring(0, 4).uppercase() + t.substring(4).lowercase() else t.uppercase()
    }

    /** The number as it is sent: at least three digits, 001. */
    fun serial(n: Int): String = "%03d".format(n)

    /** The number the next contact sends: the one just sent plus one, or [current] plus one when it was not a number. */
    fun nextSerial(sent: String, current: Int): Int = (sent.trim().toIntOrNull() ?: current) + 1
}
