package ru.r3xed.qsolog.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class QsoDb(context: Context) : SQLiteOpenHelper(context, "qsolog.db", null, 5), SyncStore {

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
                pending_lookup INTEGER,
                uid TEXT
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
        if (oldVersion < 5) db.execSQL("ALTER TABLE qso ADD COLUMN uid TEXT")
    }

    /** Every record gets its UUID (a log from an older version gets them here, once) before the log is read. */
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (db.isReadOnly) return
        val missing = db.rawQuery("SELECT id FROM qso WHERE uid IS NULL OR uid = ''", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        if (missing.isNotEmpty()) {
            db.beginTransaction()
            try {
                for (id in missing) db.execSQL("UPDATE qso SET uid = ? WHERE id = ?", arrayOf<Any>(UUID.randomUUID().toString(), id))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS qso_uid ON qso(uid)")
        // Contacts deleted on this device, so the table sync can delete them on the others.
        db.execSQL("CREATE TABLE IF NOT EXISTS qso_deleted (uid TEXT PRIMARY KEY, at INTEGER)")
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
        return c.use { CallHistory.of(buildList { while (it.moveToNext()) add(it.toQso()) }) }
    }

    fun get(id: Long): Qso? =
        readableDatabase.rawQuery("SELECT * FROM qso WHERE id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.toQso() else null }

    fun save(qso: Qso): Long {
        val values = qso.toValues()
        return if (qso.id == 0L) {
            if (qso.uid.isBlank()) values.put("uid", UUID.randomUUID().toString())
            // Back again (undo, or from the table): no longer deleted.
            else writableDatabase.delete("qso_deleted", "uid = ?", arrayOf(qso.uid))
            writableDatabase.insert("qso", null, values)
        } else {
            writableDatabase.update("qso", values, "id = ?", arrayOf(qso.id.toString()))
            qso.id
        }
    }

    fun deleteAll(): Int = writableDatabase.delete("qso", null, null)

    /** Deletes the record and remembers its UUID: the next table sync deletes it on the other devices too. */
    fun delete(id: Long) {
        val uid = get(id)?.uid.orEmpty()
        if (uid.isNotBlank()) {
            writableDatabase.insertWithOnConflict(
                "qso_deleted", null,
                ContentValues().apply { put("uid", uid); put("at", System.currentTimeMillis()) },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        writableDatabase.delete("qso", "id = ?", arrayOf(id.toString()))
    }

    // ---------- table sync ----------

    override fun changedSince(t: Long): List<Qso> =
        readableDatabase.rawQuery("SELECT * FROM qso WHERE updated_at > ?", arrayOf(t.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.toQso()) } }

    override fun deletedSince(t: Long): List<Pair<String, Long>> =
        readableDatabase.rawQuery("SELECT uid, at FROM qso_deleted WHERE at > ?", arrayOf(t.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }

    override fun byUid(uid: String): Qso? =
        readableDatabase.rawQuery("SELECT * FROM qso WHERE uid = ?", arrayOf(uid))
            .use { if (it.moveToFirst()) it.toQso() else null }

    override fun insert(q: Qso) {
        save(q.copy(id = 0))
    }

    override fun update(q: Qso) {
        save(q)
    }

    override fun deleteByUid(uid: String) {
        writableDatabase.delete("qso", "uid = ?", arrayOf(uid))
    }

    override fun changeUid(old: String, new: String) {
        writableDatabase.execSQL("UPDATE qso SET uid = ? WHERE uid = ?", arrayOf(new, old))
    }

    /**
     * True if the contact is already in the log: the same UUID, or (for files without one) the same call, minute,
     * band and mode. Used to skip duplicates on import.
     */
    fun exists(qso: Qso): Boolean {
        if (qso.uid.isNotBlank() &&
            readableDatabase.rawQuery("SELECT 1 FROM qso WHERE uid = ? LIMIT 1", arrayOf(qso.uid)).use { it.moveToFirst() }
        ) return true
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
                    val q = if (qso.uid.isBlank()) qso.copy(id = 0, uid = UUID.randomUUID().toString()) else qso.copy(id = 0)
                    writableDatabase.insert("qso", null, q.toValues())
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
        // A card saved from the editor has no UUID of its own: the one in the database stays.
        if (uid.isNotBlank()) put("uid", uid)
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
        uid = str("uid"),
        audio = str("audio"),
        adif = Adif.decodeFields(str("adif_extra")),
        pendingLookup = getColumnIndexOrThrow("pending_lookup").let { !isNull(it) && getInt(it) == 1 },
    )
}
