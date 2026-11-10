package ru.r3xed.qsolog.data

import ru.r3xed.qsolog.tr

/**
 * The program's settings through a Google Table of their own: a second Apps Script ([SCRIPT], its own web app
 * address …/exec), apart from the log's ([SheetSync]), which stays as it was. The table keeps a key and a value per
 * row. Accounts of outside services (logins, passwords, keys) go only when the user allows it ([CREDENTIAL_KEYS]);
 * what belongs to this device (ports, paths, scale, the sync's own state) never goes ([isLocal]).
 */
object SettingsSync {
    /** The Apps Script the user pastes into a new table. */
    val SCRIPT: String by lazy {
        SettingsSync::class.java.getResourceAsStream("/qsolog-settings-sync.gs")!!.use { it.readBytes().decodeToString() }
    }

    /** Logins, passwords and keys of the callbooks, online logs, the cluster and the radio's network login. */
    val CREDENTIAL_KEYS: Set<String> = setOf(
        "qrz_login", "qrz_password", "qrz_site_email", "qrz_site_password", "qrzcom_login", "qrzcom_password",
        "qrzcom_xml_login", "qrzcom_xml_password", "hamqth_login", "hamqth_password", "qrzcq_login", "qrzcq_password",
        "cat_lan_user", ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD, "dxc_login",
        "clublog_email", "eqsl_user", "eqsl_nickname", "lotw_user",
        "qrzcom_key", "clublog_password", "clublog_key", "eqsl_password", "lotw_password", "lotw_p12_password", "lotw_p12_passwords",
    )

    /** The passwords and keys among [CREDENTIAL_KEYS]: kept where the device keeps passwords, not in the plain settings. */
    val SECRET_KEYS: Set<String> = setOf(
        "qrz_password", "qrz_site_password", "qrzcom_password", "qrzcom_xml_password", "hamqth_password", "qrzcq_password",
        ru.r3xed.qsolog.cat.CatConfig.LAN_PASSWORD,
    ) + OnlineConfig.SECRET_KEYS

    private val LOCAL_KEYS = setOf(
        "ui_scale", "launch_count", "welcome_done", "whats_new_seen", "update_dismissed", "cat_device", "cw_device",
        "cw_ptt_device", "tqsl_path", "start_apps", "start_apps_return", "backup_last", "last_band", "last_freq",
        "last_mode", "last_power", "contest_serial", "settings", "secret_fallback",
    )
    private val LOCAL_PREFIXES = listOf("sheet_", "settings_sync_", "window_")

    /** A key that stays on this device: its port, its paths, its scale, the sync's own state. */
    fun isLocal(key: String) = key in LOCAL_KEYS || LOCAL_PREFIXES.any { key.startsWith(it) }

    /** What of [all] (the plain settings and the passwords together) goes to the table. */
    fun outgoing(all: Map<String, String>, credentials: Boolean): Map<String, String> =
        all.filterKeys { !isLocal(it) && (credentials || it !in CREDENTIAL_KEYS) }

    /** What of the table's [remote] settings this device takes: never its own ones, accounts only when allowed. */
    fun incoming(remote: Map<String, String>, credentials: Boolean): Map<String, String> = outgoing(remote, credentials)

    /** A short fingerprint of [m]: the settings changed here since the last exchange when it differs. */
    fun hash(m: Map<String, String>): String =
        m.toSortedMap().entries.joinToString("\u0001") { "${it.key}=${it.value}" }.hashCode().toUInt().toString(16)

    /** What the table holds: when and from which device it was written, and the settings. */
    data class Remote(val updated: String, val device: String, val settings: Map<String, String>)

    /** The last exchange: the table's time then, and the fingerprint of the settings both sides had. */
    data class State(val remoteStamp: String = "", val hash: String = "")

    sealed interface Step {
        /** The table was written by another device after the last exchange: take its settings. */
        data class Take(val settings: Map<String, String>, val stamp: String) : Step
        /** The settings changed here: send them. */
        data object Send : Step
        data object Nothing : Step
    }

    /**
     * Who wins: the table when another device wrote it since [state] (the newer change elsewhere is taken even if
     * something changed here too), otherwise this device's settings when they changed since then.
     */
    fun plan(remote: Remote, local: Map<String, String>, state: State, device: String): Step = when {
        remote.updated.isNotBlank() && remote.updated > state.remoteStamp && remote.device != device -> Step.Take(remote.settings, remote.updated)
        hash(local) != state.hash || remote.updated.isBlank() -> Step.Send
        else -> Step.Nothing
    }

    suspend fun fetch(url: String): Remote {
        val text = SheetSync.get(url.trim())
        val json = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *>
            ?: throw SyncException(
                if (text.contains("<html", ignoreCase = true)) tr("Скрипт таблицы закрыт: в развёртывании выберите «У кого есть доступ: все»")
                else tr("По этому адресу не скрипт настроек QSO-LOG")
            )
        if (json["app"] != "QSO-LOG-SETTINGS") {
            throw SyncException(if (json["app"] == "QSO-LOG") tr("Это адрес скрипта журнала: для настроек нужен второй скрипт, из второй таблицы") else tr("По этому адресу не скрипт настроек QSO-LOG"))
        }
        val s = (json["settings"] as? Map<*, *>).orEmpty().entries.associate { (k, v) -> k.toString() to (v?.toString() ?: "") }
        return Remote(json["updated"]?.toString().orEmpty(), json["device"]?.toString().orEmpty(), s)
    }

    /** Writes [settings] over the table; the table's new time. */
    suspend fun send(url: String, device: String, settings: Map<String, String>): String {
        val text = SheetSync.post(url.trim(), MiniJson.encode(mapOf("device" to device, "settings" to settings)))
        val json = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *> ?: throw SyncException(tr("По этому адресу не скрипт настроек QSO-LOG"))
        if (json["ok"] != true) throw SyncException(json["error"]?.toString() ?: tr("По этому адресу не скрипт настроек QSO-LOG"))
        return json["updated"]?.toString().orEmpty()
    }
}
