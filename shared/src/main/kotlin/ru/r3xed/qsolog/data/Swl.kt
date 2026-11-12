package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr

/**
 * A short-wave listener's log (SWL). A station of «Мои станции» marked as a listener's has the ADIF field SWL = Y among
 * its fields, so it goes into every contact logged with it; a record then knows by itself that it is a report: the
 * callsign is the station heard, [WITH] — whom it was working, RST — the listener's report. The callsign field of the
 * station takes the listener's number (R3A-1234, UA3-170-1234). Such records do not go to LoTW and the other online
 * logbooks, and print as SWL reports on QSL labels.
 */
object Swl {
    /** ADIF: the contact is a report of a short-wave listener ("Y"). */
    const val FIELD = "SWL"

    /** Whom the heard station was working: ADIF has no field of its own for it. */
    const val WITH = "APP_QSOLOG_SWL_WITH"

    fun isSwl(adif: Map<String, String>): Boolean = adif[FIELD].equals("Y", ignoreCase = true)

    fun isSwl(q: Qso): Boolean = isSwl(q.adif)

    /** The station's fields with the listener's mark set or taken away. */
    fun station(fields: Map<String, String>, on: Boolean): Map<String, String> = if (on) fields + (FIELD to "Y") else fields - FIELD

    /** A listener's number: letters and digits in groups joined by "-" (R3A-1234, UA3-170-1234, DE1ABC… ). */
    fun isListenerNumber(s: String): Boolean = Regex("""^[A-Z0-9]{1,5}(-[A-Z0-9]{1,6}){1,3}$""").matches(s.trim().uppercase())

    /** The partner of a record, or "". */
    fun with(adif: Map<String, String>): String = adif[WITH].orEmpty()

    /** "59 · с UA3AAA" for the log row of a report. */
    fun summary(q: Qso): String = listOfNotNull(q.rstSent.ifBlank { null }, with(q.adif).ifBlank { null }?.let { tr("с %s", it) }).joinToString(" · ")
}
