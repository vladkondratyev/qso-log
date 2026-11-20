package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formats a contact can be exported to. When the user asks for it, an export marks each exported contact with the
 * UTC date and time in our own ADIF field [field], so the card shows it and the next export can take only new contacts.
 */
enum class ExportFormat(val field: String) {
    ADIF("APP_QSOLOG_EXPORT_ADIF"),
    CSV("APP_QSOLOG_EXPORT_CSV"),
    CONTEST("APP_QSOLOG_EXPORT_CONTEST"),
    ;

    val title: String get() = when (this) {
        ADIF -> "ADIF"
        CSV -> "CSV"
        CONTEST -> tr("ЕРМАК / Cabrillo")
    }

    fun isExported(adif: Map<String, String>): Boolean = !adif[field].isNullOrBlank()

    fun mark(q: Qso, now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): Qso =
        q.copy(adif = q.adif + (field to now.format(STORED)), updatedAt = System.currentTimeMillis())

    /** The contact without this mark, so the next "only new" export takes it again. */
    fun unmarkFields(adif: Map<String, String>): Map<String, String> = adif - field

    /** "27.09.2026 10:15 UTC", or null when the contact has not been exported to this format. */
    fun exportedAt(adif: Map<String, String>): String? {
        val v = adif[field]?.trim().orEmpty()
        if (v.isEmpty()) return null
        return runCatching { LocalDateTime.parse(v, STORED).format(SHOWN) + " UTC" }.getOrDefault(v)
    }

    companion object {
        private val STORED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val SHOWN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        /** The mark fields: the card shows them in their own block, not among the other ADIF fields. */
        val FIELDS: Set<String> = entries.map { it.field }.toSet()

        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

        /**
         * The file name offered for an export: "UA3DEMO_2026-11-15_164059.adi" (local date and time of the export), so a
         * folder sorted by name lists the files in the order they were made and two exports of one day differ.
         * [kind] goes between the callsign and the date ("UA3DEMO_lotw_2026-11-15_164059.adi").
         */
        fun fileName(myCall: String, ext: String, kind: String = "", now: LocalDateTime = LocalDateTime.now()): String {
            val call = myCall.trim().ifBlank { "log" }.replace('/', '-').replace('\\', '-')
            return call + (if (kind.isEmpty()) "" else "_$kind") + "_" + now.format(NAME_TIME) + "." + ext
        }
    }
}
