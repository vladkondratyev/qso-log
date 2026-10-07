package ru.r3xed.qsolog.data

import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A device's log in memory, with deletion marks like the real database. */
private class MemStore : SyncStore {
    val rows = mutableListOf<Qso>()
    val deleted = mutableMapOf<String, Long>()
    private var nextId = 1L

    fun add(q: Qso) = q.copy(id = nextId++, uid = q.uid.ifBlank { UUID.randomUUID().toString() }).also { rows += it }
    fun edit(q: Qso) { rows.replaceAll { if (it.id == q.id) q.copy(updatedAt = System.currentTimeMillis()) else it } }
    fun remove(q: Qso) { rows.removeAll { it.id == q.id }; deleted[q.uid] = System.currentTimeMillis() }

    override fun changedSince(t: Long) = rows.filter { it.updatedAt > t }
    override fun deletedSince(t: Long) = deleted.filter { it.value > t }.map { it.key to it.value }
    override fun byUid(uid: String) = rows.firstOrNull { it.uid == uid }
    override fun insert(q: Qso) { add(q) }
    override fun update(q: Qso) { rows.replaceAll { if (it.id == q.id) q else it } }
    override fun deleteByUid(uid: String) { rows.removeAll { it.uid == uid } }
    override fun changeUid(old: String, new: String) { rows.replaceAll { if (it.uid == old) it.copy(uid = new) else it } }
}

class SheetSyncTest {
    @Test
    fun rowRoundTripKeepsTextAsIs() {
        val q = Qso(
            uid = "u1", call = "R9DEMO", timeUtc = 1_790_341_860_000, band = "40m", mode = "SSB", freqMhz = "7.074",
            rstSent = "59", rstRcvd = "59", lat = 55.75, lon = 37.61, qslSent = true,
            adif = mapOf(ContestMode.FIELD to "Y", ContestMode.SENT to "001", ContestMode.RCVD to "MO69"),
            createdAt = 1_790_341_860_123, updatedAt = 1_790_341_900_456,
        )
        val row = SheetSync.COLUMNS.zip(SheetSync.toRow(q)).toMap()
        assertEquals("2026-09-25T13:11:40.456Z", row["updated_utc"])
        val (back, deleted) = SheetSync.fromRow(row)!!
        assertEquals(false, deleted)
        assertEquals(q.copy(id = 0, distanceKm = null, bearing = null), back)
    }

    @Test
    fun jsonRoundTrip() {
        val v = mapOf("a" to listOf("x\"y\n", "", null), "b" to true, "n" to 3)
        assertEquals(mapOf("a" to listOf("x\"y\n", "", null), "b" to true, "n" to 3.0), MiniJson.parse(MiniJson.encode(v)))
    }

    /**
     * Two devices against a real script: SHEET_SYNC_URL is the …/exec address (the local mock of Apps Script,
     * or a test table). Skipped without it.
     */
    @Test
    fun twoDevicesThroughTheTable() = runBlocking {
        val url = System.getenv("SHEET_SYNC_URL") ?: return@runBlocking
        val t0 = 1_790_341_860_000L
        val a = MemStore()
        val b = MemStore()
        var sa = SyncState()
        var sb = SyncState()
        val a1 = a.add(Qso(call = "R9DEMO", timeUtc = t0, band = "40m", mode = "SSB", adif = mapOf(ContestMode.SENT to "001")))
        val a2 = a.add(Qso(call = "DL1DEMO", timeUtc = t0 + 60_000, band = "20m", mode = "CW"))
        b.add(Qso(call = "UA9DEMO", timeUtc = t0 + 120_000, band = "15m", mode = "FT8"))
        // The same contact as a1, copied to B by ADIF before there were UUIDs in files.
        b.add(Qso(call = "R9DEMO", timeUtc = t0 + 20_000, band = "40m", mode = "SSB"))

        sa = SheetSync.sync(url, a, sa).also { assertEquals(2, it.sent) }.state
        sb = SheetSync.sync(url, b, sb).state
        assertEquals(setOf("R9DEMO", "DL1DEMO", "UA9DEMO"), b.rows.map { it.call }.toSet())
        assertEquals(3, b.rows.size) // R9DEMO once, under A's UUID
        assertEquals(a1.uid, b.rows.single { it.call == "R9DEMO" }.uid)

        sa = SheetSync.sync(url, a, sa).state
        assertEquals(3, a.rows.size)
        assertEquals("001", a.rows.single { it.call == "R9DEMO" }.adif[ContestMode.SENT])

        // An edit on B reaches A.
        Thread.sleep(5)
        b.edit(b.byUid(a2.uid)!!.copy(name = "Hans"))
        sb = SheetSync.sync(url, b, sb).state
        sa = SheetSync.sync(url, a, sa).state
        assertEquals("Hans", a.byUid(a2.uid)!!.name)

        // A deletion on A reaches B.
        Thread.sleep(5)
        val ua = a.rows.single { it.call == "UA9DEMO" }
        a.remove(ua)
        sa = SheetSync.sync(url, a, sa).state
        sb = SheetSync.sync(url, b, sb).state
        assertNull(b.byUid(ua.uid))
        assertEquals(2, b.rows.size)

        // Nothing new: nothing sent, nothing taken.
        val again = SheetSync.sync(url, b, sb)
        assertEquals(0, again.received)
        assertTrue(again.sent <= 1)
    }
}
