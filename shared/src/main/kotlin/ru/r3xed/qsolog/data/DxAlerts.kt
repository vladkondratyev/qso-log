package ru.r3xed.qsolog.data

/**
 * DX cluster notifications: a spot of a watched callsign ([watch], "*" any letters: "VP8*", "3Y0J"), or one the log
 * marks as new — country, region of Russia, band. The same spot (callsign and band) is reported once per [quietMinutes].
 */
data class DxAlertConfig(
    val enabled: Boolean = false,
    val newCountry: Boolean = true,
    val newRegion: Boolean = false,
    val newBand: Boolean = false,
    val watch: List<String> = emptyList(),
    /** Android: keep the cluster connected in the background (a notification shows it), to be told while elsewhere. */
    val background: Boolean = false,
    val quietMinutes: Int = 30,
) {
    fun toMap(): Map<String, String> = mapOf(
        "dxa_on" to enabled.toString(), "dxa_country" to newCountry.toString(), "dxa_region" to newRegion.toString(),
        "dxa_band" to newBand.toString(), "dxa_watch" to watch.joinToString(" "), "dxa_bg" to background.toString(),
    )

    companion object {
        val KEYS = listOf("dxa_on", "dxa_country", "dxa_region", "dxa_band", "dxa_watch", "dxa_bg")

        fun from(m: Map<String, String?>) = DxAlertConfig(
            enabled = m["dxa_on"] == "true",
            newCountry = m["dxa_country"]?.let { it == "true" } ?: true,
            newRegion = m["dxa_region"] == "true",
            newBand = m["dxa_band"] == "true",
            watch = DxAlerts.parseWatch(m["dxa_watch"].orEmpty()),
            background = m["dxa_bg"] == "true",
        )
    }
}

object DxAlerts {
    fun parseWatch(text: String): List<String> =
        text.split(' ', ',', ';', '\n').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()

    fun watched(call: String, watch: List<String>): Boolean {
        val c = call.uppercase()
        return watch.any { w -> Regex("^" + w.split('*').joinToString(".*") { Regex.escape(it) } + "$").matches(c) }
    }

    /** Why [spot] is worth a notification, or null. */
    fun reason(spot: DxSpot, mark: DxMark, c: DxAlertConfig): String? = when {
        !c.enabled -> null
        watched(spot.call, c.watch) -> ru.r3xed.qsolog.tr("в списке ожидания")
        mark == DxMark.NEW_COUNTRY && c.newCountry -> ru.r3xed.qsolog.tr("новая страна")
        mark == DxMark.NEW_REGION && c.newRegion -> ru.r3xed.qsolog.tr("новая область")
        mark == DxMark.NEW_BAND && c.newBand -> ru.r3xed.qsolog.tr("новый диапазон")
        else -> null
    }

    /** "VP8DEMO 14.025 CW — новая страна (Falkland Islands)" */
    fun text(spot: DxSpot, why: String): String {
        val country = Cty.lookup(spot.call)?.entity?.name
        return listOfNotNull(spot.call, "%.3f".format(java.util.Locale.ROOT, spot.khz / 1000.0), spot.mode).joinToString(" ") +
            " — " + why + (country?.let { " ($it)" } ?: "")
    }
}
