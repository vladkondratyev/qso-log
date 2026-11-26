package ru.r3xed.qsolog.data

/** Marks the log shows on its rows. */
object LogMarks {
    /**
     * The contacts that were the first with their country (the earliest by time; on a tie, the first saved): a star in
     * the log. The country is the card's (from the callbooks or the prefix list); contacts without one take no part.
     */
    fun firstWithCountry(log: List<Qso>): Set<Long> {
        val first = HashMap<String, Qso>()
        for (q in log) {
            val c = q.country.trim().lowercase()
            if (c.isEmpty()) continue
            val old = first[c]
            if (old == null || q.timeUtc < old.timeUtc || (q.timeUtc == old.timeUtc && q.id < old.id)) first[c] = q
        }
        return first.values.mapTo(HashSet()) { it.id }
    }

    /**
     * Index of the group to jump to for the day [epochDay] among the log's day groups ([days], each group's day as an
     * epoch day, in any order): that day, or the nearest one before it (the earliest, when nothing is before). -1: none.
     */
    fun dayIndex(days: List<Long>, epochDay: Long): Int {
        if (days.isEmpty()) return -1
        days.indexOf(epochDay).let { if (it >= 0) return it }
        val before = days.withIndex().filter { it.value < epochDay }
        return (before.maxByOrNull { it.value } ?: days.withIndex().minByOrNull { it.value }!!).index
    }
}
