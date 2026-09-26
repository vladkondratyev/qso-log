package ru.r3xed.qsolog.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // The QRZ.ru password lives in a separate, encrypted file.
    private val secret: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secret", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("secret_fallback", Context.MODE_PRIVATE)
    }

    fun load() = StationSettings(
        myCall = prefs.getString("my_call", "") ?: "",
        myLocator = prefs.getString("my_locator", "") ?: "",
        myQth = prefs.getString("my_qth", "") ?: "",
        qrzLogin = prefs.getString("qrz_login", "") ?: "",
        qrzPassword = secret.getString("qrz_password", "") ?: "",
        power = prefs.getString("my_power", "") ?: "",
        station = AdifLabels.MINE.keys.associateWith { prefs.getString("station_$it", "") ?: "" }.filterValues { it.isNotEmpty() },
    )

    fun save(s: StationSettings) {
        prefs.edit()
            .putString("my_call", s.myCall.trim().uppercase())
            .putString("my_locator", s.myLocator.trim())
            .putString("my_qth", s.myQth.trim())
            .putString("qrz_login", s.qrzLogin.trim())
            .putString("my_power", s.power.trim())
            .apply {
                for (key in AdifLabels.MINE.keys) putString("station_$key", s.station[key].orEmpty().trim())
            }
            .apply()
        secret.edit().putString("qrz_password", s.qrzPassword).apply()
    }

    /** Band, mode and frequency from the previous contact, so the next one starts pre-filled. */
    var lastBand: String
        get() = prefs.getString("last_band", "20m") ?: "20m"
        set(v) = prefs.edit().putString("last_band", v).apply()
    var lastMode: String
        get() = prefs.getString("last_mode", "SSB") ?: "SSB"
        set(v) = prefs.edit().putString("last_mode", v).apply()
    var lastFreq: String
        get() = prefs.getString("last_freq", "") ?: ""
        set(v) = prefs.edit().putString("last_freq", v).apply()
    /** Bands and modes shown as buttons in the contact card. */
    var enabledBands: Set<String>
        get() = prefs.getStringSet("enabled_bands", null)?.toSet() ?: DEFAULT_BANDS.toSet()
        set(v) = prefs.edit().putStringSet("enabled_bands", v).apply()
    var enabledModes: Set<String>
        get() = prefs.getStringSet("enabled_modes", null)?.toSet() ?: DEFAULT_MODES.toSet()
        set(v) = prefs.edit().putStringSet("enabled_modes", v).apply()

    /** Second lookup source: country and region by prefix from HamQTH (free, no account). On by default. */
    var hamqthEnabled: Boolean
        get() = prefs.getBoolean("hamqth_enabled", true)
        set(v) = prefs.edit().putBoolean("hamqth_enabled", v).apply()
    /** Last ЕРМАК / Cabrillo export: format name, CONTEST code, CATEGORY-OPERATOR. */
    var contestFormat: String
        get() = prefs.getString("contest_format", "ERMAK") ?: "ERMAK"
        set(v) = prefs.edit().putString("contest_format", v).apply()
    var contestCode: String
        get() = prefs.getString("contest_code", "") ?: ""
        set(v) = prefs.edit().putString("contest_code", v).apply()
    var contestOperator: String
        get() = prefs.getString("contest_operator", "SINGLE-OP") ?: "SINGLE-OP"
        set(v) = prefs.edit().putString("contest_operator", v).apply()
    /** Colour theme: "system" (follow Android), "light" or "dark". */
    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(v) = prefs.edit().putString("theme", v).apply()
    /** App starts so far; the "hold to record" hint under the add button shows only on the first few. */
    var launchCount: Int
        get() = prefs.getInt("launch_count", 0)
        set(v) = prefs.edit().putInt("launch_count", v).apply()
    var lastPower: String
        get() = prefs.getString("last_power", "") ?: ""
        set(v) = prefs.edit().putString("last_power", v).apply()
}
