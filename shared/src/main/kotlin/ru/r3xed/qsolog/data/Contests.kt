package ru.r3xed.qsolog.data

import java.io.File
import java.util.UUID

/**
 * A contest from the contest book: its name and CONTEST code for the report, the time it runs (UTC) and, for
 * minitests, the tours. Contacts logged while it is the active one carry its [id] and the tour number, so the report
 * takes exactly them.
 */
data class Contest(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** The CONTEST: line of ЕРМАК / Cabrillo (RDXC, R3X-CHAMP, …); also written to ADIF CONTEST_ID. */
    val code: String = "",
    /** Start and end, epoch ms UTC; null — not set. */
    val start: Long? = null,
    val end: Long? = null,
    /** Tours of [tourMinutes] each, [tourCount] of them from [start]; 0 — no tours. */
    val tourMinutes: Int = 0,
    val tourCount: Int = 0,
    /** Every tour starts with a clean slate: the same station may be worked again in the next tour. */
    val dupesPerTour: Boolean = false,
) {
    val title: String get() = name.ifBlank { code }.ifBlank { "—" }

    val hasTours: Boolean get() = start != null && tourMinutes > 0 && tourCount > 0

    private val tourMs: Long get() = tourMinutes * 60_000L

    /** The end: as set, or after the last tour. */
    val finish: Long? get() = end ?: if (hasTours) start!! + tourMs * tourCount else null

    /** The tour running at [t], 1-based; null without tours, before the first and after the last. */
    fun tourAt(t: Long): Int? {
        if (!hasTours || t < start!!) return null
        val n = ((t - start) / tourMs).toInt() + 1
        return n.takeIf { it <= tourCount }
    }

    /** Where the contest is at [now], for the timer in the log. */
    fun phase(now: Long): Phase {
        val s = start
        val e = finish
        return when {
            s != null && now < s -> Phase.Before(s - now)
            e != null && now >= e -> Phase.Over
            hasTours -> tourAt(now)!!.let { n -> Phase.Tour(n, tourCount, start!! + tourMs * n - now) }
            e != null -> Phase.Running(e - now)
            else -> Phase.Running(null)
        }
    }

    sealed interface Phase {
        /** Starts in [left] ms. */
        data class Before(val left: Long) : Phase
        /** Tour [n] of [count], ends in [left] ms. */
        data class Tour(val n: Int, val count: Int, val left: Long) : Phase
        /** On, ends in [left] ms (null — no end set). */
        data class Running(val left: Long?) : Phase
        data object Over : Phase
    }

    companion object {
        /** The contest the contact was logged in (its [Contest.id]). */
        const val REF = "APP_QSOLOG_CONTEST_REF"
        /** Tour number of a contact in a contest with tours. */
        const val TOUR = "APP_QSOLOG_TOUR"
        /** The standard ADIF field the report programs read. */
        const val CODE = "CONTEST_ID"

        /** Fields a contact logged at [t] in [c] gets: the contest, its code and the tour. */
        fun stamp(c: Contest, t: Long): Map<String, String> = buildMap {
            put(REF, c.id)
            if (c.code.isNotBlank()) put(CODE, c.code)
            c.tourAt(t)?.let { put(TOUR, it.toString()) }
        }

        /** Remaining time as the timer shows it: 1:05:09, 04:12. */
        fun clock(ms: Long): String {
            val s = (ms.coerceAtLeast(0) + 999) / 1000
            val h = s / 3600
            return if (h > 0) "%d:%02d:%02d".format(h, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
        }
    }
}

/**
 * The repeat rule: in a contest — the same station, band and mode anywhere in that contest (in the same tour when
 * dupes reset with each tour); without one — on the same UTC day, as before. [day] gives a contact's UTC day.
 */
object Dupes {
    fun of(call: String, band: String, mode: String, contestRef: String?, tour: Int?, day: String, id: Long, log: List<Qso>, dayOf: (Qso) -> String): Qso? {
        if (call.length < 3) return null
        return log.filter {
            it.id != id && it.call == call && it.band.equals(band, ignoreCase = true) && it.mode.equals(mode, ignoreCase = true) &&
                if (contestRef != null) it.adif[Contest.REF] == contestRef && (tour == null || it.adif[Contest.TOUR] == tour.toString())
                else dayOf(it) == day
        }.maxByOrNull { it.timeUtc }
    }
}

/** The contest book in a small JSON file next to the log; the active contest is kept with it. */
class ContestBook(private val file: File) {
    private var cache: Pair<List<Contest>, String?>? = null

    @Synchronized
    private fun state(): Pair<List<Contest>, String?> = cache ?: read().also { cache = it }

    fun all(): List<Contest> = state().first

    /** The contest new contest-mode contacts go to; null — none (contacts get only the contest flag). */
    fun active(): Contest? = state().let { (list, id) -> list.firstOrNull { it.id == id } }

    fun get(id: String?): Contest? = all().firstOrNull { it.id == id }

    @Synchronized
    fun put(c: Contest) {
        val (list, active) = state()
        val i = list.indexOfFirst { it.id == c.id }
        write((if (i < 0) list + c else list.toMutableList().also { it[i] = c }), active)
    }

    @Synchronized
    fun delete(id: String) {
        val (list, active) = state()
        write(list.filter { it.id != id }, active.takeIf { it != id })
    }

    @Synchronized
    fun activate(id: String?) = write(all(), id)

    private fun write(list: List<Contest>, active: String?) {
        cache = list to active
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(MiniJson.encode(mapOf("version" to 1, "active" to active, "contests" to list.map(::toJson))))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun read(): Pair<List<Contest>, String?> {
        if (!file.isFile) return emptyList<Contest>() to null
        val json = runCatching { MiniJson.parse(file.readText()) }.getOrNull() as? Map<*, *> ?: return emptyList<Contest>() to null
        val list = (json["contests"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::fromJson) }
        return list to (json["active"] as? String)
    }

    private fun toJson(c: Contest): Map<String, Any?> = mapOf(
        "id" to c.id, "name" to c.name, "code" to c.code, "start" to c.start, "end" to c.end,
        "tourMinutes" to c.tourMinutes, "tourCount" to c.tourCount, "dupesPerTour" to c.dupesPerTour,
    )

    private fun fromJson(m: Map<*, *>): Contest? {
        val id = (m["id"] as? String)?.ifBlank { null } ?: return null
        return Contest(
            id = id, name = m["name"] as? String ?: "", code = m["code"] as? String ?: "",
            start = (m["start"] as? Number)?.toLong(), end = (m["end"] as? Number)?.toLong(),
            tourMinutes = (m["tourMinutes"] as? Number)?.toInt() ?: 0, tourCount = (m["tourCount"] as? Number)?.toInt() ?: 0,
            dupesPerTour = m["dupesPerTour"] == true,
        )
    }
}
