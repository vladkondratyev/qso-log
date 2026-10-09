package ru.r3xed.qsolog.data

/**
 * A command typed into the callsign field instead of a callsign and confirmed with Enter, for keyboards without
 * F keys or comfortable Alt combinations: "20m" or "20" switches the band, "CW" the mode, "14195" or "7.074" sets the
 * frequency (and the band with it). A callsign never looks like any of these: it has letters and digits mixed.
 */
sealed class CallCommand {
    data class Band(val band: String) : CallCommand()
    data class Mode(val mode: String) : CallCommand()
    data class Freq(val freq: String) : CallCommand()

    companion object {
        fun parse(typed: String): CallCommand? {
            val t = typed.trim().uppercase()
            if (t.isEmpty()) return null
            BANDS.firstOrNull { it.equals(t, ignoreCase = true) }?.let { return Band(it) }
            // "20" alone: a band in metres, as said on the air; "70" means 70 cm.
            if (t.all { it.isDigit() } && t.length <= 3) {
                val band = if (t == "70") "70cm" else "${t}m"
                if (band in BANDS) return Band(band)
            }
            MODES.firstOrNull { it == t }?.let { return Mode(it) }
            if (t == "USB" || t == "LSB") return Mode("SSB")
            // A frequency in kHz (14195) or MHz (14.195, 7,074) inside a band.
            if (t.all { it.isDigit() || it == '.' || it == ',' } && t.any { it.isDigit() }) {
                val mhz = freqMhz(t) ?: return null
                if (bandForFreq(mhz) != null) return Freq(normalizeFreq(t))
            }
            return null
        }
    }
}
