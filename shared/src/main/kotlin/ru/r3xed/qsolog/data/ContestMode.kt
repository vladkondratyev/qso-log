package ru.r3xed.qsolog.data

/**
 * Contacts entered in contest mode: flagged with an app field, the exchanged numbers in the standard ADIF fields
 * that ЕРМАК / Cabrillo reports read ([SENT], [RCVD]).
 */
object ContestMode {
    const val FIELD = "APP_QSOLOG_CONTEST"
    const val SENT = "STX_STRING"
    const val RCVD = "SRX_STRING"

    fun isContest(adif: Map<String, String>) = adif[FIELD] == "Y"

    /** The number as it is sent: at least three digits, 001. */
    fun serial(n: Int): String = "%03d".format(n)

    /** The number the next contact sends: the one just sent plus one, or [current] plus one when it was not a number. */
    fun nextSerial(sent: String, current: Int): Int = (sent.trim().toIntOrNull() ?: current) + 1
}
