package ru.r3xed.qsolog

import ru.r3xed.qsolog.data.Qso
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

class QsoDbTest {
    private fun tempDb() = File.createTempFile("qsolog", ".db").apply { delete(); deleteOnExit() }

    @Test
    fun extraAdifFieldsAreStored() {
        val db = QsoDb(tempDb())
        val id = db.save(Qso(call = "R9DEMO", timeUtc = 1_790_341_860_000, band = "20m", mode = "SSB", adif = mapOf("STATE" to "MA", "QSL_VIA" to "L;E")))
        assertEquals(mapOf("STATE" to "MA", "QSL_VIA" to "L;E"), db.get(id)!!.adif)
    }

    @Test
    fun databaseFromVersion10IsUpgraded() {
        val file = tempDb()
        // Schema of desktop 1.0.0, without adif_extra.
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
            c.createStatement().use {
                it.executeUpdate(
                    "CREATE TABLE qso (id INTEGER PRIMARY KEY AUTOINCREMENT, call TEXT NOT NULL, time_utc INTEGER NOT NULL, band TEXT, mode TEXT, freq TEXT, " +
                        "rst_sent TEXT, rst_rcvd TEXT, name TEXT, qth TEXT, country TEXT, locator TEXT, lat REAL, lon REAL, distance_km REAL, bearing REAL, " +
                        "power TEXT, qsl_sent INTEGER, qsl_rcvd INTEGER, comment TEXT, my_call TEXT, my_locator TEXT, created_at INTEGER, updated_at INTEGER)"
                )
                it.executeUpdate("INSERT INTO qso (call, time_utc, band, mode, qsl_sent, qsl_rcvd, created_at, updated_at) VALUES ('DL1DEMO', 1790247060000, '40m', 'CW', 0, 0, 0, 0)")
            }
        }
        val db = QsoDb(file)
        val old = db.all().single()
        assertEquals("DL1DEMO", old.call)
        assertEquals(emptyMap(), old.adif)
    }

    @Test
    fun voiceNoteAndPendingMarkAreStored() {
        val db = QsoDb(tempDb())
        val id = db.save(Qso(call = "R5DEMO", timeUtc = 1_790_372_580_000, audio = "qso_20260925_214300.wav", pendingLookup = true))
        val q = db.get(id)!!
        assertEquals("qso_20260925_214300.wav", q.audio)
        assertEquals(true, q.pendingLookup)
        assertEquals(setOf("qso_20260925_214300.wav"), db.audioFiles())
        db.save(q.copy(pendingLookup = false))
        assertEquals(false, db.get(id)!!.pendingLookup)
    }

    @Test
    fun deleteAllEmptiesTheLog() {
        val db = QsoDb(tempDb())
        db.save(Qso(call = "R9DEMO", timeUtc = 1_790_341_860_000))
        db.save(Qso(call = "DL1DEMO", timeUtc = 1_790_333_460_000))
        assertEquals(2, db.deleteAll())
        assertEquals(0, db.count())
    }

    @Test
    fun databaseFromVersion11GetsNewColumns() {
        val file = tempDb()
        // Schema of desktop 1.1.x: adif_extra, but no audio and pending_lookup.
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
            c.createStatement().use {
                it.executeUpdate(
                    "CREATE TABLE qso (id INTEGER PRIMARY KEY AUTOINCREMENT, call TEXT NOT NULL, time_utc INTEGER NOT NULL, band TEXT, mode TEXT, freq TEXT, " +
                        "rst_sent TEXT, rst_rcvd TEXT, name TEXT, qth TEXT, country TEXT, locator TEXT, lat REAL, lon REAL, distance_km REAL, bearing REAL, " +
                        "power TEXT, qsl_sent INTEGER, qsl_rcvd INTEGER, comment TEXT, my_call TEXT, my_locator TEXT, created_at INTEGER, updated_at INTEGER, adif_extra TEXT)"
                )
                it.executeUpdate("INSERT INTO qso (call, time_utc, band, mode, qsl_sent, qsl_rcvd, created_at, updated_at) VALUES ('EA5DEMO', 1790187720000, '15m', 'CW', 0, 0, 0, 0)")
            }
        }
        val old = QsoDb(file).all().single()
        assertEquals("", old.audio)
        assertEquals(false, old.pendingLookup)
    }
}
