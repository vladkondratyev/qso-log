package ru.r3xed.qsolog

import com.github.javakeyring.Keyring
import ru.r3xed.qsolog.data.Adif
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.CallHistory
import ru.r3xed.qsolog.data.DEFAULT_BANDS
import ru.r3xed.qsolog.data.DEFAULT_MODES
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.StationSettings
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties

/**
 * Per-user data folder: ~/Library/Application Support/QSO Log on macOS, %APPDATA%\QSO Log on Windows,
 * ~/.local/share/qso-log on Linux. The macOS and Windows folders keep the name from before the rename to QSO-LOG,
 * so existing logs and settings stay where they are. The `qsolog.data` system property overrides it (tests, screenshots).
 */
object AppDirs {
    val data: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val dir = System.getProperty("qsolog.data")?.let(::File) ?: when {
            os.contains("mac") -> File(home, "Library/Application Support/QSO Log")
            os.contains("win") -> File(System.getenv("APPDATA") ?: home, "QSO Log")
            else -> File(System.getenv("XDG_DATA_HOME")?.ifBlank { null } ?: "$home/.local/share", "qso-log")
        }
        dir.mkdirs()
        dir
    }
    val tiles: File get() = File(data, "tiles").apply { mkdirs() }
    val audio: File get() = File(data, "audio").apply { mkdirs() }
}

/** The log in a local SQLite file, same schema as the Android app. */
class QsoDb(file: File = File(AppDirs.data, "qsolog.db")) {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")

    init {
        conn.createStatement().use {
            it.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS qso (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    call TEXT NOT NULL,
                    time_utc INTEGER NOT NULL,
                    band TEXT, mode TEXT, freq TEXT,
                    rst_sent TEXT, rst_rcvd TEXT,
                    name TEXT, qth TEXT, country TEXT, locator TEXT,
                    lat REAL, lon REAL, distance_km REAL, bearing REAL,
                    power TEXT, qsl_sent INTEGER, qsl_rcvd INTEGER, comment TEXT,
                    my_call TEXT, my_locator TEXT,
                    created_at INTEGER, updated_at INTEGER
                )
                """.trimIndent()
            )
            // Columns added later, same names as on Android: extra ADIF fields in "<KEY:len>value" form (1.1.0),
            // voice note file and "QRZ.ru data still missing" mark (1.2.0).
            val have = it.executeQuery("PRAGMA table_info(qso)").use { rs -> buildSet { while (rs.next()) add(rs.getString("name")) } }
            if ("adif_extra" !in have) it.executeUpdate("ALTER TABLE qso ADD COLUMN adif_extra TEXT")
            if ("audio" !in have) it.executeUpdate("ALTER TABLE qso ADD COLUMN audio TEXT")
            if ("pending_lookup" !in have) it.executeUpdate("ALTER TABLE qso ADD COLUMN pending_lookup INTEGER")
            it.executeUpdate("CREATE INDEX IF NOT EXISTS qso_call ON qso(call)")
            it.executeUpdate("CREATE INDEX IF NOT EXISTS qso_time ON qso(time_utc)")
        }
    }

    @Synchronized
    fun all(query: String = ""): List<Qso> {
        val list = conn.prepareStatement("SELECT * FROM qso ORDER BY time_utc DESC").use { st ->
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toQso()) } }
        }
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return list
        // Matched in Kotlin: SQLite's LIKE ignores case only for Latin letters.
        return list.filter { q ->
            listOf(q.call, q.name, q.qth, q.country, q.locator, q.comment, q.band, q.mode, q.freqMhz)
                .any { it.lowercase().contains(needle) }
        }
    }

    @Synchronized
    fun count(): Int = conn.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM qso").use { it.next(); it.getInt(1) } }

    @Synchronized
    fun history(call: String, excludeId: Long = 0): CallHistory =
        conn.prepareStatement("SELECT * FROM qso WHERE call = ? AND id != ? ORDER BY time_utc DESC").use { st ->
            st.setString(1, call.uppercase())
            st.setLong(2, excludeId)
            st.executeQuery().use { rs ->
                var count = 0
                var last: Qso? = null
                while (rs.next()) {
                    if (count == 0) last = rs.toQso()
                    count++
                }
                CallHistory(count, last)
            }
        }

    @Synchronized
    fun get(id: Long): Qso? = conn.prepareStatement("SELECT * FROM qso WHERE id = ?").use { st ->
        st.setLong(1, id)
        st.executeQuery().use { if (it.next()) it.toQso() else null }
    }

    @Synchronized
    fun save(qso: Qso): Long {
        if (qso.id == 0L) return insert(qso)
        conn.prepareStatement("UPDATE qso SET ${COLUMNS.joinToString { "$it = ?" }} WHERE id = ?").use { st ->
            st.bind(qso)
            st.setLong(COLUMNS.size + 1, qso.id)
            st.executeUpdate()
        }
        return qso.id
    }

    @Synchronized
    fun delete(id: Long) {
        conn.prepareStatement("DELETE FROM qso WHERE id = ?").use { it.setLong(1, id); it.executeUpdate() }
    }

    /** Deletes every record; returns how many there were. */
    @Synchronized
    fun deleteAll(): Int = conn.createStatement().use { it.executeUpdate("DELETE FROM qso") }

    /** Voice note files that records refer to. */
    @Synchronized
    fun audioFiles(): Set<String> = conn.createStatement().use { st ->
        st.executeQuery("SELECT audio FROM qso WHERE audio IS NOT NULL AND audio != ''").use { rs ->
            buildSet { while (rs.next()) add(rs.getString(1)) }
        }
    }

    /** Same call, same minute, same band and mode: treated as the same contact on import. */
    @Synchronized
    fun exists(qso: Qso): Boolean =
        conn.prepareStatement("SELECT 1 FROM qso WHERE call = ? AND time_utc / 60000 = ? AND band = ? AND mode = ? LIMIT 1").use { st ->
            st.setString(1, qso.call)
            st.setLong(2, qso.timeUtc / 60_000)
            st.setString(3, qso.band)
            st.setString(4, qso.mode)
            st.executeQuery().use { it.next() }
        }

    @Synchronized
    fun insertAll(list: List<Qso>): Int {
        conn.autoCommit = false
        try {
            var added = 0
            for (q in list) if (!exists(q)) { insert(q.copy(id = 0)); added++ }
            conn.commit()
            return added
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    private fun insert(qso: Qso): Long {
        val sql = "INSERT INTO qso (${COLUMNS.joinToString()}) VALUES (${COLUMNS.joinToString { "?" }})"
        conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { st ->
            st.bind(qso)
            st.executeUpdate()
            return st.generatedKeys.use { if (it.next()) it.getLong(1) else 0 }
        }
    }

    private fun PreparedStatement.bind(q: Qso) {
        val values = listOf<Any?>(
            q.call.uppercase(), q.timeUtc, q.band, q.mode, q.freqMhz, q.rstSent, q.rstRcvd,
            q.name, q.qth, q.country, q.locator, q.lat, q.lon, q.distanceKm, q.bearing,
            q.power, if (q.qslSent) 1 else 0, if (q.qslRcvd) 1 else 0, q.comment,
            q.myCall, q.myLocator, q.createdAt, q.updatedAt, Adif.encodeFields(q.adif),
            q.audio, if (q.pendingLookup) 1 else 0,
        )
        values.forEachIndexed { i, v ->
            when (v) {
                null -> setNull(i + 1, Types.REAL)
                is String -> setString(i + 1, v)
                is Long -> setLong(i + 1, v)
                is Int -> setInt(i + 1, v)
                is Double -> setDouble(i + 1, v)
            }
        }
    }

    private fun ResultSet.dbl(col: String): Double? = getDouble(col).let { if (wasNull()) null else it }
    private fun ResultSet.str(col: String): String = getString(col) ?: ""

    private fun ResultSet.toQso() = Qso(
        id = getLong("id"),
        call = str("call"),
        timeUtc = getLong("time_utc"),
        band = str("band"), mode = str("mode"), freqMhz = str("freq"),
        rstSent = str("rst_sent"), rstRcvd = str("rst_rcvd"),
        name = str("name"), qth = str("qth"), country = str("country"), locator = str("locator"),
        lat = dbl("lat"), lon = dbl("lon"), distanceKm = dbl("distance_km"), bearing = dbl("bearing"),
        power = str("power"),
        qslSent = getInt("qsl_sent") == 1, qslRcvd = getInt("qsl_rcvd") == 1,
        comment = str("comment"),
        myCall = str("my_call"), myLocator = str("my_locator"),
        createdAt = getLong("created_at"), updatedAt = getLong("updated_at"),
        adif = Adif.decodeFields(getString("adif_extra")),
        audio = str("audio"),
        pendingLookup = getInt("pending_lookup") == 1,
    )

    private companion object {
        val COLUMNS = listOf(
            "call", "time_utc", "band", "mode", "freq", "rst_sent", "rst_rcvd",
            "name", "qth", "country", "locator", "lat", "lon", "distance_km", "bearing",
            "power", "qsl_sent", "qsl_rcvd", "comment", "my_call", "my_locator", "created_at", "updated_at",
            "adif_extra", "audio", "pending_lookup",
        )
    }
}

/**
 * Settings in a properties file in the data folder.
 * The QRZ.ru password goes to the system keychain (macOS Keychain, Windows Credential Manager);
 * if the keychain is unavailable it falls back to the settings file.
 */
class Settings {
    private val file = File(AppDirs.data, "settings.properties")
    private val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    // A test or screenshot run with its own data folder must not touch the real keychain.
    private val keyring: Keyring? = if (System.getProperty("qsolog.data") != null) null else try { Keyring.create() } catch (e: Exception) { null }

    private fun get(key: String, def: String = "") = props.getProperty(key, def)
    private fun put(key: String, value: String) {
        props.setProperty(key, value)
        file.outputStream().use { props.store(it, "QSO Log") }
    }

    private fun loadPassword(): String =
        keyring?.let { try { it.getPassword(SERVICE, ACCOUNT) } catch (e: Exception) { null } } ?: get("qrz_password")

    private fun savePassword(pw: String) {
        val ring = keyring
        if (ring != null) {
            try {
                if (pw.isEmpty()) ring.deletePassword(SERVICE, ACCOUNT) else ring.setPassword(SERVICE, ACCOUNT, pw)
                if (props.containsKey("qrz_password")) { props.remove("qrz_password"); put("qrz_login", get("qrz_login")) }
                return
            } catch (e: Exception) {
                if (pw.isEmpty()) return
            }
        }
        put("qrz_password", pw)
    }

    private var cachedPassword: String? = null

    /** Everything except the password, which may wait on a keychain prompt; read it with [password]. */
    fun load() = StationSettings(
        myCall = get("my_call"),
        myLocator = get("my_locator"),
        myQth = get("my_qth"),
        qrzLogin = get("qrz_login"),
        qrzPassword = cachedPassword.orEmpty(),
        power = get("my_power"),
        station = AdifLabels.MINE.keys.associateWith { get("station_$it") }.filterValues { it.isNotEmpty() },
    )

    fun password(): String = cachedPassword ?: loadPassword().also { cachedPassword = it }

    fun save(s: StationSettings) {
        props.setProperty("my_call", s.myCall.trim().uppercase())
        props.setProperty("my_locator", s.myLocator.trim())
        props.setProperty("my_qth", s.myQth.trim())
        props.setProperty("my_power", s.power.trim())
        for (key in AdifLabels.MINE.keys) props.setProperty("station_$key", s.station[key].orEmpty().trim())
        put("qrz_login", s.qrzLogin.trim())
        // Until the stored password has been read, an empty field means "not loaded yet", not "cleared".
        if (cachedPassword != null && s.qrzPassword != cachedPassword) {
            cachedPassword = s.qrzPassword
            savePassword(s.qrzPassword)
        }
    }

    var lastBand: String
        get() = get("last_band", "20m")
        set(v) = put("last_band", v)
    var lastMode: String
        get() = get("last_mode", "SSB")
        set(v) = put("last_mode", v)
    var lastFreq: String
        get() = get("last_freq")
        set(v) = put("last_freq", v)
    var lastPower: String
        get() = get("last_power")
        set(v) = put("last_power", v)

    /** Bands and modes shown as buttons in the contact card. */
    var enabledBands: Set<String>
        get() = props.getProperty("enabled_bands")?.let(::splitSet) ?: DEFAULT_BANDS.toSet()
        set(v) = put("enabled_bands", v.joinToString(","))
    var enabledModes: Set<String>
        get() = props.getProperty("enabled_modes")?.let(::splitSet) ?: DEFAULT_MODES.toSet()
        set(v) = put("enabled_modes", v.joinToString(","))

    var sortBy: String
        get() = get("sort_by", "DATE")
        set(v) = put("sort_by", v)
    var sortDesc: Boolean
        get() = get("sort_desc", "true") == "true"
        set(v) = put("sort_desc", v.toString())

    private fun splitSet(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private companion object {
        const val SERVICE = "QSO Log"
        const val ACCOUNT = "qrz.ru"
    }
}
