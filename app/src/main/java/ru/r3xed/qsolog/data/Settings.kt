package ru.r3xed.qsolog.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // The QRZ.ru password lives in a separate, encrypted file. Opening it (Android Keystore, Tink) takes a noticeable
    // part of a second, so it is opened on first use — off the main thread at start (see [loadSecrets]).
    private val secret: SharedPreferences by lazy { openSecret(context) }

    private fun openSecret(context: Context): SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secret", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("secret_fallback", Context.MODE_PRIVATE)
    }

    /** The settings without the passwords: quick, from the plain file. The passwords come from [loadSecrets]. */
    fun loadPublic() = StationSettings(
        myCall = prefs.getString("my_call", "") ?: "",
        myLocator = prefs.getString("my_locator", "") ?: "",
        myQth = prefs.getString("my_qth", "") ?: "",
        qrzLogin = prefs.getString("qrz_login", "") ?: "",
        qrzSiteEmail = prefs.getString("qrz_site_email", "") ?: "",
        qrzComLogin = prefs.getString("qrzcom_login", "") ?: "",
        hamqthLogin = prefs.getString("hamqth_login", "") ?: "",
        qrzcqLogin = prefs.getString("qrzcq_login", "") ?: "",
        power = prefs.getString("my_power", "") ?: "",
        station = AdifLabels.MINE.keys.associateWith { prefs.getString("station_$it", "") ?: "" }.filterValues { it.isNotEmpty() },
    )

    /** [s] with the passwords from the encrypted file; slow the first time — call it off the main thread. */
    fun loadSecrets(s: StationSettings) = s.copy(
        qrzPassword = secret.getString("qrz_password", "") ?: "",
        qrzSitePassword = secret.getString("qrz_site_password", "") ?: "",
        qrzComPassword = secret.getString("qrzcom_password", "") ?: "",
        hamqthPassword = secret.getString("hamqth_password", "") ?: "",
        qrzcqPassword = secret.getString("qrzcq_password", "") ?: "",
    )

    fun save(s: StationSettings) {
        prefs.edit()
            .putString("my_call", s.myCall.trim().uppercase())
            .putString("my_locator", s.myLocator.trim())
            .putString("my_qth", s.myQth.trim())
            .putString("qrz_login", s.qrzLogin.trim())
            .putString("qrz_site_email", s.qrzSiteEmail.trim())
            .putString("qrzcom_login", s.qrzComLogin.trim())
            .putString("hamqth_login", s.hamqthLogin.trim())
            .putString("qrzcq_login", s.qrzcqLogin.trim())
            .putString("my_power", s.power.trim())
            .apply {
                for (key in AdifLabels.MINE.keys) putString("station_$key", s.station[key].orEmpty().trim())
            }
            .apply()
        secret.edit().putString("qrz_password", s.qrzPassword).putString("qrz_site_password", s.qrzSitePassword)
            .putString("qrzcom_password", s.qrzComPassword).putString("hamqth_password", s.hamqthPassword)
            .putString("qrzcq_password", s.qrzcqPassword).apply()
    }

    /** The online logbooks' accounts: the e-mail and the TQSL location plain, the keys and the password encrypted. Slow — off the main thread. */
    fun loadOnline(): OnlineConfig = OnlineConfig.from(
        OnlineConfig.PUBLIC_KEYS.associateWith { prefs.getString(it, null) } + OnlineConfig.SECRET_KEYS.associateWith { secret.getString(it, null) },
    )

    fun saveOnline(c: OnlineConfig) {
        prefs.edit().apply { c.publicMap().forEach { (k, v) -> putString(k, v.trim()) } }.apply()
        secret.edit().apply { c.secretMap().forEach { (k, v) -> putString(k, v.trim()) } }.apply()
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
    /** The screen stays on while the app is open. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", false)
        set(v) = prefs.edit().putBoolean("keep_screen_on", v).apply()
    /** How long a finger rests on the app's keypad before it moves the cursor, ms. */
    var keypadHoldMs: Int
        get() = prefs.getInt("keypad_hold_ms", 400)
        set(v) = prefs.edit().putInt("keypad_hold_ms", v).apply()
    var hamqthEnabled: Boolean
        get() = prefs.getBoolean("hamqth_enabled", true)
        set(v) = prefs.edit().putBoolean("hamqth_enabled", v).apply()
    /** Last ЕРМАК / Cabrillo export: format name, CONTEST code, CATEGORY-OPERATOR. */
    /** ADIF export encoding: UTF-8 (most programs and sites) or Windows-1251 (LogHX, UR5EQF). */
    /** Stations looked up but not logged are kept in the search history (off until switched on). */
    var searchHistory: Boolean
        get() = prefs.getBoolean("search_history", false)
        set(v) = prefs.edit().putBoolean("search_history", v).apply()
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
    /** The version whose "Что нового" has been shown; empty — none yet. */
    var whatsNewSeen: String
        get() = prefs.getString("whats_new_seen", "") ?: ""
        set(v) = prefs.edit().putString("whats_new_seen", v).apply()
    /** A newer version is looked for at every start (on since 2.7). */
    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean("auto_update_check", true)
        set(v) = prefs.edit().putBoolean("auto_update_check", v).apply()
    /** The version whose "Доступна версия" window was closed: it is not offered again at start. */
    var updateDismissed: String
        get() = prefs.getString("update_dismissed", "") ?: ""
        set(v) = prefs.edit().putString("update_dismissed", v).apply()
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

    /**
     * The app's own keyboard in the QSO cards (normal and contest) instead of the system one, and its size.
     * Before 0.34.0 it was a contest-only switch, `contest_keypad`: its value carries over.
     */
    var keypad: KeypadMode
        get() = prefs.getString("keypad", null)?.let { v -> KeypadMode.entries.firstOrNull { it.name == v } }
            ?: if (prefs.getBoolean("contest_keypad", true)) KeypadMode.NORMAL else KeypadMode.OFF
        set(v) = prefs.edit().putString("keypad", v.name).apply()

    /** Contest mode: the QTH locators are exchanged too (VHF contests): two more fields in the card. */
    var contestGrids: Boolean
        get() = prefs.getBoolean("contest_grids", false)
        set(v) = prefs.edit().putBoolean("contest_grids", v).apply()
    /** The locator the contest card sends, when it differs from the station's (a portable QTH); empty — the station's. */
    var contestMyLocator: String
        get() = prefs.getString("contest_my_locator", "") ?: ""
        set(v) = prefs.edit().putString("contest_my_locator", v).apply()

    /** Contest card: the RST fields are shown (else folded into one line). */
    var contestRstShown: Boolean
        get() = prefs.getBoolean("contest_rst_shown", true)
        set(v) = prefs.edit().putBoolean("contest_rst_shown", v).apply()
    /** CAT: the transceiver's frequency, mode and S-meter (see ru.r3xed.qsolog.cat). */
    var cat: ru.r3xed.qsolog.cat.CatConfig
        get() = ru.r3xed.qsolog.cat.CatConfig.fromMap(
            ru.r3xed.qsolog.cat.CatConfig.KEYS.associateWith { prefs.getString(it, null) } +
                (ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD to secret.getString(ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD, "")),
            ru.r3xed.qsolog.cat.CatLinkType.USB,
        )
        set(v) {
            // The radio's network password is kept encrypted, with the other passwords.
            val m = v.toMap()
            prefs.edit().apply { (m - ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD).forEach { (k, x) -> putString(k, x) } }.apply()
            val pw = m[ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD].orEmpty()
            if (secret.getString(ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD, "") != pw) secret.edit().putString(ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD, pw).apply()
        }
    /** Contacts from WSJT-X, JTDX, MSHV over UDP (see ru.r3xed.qsolog.digi). */
    var digi: ru.r3xed.qsolog.digi.DigiConfig
        get() = ru.r3xed.qsolog.digi.DigiConfig.fromMap(ru.r3xed.qsolog.digi.DigiConfig.KEYS.associateWith { prefs.getString(it, null) })
        set(v) = prefs.edit().apply { v.toMap().forEach { (k, x) -> putString(k, x) } }.apply()
    var lastPower: String
        get() = prefs.getString("last_power", "") ?: ""
        set(v) = prefs.edit().putString("last_power", v).apply()

    /** Keys the user gave the actions (see ru.r3xed.qsolog.keys.KeyMap.encode). */
    var keymap: String
        get() = prefs.getString("keymap", "") ?: ""
        set(v) = prefs.edit().putString("keymap", v).apply()
    /** Telegraph on the DTR or RTS line of a USB adapter (see ru.r3xed.qsolog.cw). */
    var cw: ru.r3xed.qsolog.cw.CwConfig
        get() = ru.r3xed.qsolog.cw.CwConfig.fromMap(ru.r3xed.qsolog.cw.CwConfig.KEYS.associateWith { prefs.getString(it, null) })
        set(v) = prefs.edit().apply { v.toMap().forEach { (k, x) -> putString(k, x) } }.apply()
    /** Apps started together with QSO-LOG (see ru.r3xed.qsolog.data.StartApps.encode). */
    var startApps: String
        get() = prefs.getString("start_apps", "") ?: ""
        set(v) = prefs.edit().putString("start_apps", v).apply()
    /** After starting them, QSO-LOG comes back to the front at once; off: the last started app stays in front. */
    var startAppsReturn: Boolean
        get() = prefs.getBoolean("start_apps_return", true)
        set(v) = prefs.edit().putBoolean("start_apps_return", v).apply()
    /** The radio's last frequency on each band, "20m=14025000,40m=7074000": a band picked on the panel goes back there. */
    var bandMemory: String
        get() = prefs.getString("cat_band_memory", "") ?: ""
        set(v) = prefs.edit().putString("cat_band_memory", v).apply()
    /** The tuning step of the transceiver panel, Hz. */
    var tuneStep: Int
        get() = prefs.getInt("cat_tune_step", 100)
        set(v) = prefs.edit().putInt("cat_tune_step", v).apply()
}

/** The app's keyboard in the QSO cards: off (the system one), full size or compact (keys 30% lower). */
enum class KeypadMode { OFF, NORMAL, COMPACT }
