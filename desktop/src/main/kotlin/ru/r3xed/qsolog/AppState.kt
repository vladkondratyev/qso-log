package ru.r3xed.qsolog

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.cat.CatConfig
import ru.r3xed.qsolog.cat.CatFormat
import ru.r3xed.qsolog.cat.CatModes
import ru.r3xed.qsolog.cat.CatSession
import ru.r3xed.qsolog.cat.CatStatus
import ru.r3xed.qsolog.cat.RigState
import ru.r3xed.qsolog.cat.BandPlan
import ru.r3xed.qsolog.cat.CatCommand
import ru.r3xed.qsolog.cat.RadioMode
import ru.r3xed.qsolog.cw.CwConfig
import ru.r3xed.qsolog.cw.CwKeyer
import ru.r3xed.qsolog.cw.CwMacros
import ru.r3xed.qsolog.cw.CwState
import ru.r3xed.qsolog.data.StartApp
import ru.r3xed.qsolog.data.StartApps
import ru.r3xed.qsolog.keys.Chord
import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap
import ru.r3xed.qsolog.data.Adif
import ru.r3xed.qsolog.data.Callsign
import ru.r3xed.qsolog.digi.DigiConfig
import ru.r3xed.qsolog.digi.DigiListener
import ru.r3xed.qsolog.digi.DigiStatus
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Cabrillo
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.data.CallHistory
import ru.r3xed.qsolog.data.CallCommand
import ru.r3xed.qsolog.data.SearchEntry
import ru.r3xed.qsolog.data.SearchHistory
import ru.r3xed.qsolog.data.SearchSource
import ru.r3xed.qsolog.data.HistoryFilter
import ru.r3xed.qsolog.data.HistorySort
import ru.r3xed.qsolog.data.HistoryView
import ru.r3xed.qsolog.data.HISTORY_FIELDS
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.QrzInfo
import ru.r3xed.qsolog.data.Csv
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.HamQth
import ru.r3xed.qsolog.data.approxPosition
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzClient
import ru.r3xed.qsolog.data.QrzSite
import ru.r3xed.qsolog.data.QrzCom
import ru.r3xed.qsolog.data.Contest
import ru.r3xed.qsolog.data.ContestBook
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.Dupes
import ru.r3xed.qsolog.data.SheetSync
import ru.r3xed.qsolog.data.StatBucket
import ru.r3xed.qsolog.data.StatFilter
import ru.r3xed.qsolog.data.SyncState
import ru.r3xed.qsolog.data.QrzException
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.StationSettings
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.data.freqMhz
import ru.r3xed.qsolog.data.normalizeFreq
import ru.r3xed.qsolog.data.defaultRst
import ru.r3xed.qsolog.data.withDistance
import ru.r3xed.qsolog.ui.MapCamera
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

enum class Pane {
    Empty, Edit, Settings, Map, Welcome, Reference,
    /** The simplified contact card of the contest mode. */
    Contest,
    /** Charts of the log: bands, modes, days, stations… */
    Dashboard,
    /** Stations looked up but not logged. */
    History,
    /** The contest book: contests with their time and tours; in the ⋮ menu while contest mode is on. */
    Contests,
}

/** The contest card's message when the received number is missing; the card then puts the cursor in that field. */
val CONTEST_RCVD_ERROR get() = tr("Введите принятый код")

/** Callsign length at which the QRZ.ru / HamQTH lookup starts. */
const val MIN_LOOKUP_LENGTH = 4

/** A log search that looks like a callsign: 4–6 Latin letters and digits, at least one of each. */
private val SEARCH_CALL = Regex("^(?=.*[A-Z])(?=.*\\d)[A-Z0-9]{4,6}$")

sealed interface SearchLookup {
    data object Idle : SearchLookup
    data class Searching(val call: String) : SearchLookup
    data class NotFound(val call: String) : SearchLookup
    data object NoAccount : SearchLookup
    data class Failed(val message: String) : SearchLookup
}

/** Records for a contest report: [only] the picked ones, or the whole log when null. */
data class ContestTarget(val only: Set<Long>?)

/** The ADIF / CSV export dialog: which format, for [only] records picked in the log (null = the whole log). */
data class ExportTarget(val format: ExportFormat, val only: Set<Long>?)

/** Records the online-logbook upload is for; null = the whole log. */
data class UploadTarget(val only: Set<Long>?)

enum class ThemeMode(private val ru: String) {
    SYSTEM("Как в системе"), // no-tr
    LIGHT("Светлая"), // no-tr
    DARK("Тёмная"), // no-tr
    ;

    val label: String get() = tr(ru)
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: DesktopRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

enum class SortBy(private val ru: String, val defaultDesc: Boolean) {
    DATE("Дата", true), // no-tr
    DISTANCE("Км", true), // no-tr
    CALL("Позывной", false), // no-tr
    BAND("Диапазон", false), // no-tr
    ;

    val label: String get() = tr(ru)
}

/** Desktop counterpart of the Android AppViewModel. */
class AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = QsoDb()
    private val prefs = Settings()

    /** Interface language, set before anything builds a text. Changing it re-creates the screen (key in the root). */
    var language by mutableStateOf(I18n.fromCode(prefs.language).also { I18n.chosen = it }); private set

    fun changeLanguage(l: Lang) {
        I18n.chosen = l
        prefs.language = l.code
        language = l
    }
    val voice = VoiceNotes()

    var settings by mutableStateOf(prefs.load()); private set
    private val qrz = QrzClient { settings.qrzLogin to settings.qrzPassword }
    private val qrzSite = QrzSite { settings.qrzSiteEmail to settings.qrzSitePassword }
    private val qrzCom = QrzCom { settings.qrzComLogin to settings.qrzComPassword }

    /** Some callsign account is set: the QRZ.ru XML API (preferred), the QRZ.ru site's e-mail login or QRZ.com. */
    val hasQrzAccount get() = settings.qrzLogin.isNotBlank() || settings.qrzSiteEmail.isNotBlank() || settings.qrzComLogin.isNotBlank()

    /**
     * Station data for a callsign: through the QRZ.ru XML API when its account is set (it wins when several are set),
     * otherwise — or when the API fails — from the QRZ.ru site's callsign page with the e-mail login, and then from
     * the QRZ.com page (also when QRZ.ru does not know the callsign: foreign stations). Null: known to none of them.
     */
    private suspend fun qrzLookup(call: String, stillWanted: () -> Boolean = { true }): QrzInfo? {
        // Callbooks know R0DEMO, not R0DEMO/P or DL/R0DEMO; the card keeps the callsign as typed.
        val home = Callsign.home(call)
        var problem: Exception? = null
        // Some source answered that it does not know the callsign.
        var notFound = false
        suspend fun ask(source: suspend () -> QrzInfo?): QrzInfo? = try {
            source().also { if (it == null) notFound = true }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (problem == null) problem = e
            null
        }
        // The XML API gives no RDA district: for a Russian station it is read from the callsign page of the site.
        if (settings.qrzLogin.isNotBlank()) ask { qrz.lookup(home, stillWanted) }?.let { return qrzSite.withRda(it).copy(source = SearchSource.QRZ_RU.key) }
        // The QRZ.ru site is the same database as its XML API: asked only when the API is not set or failed.
        if (settings.qrzSiteEmail.isNotBlank() && !notFound) ask { qrzSite.lookup(home) }?.let { return it.copy(source = SearchSource.QRZ_RU_SITE.key) }
        if (settings.qrzComLogin.isNotBlank()) ask { qrzCom.lookup(home) }?.let { return it.copy(source = SearchSource.QRZ_COM.key) }
        if (notFound) return null
        throw problem ?: QrzException(403, tr("Укажите учётную запись QRZ.ru в настройках"))
    }

    /** Region and RDA (QRZ.ru site), region and zones (QRZ.com) into ADIF STATE / CNTY / CQZ / ITUZ unless the card has them. */
    private fun withQrzExtras(adif: Map<String, String>, info: QrzInfo): Map<String, String> =
        adif + listOf("STATE" to info.region, "CNTY" to info.rda, "CQZ" to info.cqZone, "ITUZ" to info.ituZone)
            .filter { (k, v) -> v.isNotBlank() && adif[k].isNullOrBlank() }

    var pane by mutableStateOf(Pane.Empty)
    var query by mutableStateOf(""); private set
    var qsos by mutableStateOf<List<Qso>>(emptyList()); private set
    /** The whole log regardless of the search, for the map. */
    var allQsos by mutableStateOf<List<Qso>>(emptyList()); private set
    var total by mutableStateOf(0); private set

    /** Records picked with a long press or ⌘/Ctrl-click; non-empty means the log is in selection mode. */
    var selected by mutableStateOf<Set<Long>>(emptySet()); private set
    val selecting get() = selected.isNotEmpty()

    fun toggleSelected(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    /** Everything the log currently shows (the search applies). */
    fun selectAllShown() {
        selected = qsos.map { it.id }.toSet()
    }

    /** "Выбрать записи…" (by dates, bands, modes) is open. */
    var showSelect by mutableStateOf(false)

    /** The contacts found by "Выбрать записи…" become the selection, whatever the search shows. */
    fun selectIds(ids: Set<Long>) {
        showSelect = false
        showTransfer = false
        selected = ids
    }

    fun clearSelection() {
        selected = emptySet()
    }

    var enabledBands by mutableStateOf(prefs.enabledBands); private set
    var enabledModes by mutableStateOf(prefs.enabledModes); private set

    // At least one band and one mode stay on: the card needs something to pick. The switch simply does not move.
    fun setBandEnabled(band: String, on: Boolean) {
        if (!on && enabledBands.count { it in BANDS && it != band } == 0) {
            say(tr("Должен быть включён хотя бы один диапазон"))
            return
        }
        enabledBands = if (on) enabledBands + band else enabledBands - band
        prefs.enabledBands = enabledBands
    }

    fun setModeEnabled(mode: String, on: Boolean) {
        if (!on && enabledModes.count { it in MODES && it != mode } == 0) {
            say(tr("Должен быть включён хотя бы один вид связи"))
            return
        }
        enabledModes = if (on) enabledModes + mode else enabledModes - mode
        prefs.enabledModes = enabledModes
    }

    // The log always opens by date, newest on top; another sort lasts only until the app is closed.
    var sortBy by mutableStateOf(SortBy.DATE); private set
    var sortDesc by mutableStateOf(true); private set

    /** A click on the current sort flips its direction; a click on another one selects it with its natural direction. */
    fun sort(by: SortBy) {
        if (by == sortBy) sortDesc = !sortDesc else { sortBy = by; sortDesc = by.defaultDesc }
    }

    /** Last view of the QSO map, kept while the app runs so returning from a card shows the same place. */
    var mapCamera: MapCamera? = null

    /** Where closing the contact card returns to: the empty pane or the map. */
    private var editReturn = Pane.Empty

    /** Recording started by holding "Новый QSO"; the value is the start time. */
    var recordingSince by mutableStateOf<Long?>(null); private set

    /** Bumped when a new contact is saved; the log scrolls to the top to show it. */
    var newSavedTick by mutableStateOf(0); private set

    var hamqthEnabled by mutableStateOf(prefs.hamqthEnabled); private set

    /** "Время связи": false — when the card was opened (default), true — when it is saved. */
    var timeOnSave by mutableStateOf(prefs.timeOnSave); private set

    fun changeTimeOnSave(on: Boolean) {
        timeOnSave = on
        prefs.timeOnSave = on
    }

    fun setHamqth(on: Boolean) {
        hamqthEnabled = on
        prefs.hamqthEnabled = on
    }

    var themeMode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.theme.uppercase()) }.getOrDefault(ThemeMode.SYSTEM)); private set

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.theme = mode.name.lowercase()
    }

    /** Result of "Проверить обновления" in the about block of the settings. */
    var update by mutableStateOf<UpdateState>(UpdateState.Idle); private set

    fun checkUpdate() {
        if (update == UpdateState.Checking) return
        update = UpdateState.Checking
        scope.launch {
            update = try {
                val r = withContext(Dispatchers.IO) { DesktopUpdates.latest() }
                if (DesktopUpdates.isNewer(r.version, APP_VERSION)) UpdateState.Available(r) else UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(
                    if (e is java.net.UnknownHostException) tr("Нет интернета: не удаётся связаться с GitHub")
                    else tr("Не удалось проверить: %s", e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    fun dismissUpdate() {
        update = UpdateState.Idle
    }

    /** Where "Назад" in the settings returns: the empty pane, or the card that sent the user there. */
    private var settingsReturn = Pane.Empty

    /** Leaves the first-start setup (done or "Настроить позже"); it is not shown again. */
    fun finishWelcome() {
        prefs.welcomeDone = true
        flushSettings()
        pane = Pane.Empty
    }

    fun openSettings(from: Pane = Pane.Empty, page: SettingsPage = SettingsPage.MAIN) {
        // A fresh visit starts at the top.
        scope.launch { settingsScroll.scrollTo(0) }
        settingsPage = page
        settingsPageDirect = page != SettingsPage.MAIN
        settingsReturn = from
        pane = Pane.Settings
    }

    /** Which settings page is open; Back (Esc) from the equipment or the callbooks returns to the main one. */
    var settingsPage by mutableStateOf(SettingsPage.MAIN)

    /** The page was opened straight away (a long press on TRX, the menu): Back returns to where that was. */
    private var settingsPageDirect = false

    val settingsScroll = ScrollState(0)

    /** The reference (bands, Morse, spelling alphabets, calculators): opened from the log's ⋮ menu or the "Файл" menu. */
    fun openReference() {
        pane = Pane.Reference
    }

    fun closeReference() {
        pane = Pane.Empty
    }

    fun closeSettings() {
        if (settingsPage != SettingsPage.MAIN && !settingsPageDirect && pane == Pane.Settings) {
            settingsPage = SettingsPage.MAIN
            return
        }
        pane = settingsReturn
        settingsReturn = Pane.Empty
        // Coming back to a card after entering the QRZ.ru account: look the callsign up now.
        if (pane == Pane.Edit && form.call.length >= MIN_LOOKUP_LENGTH && (lookup as? Lookup.Failed)?.noAccount == true) retryLookup()
    }

    /** The card as it was opened, to tell whether closing it would lose something. */
    private var formOriginal: Form? = null

    val hasUnsavedChanges: Boolean
        get() = (pane == Pane.Edit || pane == Pane.Contest) && formOriginal.let { it != null && form != it }

    /** "Закрыть без сохранения?" is on screen (from ✕ or Esc). */
    var confirmClose by mutableStateOf(false)

    /** The F1 table of keys is open. */
    var showKeys by mutableStateOf(false)

    /** Closing a card with typed data asks first; an untouched one closes at once. */
    fun requestClose() {
        if (hasUnsavedChanges) confirmClose = true else closeEditor()
    }

    /** The contest card asks before closing only when something was typed there. */
    fun requestCloseContest() {
        if (contestTyped) confirmClose = true else closeEditor()
    }

    /** Records whose QRZ.ru data is being fetched again right now (spinner instead of the button). */
    var refreshing by mutableStateOf<Set<Long>>(emptySet()); private set

    var form by mutableStateOf(Form()); private set
    var history by mutableStateOf(CallHistory(0, null)); private set
    var lookup by mutableStateOf<Lookup>(Lookup.Idle); private set
    var qrzStatus by mutableStateOf<String?>(null); private set
    var qrzOk by mutableStateOf<Boolean?>(null); private set
    /** Validation message shown under the form. */
    var formError by mutableStateOf<String?>(null); private set
    /** Bumped each time a form is opened, so per-form UI state (map, expanded fields) starts fresh. */
    var editSession by mutableStateOf(0); private set

    class Message(val text: String, val undo: (() -> Unit)? = null)

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var lookupJob: Job? = null
    private var saveSettingsJob: Job? = null

    /** "Что нового" is open: once after an update (not on the first install), or from the settings. */
    var showWhatsNew by mutableStateOf(false)

    fun closeWhatsNew() {
        showWhatsNew = false
        prefs.whatsNewSeen = APP_VERSION
    }

    init {
        // A first install has nothing new to tell: the version counts as seen.
        val firstInstall = settings.myCall.isBlank() && !prefs.welcomeDone
        if (prefs.whatsNewSeen != APP_VERSION) {
            if (firstInstall) prefs.whatsNewSeen = APP_VERSION else showWhatsNew = true
        }
        reload()
        KeyMap.custom = KeyMap.decode(prefs.keymap)
        scope.launch { restartCat() }
        scope.launch { restartDigi() }
        scope.launch { restartCw() }
        scope.launch { launchStartApps() }
        // First start: a short three-step setup; later, without a callsign, the settings as before.
        if (settings.myCall.isBlank()) pane = if (prefs.welcomeDone) Pane.Settings else Pane.Welcome
        scope.launch(Dispatchers.IO) { voice.cleanup(keep = db.audioFiles()) }
        scope.launch {
            val pw = withContext(Dispatchers.IO) { prefs.password() }
            if (settings.qrzPassword.isEmpty()) settings = settings.copy(qrzPassword = pw)
            // Read once even without an e-mail: the settings only save a password that has been loaded.
            val sitePw = withContext(Dispatchers.IO) { prefs.sitePassword() }
            if (settings.qrzSitePassword.isEmpty()) settings = settings.copy(qrzSitePassword = sitePw)
            val comPw = withContext(Dispatchers.IO) { prefs.comPassword() }
            if (settings.qrzComPassword.isEmpty()) settings = settings.copy(qrzComPassword = comPw)
            online = withContext(Dispatchers.IO) { prefs.loadOnline() }
        }
    }

    fun say(text: String, undo: (() -> Unit)? = null) {
        scope.launch { _messages.send(Message(text, undo)) }
    }

    // ---------- log ----------

    fun reload() {
        scope.launch {
            val (all, list) = withContext(Dispatchers.IO) {
                val all = db.all()
                all to if (query.isBlank()) all else db.all(query)
            }
            allQsos = all
            qsos = list
            total = all.size
        }
    }

    fun search(q: String) {
        query = q
        reload()
        searchLookupJob?.cancel()
        searchLookup = SearchLookup.Idle
        val call = q.trim().uppercase()
        if (!SEARCH_CALL.matches(call)) return
        // Typed a callsign that is not in the log: ask QRZ.ru and, if it knows it, open a new card filled in.
        searchLookupJob = scope.launch {
            delay(900) // wait until typing pauses; QRZ.ru allows one request per 3 s
            val inLog = withContext(Dispatchers.IO) { db.all(call) }
            if (inLog.isNotEmpty() || query.trim().uppercase() != call) return@launch
            if (!hasQrzAccount) {
                searchLookup = SearchLookup.NoAccount
                return@launch
            }
            searchLookup = SearchLookup.Searching(call)
            searchLookup = try {
                val info = qrzLookup(call) { query.trim().uppercase() == call }
                if (info == null) SearchLookup.NotFound(call) else {
                    newQsoFor(call, info)
                    SearchLookup.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: QrzException) {
                SearchLookup.Failed(e.message ?: tr("Ошибка QRZ.ru"))
            } catch (e: Exception) {
                SearchLookup.Failed(networkError(e))
            }
        }
    }

    /** QRZ.ru lookup started from the log search (the callsign is not in the log). */
    var searchLookup by mutableStateOf<SearchLookup>(SearchLookup.Idle); private set
    private var searchLookupJob: Job? = null

    fun retrySearchLookup() = search(query)

    /** New card for a callsign found on QRZ.ru from the log search; the search is cleared for when the user returns. */
    private fun newQsoFor(call: String, info: QrzInfo) {
        newQso()
        form = form.copy(
            call = call, name = info.fullName, qth = info.city, country = info.country,
            locator = info.locator.ifBlank { info.position?.let { Geo.latLonToLocator(it) }.orEmpty() },
            lat = info.lat, lon = info.lon, infoFromQrz = true,
            adif = withQrzExtras(form.adif, info),
        )
        // Nothing typed by the user yet: closing this card right away does not ask.
        formOriginal = form
        lookup = Lookup.Found(info)
        rememberSearch(requireNew = false)
        loadHistory(call)
        query = ""
        reload()
    }

    fun delete(qso: Qso) {
        selected = selected - qso.id
        // Hide the row at once, so the list never shows a record the message calls deleted.
        qsos = qsos.filter { it.id != qso.id }
        allQsos = allQsos.filter { it.id != qso.id }
        scope.launch {
            withContext(Dispatchers.IO) { db.delete(qso.id) }
            if ((pane == Pane.Edit || pane == Pane.Contest) && form.id == qso.id) pane = editReturn
            reload()
            val t = utc(qso.timeUtc)
            // Date and time say which contact went: the log may hold several with the same callsign.
            val left = allQsos.count { it.call == qso.call }
            val tail = if (left > 0) tr(". Других связей с %s: %s", qso.call, left) else ""
            say(tr("Удалена связь с %s %s %s%s", qso.call, DATE_FMT.format(t), TIME_FMT.format(t), tail)) { restore(qso) }
        }
    }

    /** Deletes the whole log and all voice notes. The settings pane asks for confirmation first. */
    fun deleteAll() {
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                val n = db.deleteAll()
                voice.deleteAll()
                n
            }
            selected = emptySet()
            reload()
            say(tr("История QSO удалена: %s записей", n))
        }
    }

    private fun restore(qso: Qso) {
        scope.launch {
            // A fresh change time: a deletion already sent to the table must not win over the undo.
            withContext(Dispatchers.IO) { db.save(qso.copy(id = 0, updatedAt = System.currentTimeMillis())) }
            reload()
        }
    }

    // ---------- voice ----------

    /** Returns false if the microphone could not be opened. */
    fun startRecording(): Boolean {
        return try {
            voice.start()
            recordingSince = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            recordingSince = null
            say(tr("Не удалось включить микрофон: %s", e.message ?: e.javaClass.simpleName))
            false
        }
    }

    /** Ends the recording and opens a new contact card with it attached. */
    fun finishRecording() {
        if (recordingSince == null) return
        recordingSince = null
        scope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { voice.stop() }.getOrNull() }
            addQso(audio = name.orEmpty())
            if (name == null) say(tr("Запись слишком короткая, аудио не сохранено"))
        }
    }

    /** Recording started from the card's header (🎤): runs until ■ or "Сохранить". */
    var cardRecordingSince by mutableStateOf<Long?>(null); private set

    fun startCardRecording() {
        try {
            voice.start()
            cardRecordingSince = System.currentTimeMillis()
        } catch (e: Exception) {
            cardRecordingSince = null
            say(tr("Не удалось включить микрофон: %s", e.message ?: e.javaClass.simpleName))
        }
    }

    /** Stops the card recording and attaches it to the form. */
    fun stopCardRecording() {
        if (cardRecordingSince == null) return
        cardRecordingSince = null
        val name = runCatching { voice.stop() }.getOrNull()
        if (name == null) say(tr("Запись слишком короткая, аудио не сохранено"))
        else form = form.copy(audio = name)
    }

    /** Leaving the card without saving: the recording goes with it. */
    private fun discardCardRecording() {
        if (cardRecordingSince == null) return
        cardRecordingSince = null
        runCatching { voice.stopAndDiscard() }
    }

    // ---------- ЕРМАК / Cabrillo ----------

    /** The contest-report dialog is open: for [ContestTarget.only] records, or the whole log when null. */
    var contestTarget by mutableStateOf<ContestTarget?>(null); private set

    /** Header and choices from the dialog, waiting for the file picker. */
    private var pendingContest: Pair<Cabrillo.Header, PendingExport>? = null

    fun openContestExport(selectedOnly: Boolean) {
        contestTarget = ContestTarget(if (selectedOnly) selected else null)
    }

    fun closeContestExport() {
        contestTarget = null
    }

    /** Values the dialog starts with: last format and contest, RDA from "Мой район", operator from the station. */
    fun contestDefaults(): Cabrillo.Header = Cabrillo.Header(
        format = runCatching { Cabrillo.Format.valueOf(prefs.contestFormat) }.getOrDefault(Cabrillo.Format.ERMAK),
        contest = activeContest?.code?.ifBlank { null } ?: prefs.contestCode,
        callsign = settings.myCall,
        categoryOperator = prefs.contestOperator,
        location = settings.station["MY_CNTY"].orEmpty().replace("-", "").uppercase(),
        operators = settings.station["OPERATOR"].orEmpty(),
        createdBy = "QSO-LOG $APP_VERSION",
    )

    /** Remembers the dialog's choice and returns the file name to offer. */
    /** Contests of the book with contacts among [only] (or the whole log), and how many: the report can take one of them. */
    fun contestsInLog(only: Set<Long>?): List<Pair<Contest, Int>> = contestBook.all().map { c ->
        c to allQsos.count { (only == null || it.id in only) && it.adif[Contest.REF] == c.id }
    }.filter { it.second > 0 }

    /** The contest the report dialog starts with: the active one when it has contacts. */
    fun contestReportDefault(only: Set<Long>?): String? = activeContest?.id?.takeIf { id -> contestsInLog(only).any { it.first.id == id } }

    /** Contacts entered in contest mode among [only] (or the whole log): the report dialog then offers to take only them. */
    fun hasContestQsos(only: Set<Long>?): Boolean = allQsos.any { (only == null || it.id in only) && ContestMode.isContest(it.adif) }

    /** Remembers the dialog's choice and returns the file name to offer. */
    fun prepareContest(h: Cabrillo.Header, onlyNew: Boolean, mark: Boolean, contestOnly: Boolean, contestRef: String? = null): String {
        prefs.contestFormat = h.format.name
        prefs.contestCode = h.contest
        prefs.contestOperator = h.categoryOperator
        pendingContest = h to PendingExport(ExportFormat.CONTEST, contestTarget?.only, onlyNew, mark, contestOnly, contestRef.takeIf { contestOnly })
        contestTarget = null
        val code = h.contest.uppercase().ifBlank { "LOG" }.replace('/', '-')
        return "${h.callsign.ifBlank { "log" }.replace('/', '-')}_$code.${h.format.extension}"
    }

    /** The save dialog was cancelled: forget the chosen header. */
    fun cancelContest() {
        pendingContest = null
    }

    fun exportContest(file: File) {
        val (h, p) = pendingContest ?: return
        pendingContest = null
        writeExport(p, file, h.format.title) { list, out -> out.write(Cabrillo.export(list, h).toByteArray(Cabrillo.charset(h.format))) }
    }

    // ---------- export marks: ADIF, CSV, ЕРМАК / Cabrillo ----------

    /** The ADIF / CSV export dialog is open: for [ExportTarget.only] records picked in the log, or the whole log when null. */
    var exportTarget by mutableStateOf<ExportTarget?>(null); private set

    /** Choices of an export dialog, waiting for the file picker. */
    class PendingExport(
        val format: ExportFormat, val only: Set<Long>?, val onlyNew: Boolean, val mark: Boolean, val contestOnly: Boolean = false,
        /** Only the contacts of this contest from the book. */
        val contestRef: String? = null,
    )

    private var pendingExport: PendingExport? = null

    /** Encoding of ADIF files: UTF-8 by default; Windows-1251 for LogHX and UR5EQF. Remembered between exports. */
    var adifUtf8 by mutableStateOf(prefs.adifUtf8); private set

    fun chooseAdifUtf8(utf8: Boolean) {
        adifUtf8 = utf8
        prefs.adifUtf8 = utf8
    }

    fun openExport(format: ExportFormat, selectedOnly: Boolean) {
        if (format == ExportFormat.CONTEST) openContestExport(selectedOnly)
        else exportTarget = ExportTarget(format, if (selectedOnly) selected else null)
    }

    fun closeExport() {
        exportTarget = null
    }

    /**
     * The contacts an export takes: the picked ones or the whole log, without those already exported to [format] when
     * [onlyNew], and only those entered in contest mode when [contestOnly].
     */
    fun exportCandidates(format: ExportFormat, only: Set<Long>?, onlyNew: Boolean, contestOnly: Boolean = false, contestRef: String? = null): List<Qso> =
        allQsos.filter { takes(it, only, onlyNew, format, contestOnly, contestRef.takeIf { contestOnly }) }

    private fun takes(q: Qso, only: Set<Long>?, onlyNew: Boolean, format: ExportFormat, contestOnly: Boolean, contestRef: String? = null) =
        (only == null || q.id in only) && (!onlyNew || !format.isExported(q.adif)) && (!contestOnly || ContestMode.isContest(q.adif)) &&
            (contestRef == null || q.adif[Contest.REF] == contestRef)

    /** Remembers the dialog's choice and returns the format and the file name to offer in the save dialog. */
    fun prepareExport(onlyNew: Boolean, mark: Boolean): Pair<ExportFormat, String>? {
        val t = exportTarget ?: return null
        pendingExport = PendingExport(t.format, t.only, onlyNew, mark)
        exportTarget = null
        val call = settings.myCall.ifBlank { "log" }.replace('/', '-')
        val n = exportCandidates(t.format, t.only, onlyNew).size
        return t.format to when (t.format) {
            ExportFormat.CSV -> "qso_${call}_${LocalDate.now()}.csv"
            else -> if (t.only != null || onlyNew) "${call}_${n}qso_${LocalDate.now()}.adi" else "${call}_${LocalDate.now()}.adi"
        }
    }

    /** Saves the ADIF or CSV file chosen after [prepareExport]; null (the save dialog was cancelled) drops the choice. */
    fun exportFile(file: File?) {
        val p = pendingExport ?: return
        pendingExport = null
        if (file == null) return
        writeExport(p, file, p.format.title) { list, out ->
            if (p.format == ExportFormat.CSV) Csv.export(list, out)
            else Adif.export(list, out, if (adifUtf8) Charsets.UTF_8 else Adif.WINDOWS_1251, program = "QSO-LOG", version = APP_VERSION)
        }
    }

    /** Writes the export, then (if asked) marks the exported contacts with the date and time. */
    private fun writeExport(p: PendingExport, file: File, title: String, write: (List<Qso>, java.io.OutputStream) -> Unit) {
        if (p.only != null) clearSelection()
        scope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all().filter { takes(it, p.only, p.onlyNew, p.format, p.contestOnly, p.contestRef) }
                    file.outputStream().use { write(list, it) }
                    if (p.mark) list.forEach { db.save(p.format.mark(it)) }
                    list.size
                }
                reload()
                tr("Экспортировано в %s: %s, файл %s", title, count, file.name) + if (p.mark && count > 0) tr(", отметка поставлена") else ""
            } catch (e: Exception) {
                tr("Не удалось сохранить файл: %s", e.message)
            }
            say(msg)
        }
    }

    /** Takes the [format] export mark off the open card (saved with the card), so the next "only new" export takes it. */
    fun clearExportMark(format: ExportFormat) {
        form = form.copy(adif = format.unmarkFields(form.adif))
    }

    fun importContest(file: File) {
        scope.launch {
            val msg = try {
                val (added, dup, r) = withContext(Dispatchers.IO) {
                    val r = file.inputStream().use { Cabrillo.import(it) }
                    val rows = r.rows.map { it.withDistance(settings.myPosition) }
                    val added = db.insertAll(rows)
                    Triple(added, rows.size - added, r)
                }
                buildString {
                    append(tr("Импортировано из отчёта"))
                    if (r.contest.isNotBlank()) append(" ${r.contest}")
                    append(": $added")
                    if (dup > 0) append(tr(", повторов пропущено: %s", dup))
                    if (r.skipped > 0) append(tr(", строк с ошибками: %s", r.skipped))
                }
            } catch (e: Exception) {
                tr("Не удалось прочитать файл: %s", e.message)
            }
            reload()
            say(msg)
        }
    }

    // ---------- form ----------

    fun openMap() {
        clearSelection()
        pane = Pane.Map
    }

    /** Dashboard parameters, kept while the app runs. */
    var dashFilter by mutableStateOf(StatFilter()); private set
    var dashBucket by mutableStateOf(StatBucket.AUTO); private set

    fun changeDashFilter(f: StatFilter) {
        dashFilter = f
    }

    fun changeDashBucket(b: StatBucket) {
        dashBucket = b
    }

    fun openDashboard() {
        clearSelection()
        pane = Pane.Dashboard
    }

    /** Closes the card without saving; a voice note recorded for an unsaved contact is deleted. */
    fun closeEditor() {
        rememberSearch()
        lookupJob?.cancel() // a lookup for another card must not land in this one
        discardCardRecording()
        contestDraft = null
        val f = form
        if (f.isNew) {
            if (f.audio.isNotBlank()) voice.delete(f.audio)
            if (f.removedAudio.isNotBlank()) voice.delete(f.removedAudio)
        }
        pane = editReturn
        returnToNewCard(f.isNew)
    }

    /**
     * "Новый QSO". When the log search has narrowed down to one station (or the query is exactly its callsign),
     * the card opens for that station: its data from the last contact, then refreshed from QRZ.ru.
     */
    fun addQso(audio: String = "") {
        if (contestMode) return newContestQso(audio)
        val last = searchedStation()
        if (last == null) newQso(audio) else newQsoFromLog(last, audio)
    }

    /** The search has narrowed the log to one station: its contacts, for the "worked on these bands" line. */
    fun searchedHistory(): CallHistory? {
        val call = searchedStation()?.call ?: return null
        return CallHistory.of(allQsos.filter { it.call == call }.sortedByDescending { it.timeUtc })
    }

    private fun searchedStation(): Qso? {
        if (query.isBlank()) return null
        val q = query.trim().uppercase()
        val calls = qsos.map { it.call }.distinct()
        val call = calls.firstOrNull { it == q } ?: calls.singleOrNull() ?: return null
        return allQsos.filter { it.call == call }.maxByOrNull { it.timeUtc }
    }

    /** The card was filled from the log: a fresh QRZ.ru answer updates it, HamQTH's rough region must not replace it. */
    private var cardFromLog = false

    private fun newQsoFromLog(last: Qso, audio: String) {
        newQso(audio)
        form = form.copy(
            call = last.call, name = last.name, qth = last.qth, country = last.country, locator = last.locator,
            lat = last.lat, lon = last.lon, infoFromQrz = true,
            // What else is known about the station: region, zones, QSL via… (and the "≈ region" mark if it was approximate).
            adif = form.adif + last.adif.filterKeys { it in AdifLabels.THEM || it == HamQth.POSITION_FIELD },
        )
        formOriginal = form
        cardFromLog = true
        loadHistory(last.call)
        query = ""
        reload()
        // Without an account there is nothing to update: the card keeps what the log had.
        if (hasQrzAccount) lookupJob = scope.launch { runLookup(last.call) }
    }

    fun newQso(audio: String = "") {
        lookupJob?.cancel() // a lookup for another card must not land in this one
        if (pane == Pane.Edit && form.isNew) closeEditor()
        cardFromLog = false
        val now = LocalDateTime.now(ZoneOffset.UTC)
        // Mode, band and frequency of the most recent contact in the log: usually the next one is on the same.
        // An empty log falls back to what the last saved card had.
        val latest = allQsos.maxByOrNull { it.timeUtc }
        // Only one mode or band switched on in the settings: that one, whatever the last contact had.
        val onlyMode = MODES.filter { it in enabledModes }.singleOrNull()
        val onlyBand = BANDS.filter { it in enabledBands }.singleOrNull()
        val mode = onlyMode ?: latest?.mode?.ifBlank { null } ?: prefs.lastMode
        val band = latest?.band?.ifBlank { null } ?: prefs.lastBand
        val freq = if (latest != null) latest.freqMhz else prefs.lastFreq
        form = Form(
            date = DATE_FMT.format(now),
            time = TIME_FMT.format(now),
            band = onlyBand ?: band,
            mode = mode,
            // The last frequency belongs to another band than the only one allowed: leave it empty.
            freq = if (onlyBand == null || onlyBand == band) freq else "",
            rstSent = defaultRst(mode),
            rstRcvd = defaultRst(mode),
            power = settings.power.ifBlank { prefs.lastPower },
            audio = audio,
            // "Моя станция" comes from the settings; each record then keeps its own copy.
            myCall = settings.myCall,
            myLocator = settings.myLocator,
            adif = settings.station,
        )
        formOriginal = form
        history = CallHistory(0, null)
        lookup = Lookup.Idle
        formError = null
        editSession++
        editReturn = Pane.Empty
        pane = Pane.Edit
        catFollowNewCard()
    }

    fun edit(qso: Qso, from: Pane = Pane.Empty) {
        openedByKeys = false
        catWrote = null // a saved contact keeps its frequency and mode
        lookupJob?.cancel() // a lookup for another card must not land in this one
        if (pane == Pane.Edit && form.isNew) closeEditor()
        cardFromLog = false
        val t = utc(qso.timeUtc)
        form = Form(
            id = qso.id, call = qso.call,
            date = DATE_FMT.format(t), time = TIME_FMT.format(t),
            band = qso.band, mode = qso.mode, freq = qso.freqMhz,
            rstSent = qso.rstSent, rstRcvd = qso.rstRcvd,
            name = qso.name, qth = qso.qth, country = qso.country, locator = qso.locator,
            lat = qso.lat, lon = qso.lon, power = qso.power,
            qslSent = qso.qslSent, qslRcvd = qso.qslRcvd, comment = qso.comment,
            audio = qso.audio,
            myCall = qso.myCall, myLocator = qso.myLocator,
            adif = qso.adif - AdifLabels.TIME_OFF,
            dateOff = Adif.displayDate(qso.adif["QSO_DATE_OFF"]),
            timeOff = Adif.displayTime(qso.adif["TIME_OFF"]),
            bandRxFollows = qso.adif["BAND_RX"].let { it.isNullOrBlank() || it.equals(qso.band, ignoreCase = true) },
            freqRxFollows = qso.adif["FREQ_RX"].let { it.isNullOrBlank() || it.toDoubleOrNull() == qso.freqMhz.toDoubleOrNull() },
            timeOffFollows = qso.adif["TIME_OFF"].isNullOrBlank() ||
                (Adif.displayDate(qso.adif["QSO_DATE_OFF"]) == DATE_FMT.format(t) && Adif.displayTime(qso.adif["TIME_OFF"]) == TIME_FMT.format(t)),
            pendingLookup = qso.pendingLookup,
            distanceKm = qso.distanceKm, bearing = qso.bearing,
            createdAt = qso.createdAt,
        )
        formOriginal = form
        lookup = Lookup.Idle
        formError = null
        editSession++
        loadHistory(qso.call)
        editReturn = if (from == Pane.Map) Pane.Map else Pane.Empty
        // In contest mode a contest contact opens in the contest card, where "назад / вперёд" go through the others.
        pane = if (contestMode && ContestMode.isContest(qso.adif)) Pane.Contest else Pane.Edit
    }

    fun setBand(band: String) {
        form = form.copy(band = band)
        if (controlsRig()) {
            rigBand(band)?.let { form = form.copy(freq = CatFormat.mhz(it)) }
            catTrackCard()
        }
    }

    fun setAdif(key: String, value: String) {
        form = form.copy(adif = form.adif + (key to value))
    }

    /** Detaches the voice note. The file itself goes when the card is saved, so closing without saving keeps it. */
    fun removeAudio() {
        val f = form
        if (f.audio.isBlank()) return
        form = f.copy(audio = "", removedAudio = f.audio)
        say(tr("Аудиозапись удалена")) {
            if (form.audio.isBlank() && form.removedAudio == f.audio) form = form.copy(audio = f.audio, removedAudio = "")
        }
    }

    /** The station a record was made from: its own locator, or the one from the settings. */
    fun myPositionFor(f: Form): LatLon? = Geo.locatorToLatLon(f.myLocator) ?: settings.myPosition

    fun update(f: Form) {
        form = f
    }

    fun setNow() {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        form = form.copy(date = DATE_FMT.format(now), time = TIME_FMT.format(now))
    }

    fun setMode(mode: String) {
        form = withMode(form, mode)
        if (controlsRig()) {
            rigMode(mode)?.let { hz -> form = form.copy(freq = CatFormat.mhz(hz), band = bandForFreq(hz / 1e6) ?: form.band) }
            catTrackCard()
        }
    }

    private fun withMode(f: Form, mode: String): Form {
        // Swap the default report only if the user has not typed their own.
        val oldDefault = defaultRst(f.mode)
        return f.copy(
            mode = mode,
            rstSent = if (f.rstSent.isBlank() || f.rstSent == oldDefault) defaultRst(mode) else f.rstSent,
            rstRcvd = if (f.rstRcvd.isBlank() || f.rstRcvd == oldDefault) defaultRst(mode) else f.rstRcvd,
        )
    }

    fun setFreq(freq: String) {
        // "14195" works too: taken as kHz (see freqMhz), stored as MHz on saving.
        val band = freqMhz(freq)?.let { bandForFreq(it) }
        form = form.copy(freq = freq, band = band ?: form.band)
    }

    fun setLocator(loc: String) {
        form = form.copy(locator = loc, lat = null, lon = null, infoFromQrz = false, adif = form.adif - HamQth.POSITION_FIELD)
    }

    fun setCall(raw: String) {
        val call = raw.uppercase().filter { it.isLetterOrDigit() || it == '/' }
        if (formError == tr("Введите позывной")) formError = null
        val f = form
        if (call != f.call) cardFromLog = false
        form = if (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && locatorFree(f))) {
            f.copy(call = call, name = "", qth = "", country = "", locator = "", lat = null, lon = null, infoFromQrz = false, adif = f.adif - HISTORY_FIELDS)
        } else f.copy(call = call)
        // VHF contest: the station's locator from its earlier contact (the previous tour), shown at once.
        if (pane == Pane.Contest && ContestMode.hasGrids(form.adif) && form.isNew && locatorFree(form)) {
            val loc = ContestMode.previousLocator(call, allQsos, form.adif[Contest.REF])
            pinnedLocator = loc?.let { call to it }
            if (loc != null) form = form.copy(locator = loc, lat = null, lon = null, adif = form.adif - HamQth.POSITION_FIELD)
        }
        loadHistory(call)
        lookupJob?.cancel()
        // Searching starts from the 4th character: shorter prefixes only waste the QRZ.ru request limit.
        if (call.length < MIN_LOOKUP_LENGTH) {
            lookup = Lookup.Idle
            return
        }
        lookupJob = scope.launch {
            delay(900) // wait until typing pauses; QRZ.ru allows one request per 3 s
            runLookup(call)
        }
    }

    fun retryLookup() {
        lookupJob?.cancel()
        lookupJob = scope.launch { runLookup(form.call) }
    }

    /**
     * QRZ.ru first (full data: name, QTH, locator). Without an account, when it does not know the callsign or fails,
     * HamQTH's free prefix search fills country, region, zones and an approximate position, if switched on.
     */
    private suspend fun runLookup(call: String) {
        lookupOnce(call)
        keepPinnedLocator(call)
        // Whatever was found goes to the search history: a cache to look at and to fill a card offline, never a contact.
        if (form.call == call) rememberSearch(requireNew = false)
    }

    private suspend fun lookupOnce(call: String) {
        lookup = Lookup.Loading
        if (!hasQrzAccount) {
            lookup = historyFallback(call, problem = null) ?: hamqthFallback(call, qrzProblem = null)
                ?: Lookup.Failed(tr("Укажите учётную запись QRZ.ru в настройках"), noAccount = true)
            return
        }
        lookup = try {
            val info = qrzLookup(call) { form.call == call }
            if (info == null) hamqthFallback(call, qrzProblem = null) ?: Lookup.NotFound else {
                val f = form
                if (f.call == call && cardFromLog) {
                    // Filled from the log: QRZ.ru's values win, but what it leaves blank keeps the log's.
                    val pos = info.position
                    form = f.copy(
                        name = info.fullName.ifBlank { f.name }, qth = info.city.ifBlank { f.qth }, country = info.country.ifBlank { f.country },
                        locator = info.locator.ifBlank { pos?.let { Geo.latLonToLocator(it) } ?: f.locator },
                        lat = if (pos != null) info.lat else f.lat, lon = if (pos != null) info.lon else f.lon,
                        adif = withQrzExtras(if (pos != null || info.locator.isNotBlank()) f.adif - HamQth.POSITION_FIELD else f.adif, info),
                    )
                } else if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && locatorFree(f)))) {
                    form = f.copy(
                        name = info.fullName, qth = info.city, country = info.country,
                        locator = info.locator.ifBlank { info.position?.let { Geo.latLonToLocator(it) }.orEmpty() },
                        lat = info.lat, lon = info.lon, infoFromQrz = true,
                        // Region, RDA and zones are the found station's: none stay from the callsign typed before.
                        adif = withQrzExtras(f.adif - HISTORY_FIELDS, info),
                    )
                }
                Lookup.Found(info)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: QrzException) {
            val msg = e.message ?: tr("Ошибка QRZ.ru")
            historyFallback(call, msg) ?: hamqthFallback(call, qrzProblem = msg) ?: Lookup.Failed(msg)
        } catch (e: Exception) {
            val msg = networkError(e)
            historyFallback(call, msg) ?: hamqthFallback(call, qrzProblem = msg) ?: Lookup.Failed(msg)
        }
    }

    /** HamQTH prefix search into the card; null when switched off, unknown or unreachable. */
    private suspend fun hamqthFallback(call: String, qrzProblem: String?): Lookup? {
        if (!hamqthEnabled || cardFromLog) return null
        val d = try {
            withContext(Dispatchers.IO) { HamQth.dxcc(call) } ?: return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val f = form
        if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && locatorFree(f)))) {
            // Zones and DXCC go to their ADIF fields unless the card already has them.
            val extra = mapOf("CQZ" to d.cqZone, "ITUZ" to d.ituZone, "DXCC" to d.dxcc, "CONT" to d.continent)
                .filter { (k, v) -> v.isNotBlank() && f.adif[k].isNullOrBlank() }
            form = f.copy(
                name = "", qth = d.region, country = d.country, locator = "",
                lat = d.lat, lon = d.lon, infoFromQrz = true,
                adif = f.adif + extra + if (d.position != null) mapOf(HamQth.POSITION_FIELD to HamQth.POSITION_REGION) else emptyMap(),
            )
        }
        return Lookup.Approx(d, qrzProblem)
    }

    private fun networkError(e: Exception) = when (e) {
        is java.net.UnknownHostException -> tr("Нет интернета: не удаётся найти api.qrz.ru")
        is java.net.SocketTimeoutException -> tr("QRZ.ru не ответил вовремя")
        is javax.net.ssl.SSLException -> tr("Ошибка защищённого соединения: %s", e.message)
        else -> tr("Нет связи с QRZ.ru: %s %s", e.javaClass.simpleName, e.message.orEmpty()).trim()
    }

    private fun loadHistory(call: String) {
        val id = form.id
        scope.launch {
            history = if (call.length < 3) CallHistory(0, null)
            else withContext(Dispatchers.IO) { db.history(call, excludeId = id) }
        }
    }

    /** Returns the error shown under the form, or null when saved. */
    fun trySave(): String? {
        formError = save()
        return formError
    }

    /** Retries the QRZ.ru lookup for a contact saved offline; fills only fields that are still empty. */
    fun refreshLookup(qso: Qso) {
        if (qso.id in refreshing) return
        refreshing = refreshing + qso.id
        scope.launch {
            val msg = try {
                fillFromQrz(qso)
            } finally {
                refreshing = refreshing - qso.id
            }
            reload()
            say(msg)
        }
    }

    /** Fills the saved contact's empty fields from QRZ.ru; returns what to tell the user. */
    private suspend fun fillFromQrz(qso: Qso): String = try {
        val info = qrzLookup(qso.call)
        if (info == null) {
            withContext(Dispatchers.IO) { db.save(qso.copy(pendingLookup = false)) }
            tr("На QRZ.ru позывного %s нет", qso.call)
        } else {
            val pos = info.position
            // A region-centre position from HamQTH gives way to the station's own one.
            val approx = qso.approxPosition && pos != null
            val lat = if (approx) pos!!.lat else qso.lat ?: pos?.lat
            val lon = if (approx) pos!!.lon else qso.lon ?: pos?.lon
            val me = Geo.locatorToLatLon(qso.myLocator) ?: settings.myPosition
            val them = if (lat != null && lon != null) LatLon(lat, lon) else null
            val updated = qso.copy(
                name = qso.name.ifBlank { info.fullName },
                qth = if (approx) info.city.ifBlank { qso.qth } else qso.qth.ifBlank { info.city },
                country = qso.country.ifBlank { info.country },
                locator = qso.locator.ifBlank { info.locator.ifBlank { pos?.let { Geo.latLonToLocator(it) }.orEmpty() } },
                lat = lat, lon = lon,
                distanceKm = (if (approx) null else qso.distanceKm) ?: if (me != null && them != null) Geo.distanceKm(me, them) else null,
                bearing = (if (approx) null else qso.bearing) ?: if (me != null && them != null) Geo.bearing(me, them) else null,
                adif = withQrzExtras(if (approx) qso.adif - HamQth.POSITION_FIELD else qso.adif, info),
                pendingLookup = false,
                updatedAt = System.currentTimeMillis(),
            )
            withContext(Dispatchers.IO) { db.save(updated) }
            tr("Данные %s получены с QRZ.ru", qso.call)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: QrzException) {
        e.message ?: tr("Ошибка QRZ.ru")
    } catch (e: Exception) {
        networkError(e)
    }

    /** Returns an error message, or null when saved. [quiet]: no "Связь записана" message (the contest card says it in its own line). */
    private fun save(quiet: Boolean = false): String? {
        stopCardRecording()
        val f0 = form
        val orig = formOriginal
        // "Время связи: при сохранении": a new card whose time the user did not touch takes the moment of saving.
        val f = if (f0.isNew && timeOnSave && orig != null && f0.date == orig.date && f0.time == orig.time) {
            val now = LocalDateTime.now(ZoneOffset.UTC)
            f0.copy(date = DATE_FMT.format(now), time = TIME_FMT.format(now))
        } else f0
        if (f.call.length < 3) return tr("Введите позывной")
        val date = try { LocalDate.parse(f.date.trim(), DATE_FMT) } catch (e: Exception) { return tr("Дата в формате ДД.ММ.ГГГГ") }
        val time = try { LocalTime.parse(f.time.trim(), TIME_FMT) } catch (e: Exception) { return tr("Время в формате ЧЧ:ММ") }
        val ts = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()
        val freq = normalizeFreq(f.freq)
        // Receive band/frequency and end time repeat the main fields; kept in sync unless an imported log had its own.
        val derived = mutableMapOf<String, String>()
        if (f.bandRxFollows) derived["BAND_RX"] = f.band
        if (f.freqRxFollows) derived["FREQ_RX"] = freq
        derived["QSO_DATE_OFF"] = Adif.adifDate(if (f.timeOffFollows) f.date else f.dateOff).orEmpty()
        derived["TIME_OFF"] = Adif.adifTime(if (f.timeOffFollows) f.time else f.timeOff).orEmpty()
        // The record's own station, so editing an imported contact keeps its QTH (and does not take today's settings).
        val me = myPositionFor(f)
        val them = f.position
        val qso = Qso(
            id = f.id, call = f.call, timeUtc = ts,
            band = f.band, mode = f.mode, freqMhz = freq,
            rstSent = f.rstSent, rstRcvd = f.rstRcvd,
            name = f.name.trim(), qth = f.qth.trim(), country = f.country.trim(), locator = f.locator.trim(),
            lat = them?.lat, lon = them?.lon,
            // Without both positions keep the distance that came with an imported log.
            distanceKm = if (me != null && them != null) Geo.distanceKm(me, them) else f.distanceKm,
            bearing = if (me != null && them != null) Geo.bearing(me, them) else f.bearing,
            power = f.power.trim(), qslSent = f.qslSent, qslRcvd = f.qslRcvd, comment = f.comment.trim(),
            myCall = f.myCall.trim().uppercase(), myLocator = f.myLocator.trim(),
            audio = f.audio,
            adif = (f.adif + derived).mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
            createdAt = f.createdAt, updatedAt = System.currentTimeMillis(),
        )
        if (f.removedAudio.isNotBlank() && f.removedAudio != f.audio) voice.delete(f.removedAudio)
        // QRZ.ru did not answer (no internet, error, still waiting): keep a mark so the log can retry later.
        val l = lookup
        val lookupMissed = hasQrzAccount &&
            (!f.infoFromQrz && (l is Lookup.Failed || l is Lookup.Loading) || (l is Lookup.Approx && l.qrzProblem != null) || (l is Lookup.FromHistory && l.problem != null))
        val finalQso = qso.copy(pendingLookup = lookupMissed || (!f.isNew && f.pendingLookup && !(f.infoFromQrz && l is Lookup.Found)))
        prefs.lastBand = f.band
        prefs.lastMode = f.mode
        prefs.lastFreq = f.freq
        prefs.lastPower = f.power
        lookupJob?.cancel()
        saveJob = scope.launch {
            lastSavedId = withContext(Dispatchers.IO) { db.save(finalQso) }
            reload()
            val note = if (finalQso.pendingLookup) tr(". Данные QRZ.ru не получены: обновите их кнопкой ⟳ в логе") else ""
            if (f.isNew) newSavedTick++
            if (!quiet) say((if (f.isNew) tr("Связь с %s записана", f.call) else tr("Изменения сохранены")) + note)
        }
        pane = editReturn
        returnToNewCard(f.isNew)
        return null
    }

    /**
     * "＋ Следующая": saves the card and opens a new one on the same band, mode and frequency with the cursor in the
     * callsign — for pile-ups and contests. Returns the error like [trySave].
     */
    fun saveAndNext(): String? {
        val f = form
        val err = trySave()
        if (err != null) return err
        newQso()
        val freq = normalizeFreq(f.freq)
        form = form.copy(band = f.band, mode = f.mode, freq = freq, rstSent = defaultRst(f.mode), rstRcvd = defaultRst(f.mode))
        formOriginal = form
        return null
    }

    // ---------- moving through lists with the keyboard ----------

    /** The log row the arrows are on (its id); the list draws it outlined and scrolls to it. */
    var logCursor by mutableStateOf<Long?>(null)
    /** The rows in the order the log shows them (groups and sorting applied), set by the list. */
    var logOrder: List<Long> = emptyList()
    /** The log's search field has the cursor: letters and arrows belong to it. */
    var searchFocused by mutableStateOf(false)

    /** ↑ ↓ PgUp PgDn: [delta] rows; [to] ±1 jumps to the first (Home) or last (End). */
    fun moveLogCursor(delta: Int, to: Int = 0) {
        val o = logOrder
        if (o.isEmpty()) return
        val i = o.indexOf(logCursor)
        logCursor = o[when {
            to < 0 -> 0
            to > 0 -> o.lastIndex
            i < 0 -> if (delta >= 0) 0 else o.lastIndex
            else -> (i + delta).coerceIn(0, o.lastIndex)
        }]
    }

    private fun cursorQso(): Qso? = logCursor?.let { id -> qsos.firstOrNull { it.id == id } }

    /** Enter on the log: opens the row (while picking, picks it). */
    fun openLogCursor() {
        val q = cursorQso() ?: return
        if (selecting) toggleSelected(q.id) else edit(q)
    }

    /** Space on the log: picks or unpicks the row. */
    fun toggleLogCursor() { cursorQso()?.let { toggleSelected(it.id) } }

    /** Delete on the log: the row goes (the message offers to undo), the cursor stays at that place. */
    fun deleteLogCursor() {
        val q = cursorQso() ?: return
        val o = logOrder
        val i = o.indexOf(q.id)
        logCursor = o.getOrNull(i + 1) ?: o.getOrNull(i - 1)
        delete(q)
    }

    /** Delete or Ctrl+D (⌘D) on the log: the records about to go (the picked ones, or the row under the cursor); a dialog asks. */
    var logDeleteAsk by mutableStateOf<List<Qso>?>(null)

    /** Asks before deleting the picked records or the cursor's row; false when there is nothing to delete. */
    fun askDeleteLog(): Boolean {
        val list = if (selecting) qsos.filter { it.id in selected } else listOfNotNull(cursorQso())
        if (list.isEmpty()) return false
        logDeleteAsk = list
        return true
    }

    fun confirmDeleteLog() {
        val list = logDeleteAsk ?: return
        logDeleteAsk = null
        if (!selecting && list.size == 1) return deleteLogCursor()
        val ids = list.map { it.id }.toSet()
        selected = selected - ids
        if (logCursor in ids) logCursor = null
        qsos = qsos.filter { it.id !in ids }
        allQsos = allQsos.filter { it.id !in ids }
        scope.launch {
            withContext(Dispatchers.IO) { ids.forEach { db.delete(it) } }
            reload()
            _messages.send(Message(tr("Удалено связей: %s", list.size)) { list.forEach(::restore) })
        }
    }

    /** Ctrl+D (⌘D) in a saved card: the card asks before deleting it. */
    var askDeleteCard by mutableStateOf(false)

    /**
     * Ctrl+D (⌘D), also from the menu bar: in the log with a picked record or the cursor on a row — delete it (a dialog
     * asks); in a saved card — delete the card; otherwise the dashboard.
     */
    fun dashboardOrDelete() {
        when {
            (pane == Pane.Edit || pane == Pane.Contest) && !form.isNew -> askDeleteCard = true
            pane == Pane.Edit || pane == Pane.Contest -> {}
            pane == Pane.Empty && !searchFocused && logDeleteAsk == null && askDeleteLog() -> {}
            else -> openDashboard()
        }
    }

    /** "Экспорт и импорт…" from the ⋮ menu is open. */
    var showTransfer by mutableStateOf(false)

    /** A new card whose time will be taken on saving: it shows a running clock. */
    val timeLive: Boolean
        get() = timeOnSave && form.isNew && formOriginal.let { it != null && it.date == form.date && it.time == form.time }

    /** Ctrl+K: contest mode on or off. */
    fun toggleContestMode() {
        changeContestMode(!contestMode)
        say(if (contestMode) tr("CONTEST MODE включён") else tr("CONTEST MODE выключен"))
    }

    /** Ctrl+R: the table sync, if it is set up. */
    fun syncByKey() {
        if (sheetUrl.isBlank()) say(tr("Синхронизация не настроена: укажите адрес таблицы в настройках")) else syncSheet()
    }

    /** The history row the arrows are on (its callsign). */
    var historyCursor by mutableStateOf<String?>(null)
    /** The history's filter field has the cursor: letters belong to it. */
    var historyTyping by mutableStateOf(false)
    /** The edit dialog of the history: these callsigns (one or several); null — closed. */
    var historyEditCalls by mutableStateOf<List<String>?>(null)
    /** "Удалить из истории?" for these callsigns; null — closed. */
    var historyDeleteAsk by mutableStateOf<List<String>?>(null)

    fun moveHistoryCursor(delta: Int) {
        val list = historyShown()
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.call == historyCursor }
        historyCursor = list[if (i < 0) (if (delta >= 0) 0 else list.lastIndex) else (i + delta).coerceIn(0, list.lastIndex)].call
    }

    /** What a history key acts on: the open entry, else the picked ones, else the row under the cursor. */
    fun historyTargets(): List<String> = historyOpen?.let { listOf(it.call) }
        ?: historySelected.toList().ifEmpty { listOfNotNull(historyCursor) }

    fun openHistoryCursor() {
        val e = historyEntries.firstOrNull { it.call == historyCursor } ?: return
        if (historySelected.isNotEmpty()) toggleHistorySelected(e.call) else openHistoryEntry(e)
    }

    fun newQsoFromHistoryTarget() {
        val call = historyOpen?.call ?: historyCursor ?: return
        historyEntries.firstOrNull { it.call == call }?.let(::newQsoFromHistory)
    }

    // ---------- search history ----------

    /** Stations looked up but not logged: the "История поиска" screen, and a card filled without the internet. */
    private val searchHistory = SearchHistory(java.io.File(AppDirs.data, "search_history.json"))
    var historyOn by mutableStateOf(prefs.searchHistory); private set
    var historyEntries by mutableStateOf(searchHistory.all()); private set
    /** Callsigns picked with a long press (a click with ⌘/Ctrl on the computer): delete or edit them together. */
    var historySelected by mutableStateOf<Set<String>>(emptySet()); private set
    /** The entry shown with its map; null — the list. */
    var historyOpen by mutableStateOf<SearchEntry?>(null); private set

    fun changeHistoryOn(on: Boolean) {
        historyAsk = false
        historyOn = on
        prefs.searchHistory = on
    }

    /** "Включить историю поиска?" is on screen: switching it on says first what it is used for. */
    var historyAsk by mutableStateOf(false)

    /** The switch in the menu or on the history screen: on asks first, off is at once (the entries stay). */
    fun requestHistoryOn(on: Boolean) {
        if (on && !historyOn) historyAsk = true else changeHistoryOn(on)
    }

    fun openHistory() {
        historySelected = emptySet()
        historyOpen = null
        historyEntries = searchHistory.all()
        historyMap = false
        pane = Pane.History
    }

    /** Back: from an entry to the list, from a selection to the plain list, from the list to the log. */
    fun closeHistory() {
        when {
            historyOpen != null -> historyOpen = null
            historyMap -> historyMap = false
            historySelected.isNotEmpty() -> historySelected = emptySet()
            else -> pane = Pane.Empty
        }
    }

    fun openHistoryEntry(e: SearchEntry) { historyMap = false; historyOpen = e }

    /** Filters and order of the list; starred entries are always on top. */
    var historyFilter by mutableStateOf(HistoryFilter()); private set
    var historySort by mutableStateOf(HistorySort.DATE); private set
    /** All shown entries on one map instead of the list. */
    var historyMap by mutableStateOf(false)

    fun changeHistoryFilter(f: HistoryFilter) { historyFilter = f }
    fun changeHistorySort(s: HistorySort) { historySort = s }

    /** The list as shown: filtered, sorted, starred first. */
    fun historyShown(): List<SearchEntry> = HistoryView.of(historyEntries, historyFilter, historySort, settings.myPosition)

    /** Stars or unstars [calls] (all get the same mark: starred unless every one already is). */
    fun toggleFavorite(calls: Collection<String>) {
        val star = !calls.all { c -> historyEntries.firstOrNull { it.call == c }?.favorite == true }
        searchHistory.update(calls) { it.copy(favorite = star) }
        historyEntries = searchHistory.all()
        historyOpen = historyOpen?.let { o -> historyEntries.firstOrNull { it.call == o.call } }
        historySelected = emptySet()
    }

    fun toggleHistorySelected(call: String) {
        historySelected = if (call in historySelected) historySelected - call else historySelected + call
    }

    fun selectAllHistory() { historySelected = historyEntries.map { it.call }.toSet() }

    fun deleteHistory(calls: Collection<String>) {
        searchHistory.delete(calls)
        historyEntries = searchHistory.all()
        historySelected = historySelected - calls.toSet()
        if (historyOpen?.call in calls) historyOpen = null
        say(tr("Удалено из истории поиска: %s", calls.size))
    }

    /**
     * Sets the given fields of all [calls] at once; a null field stays as each entry has it. A new locator moves the
     * station there (the coordinates of the old answer no longer apply).
     */
    fun editHistory(calls: Collection<String>, name: String?, qth: String?, country: String?, locator: String?, note: String?) {
        val n = searchHistory.update(calls) { e ->
            val loc = locator?.trim()?.let { if (it.length >= 4) it.substring(0, 4).uppercase() + it.substring(4).lowercase() else it.uppercase() }
            e.copy(
                name = name?.trim() ?: e.name, qth = qth?.trim() ?: e.qth, country = country?.trim() ?: e.country,
                locator = loc ?: e.locator,
                lat = if (loc != null && loc != e.locator) null else e.lat, lon = if (loc != null && loc != e.locator) null else e.lon,
                note = note?.trim() ?: e.note,
            )
        }
        historyEntries = searchHistory.all()
        historyOpen = historyOpen?.let { o -> historyEntries.firstOrNull { it.call == o.call } }
        historySelected = emptySet()
        say(tr("Изменено записей: %s", n))
    }

    /** A new card for a station from the history; online, the card is refreshed as usual. */
    fun newQsoFromHistory(e: SearchEntry) {
        addQso()
        if (pane != Pane.Edit) return // the contest card fills only the callsign
        form = form.copy(
            call = e.call, name = e.name, qth = e.qth, country = e.country, locator = e.locator,
            lat = e.lat, lon = e.lon, infoFromQrz = true,
            adif = form.adif + e.adif.filter { (k, v) -> v.isNotBlank() && form.adif[k].isNullOrBlank() },
        )
        formOriginal = form
        cardFromLog = true
        editReturn = Pane.History
        loadHistory(e.call)
        lookup = Lookup.FromHistory(e)
        if (hasQrzAccount) lookupJob = scope.launch { runLookup(e.call) }
    }

    /** The card was filled from the history: the station's data as it was found earlier. */
    private fun historyFallback(call: String, problem: String?): Lookup? {
        if (!historyOn) return null
        val e = searchHistory.get(call) ?: searchHistory.get(Callsign.home(call)) ?: return null
        val f = form
        if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && locatorFree(f)))) {
            form = f.copy(
                name = e.name, qth = e.qth, country = e.country, locator = e.locator, lat = e.lat, lon = e.lon, infoFromQrz = true,
                adif = f.adif + e.adif.filter { (k, v) -> v.isNotBlank() && f.adif[k].isNullOrBlank() },
            )
        }
        return Lookup.FromHistory(e, problem)
    }

    /**
     * What was found about the station goes to the search history — a cache, whether or not the contact is then logged;
     * it never counts as a contact. Called when a lookup answers and, with [requireNew], when a new card closes (then the
     * corrections typed by hand are kept too).
     */
    private fun rememberSearch(requireNew: Boolean = true) {
        if (!historyOn) return
        val f = form
        if ((requireNew && !f.isNew) || f.call.length < 3) return
        val source = when (val l = lookup) {
            is Lookup.Found -> SearchSource.of(l.info.source.ifBlank { SearchSource.QRZ_RU.key })
            is Lookup.Approx -> SearchSource.HAMQTH
            is Lookup.FromHistory -> l.entry.source
            else -> return
        }
        val e = SearchEntry(
            call = f.call, name = f.name, qth = f.qth, country = f.country, locator = f.locator, lat = f.lat, lon = f.lon,
            adif = f.adif.filterKeys { it in HISTORY_FIELDS }.filterValues { it.isNotBlank() }, source = source,
        )
        if (e.isEmpty) return
        searchHistory.put(e)
        historyEntries = searchHistory.all()
    }

    // ---------- keyboard-only work (an external keyboard on a tablet, or the computer) ----------

    /** The contact saved last from a card: ↑ or Ctrl+E opens it to fix a letter right after Enter logged it. */
    private var lastSavedId: Long? = null
    private var saveJob: Job? = null

    /** A card opened by ↑ / Ctrl+E from a new one: saving or closing it goes back to a new card. */
    private var backToNew = false

    private fun returnToNewCard(closedNew: Boolean) {
        if (!backToNew || closedNew) return
        backToNew = false
        addQso()
    }

    /** The card was opened by ↑ / Ctrl+E: the cursor goes to the callsign, so Enter and Esc work without a click. */
    var openedByKeys by mutableStateOf(false); private set

    /** Bumped by Ctrl+F: the log's search field takes the focus. */
    var searchFocus by mutableStateOf(0); private set

    fun requestSearchFocus() { searchFocus++ }

    /**
     * ↑ in an empty callsign or Ctrl+E: the last saved contact opens for editing (in the contest card — the previous
     * contest contact). Nothing happens over a card with unsaved typing.
     */
    fun editLast() {
        if (pane == Pane.Contest) { contestPrev(); return }
        if (hasUnsavedChanges) return
        val fromNew = pane == Pane.Edit && form.isNew
        val job = saveJob
        scope.launch {
            job?.join()
            val all = withContext(Dispatchers.IO) { db.all() }
            val q = all.firstOrNull { it.id == lastSavedId } ?: all.maxByOrNull { it.createdAt } ?: return@launch
            backToNew = false
            edit(q)
            backToNew = fromNew
            openedByKeys = true
        }
    }

    /**
     * Enter on a callsign field holding "20m", "CW", "14195"…: switches the band, mode or frequency and clears the field.
     * Returns false when the text is a callsign.
     */
    fun runCallCommand(): Boolean {
        val c = CallCommand.parse(form.call) ?: return false
        val untouched = form.isNew && formOriginal?.copy(call = form.call) == form
        setCall("")
        when (c) {
            is CallCommand.Band -> setBand(c.band)
            is CallCommand.Mode -> setMode(c.mode)
            is CallCommand.Freq -> {
                setFreq(c.freq)
                if (controlsRig()) {
                    freqMhz(c.freq)?.let { rigTune(Math.round(it * 1_000_000)) }
                    catTrackCard()
                }
            }
        }
        // A fresh card stays "untouched": closing it does not ask.
        if (untouched) formOriginal = form
        return true
    }

    /** Esc on a new card with something typed: a clean card on the same band, mode and frequency. */
    fun clearCard() {
        val f = form
        if (pane == Pane.Contest) {
            setCall("")
            setAdif(ContestMode.RCVD, "")
            return
        }
        rememberSearch()
        lookupJob?.cancel()
        discardCardRecording()
        if (f.audio.isNotBlank()) voice.delete(f.audio)
        newQso()
        form = form.copy(band = f.band, mode = f.mode, freq = f.freq, rstSent = defaultRst(f.mode), rstRcvd = defaultRst(f.mode))
        formOriginal = form
    }

    /** Callsigns from the log that start with what is typed, most recent first: one tap instead of the rest of the call. */
    fun callSuggestions(typed: String): List<String> {
        if (typed.length < 2) return emptyList()
        return allQsos.sortedByDescending { it.timeUtc }.asSequence().map { it.call }.distinct()
            .filter { it.startsWith(typed) && it != typed }.take(3).toList()
    }

    /**
     * Same station, band and mode: a repeat (a dupe in a contest). In a contest from the book — anywhere in it, or in
     * the same tour when its dupes reset with each tour; otherwise on the card's UTC day.
     */
    fun dupeOf(f: Form): Qso? {
        val ref = f.adif[Contest.REF]
        val c = contestBook.get(ref)
        // A new card gets its tour when it is logged, that is now.
        val tour = if (c?.dupesPerTour == true) f.adif[Contest.TOUR]?.toIntOrNull().takeIf { !f.isNew } ?: c.tourAt(System.currentTimeMillis()) else null
        return Dupes.of(f.call, f.band, f.mode, ref, tour, f.date.trim(), f.id, allQsos) { DATE_FMT.format(utc(it.timeUtc)) }
    }

    fun deleteCurrent() {
        val id = form.id
        if (id == 0L) return
        scope.launch {
            val qso = withContext(Dispatchers.IO) { db.get(id) } ?: return@launch
            // In the contest card the work goes on: the waiting draft or a new card instead of the log.
            if (pane == Pane.Contest) contestDraftOrNew() else pane = editReturn
            delete(qso)
        }
    }


    // ---------- table sync ----------

    /** The Apps Script address of the user's Google Sheet; sync runs only when the user asks for it. */
    var sheetUrl by mutableStateOf(prefs.sheetUrl); private set
    var sheetSyncing by mutableStateOf(false); private set
    /** The last sync's outcome, kept across restarts; [sheetOk] false for an error. */
    var sheetStatus by mutableStateOf(prefs.sheetStatus); private set
    var sheetOk by mutableStateOf<Boolean?>(null); private set

    fun changeSheetUrl(url: String) {
        val u = url.trim()
        if (u == sheetUrl) return
        sheetUrl = u
        prefs.sheetUrl = u
        // Another table: everything is sent and taken again (the script merges what it already has).
        prefs.sheetSince = ""
        prefs.sheetLastPush = 0
        sheetStatus = ""
        prefs.sheetStatus = ""
        sheetOk = null
    }

    fun syncSheet() {
        val url = sheetUrl
        if (sheetSyncing) return
        if (!SheetSync.isScriptUrl(url)) {
            sheetOk = false
            sheetStatus = tr("Вставьте адрес веб-приложения скрипта: https://script.google.com/macros/s/…/exec")
            return
        }
        sheetSyncing = true
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { SheetSync.sync(url, db, SyncState(prefs.sheetSince, prefs.sheetLastPush)) }
            }
            sheetSyncing = false
            val time = TIME_FMT.format(LocalDateTime.now(ZoneOffset.UTC)) + " UTC"
            r.onSuccess { res ->
                prefs.sheetSince = res.state.since
                prefs.sheetLastPush = res.state.lastPush
                sheetOk = true
                sheetStatus = tr("Синхронизировано в %s: отправлено %s, получено %s", time, res.sent, res.received)
                reload()
                say(tr("Журнал синхронизирован: отправлено %s, получено %s", res.sent, res.received))
            }.onFailure { e ->
                sheetOk = false
                sheetStatus = tr("Не удалось синхронизировать в %s: %s", time, e.message ?: e.javaClass.simpleName)
                say(sheetStatus)
            }
            prefs.sheetStatus = sheetStatus
        }
    }

    // ---------- contest mode ----------

    /**
     * Contest mode: "Добавить QSO" opens the simplified card (callsign, reports, numbers) and "назад / вперёд" go
     * through the contest contacts. Off at every start of the app, switched on in the settings.
     */
    var contestMode by mutableStateOf(false); private set

    /** The number the next contest contact sends; goes up by one with each saved contact. */
    var contestSerial by mutableStateOf(prefs.contestSerial); private set

    fun changeContestMode(on: Boolean) {
        contestMode = on
        // The book is read only when it is needed.
        if (on) contestBookChanged()
        contestWork = null
        contestLastSaved = null
    }

    fun changeContestSerial(n: Int) {
        if (n < 1) return
        contestSerial = n
        prefs.contestSerial = n
    }

    /** Send the same [contestSentText] with every contact instead of a serial number. */
    var contestSentFixed by mutableStateOf(prefs.contestSentFixed); private set
    var contestSentText by mutableStateOf(prefs.contestSentText); private set

    fun changeContestSentFixed(on: Boolean) {
        contestSentFixed = on
        prefs.contestSentFixed = on
    }

    fun changeContestSentText(text: String) {
        contestSentText = text
        prefs.contestSentText = text
    }

    /** What the next contest contact sends: the fixed code or the serial. */
    fun contestSentNext(): String = if (contestSentFixed) contestSentText else ContestMode.serial(contestSerial)

    var contestRstShown by mutableStateOf(prefs.contestRstShown); private set

    fun changeContestRstShown(on: Boolean) {
        contestRstShown = on
        prefs.contestRstShown = on
    }

    /** VHF contests: the card has "Мой локатор" and "Локатор корр.", the report puts them after the numbers. */
    var contestGrids by mutableStateOf(prefs.contestGrids); private set

    fun changeContestGrids(on: Boolean) {
        contestGrids = on
        prefs.contestGrids = on
        // The open new card takes it at once.
        if (pane == Pane.Contest && form.isNew) form = withGrids(form)
    }

    private fun withGrids(f: Form): Form = if (!contestGrids) f.copy(adif = f.adif - ContestMode.GRIDS) else f.copy(
        myLocator = prefs.contestMyLocator.ifBlank { settings.myLocator.trim() },
        adif = f.adif + (ContestMode.GRIDS to "Y"),
    )

    /** The locator the contest card sends; kept for the next cards (a portable QTH stays for the whole contest). */
    fun setContestMyLocator(v: String) {
        val loc = v.trim().uppercase().filter { it.isLetterOrDigit() }.take(8)
        form = form.copy(myLocator = loc)
        prefs.contestMyLocator = if (loc == settings.myLocator.trim().uppercase()) "" else loc
    }

    /** The locator the other station gave (or the one from its earlier contact): QRZ.ru and HamQTH do not replace it. */
    fun setContestLocator(v: String) {
        val loc = v.trim().uppercase().filter { it.isLetterOrDigit() }.take(8)
        setLocator(loc)
        pinnedLocator = if (loc.isEmpty()) null else form.call to loc
    }

    /** The other station's locator in a VHF contest card, typed or from its earlier contact: (callsign, locator). */
    private var pinnedLocator: Pair<String, String>? = null

    /** The card's locator is the pinned one: lookups may fill the name and QTH but leave the locator alone. */
    private fun locatorFree(f: Form): Boolean = f.locator.isBlank() || pinnedLocator == (f.call to f.locator)

    private fun keepPinnedLocator(call: String) {
        val p = pinnedLocator ?: return
        val f = form
        if (f.call == call && p.first == call && f.locator != p.second) form = f.copy(locator = p.second, lat = null, lon = null, adif = f.adif - HamQth.POSITION_FIELD)
    }

    /**
     * Band, mode and frequency the contest is being worked on: taken from the last new card, so the next one stays
     * there. (The log is saved in the background, so its newest contact may not be the one just saved yet.)
     */
    private var contestWork: Form? = null

    /** The callsign of the contact the contest card has just logged: shown in place of a message. */
    var contestLastSaved by mutableStateOf<String?>(null); private set

    /** The latest contest contacts, newest first: the list under the contest card. */
    fun contestRecent(n: Int): List<Qso> = contestQsos().takeLast(n).reversed()

    /** Contest contacts so far and in the hour before [now]: the rate in the card's header. */
    fun contestCounts(now: Long): Pair<Int, Int> {
        val list = contestQsos()
        return list.size to list.count { it.timeUtc in (now - 3_600_000L)..now }
    }

    /** A new contest card that was typed in and left by going back; it comes back at the end. */
    private var contestDraft: Form? = null

    /** Contest contacts in the order they were made: "назад / вперёд" go through them. */
    private fun contestQsos(): List<Qso> {
        // With a contest chosen in the book, only its contacts: the count, the rate and "назад / вперёд" are about it.
        val ref = activeContest?.id
        return allQsos.filter { ContestMode.isContest(it.adif) && (ref == null || it.adif[Contest.REF] == ref) }
            .sortedWith(compareBy({ it.timeUtc }, { it.createdAt }, { it.id }))
    }

    // ---------- contest book ----------

    private val contestBook = ContestBook(File(AppDirs.data, "contests.json"))
    var contests by mutableStateOf<List<Contest>>(emptyList()); private set
    /** The contest new contest-mode contacts are logged in; null — none chosen. */
    var activeContest by mutableStateOf<Contest?>(null); private set

    private fun contestBookChanged() {
        contests = contestBook.all()
        activeContest = contestBook.active()
    }

    fun openContests() {
        clearSelection()
        pane = Pane.Contests
    }

    fun closeContests() {
        pane = Pane.Empty
    }

    /** Adds or changes [c]; a new one becomes the active contest at once. */
    fun saveContest(c: Contest) {
        val isNew = contestBook.get(c.id) == null
        contestBook.put(c)
        if (isNew) contestBook.activate(c.id)
        contestBookChanged()
    }

    fun deleteContest(id: String) {
        contestBook.delete(id)
        contestBookChanged()
    }

    fun activateContest(id: String?) {
        contestBook.activate(id)
        contestBookChanged()
        contestWork = null
    }

    /** Contacts logged in [c]: the number in the book's list. */
    fun contestCount(c: Contest): Int = allQsos.count { it.adif[Contest.REF] == c.id }

    /** Position of the open contest card: "№ in the contest / of how many"; null for a new one. */
    fun contestPosition(): Pair<Int, Int>? {
        val list = contestQsos()
        val i = list.indexOfFirst { it.id == form.id }
        return if (form.isNew || i < 0) null else (i + 1) to list.size
    }

    /** Whether there is an older contest contact to go back to. */
    fun contestHasPrev(): Boolean {
        val list = contestQsos()
        return if (form.isNew) list.isNotEmpty() else list.indexOfFirst { it.id == form.id } > 0
    }

    private fun newContestQso(audio: String = "") {
        newQso(audio)
        val w = contestWork
        val f = if (w == null) form else form.copy(band = w.band, mode = w.mode, freq = normalizeFreq(w.freq))
        form = f.copy(
            rstSent = defaultRst(f.mode), rstRcvd = defaultRst(f.mode),
            adif = f.adif + (ContestMode.FIELD to "Y") + (ContestMode.SENT to contestSentNext()) +
                listOfNotNull(activeContest?.let { Contest.REF to it.id }),
        )
        form = withGrids(form)
        formOriginal = form
        pane = Pane.Contest
        catFollowNewCard()
    }

    /**
     * "Записать" (Enter in the received number): saves the contact with the time of saving, moves the number on and
     * opens the next card on the same band and mode. On a saved contact — the next one. Returns the error like [trySave].
     */
    fun contestSaveAndNext(): String? {
        val f = form
        if (!f.isNew) return contestNext()
        contestError(f)?.let { return it }
        val ms = System.currentTimeMillis()
        val now = utc(ms)
        // The contest it was logged in, with its code and the tour of this moment: the report finds it by them.
        val stamp = contestBook.get(f.adif[Contest.REF])?.let { Contest.stamp(it, ms) }.orEmpty()
        form = f.copy(date = DATE_FMT.format(now), time = TIME_FMT.format(now), adif = f.adif - Contest.TOUR + stamp)
        val err = save(quiet = true)
        if (err != null) {
            pane = Pane.Contest
            return err
        }
        contestLastSaved = f.call
        contestWork = f
        if (!contestSentFixed) changeContestSerial(ContestMode.nextSerial(f.adif[ContestMode.SENT].orEmpty(), contestSerial))
        contestDraft = null
        newContestQso()
        return null
    }

    /** "назад": the previous contest contact. Changes to a saved one are saved; a typed new card waits as a draft. */
    fun contestPrev(): String? {
        val list = contestQsos()
        val f = form
        val target = if (f.isNew) list.lastOrNull() else list.getOrNull(list.indexOfFirst { it.id == f.id } - 1)
        return if (target == null) null else contestOpen(target)
    }

    /** Opens [target] in the contest card, as "назад" does: the open card is saved or kept as a draft first. */
    fun contestOpen(target: Qso): String? {
        val f = form
        if (!f.isNew && f.id == target.id) return null
        contestLastSaved = null
        if (f.isNew) {
            contestWork = f
            contestDraft = f.takeIf { it.call.isNotBlank() || !it.adif[ContestMode.RCVD].isNullOrBlank() }
        } else if (hasUnsavedChanges) {
            contestError(f)?.let { return it }
            save()?.let { pane = Pane.Contest; return it }
        }
        edit(target)
        pane = Pane.Contest
        return null
    }

    /** "вперёд" from a saved contact: the next one, and after the last — the draft or a new card. */
    private fun contestNext(): String? {
        val list = contestQsos()
        val f = form
        if (hasUnsavedChanges) {
            contestError(f)?.let { return it }
            save()?.let { pane = Pane.Contest; return it }
        }
        val next = list.getOrNull(list.indexOfFirst { it.id == f.id } + 1)
        if (next != null) edit(next) else contestDraftOrNew()
        pane = Pane.Contest
        return null
    }

    /** After the last contest contact: the card typed in and left by going back, or a new one. */
    private fun contestDraftOrNew() {
        val draft = contestDraft
        newContestQso()
        if (draft == null) return
        contestDraft = null
        form = draft.copy(adif = draft.adif + (ContestMode.SENT to (draft.adif[ContestMode.SENT] ?: contestSentNext())))
        loadHistory(draft.call)
        // The lookup may have been cut short by going back.
        if (draft.call.length >= MIN_LOOKUP_LENGTH && !draft.infoFromQrz && draft.name.isBlank()) retryLookup()
    }

    /**
     * A contest contact needs the number the other station gave: without it the report line ends after the RST and
     * the judges' software does not count the contact. (The callsign is checked by [save].)
     */
    private fun contestError(f: Form): String? =
        if (f.call.length >= 3 && f.adif[ContestMode.RCVD].isNullOrBlank()) CONTEST_RCVD_ERROR else null

    /** Closing the contest card: a new card with something typed, or changes to a saved one, ask first. */
    val contestTyped: Boolean
        get() = if (form.isNew) form.call.isNotBlank() || !form.adif[ContestMode.RCVD].isNullOrBlank() else hasUnsavedChanges

    // ---------- settings ----------

    fun updateSettings(s: StationSettings) {
        val credentialsChanged = s.qrzLogin != settings.qrzLogin || s.qrzPassword != settings.qrzPassword
        val siteChanged = s.qrzSiteEmail != settings.qrzSiteEmail || s.qrzSitePassword != settings.qrzSitePassword
        val comChanged = s.qrzComLogin != settings.qrzComLogin || s.qrzComPassword != settings.qrzComPassword
        settings = s
        // Written a moment after typing stops, so the keychain is not touched on every keystroke.
        saveSettingsJob?.cancel()
        saveSettingsJob = scope.launch {
            delay(500)
            withContext(Dispatchers.IO) { prefs.save(s) }
        }
        if (credentialsChanged) {
            qrz.reset()
            qrzOk = null
            qrzStatus = null
        }
        if (siteChanged) {
            qrzSite.reset()
            qrzSiteOk = null
            qrzSiteStatus = null
        }
        if (comChanged) {
            qrzCom.reset()
            qrzComOk = null
            qrzComStatus = null
        }
    }

    /** Result of "Проверить вход" for the QRZ.com account. */
    var qrzComStatus by mutableStateOf<String?>(null); private set
    var qrzComOk by mutableStateOf<Boolean?>(null); private set

    fun testQrzCom() {
        scope.launch {
            qrzComOk = null
            qrzComStatus = tr("Проверяю…")
            qrzCom.reset()
            try {
                qrzCom.login()
                qrzComOk = true
                qrzComStatus = tr("Вход выполнен")
            } catch (e: QrzException) {
                qrzComOk = false
                qrzComStatus = e.message
            } catch (e: Exception) {
                qrzComOk = false
                qrzComStatus = networkError(e).replace("api.qrz.ru", "www.qrz.com").replace("QRZ.ru", "QRZ.com")
            }
        }
    }

    // ---------- CAT ----------

    private companion object {
        const val RIG_HOLD_MS = 700L
    }

    var catConfig by mutableStateOf(prefs.cat); private set
    var catStatus by mutableStateOf<CatStatus>(CatStatus.Off); private set
    private var catSession: CatSession? = null
    private var catRestartJob: Job? = null

    /**
     * What the new card had or the radio last wrote into it. A field that no longer matches was changed by hand:
     * the radio leaves it alone in this card. Null — the card does not follow the radio (a saved contact).
     */
    private data class CatWrote(val freq: String, val band: String, val mode: String, val rst: String)
    private var catWrote: CatWrote? = null

    fun changeCat(c: CatConfig) {
        val old = catConfig
        catConfig = c
        prefs.cat = c
        // Only what the card takes changed: the link stays. Typing an address: reconnect once the typing pauses.
        val relink = c.copy(
            followFreq = old.followFreq, followMode = old.followMode, sMeterToRst = old.sMeterToRst, control = old.control,
            serve = old.serve, servePort = old.servePort, serveLan = old.serveLan,
        ) != old
        if (c.serve != old.serve || c.servePort != old.servePort || c.serveLan != old.serveLan) restartServer()
        if (relink) {
            catRestartJob?.cancel()
            catRestartJob = scope.launch {
                if (c.enabled == old.enabled) delay(700)
                restartCat()
            }
        }
    }

    fun restartCat() {
        catRestartJob?.cancel()
        catSession?.stop()
        catSession = null
        catStatus = CatStatus.Off
        val c = catConfig
        // The server goes with CAT: on when both are, off when CAT is switched off.
        if ((rigServer != null) != (c.enabled && c.serve)) restartServer()
        if (!c.enabled) return
        lateinit var session: CatSession
        session = CatSession(c, { CatDesktop.open(c) }, { st -> scope.launch { if (catSession === session) onCat(st) } })
        catSession = session
        session.start()
    }

    // ---------- the radio shared with JTDX, WSJT-X… (a Hamlib rigctld server) ----------

    var rigServerStatus by mutableStateOf<ru.r3xed.qsolog.cat.RigServerStatus>(ru.r3xed.qsolog.cat.RigServerStatus.Off); private set
    private var rigServer: ru.r3xed.qsolog.cat.RigServer? = null

    fun restartServer() {
        rigServer?.stop()
        rigServer = null
        rigServerStatus = ru.r3xed.qsolog.cat.RigServerStatus.Off
        val c = catConfig
        if (!c.enabled || !c.serve) return
        lateinit var srv: ru.r3xed.qsolog.cat.RigServer
        srv = ru.r3xed.qsolog.cat.RigServer(
            c.servePort, c.serveLan,
            state = { catRig },
            send = { cmd -> catSession?.send(cmd) },
            onStatus = { st -> scope.launch { if (rigServer === srv) rigServerStatus = st } },
        )
        rigServer = srv
        srv.start()
    }

    fun stopServer() {
        rigServer?.stop()
        rigServer = null
    }

    fun stopCat() {
        catSession?.stop()
        catSession = null
    }

    fun catPorts(): List<CatDevice> = CatDesktop.ports()

    /** The radio's latest state over CAT, while it answers. */
    val catRig: RigState? get() = (catStatus as? CatStatus.Connected)?.takeIf { !it.silent }?.state

    /** The dial WSJT-X or JTDX reported (its Status message), and who sent it. */
    var digiRig by mutableStateOf<RigState?>(null); private set
    var digiRigFrom by mutableStateOf(""); private set

    /**
     * The radio's state: from CAT when it answers, else from WSJT-X or JTDX — they hold the radio's port themselves and
     * tell its dial over UDP. Their state counts while the program is heard (a heartbeat every 15 s).
     */
    val rig: RigState? get() = catRig ?: digiRig?.takeIf { d ->
        val heard = (digiStatus as? DigiStatus.Listening)?.heardAt ?: 0
        System.currentTimeMillis() - maxOf(d.at, heard) < 45_000
    }

    /** Where [rig] comes from: "TRX" for CAT, the program's name for WSJT-X / JTDX. */
    val rigSource: String get() = if (catRig != null) "TRX" else digiRigFrom.substringBefore(' ').ifBlank { "UDP" }

    private fun onDigiDial(m: ru.r3xed.qsolog.digi.Wsjtx.Message.Status) {
        digiRig = RigState(freqHz = m.dialHz, radioMode = m.mode.ifBlank { null })
        digiRigFrom = m.id
        if (catRig == null) rig?.let(::applyRig)
    }

    private fun onCat(st: CatStatus) {
        catStatus = st
        catRig?.let { r ->
            rememberBand(r)
            applyRig(r)
        }
    }

    // ---------- the transceiver panel: the app tunes the radio ----------

    /** The transceiver panel (a click on the TRX line, or its key) is open. */
    var showRigPanel by mutableStateOf(false)

    /** The radio takes commands: CAT is on and the radio answers. */
    val rigControllable: Boolean get() = catRig != null

    /** The new card's band and mode chips (and "14025" typed as the callsign) tune the radio too. */
    private fun controlsRig(): Boolean =
        catConfig.control && rigControllable && (pane == Pane.Edit || pane == Pane.Contest) && form.isNew

    var tuneStep by mutableStateOf(prefs.tuneStep); private set

    fun changeTuneStep(hz: Int) {
        tuneStep = hz
        prefs.tuneStep = hz
    }

    /** Where the radio was on each band: a band picked again goes back there. */
    private val bandMemory: MutableMap<String, Long> = prefs.bandMemory.split(',').mapNotNull { p ->
        val (b, f) = p.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        f.toLongOrNull()?.let { b to it }
    }.toMap().toMutableMap()
    private var saveBandJob: Job? = null

    private fun rememberBand(r: RigState) {
        val hz = r.freqHz ?: return
        val band = bandForFreq(hz / 1e6) ?: return
        if (bandMemory[band] == hz) return
        bandMemory[band] = hz
        // Saved once the dial rests for a moment, not on every step of it.
        saveBandJob?.cancel()
        saveBandJob = scope.launch {
            delay(2000)
            prefs.bandMemory = bandMemory.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
    }

    /**
     * Until then the card does not take the radio's state: an answer to a poll sent before the command would bring the
     * old frequency back for a moment.
     */
    private var rigHoldUntil = 0L

    fun rigTune(hz: Long) {
        if (hz <= 0) return
        catSession?.send(CatCommand.Freq(hz))
        rigHoldUntil = System.currentTimeMillis() + RIG_HOLD_MS
    }

    private fun rigSend(m: RadioMode) {
        catSession?.send(CatCommand.Mode(m))
        rigHoldUntil = System.currentTimeMillis() + RIG_HOLD_MS
    }

    /** The radio goes to [band]: where it was last on it, or the calling frequency of the mode it is in. Returns the frequency. */
    fun rigBand(band: String): Long? {
        val logMode = catRig?.radioMode?.let { CatModes.toLog(it, form.mode) } ?: form.mode
        val hz = bandMemory[band] ?: BandPlan.freq(band, logMode) ?: return null
        rigTune(hz)
        // LSB below 10 MHz, USB above: SSB is set again for the new band.
        if (logMode == "SSB") CatModes.toRadio("SSB", hz)?.let(::rigSend)
        return hz
    }

    /**
     * The radio goes to the log's [mode]. A digital mode away from its frequencies (FT8 picked on 14.200) also moves the
     * radio to the mode's calling frequency on that band; that frequency is returned.
     */
    fun rigMode(mode: String): Long? {
        val hz = catRig?.freqHz
        val band = hz?.let { bandForFreq(it / 1e6) }
        val target = band?.let { BandPlan.freq(it, mode) }
        val move = mode in setOf("FT8", "FT4") && target != null && hz != null && kotlin.math.abs(hz - target) > 3_000
        if (move) rigTune(target!!)
        CatModes.toRadio(mode, if (move) target else hz)?.let(::rigSend)
        return if (move) target else null
    }

    /**
     * The card has just sent its band, mode or frequency to the radio: it keeps following the radio (the field changed
     * by the user is not "changed by hand" for it), without taking the radio's old state back in the meantime.
     */
    private fun catTrackCard() {
        val f = form
        if (catWrote != null) catWrote = CatWrote(f.freq, f.band, f.mode, f.rstSent)
    }

    // ---------- telegraph: F1–F8 on the DTR or RTS line of a COM port ----------

    var cwConfig by mutableStateOf(prefs.cw); private set
    var cwState by mutableStateOf<CwState>(CwState.Idle); private set
    private var keyer: ru.r3xed.qsolog.cw.CwSender? = null

    fun changeCw(c: CwConfig) {
        val old = cwConfig
        cwConfig = c
        prefs.cw = c
        keyer?.wpm = c.wpm
        if (c.copy(wpm = old.wpm, messages = old.messages) != old) restartCw()
    }

    fun restartCw() {
        keyer?.close()
        keyer = null
        cwState = CwState.Idle
        val c = cwConfig
        if (!c.enabled) return
        lateinit var k: ru.r3xed.qsolog.cw.CwSender
        val onState = { st: CwState -> scope.launch { if (keyer === k) cwState = st }; Unit }
        // cwdaemon on another computer keys its radio; otherwise a line of the adapter here.
        k = if (c.via == ru.r3xed.qsolog.cw.CwVia.CWDAEMON) ru.r3xed.qsolog.cw.CwDaemon(c.daemon, onState, c.wpm)
        else CwKeyer({ CatDesktop.openKeyLine(c) }, onState, c.wpm, c.ptt)
        keyer = k
    }

    fun stopCw() {
        keyer?.stop()
    }

    fun closeCw() {
        keyer?.close()
        keyer = null
    }

    /** The card is in CW and the key line is on: F1–F8 send the messages. */
    val cwActive: Boolean
        get() = cwConfig.enabled && form.mode == "CW" && (pane == Pane.Edit || pane == Pane.Contest)

    fun cwText(i: Int): String {
        val f = form
        return CwMacros.expand(
            cwConfig.messages.getOrNull(i).orEmpty(), settings.myCall.trim(), f.call, f.rstSent,
            f.adif[ContestMode.SENT].orEmpty(), f.name.substringBefore(' '), settings.myLocator.trim(),
        )
    }

    fun sendCw(i: Int) {
        val text = cwText(i)
        if (text.isBlank()) return
        keyer?.send(text) ?: say(tr("Телеграф выключен: включите его в настройках"))
    }

    /** "Проверить" in the settings: VVV at the set speed. */
    fun testCw() {
        keyer?.send("VVV") ?: say(tr("Сначала включите передачу телеграфом"))
    }

    val cwSending: Boolean get() = cwState is CwState.Sending

    fun changeCwSpeed(delta: Int) {
        changeCw(cwConfig.copy(wpm = (cwConfig.wpm + delta).coerceIn(CwConfig.MIN_WPM, CwConfig.MAX_WPM)))
    }

    /** The telegraph keys of the card: true when [c] was one of them and has been done. */
    fun cwKey(c: Chord?): Boolean {
        if (!cwActive || c == null) return false
        when {
            KeyMap.hit(KeyAction.CW_STOP, c) -> if (cwSending) stopCw() else return false
            KeyMap.hit(KeyAction.CW_FASTER, c) -> changeCwSpeed(2)
            KeyMap.hit(KeyAction.CW_SLOWER, c) -> changeCwSpeed(-2)
            else -> {
                val a = KeyAction.entries.firstOrNull { it.cwMessage >= 0 && KeyMap.hit(it, c) } ?: return false
                sendCw(a.cwMessage)
            }
        }
        return true
    }

    // ---------- online logbooks: LoTW (through TQSL), QRZ.com, Club Log ----------

    var online by mutableStateOf(ru.r3xed.qsolog.data.OnlineConfig()); private set
    private var saveOnlineJob: Job? = null

    fun changeOnline(c: ru.r3xed.qsolog.data.OnlineConfig) {
        online = c
        // The keychain is slow: saved once the typing pauses.
        saveOnlineJob?.cancel()
        saveOnlineJob = scope.launch {
            delay(700)
            withContext(Dispatchers.IO) { prefs.saveOnline(c) }
        }
    }

    /** The upload dialog is open: for [UploadTarget.only] records picked in the log, or the whole log when null. */
    var uploadTarget by mutableStateOf<UploadTarget?>(null); private set

    /** The logbook being uploaded to right now (a spinner in the dialog). */
    var uploading by mutableStateOf<ru.r3xed.qsolog.data.OnlineLog?>(null); private set

    fun openUpload(selectedOnly: Boolean) {
        uploadTarget = UploadTarget(if (selectedOnly) selected else null)
    }

    fun closeUpload() {
        if (uploading == null) uploadTarget = null
    }

    fun uploadCandidates(log: ru.r3xed.qsolog.data.OnlineLog, only: Set<Long>?, onlyNew: Boolean): List<Qso> =
        allQsos.filter { (only == null || it.id in only) && (!onlyNew || !log.isSent(it)) }

    fun uploadProblem(log: ru.r3xed.qsolog.data.OnlineLog): String? =
        if (log == ru.r3xed.qsolog.data.OnlineLog.LOTW) lotwProblem else online.problem(log, settings.myCall)

    /** Sends the dialog's contacts to [log]; with [mark] the sent ones get the logbook's mark (date and time) in the card. */
    fun upload(log: ru.r3xed.qsolog.data.OnlineLog, onlyNew: Boolean, mark: Boolean) {
        val target = uploadTarget ?: return
        if (uploading != null) return
        uploadProblem(log)?.let { say(it); return }
        val c = online
        val call = settings.myCall.trim()
        val list = uploadCandidates(log, target.only, onlyNew)
        if (list.isEmpty()) { say(tr("Новых связей для %s нет", log.title)); return }
        uploading = log
        scope.launch {
            val r = when (log) {
                ru.r3xed.qsolog.data.OnlineLog.QRZCOM -> ru.r3xed.qsolog.data.OnlineLogs.uploadQrzCom(c.qrzcomKey, list, USER_AGENT)
                ru.r3xed.qsolog.data.OnlineLog.CLUBLOG -> ru.r3xed.qsolog.data.OnlineLogs.uploadClubLog(c.clublogEmail, c.clublogPassword, call, c.clublogAppKey, list, USER_AGENT)
                ru.r3xed.qsolog.data.OnlineLog.EQSL -> ru.r3xed.qsolog.data.OnlineLogs.uploadEqsl(c.eqslUser, c.eqslPassword, c.eqslNickname, list, USER_AGENT)
                ru.r3xed.qsolog.data.OnlineLog.LOTW -> uploadLotw(list)
            }
            if (mark) withContext(Dispatchers.IO) { r.sent.forEach { db.save(log.mark(it)) } }
            reload()
            uploading = null
            uploadTarget = null
            if (target.only != null && r.error == null) clearSelection()
            say(buildString {
                append(tr("%s: отправлено %s", log.title, r.sent.size))
                if (r.duplicates > 0) append(tr(", уже были там: %s", r.duplicates))
                if (mark && r.sent.isNotEmpty()) append(tr(", отметка поставлена"))
                r.error?.let { append(". ").append(it) }
            })
        }
    }

    // ---------- eQSL inbox: the cards confirm contacts of the log, the others can be added ----------

    /** The inbox dialog; null when closed. */
    var eqslInbox by mutableStateOf<ru.r3xed.qsolog.data.EqslInbox?>(null); private set

    /** Downloads the inbox and marks the confirmed contacts at once; the cards with no contact wait in the dialog. */
    fun openEqslInbox() {
        if (eqslInbox == ru.r3xed.qsolog.data.EqslInbox.Loading) return
        val c = online
        c.problem(ru.r3xed.qsolog.data.OnlineLog.EQSL, settings.myCall)?.let {
            eqslInbox = ru.r3xed.qsolog.data.EqslInbox.Failed(it, settings = true)
            return
        }
        eqslInbox = ru.r3xed.qsolog.data.EqslInbox.Loading
        scope.launch {
            eqslInbox = try {
                val records = ru.r3xed.qsolog.data.Eqsl.downloadInbox(c.eqslUser, c.eqslPassword, c.eqslNickname, USER_AGENT)
                val m = ru.r3xed.qsolog.data.Eqsl.match(records, allQsos)
                withContext(Dispatchers.IO) { m.confirmed.forEach { db.save(it) } }
                if (m.confirmed.isNotEmpty()) reload()
                ru.r3xed.qsolog.data.EqslInbox.Done(m)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ru.r3xed.qsolog.data.EqslInbox.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun closeEqslInbox() {
        if (eqslInbox != ru.r3xed.qsolog.data.EqslInbox.Loading) eqslInbox = null
    }

    /** The cards picked in the dialog become contacts of the log (repeats are skipped, as in an import). */
    fun addEqslCards(cards: List<Qso>) {
        eqslInbox = null
        if (cards.isEmpty()) return
        scope.launch {
            val added = withContext(Dispatchers.IO) { db.insertAll(cards.map { it.withDistance(settings.myPosition) }) }
            reload()
            say(tr("Добавлено связей из eQSL: %s", added))
        }
    }

    /** Takes [log]'s mark off the open card (saved with the card), so the contact goes again next time. */
    fun clearUploadMark(log: ru.r3xed.qsolog.data.OnlineLog) {
        form = form.copy(adif = log.unmarkFields(form.adif))
    }

    /** LoTW: the contacts are signed and sent by ARRL's TQSL, run in batch mode. */
    private val lotwProblem: String?
        get() = when {
            tqslExecutable() == null -> tr("Не найдена программа TQSL. Установите её с lotw.arrl.org или укажите путь к ней")
            online.lotwLocation.isBlank() -> tr("Укажите Station Location — название места станции из TQSL")
            else -> null
        }

    /** tqsl from the settings, else the usual install places. */
    fun tqslExecutable(): String? = listOfNotNull(
        online.tqslPath.trim().ifBlank { null },
        "/Applications/TrustedQSL/tqsl.app/Contents/MacOS/tqsl",
        "/Applications/tqsl.app/Contents/MacOS/tqsl",
        "C:\\Program Files (x86)\\TrustedQSL\\tqsl.exe",
        "C:\\Program Files\\TrustedQSL\\tqsl.exe",
        "/usr/bin/tqsl", "/usr/local/bin/tqsl", "/opt/homebrew/bin/tqsl",
    ).firstOrNull { java.io.File(it).canExecute() }

    /**
     * `tqsl -d -u -a compliant -x -l LOCATION file`: sign, upload, exit. Exit codes (TQSL manual): 0 — done,
     * 8 — all already in LoTW (or out of the certificate's dates), 9 — some of them; anything else is an error.
     */
    private suspend fun uploadLotw(list: List<Qso>): ru.r3xed.qsolog.data.UploadResult = withContext(Dispatchers.IO) {
        val exe = tqslExecutable() ?: return@withContext ru.r3xed.qsolog.data.UploadResult(emptyList(), 0, lotwProblem)
        val f = java.io.File.createTempFile("qsolog-lotw", ".adi")
        try {
            f.writeText(ru.r3xed.qsolog.data.OnlineLogs.file(list), Charsets.UTF_8)
            val p = ProcessBuilder(exe, "-d", "-u", "-a", "compliant", "-x", "-l", online.lotwLocation.trim(), f.absolutePath)
                .redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = kotlin.concurrent.thread(isDaemon = true) { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) {
                p.destroyForcibly()
                return@withContext ru.r3xed.qsolog.data.UploadResult(emptyList(), 0, tr("TQSL не закончил работу за 10 минут"))
            }
            reader.join(1000)
            when (val code = p.exitValue()) {
                0 -> ru.r3xed.qsolog.data.UploadResult(list, 0, null)
                8 -> ru.r3xed.qsolog.data.UploadResult(list, list.size, null)
                9 -> ru.r3xed.qsolog.data.UploadResult(list, 0, tr("часть связей LoTW уже получал раньше"))
                else -> ru.r3xed.qsolog.data.UploadResult(emptyList(), 0, tr("TQSL завершился с кодом %s: %s", code, out.lines().lastOrNull { it.isNotBlank() }.orEmpty().take(160)))
            }
        } catch (e: Exception) {
            ru.r3xed.qsolog.data.UploadResult(emptyList(), 0, tr("Не удалось запустить TQSL: %s", e.message ?: e.javaClass.simpleName))
        } finally {
            f.delete()
        }
    }

    // ---------- additional settings: keys, programs started with QSO-LOG ----------

    var keymap by mutableStateOf(KeyMap.custom); private set

    fun changeKeymap(m: Map<KeyAction, List<Chord>>) {
        KeyMap.custom = m
        keymap = m
        prefs.keymap = KeyMap.encode(m)
    }

    var startApps by mutableStateOf(StartApps.decode(prefs.startApps)); private set

    fun changeStartApps(list: List<StartApp>) {
        startApps = list
        prefs.startApps = StartApps.encode(list)
    }

    /** The programs this run of QSO-LOG started, to close the ones marked "закрывать вместе с QSO-LOG". */
    private val started = mutableListOf<Pair<StartApp, Process>>()

    /** Starts the enabled programs that are not running yet; says which could not start. */
    fun launchStartApps() {
        val failed = mutableListOf<String>()
        startApps.filter { it.enabled }.forEach { a ->
            if (started.any { it.first.target == a.target && it.second.isAlive } || StartLauncher.running(a)) return@forEach
            try {
                started += a to StartLauncher.start(a)
            } catch (e: Exception) {
                failed += a.title + ": " + (e.message ?: e.javaClass.simpleName)
            }
        }
        if (failed.isNotEmpty()) say(tr("Не удалось запустить: %s", failed.joinToString("; ")))
    }

    /** On exit: the programs marked so are closed. */
    fun stopStartApps() {
        started.filter { it.first.closeOnExit }.forEach { (_, p) -> runCatching { p.destroy() } }
        started.clear()
    }

    private fun catFollowNewCard() {
        val f = form
        catWrote = CatWrote(f.freq, f.band, f.mode, f.rstSent)
        rig?.let(::applyRig)
    }

    /** The open new card takes the radio's frequency, band, mode and S-meter — the fields not changed by hand. */
    private fun applyRig(s: RigState) {
        if (System.currentTimeMillis() < rigHoldUntil) return
        val w = catWrote ?: return
        if ((pane != Pane.Edit && pane != Pane.Contest) || !form.isNew) return
        val c = catConfig
        var f = form
        if (c.followFreq && f.freq == w.freq && f.band == w.band) s.freqMhz?.let { mhz ->
            f = f.copy(freq = mhz, band = bandForFreq(mhz.toDouble()) ?: f.band)
        }
        if (c.followMode && f.mode == w.mode) s.radioMode?.let { CatModes.toLog(it, f.mode) }?.let { m ->
            if (m != f.mode) f = withMode(f, m)
        }
        if (c.sMeterToRst && f.rstSent == w.rst) s.sDb?.let { db -> f = f.copy(rstSent = CatFormat.rstWithS(f.rstSent, db)) }
        catWrote = CatWrote(f.freq, f.band, f.mode, f.rstSent)
        if (f == form) return
        val before = form
        form = f
        // The radio's values are not the user's changes: closing the card does not ask about them.
        formOriginal = formOriginal?.let { o ->
            if (o.freq == before.freq && o.band == before.band && o.mode == before.mode && o.rstSent == before.rstSent && o.rstRcvd == before.rstRcvd)
                o.copy(freq = f.freq, band = f.band, mode = f.mode, rstSent = f.rstSent, rstRcvd = f.rstRcvd) else o
        }
    }

    // ---------- WSJT-X, JTDX, MSHV ----------

    var digiConfig by mutableStateOf(prefs.digi); private set
    var digiStatus by mutableStateOf<DigiStatus>(DigiStatus.Off); private set
    private var digiListener: DigiListener? = null
    private var digiRestartJob: Job? = null
    private val digiLock = Mutex()

    fun changeDigi(c: DigiConfig) {
        val old = digiConfig
        if (c == old) return
        digiConfig = c
        prefs.digi = c
        // Typing a port or a group: listen again once the typing pauses.
        digiRestartJob?.cancel()
        digiRestartJob = scope.launch {
            if (c.enabled == old.enabled) delay(700)
            restartDigi()
        }
    }

    fun restartDigi() {
        digiListener?.stop()
        digiListener = null
        digiStatus = DigiStatus.Off
        val c = digiConfig
        if (!c.enabled) return
        lateinit var l: DigiListener
        l = DigiListener(
            c,
            { st -> scope.launch { if (digiListener === l) digiStatus = st } },
            { qsos, from -> scope.launch { if (digiListener === l) logDigi(qsos, from) } },
            onDial = { m -> scope.launch { if (digiListener === l) onDigiDial(m) } },
        )
        digiListener = l
        l.start()
    }

    fun stopDigi() {
        digiListener?.stop()
        digiListener = null
    }

    /**
     * A contact logged in WSJT-X: straight into the log, with "Моя станция" from the settings where the program did
     * not say, then name and QTH from QRZ.ru. The same contact twice (WSJT-X sends it as fields and as ADIF) is one.
     */
    private suspend fun logDigi(qsos: List<Qso>, from: String) {
        val s = settings
        val added = digiLock.withLock {
            withContext(Dispatchers.IO) {
                qsos.mapNotNull { q0 ->
                    val q = q0.copy(
                        myCall = q0.myCall.ifBlank { s.myCall.trim().uppercase() },
                        myLocator = q0.myLocator.ifBlank { s.myLocator.trim() },
                        power = q0.power.ifBlank { s.power.trim() },
                        adif = s.station + q0.adif,
                        pendingLookup = hasQrzAccount,
                    ).withDistance(s.myPosition)
                    if (db.exists(q)) null else q.copy(id = db.save(q))
                }
            }
        }
        if (added.isEmpty()) return
        reload()
        // The log shows the new contact on top, as after saving a card (a new day's group would start above the view).
        newSavedTick++
        say(if (added.size == 1) tr("Связь с %s из %s записана", added[0].call, from) else tr("Из %s записано связей: %s", from, added.size))
        if (hasQrzAccount) added.forEach { q ->
            refreshing = refreshing + q.id
            try { fillFromQrz(q) } finally { refreshing = refreshing - q.id }
            reload()
        }
    }

    fun flushSettings() {
        saveSettingsJob?.cancel()
        prefs.save(settings)
    }

    /** Result of "Проверить вход" for the site account. */
    var qrzSiteStatus by mutableStateOf<String?>(null); private set
    var qrzSiteOk by mutableStateOf<Boolean?>(null); private set

    fun testQrzSite() {
        scope.launch {
            qrzSiteOk = null
            qrzSiteStatus = tr("Проверяю…")
            qrzSite.reset()
            try {
                qrzSite.login()
                qrzSiteOk = true
                qrzSiteStatus = tr("Вход выполнен")
            } catch (e: QrzException) {
                qrzSiteOk = false
                qrzSiteStatus = e.message
            } catch (e: Exception) {
                qrzSiteOk = false
                qrzSiteStatus = networkError(e).replace("api.qrz.ru", "www.qrz.ru")
            }
        }
    }

    fun testQrz() {
        scope.launch {
            qrzOk = null
            qrzStatus = tr("Проверяю…")
            qrz.reset()
            try {
                qrz.login()
                qrzOk = true
                qrzStatus = tr("Подключено")
            } catch (e: QrzException) {
                qrzOk = false
                qrzStatus = e.message
            } catch (e: Exception) {
                qrzOk = false
                qrzStatus = networkError(e)
            }
        }
    }

    /** Fills my locator from QRZ.ru, or from the QTH address via OpenStreetMap search. */
    fun findMyLocator() {
        scope.launch {
            val s = settings
            var pos: LatLon? = null
            var qth = s.myQth
            if (s.myCall.isNotBlank() && hasQrzAccount) {
                try {
                    qrzLookup(s.myCall)?.let { info ->
                        pos = info.position
                        if (qth.isBlank()) qth = info.city
                    }
                } catch (_: Exception) {
                }
            }
            if (pos == null && qth.isNotBlank()) pos = geocode(qth)
            val found = pos
            if (found == null) {
                say(tr("Не удалось найти координаты. Введите локатор вручную"))
            } else {
                val loc = Geo.latLonToLocator(found)
                updateSettings(settings.copy(myLocator = loc, myQth = qth))
                say(tr("Локатор определён: %s", loc))
            }
        }
    }

    /** OpenStreetMap Nominatim: https://nominatim.org/release-docs/latest/api/Search/ */
    private suspend fun geocode(address: String): LatLon? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=ru&q=" + URLEncoder.encode(address, "UTF-8"))
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val lat = Regex("\"lat\"\\s*:\\s*\"([-0-9.]+)\"").find(body)?.groupValues?.get(1)?.toDouble()
            val lon = Regex("\"lon\"\\s*:\\s*\"([-0-9.]+)\"").find(body)?.groupValues?.get(1)?.toDouble()
            if (lat != null && lon != null) LatLon(lat, lon) else null
        } catch (_: Exception) {
            null
        }
    }

    // ---------- CSV ----------

    fun importAdif(file: File) {
        scope.launch {
            val msg = try {
                val (added, dup, bad) = withContext(Dispatchers.IO) {
                    val r = file.inputStream().use { Adif.import(it) }
                    // A voice note name only means something if the file is on this computer.
                    val rows = r.rows.map { q ->
                        val withAudio = if (q.audio.isNotBlank() && !voice.file(q.audio).exists()) q.copy(audio = "") else q
                        withAudio.withDistance(settings.myPosition)
                    }
                    val added = db.insertAll(rows)
                    Triple(added, rows.size - added, r.skipped)
                }
                buildString {
                    append(tr("Импортировано из ADIF: %s", added))
                    if (dup > 0) append(tr(", повторов пропущено: %s", dup))
                    if (bad > 0) append(tr(", записей с ошибками: %s", bad))
                }
            } catch (e: Exception) {
                tr("Не удалось прочитать файл: %s", e.message)
            }
            reload()
            say(msg)
        }
    }

    fun importCsv(file: File) {
        scope.launch {
            val msg = try {
                val (added, dup, bad) = withContext(Dispatchers.IO) {
                    val r = file.inputStream().use { Csv.import(it) }
                    val added = db.insertAll(r.rows.map { it.withDistance(settings.myPosition) })
                    Triple(added, r.rows.size - added, r.skipped)
                }
                buildString {
                    append(tr("Импортировано: %s", added))
                    if (dup > 0) append(tr(", повторов пропущено: %s", dup))
                    if (bad > 0) append(tr(", строк с ошибками: %s", bad))
                }
            } catch (e: Exception) {
                tr("Не удалось прочитать файл: %s", e.message)
            }
            reload()
            say(msg)
        }
    }
}
