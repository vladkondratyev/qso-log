package ru.r3xed.qsolog.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class QsoDb(context: Context) : SQLiteOpenHelper(context, "qsolog.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE qso (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                call TEXT NOT NULL,
                time_utc INTEGER NOT NULL,
                band TEXT, mode TEXT, freq TEXT,
                rst_sent TEXT, rst_rcvd TEXT,
                name TEXT, qth TEXT, country TEXT, locator TEXT,
                lat REAL, lon REAL, distance_km REAL, bearing REAL,
                power TEXT, qsl_sent INTEGER, qsl_rcvd INTEGER, comment TEXT,
                my_call TEXT, my_locator TEXT,
                created_at INTEGER, updated_at INTEGER,
                audio TEXT, adif_extra TEXT,
                pending_lookup INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX qso_call ON qso(call)")
        db.execSQL("CREATE INDEX qso_time ON qso(time_utc)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE qso ADD COLUMN audio TEXT")
        if (oldVersion < 3) db.execSQL("ALTER TABLE qso ADD COLUMN adif_extra TEXT")
        if (oldVersion < 4) db.execSQL("ALTER TABLE qso ADD COLUMN pending_lookup INTEGER")
    }

    /** Voice note file names referenced by the log, used to clean up abandoned recordings. */
    fun audioFiles(): Set<String> =
        readableDatabase.rawQuery("SELECT audio FROM qso WHERE audio IS NOT NULL AND audio != ''", null)
            .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    fun all(query: String = ""): List<Qso> {
        val q = query.trim()
        val list = readableDatabase.rawQuery("SELECT * FROM qso ORDER BY time_utc DESC", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.toQso()) } }
        if (q.isEmpty()) return list
        // SQLite's LIKE is case-insensitive only for ASCII, so Cyrillic names are matched here instead.
        val needle = q.lowercase()
        return list.filter { qso ->
            listOf(qso.call, qso.name, qso.qth, qso.country, qso.locator, qso.comment, qso.band, qso.mode, qso.freqMhz)
                .any { it.lowercase().contains(needle) }
        }
    }

    fun history(call: String, excludeId: Long = 0): CallHistory {
        val c = readableDatabase.rawQuery(
            "SELECT * FROM qso WHERE call = ? AND id != ? ORDER BY time_utc DESC",
            arrayOf(call.uppercase(), excludeId.toString())
        )
        return c.use {
            val count = it.count
            val last = if (it.moveToFirst()) it.toQso() else null
            CallHistory(count, last)
        }
    }

    fun get(id: Long): Qso? =
        readableDatabase.rawQuery("SELECT * FROM qso WHERE id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.toQso() else null }

    fun save(qso: Qso): Long {
        val values = qso.toValues()
        return if (qso.id == 0L) {
            writableDatabase.insert("qso", null, values)
        } else {
            writableDatabase.update("qso", values, "id = ?", arrayOf(qso.id.toString()))
            qso.id
        }
    }

    fun deleteAll(): Int = writableDatabase.delete("qso", null, null)

    fun delete(id: Long) {
        writableDatabase.delete("qso", "id = ?", arrayOf(id.toString()))
    }

    /** True if a contact with the same call, minute, band and mode already exists. Used to skip duplicates on import. */
    fun exists(qso: Qso): Boolean {
        val minute = qso.timeUtc / 60_000
        return readableDatabase.rawQuery(
            "SELECT 1 FROM qso WHERE call = ? AND time_utc / 60000 = CAST(? AS INTEGER) AND band = ? AND mode = ? LIMIT 1",
            arrayOf(qso.call, minute.toString(), qso.band, qso.mode)
        ).use { it.moveToFirst() }
    }

    fun insertAll(list: List<Qso>): Int {
        var added = 0
        writableDatabase.beginTransaction()
        try {
            for (qso in list) {
                if (!exists(qso)) {
                    writableDatabase.insert("qso", null, qso.copy(id = 0).toValues())
                    added++
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return added
    }

    private fun Qso.toValues() = ContentValues().apply {
        put("call", call.uppercase())
        put("time_utc", timeUtc)
        put("band", band); put("mode", mode); put("freq", freqMhz)
        put("rst_sent", rstSent); put("rst_rcvd", rstRcvd)
        put("name", name); put("qth", qth); put("country", country); put("locator", locator)
        put("lat", lat); put("lon", lon); put("distance_km", distanceKm); put("bearing", bearing)
        put("power", power)
        put("qsl_sent", if (qslSent) 1 else 0); put("qsl_rcvd", if (qslRcvd) 1 else 0)
        put("comment", comment)
        put("my_call", myCall); put("my_locator", myLocator)
        put("created_at", createdAt); put("updated_at", updatedAt)
        put("audio", audio)
        put("adif_extra", Adif.encodeFields(adif))
        put("pending_lookup", if (pendingLookup) 1 else 0)
    }

    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col)) ?: ""
    private fun Cursor.dbl(col: String): Double? =
        getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
    private fun Cursor.lng(col: String) = getLong(getColumnIndexOrThrow(col))

    private fun Cursor.toQso() = Qso(
        id = lng("id"),
        call = str("call"),
        timeUtc = lng("time_utc"),
        band = str("band"), mode = str("mode"), freqMhz = str("freq"),
        rstSent = str("rst_sent"), rstRcvd = str("rst_rcvd"),
        name = str("name"), qth = str("qth"), country = str("country"), locator = str("locator"),
        lat = dbl("lat"), lon = dbl("lon"), distanceKm = dbl("distance_km"), bearing = dbl("bearing"),
        power = str("power"),
        qslSent = lng("qsl_sent") == 1L, qslRcvd = lng("qsl_rcvd") == 1L,
        comment = str("comment"),
        myCall = str("my_call"), myLocator = str("my_locator"),
        createdAt = lng("created_at"), updatedAt = lng("updated_at"),
        audio = str("audio"),
        adif = Adif.decodeFields(str("adif_extra")),
        pendingLookup = getColumnIndexOrThrow("pending_lookup").let { !isNull(it) && getInt(it) == 1 },
    )
}
