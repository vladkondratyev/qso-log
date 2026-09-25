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
}
