package ru.r3xed.qsolog.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ContestsTest {
    private val min = 60_000L
    private val start = 1_760_000_000_000L
    private val minitest = Contest(id = "m", name = "Минитест", code = "MINI", start = start, tourMinutes = 10, tourCount = 12, dupesPerTour = true)

    @Test
    fun toursAndPhases() {
        assertNull(minitest.tourAt(start - 1))
        assertEquals(1, minitest.tourAt(start))
        assertEquals(1, minitest.tourAt(start + 10 * min - 1))
        assertEquals(2, minitest.tourAt(start + 10 * min))
        assertEquals(12, minitest.tourAt(start + 120 * min - 1))
        assertNull(minitest.tourAt(start + 120 * min))
        assertEquals(start + 120 * min, minitest.finish)

        assertEquals(Contest.Phase.Before(5 * min), minitest.phase(start - 5 * min))
        assertEquals(Contest.Phase.Tour(3, 12, 4 * min), minitest.phase(start + 26 * min))
        assertIs<Contest.Phase.Over>(minitest.phase(start + 120 * min))
        assertEquals(Contest.Phase.Running(null), Contest(name = "без времени").phase(start))
        assertEquals(Contest.Phase.Running(30 * min), Contest(start = start, end = start + 60 * min).phase(start + 30 * min))

        assertEquals("04:12", Contest.clock(4 * min + 12_000))
        assertEquals("1:05:09", Contest.clock(65 * min + 9_000))
    }

    @Test
    fun stampAndDupesPerTour() {
        val t1 = start + 2 * min
        val t2 = start + 12 * min
        assertEquals(mapOf(Contest.REF to "m", Contest.CODE to "MINI", Contest.TOUR to "1"), Contest.stamp(minitest, t1))
        val worked = Qso(id = 1, call = "R9DEMO", timeUtc = t1, band = "40m", mode = "CW", adif = Contest.stamp(minitest, t1))
        val day: (Qso) -> String = { "d" }
        // Same tour: a repeat; the next tour: a new contact; another contest: not counted.
        assertEquals(worked, Dupes.of("R9DEMO", "40m", "CW", "m", 1, "d", 0, listOf(worked), day))
        assertNull(Dupes.of("R9DEMO", "40m", "CW", "m", 2, "d", 0, listOf(worked), day))
        assertNull(Dupes.of("R9DEMO", "40m", "CW", "other", null, "d", 0, listOf(worked), day))
        assertNull(Dupes.of("R9DEMO", "20m", "CW", "m", 1, "d", 0, listOf(worked), day))
        // Without a contest: the same UTC day, as before.
        assertEquals(worked, Dupes.of("R9DEMO", "40m", "CW", null, null, "d", 0, listOf(worked), day))
        assertEquals(2, Contest.stamp(minitest, t2)[Contest.TOUR]!!.toInt())
    }

    @Test
    fun bookKeepsContestsAndActive() {
        val file = Files.createTempDirectory("cb").resolve("contests.json").toFile()
        val book = ContestBook(file)
        book.put(minitest)
        book.put(Contest(id = "r", name = "RDXC", code = "RDXC"))
        book.activate("m")
        book.put(minitest.copy(tourCount = 6))

        val again = ContestBook(file)
        assertEquals(listOf("m", "r"), again.all().map { it.id })
        assertEquals(6, again.active()!!.tourCount)
        assertEquals(start, again.active()!!.start)
        assertEquals(true, again.active()!!.dupesPerTour)
        again.delete("m")
        assertNull(ContestBook(file).active())
        assertEquals(listOf("r"), ContestBook(file).all().map { it.id })
    }
}
