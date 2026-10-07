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
        qrzSiteEmail = prefs.getString("qrz_site_email", "") ?: "",
        qrzSitePassword = secret.getString("qrz_site_password", "") ?: "",
        qrzComLogin = prefs.getString("qrzcom_login", "") ?: "",
        qrzComPassword = secret.getString("qrzcom_password", "") ?: "",
        power = prefs.getString("my_power", "") ?: "",
        station = AdifLabels.MINE.keys.associateWith { prefs.getString("station_$it", "") ?: "" }.filterValues { it.isNotEmpty() },
    )

    fun save(s: StationSettings) {
        prefs.edit()
            .putString("my_call", s.myCall.trim().uppercase())
            .putString("my_locator", s.myLocator.trim())
            .putString("my_qth", s.myQth.trim())
            .putString("qrz_login", s.qrzLogin.trim())
            .putString("qrz_site_email", s.qrzSiteEmail.trim())
            .putString("qrzcom_login", s.qrzComLogin.trim())
            .putString("my_power", s.power.trim())
            .apply {
                for (key in AdifLabels.MINE.keys) putString("station_$key", s.station[key].orEmpty().trim())
            }
            .apply()
        secret.edit().putString("qrz_password", s.qrzPassword).putString("qrz_site_password", s.qrzSitePassword)
            .putString("qrzcom_password", s.qrzComPassword).apply()
    }

    /** Accounts of the online logbooks the app no longer uploads to: removed from the phone on start. */
    fun forgetOnlineLogAccounts() {
        prefs.edit().remove("eqsl_user").remove("eqsl_nickname").remove("clublog_email").apply()
        secret.edit().remove("qrzcom_key").remove("eqsl_password").remove("clublog_password").remove("clublog_key").apply()
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
        get() = prefs.getStringSet("enabled_bands", null)?.toSet()?.takeIf { s -> s.any { it in BANDS } } ?: DEFAULT_BANDS.toSet()
        set(v) = prefs.edit().putStringSet("enabled_bands", v).apply()
    var enabledModes: Set<String>
        get() = prefs.getStringSet("enabled_modes", null)?.toSet()?.takeIf { s -> s.any { it in MODES } } ?: DEFAULT_MODES.toSet()
        set(v) = prefs.edit().putStringSet("enabled_modes", v).apply()

    /** Second lookup source: country and region by prefix from HamQTH (free, no account). On by default. */
    /** Interface language code: "system", "ru" or "en" (see ru.r3xed.qsolog.Lang). */
    var language: String
        // An update of a set-up app keeps Russian (it was the only language); a new install follows the system.
        get() = prefs.getString("language", null) ?: if (prefs.contains("my_call")) "ru" else "system"
        set(v) = prefs.edit().putString("language", v).apply()
    /** Time of a new contact: when the card was opened (false) or when it is saved (true). */
    var timeOnSave: Boolean
        get() = prefs.getBoolean("time_on_save", false)
        set(v) = prefs.edit().putBoolean("time_on_save", v).apply()
    var hamqthEnabled: Boolean
        get() = prefs.getBoolean("hamqth_enabled", true)
        set(v) = prefs.edit().putBoolean("hamqth_enabled", v).apply()
    /** Last ЕРМАК / Cabrillo export: format name, CONTEST code, CATEGORY-OPERATOR. */
    /** ADIF export encoding: UTF-8 (most programs and sites) or Windows-1251 (LogHX, UR5EQF). */
    var adifUtf8: Boolean
        get() = prefs.getBoolean("adif_utf8", true)
        set(v) = prefs.edit().putBoolean("adif_utf8", v).apply()
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
    /** The first-start setup has been shown (finished or put off). */
    var welcomeDone: Boolean
        get() = prefs.getBoolean("welcome_done", false)
        set(v) = prefs.edit().putBoolean("welcome_done", v).apply()
    var launchCount: Int
        get() = prefs.getInt("launch_count", 0)
        set(v) = prefs.edit().putInt("launch_count", v).apply()
    /** Contest mode: the number the next contest contact sends (its STX_STRING). */
    var contestSerial: Int
        get() = prefs.getInt("contest_serial", 1)
        set(v) = prefs.edit().putInt("contest_serial", v).apply()

    /** Contest mode: send the same code with every contact (a region, a zone: "MO69", "16") instead of a serial. */
    var contestSentFixed: Boolean
        get() = prefs.getBoolean("contest_sent_fixed", false)
        set(v) = prefs.edit().putBoolean("contest_sent_fixed", v).apply()
    var contestSentText: String
        get() = prefs.getString("contest_sent_text", "") ?: ""
        set(v) = prefs.edit().putString("contest_sent_text", v).apply()

    /** Table sync: the address of the Apps Script web app (…/exec) and what the device remembers between syncs. */
    var sheetUrl: String
        get() = prefs.getString("sheet_url", "") ?: ""
        set(v) = prefs.edit().putString("sheet_url", v).apply()
    var sheetSince: String
        get() = prefs.getString("sheet_since", "") ?: ""
        set(v) = prefs.edit().putString("sheet_since", v).apply()
    var sheetLastPush: Long
        get() = prefs.getLong("sheet_last_push", 0)
        set(v) = prefs.edit().putLong("sheet_last_push", v).apply()
    var sheetStatus: String
        get() = prefs.getString("sheet_status", "") ?: ""
        set(v) = prefs.edit().putString("sheet_status", v).apply()

    /** Contest card: the app's own keyboard instead of the system one. */
    var contestKeypad: Boolean
        get() = prefs.getBoolean("contest_keypad", true)
        set(v) = prefs.edit().putBoolean("contest_keypad", v).apply()

    /** Contest card: the RST fields are shown (else folded into one line). */
    var contestRstShown: Boolean
        get() = prefs.getBoolean("contest_rst_shown", true)
        set(v) = prefs.edit().putBoolean("contest_rst_shown", v).apply()
    var lastPower: String
        get() = prefs.getString("last_power", "") ?: ""
        set(v) = prefs.edit().putString("last_power", v).apply()
}
