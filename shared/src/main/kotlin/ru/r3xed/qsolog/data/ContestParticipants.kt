package ru.r3xed.qsolog.data

/**
 * The contest's participants list: every station of this contest (by its code, RDXC, R3X-CHAMP…, in any year) and of the
 * contest in work, in alphabetical order, with the constant part of the code it gave (RDA district, years on the air,
 * zone, region — whatever does not change from contact to contact). A click puts the callsign and that part into the
 * card; the serial number is typed after it.
 */
object ContestParticipants {
    enum class Worked { NO, OTHER_BAND, DUPE }

    data class Participant(val call: String, val exchange: String, val worked: Worked, val count: Int)

    /**
     * The participants from [log]: contest contacts with the [code] of the contest in work (all contest contacts when
     * it has none), and the contacts of the contest in work ([current]). [band] and [mode] are the card's: a station
     * already worked on them in [current] is a dupe.
     */
    fun of(log: List<Qso>, current: List<Qso>, code: String?, band: String, mode: String): List<Participant> {
        val code = code?.trim()?.takeIf { it.isNotEmpty() }
        // The contacts of the contest in work are in the log too: each counted once.
        val known = (log.filter { q ->
            ContestMode.isContest(q.adif) && (code == null || q.adif[Contest.CODE].equals(code, ignoreCase = true))
        } + current).distinct()
        if (known.isEmpty()) return emptyList()
        val serials = usesSerials(known)
        val dupes = current.filter { it.band.equals(band, true) && it.mode.equals(mode, true) }.mapTo(HashSet()) { it.call.uppercase() }
        val workedNow = current.mapTo(HashSet()) { it.call.uppercase() }
        return known.filter { it.call.isNotBlank() }.groupBy { it.call.uppercase() }.map { (call, qs) ->
            val codes = qs.sortedByDescending { it.timeUtc }.map { it.adif[ContestMode.RCVD].orEmpty().trim() }.filter { it.isNotEmpty() }
            Participant(
                call = call,
                exchange = constantPart(codes, serials),
                worked = when (call) { in dupes -> Worked.DUPE; in workedNow -> Worked.OTHER_BAND; else -> Worked.NO },
                count = qs.size,
            )
        }.sortedBy { it.call }
    }

    /** [list] narrowed to the callsigns containing [typed] (what is in the callsign field). */
    fun filter(list: List<Participant>, typed: String): List<Participant> {
        val t = typed.trim().uppercase()
        if (t.isEmpty()) return list
        return list.filter { t in it.call }.sortedWith(compareBy({ !it.call.startsWith(t) }, { it.call }))
    }

    /**
     * The part of the received codes (newest first) that stays the same: with serial numbers in the contest, the
     * digit-only words are numbers unless the station gave the same one every time; otherwise the newest code as is.
     */
    fun constantPart(codes: List<String>, serials: Boolean): String {
        val newest = codes.firstOrNull() ?: return ""
        val words = newest.split(' ').filter { it.isNotBlank() }
        if (!serials) return words.joinToString(" ")
        return words.filter { w ->
            !w.all(Char::isDigit) || (codes.size > 1 && codes.all { c -> w in c.split(' ') })
        }.joinToString(" ")
    }

    /** Serial numbers are sent: my own codes are digit words that change from contact to contact. */
    fun usesSerials(qsos: List<Qso>): Boolean {
        val nums = qsos.mapNotNull { q -> q.adif[ContestMode.SENT]?.split(' ')?.firstOrNull { it.isNotBlank() && it.all(Char::isDigit) } }
        return nums.toSet().size > 1
    }
}
