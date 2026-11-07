package ru.r3xed.qsolog.data

import java.net.URLDecoder
import java.net.URLEncoder

/** Programs one activates from the field: their ADIF fields of one's own reference (ADIF 3.1.4). */
enum class ActivationProgram(val title: String, val sig: String, val refField: String?) {
    POTA("POTA", "POTA", "MY_POTA_REF"),
    WWFF("WWFF", "WWFF", "MY_WWFF_REF"),
    SOTA("SOTA", "SOTA", "MY_SOTA_REF"),
    IOTA("IOTA", "IOTA", "MY_IOTA"),
    RDA("RDA", "RDA", "MY_CNTY"),
    OTHER("Другое", "", null),
}

/**
 * "Выезд": an activation in progress — the program, one's reference ("RU-0001", "KG-15"), when it began. Every new
 * contact gets MY_SIG / MY_SIG_INFO, the program's own field and APP_QSOLOG_ACTIVATION = [id]; the log shows a banner
 * with the count, and "Завершить" exports the activation's contacts as ADIF.
 */
data class Activation(
    val id: String,
    val program: ActivationProgram,
    val reference: String,
    val startedAt: Long,
    /** For [ActivationProgram.OTHER]: the MY_SIG name typed by the operator. */
    val sigName: String = "",
) {
    val sig get() = if (program == ActivationProgram.OTHER) sigName.trim().uppercase() else program.sig

    /** "POTA RU-0001" for the banner and the file name. */
    val title get() = listOf(sig.ifBlank { program.title }, reference.trim().uppercase()).filter { it.isNotBlank() }.joinToString(" ")

    /** The fields a new contact gets. */
    fun fields(): Map<String, String> {
        val ref = reference.trim().uppercase()
        return buildMap {
            if (sig.isNotBlank()) put("MY_SIG", sig)
            if (ref.isNotBlank()) {
                put("MY_SIG_INFO", ref)
                program.refField?.let { put(it, ref) }
            }
            put(FIELD, id)
        }
    }

    fun owns(q: Qso) = q.adif[FIELD] == id

    fun encode(): String = listOf("id" to id, "program" to program.name, "ref" to reference, "at" to startedAt.toString(), "sig" to sigName)
        .joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    companion object {
        const val FIELD = "APP_QSOLOG_ACTIVATION"

        fun decode(text: String?): Activation? {
            if (text.isNullOrBlank()) return null
            val m = text.split('&').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i < 0) null else p.substring(0, i) to URLDecoder.decode(p.substring(i + 1), "UTF-8")
            }.toMap()
            return Activation(
                id = m["id"]?.ifBlank { null } ?: return null,
                program = ActivationProgram.entries.firstOrNull { it.name == m["program"] } ?: ActivationProgram.POTA,
                reference = m["ref"].orEmpty(),
                startedAt = m["at"]?.toLongOrNull() ?: 0L,
                sigName = m["sig"].orEmpty(),
            )
        }
    }
}
