package ru.r3xed.qsolog.data

object Callsign {
    /**
     * The station's own callsign, as callbooks know it: "R0DEMO/P", "R0DEMO/M", "R0DEMO/3", "DL/R0DEMO" → R0DEMO.
     * The log keeps what was typed; only the searches use this.
     */
    fun home(call: String): String {
        val parts = call.uppercase().trim().split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return parts.firstOrNull().orEmpty()
        // A callsign has letters and a digit; of several (VP2E/K1DEMO), the longer one is the station's.
        return parts.filter { p -> p.any { it.isDigit() } && p.any { it.isLetter() } }.maxByOrNull { it.length } ?: parts[0]
    }

    /** SQL condition on [column] for [call] and its portable forms; bind the four values of [likeArgs]. */
    fun sqlMatch(column: String) = "($column = ? OR $column LIKE ? OR $column LIKE ? OR $column LIKE ?)"

    fun likeArgs(call: String): List<String> = home(call).let { listOf(it, "$it/%", "%/$it", "%/$it/%") }
}
