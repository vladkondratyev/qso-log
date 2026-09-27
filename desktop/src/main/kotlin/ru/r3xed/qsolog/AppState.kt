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
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.data.Adif
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Cabrillo
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.data.CallHistory
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.QrzInfo
import ru.r3xed.qsolog.data.Csv
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.HamQth
import ru.r3xed.qsolog.data.approxPosition
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzClient
import ru.r3xed.qsolog.data.QrzSite
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

enum class Pane { Empty, Edit, Settings, Map, Welcome, Reference }

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

    /** Some QRZ.ru account is set: the XML API (preferred) or the site's own e-mail login. */
    val hasQrzAccount get() = settings.qrzLogin.isNotBlank() || settings.qrzSiteEmail.isNotBlank()

    /**
     * QRZ.ru data for a callsign: through the XML API when its account is set (it wins when both are set),
     * otherwise — or when the API fails — from the site's callsign page with the e-mail login. Null: not in QRZ.ru.
     */
    private suspend fun qrzLookup(call: String, stillWanted: () -> Boolean = { true }): QrzInfo? {
        var problem: Exception? = null
        if (settings.qrzLogin.isNotBlank()) {
            try {
                return qrz.lookup(call, stillWanted)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = e
            }
        }
        if (settings.qrzSiteEmail.isNotBlank()) {
            try {
                return qrzSite.lookup(call)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (problem == null) problem = e
            }
        }
        throw problem ?: QrzException(403, tr("Укажите учётную запись QRZ.ru в настройках"))
    }

    /** Region and RDA that only the site page gives, into ADIF STATE / CNTY unless the card has them. */
    private fun withQrzExtras(adif: Map<String, String>, info: QrzInfo): Map<String, String> =
        adif + listOf("STATE" to info.region, "CNTY" to info.rda).filter { (k, v) -> v.isNotBlank() && adif[k].isNullOrBlank() }

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

    fun openSettings(from: Pane = Pane.Empty) {
        // A fresh visit starts at the top; only the way back from the reference keeps the place.
        if (pane != Pane.Reference) scope.launch { settingsScroll.scrollTo(0) }
        settingsReturn = from
        pane = Pane.Settings
    }

    /** Kept here so the settings stay scrolled to the reference card while the reference is open. */
    val settingsScroll = ScrollState(0)

    /** Where the reference's close button goes: back to the settings it was opened from, or the empty pane (menu). */
    private var referenceReturn: Pane = Pane.Empty

    fun openReference() {
        referenceReturn = pane.takeIf { it == Pane.Settings } ?: Pane.Empty
        pane = Pane.Reference
    }

    fun closeReference() {
        if (referenceReturn == Pane.Settings) openSettings(settingsReturn) else pane = Pane.Empty
    }

    fun closeSettings() {
        pane = settingsReturn
        settingsReturn = Pane.Empty
        // Coming back to a card after entering the QRZ.ru account: look the callsign up now.
        if (pane == Pane.Edit && form.call.length >= MIN_LOOKUP_LENGTH && (lookup as? Lookup.Failed)?.noAccount == true) retryLookup()
    }

    /** The card as it was opened, to tell whether closing it would lose something. */
    private var formOriginal: Form? = null

    val hasUnsavedChanges: Boolean
        get() = pane == Pane.Edit && formOriginal.let { it != null && form != it }

    /** "Закрыть без сохранения?" is on screen (from ✕ or Esc). */
    var confirmClose by mutableStateOf(false)

    /** Closing a card with typed data asks first; an untouched one closes at once. */
    fun requestClose() {
        if (hasUnsavedChanges) confirmClose = true else closeEditor()
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

    init {
        reload()
        // First start: a short three-step setup; later, without a callsign, the settings as before.
        if (settings.myCall.isBlank()) pane = if (prefs.welcomeDone) Pane.Settings else Pane.Welcome
        scope.launch(Dispatchers.IO) { voice.cleanup(keep = db.audioFiles()) }
        scope.launch {
            val pw = withContext(Dispatchers.IO) { prefs.password() }
            if (settings.qrzPassword.isEmpty()) settings = settings.copy(qrzPassword = pw)
            // Read once even without an e-mail: the settings only save a password that has been loaded.
            val sitePw = withContext(Dispatchers.IO) { prefs.sitePassword() }
            if (settings.qrzSitePassword.isEmpty()) settings = settings.copy(qrzSitePassword = sitePw)
            withContext(Dispatchers.IO) { prefs.forgetOnlineLogAccounts() }
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
            if (pane == Pane.Edit && form.id == qso.id) pane = editReturn
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
            withContext(Dispatchers.IO) { db.save(qso.copy(id = 0)) }
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
        contest = prefs.contestCode,
        callsign = settings.myCall,
        categoryOperator = prefs.contestOperator,
        location = settings.station["MY_CNTY"].orEmpty().replace("-", "").uppercase(),
        operators = settings.station["OPERATOR"].orEmpty(),
        createdBy = "QSO-LOG $APP_VERSION",
    )

    /** Remembers the dialog's choice and returns the file name to offer. */
    fun prepareContest(h: Cabrillo.Header, onlyNew: Boolean, mark: Boolean): String {
        prefs.contestFormat = h.format.name
        prefs.contestCode = h.contest
        prefs.contestOperator = h.categoryOperator
        pendingContest = h to PendingExport(ExportFormat.CONTEST, contestTarget?.only, onlyNew, mark)
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
    class PendingExport(val format: ExportFormat, val only: Set<Long>?, val onlyNew: Boolean, val mark: Boolean)

    private var pendingExport: PendingExport? = null

    fun openExport(format: ExportFormat, selectedOnly: Boolean) {
        if (format == ExportFormat.CONTEST) openContestExport(selectedOnly)
        else exportTarget = ExportTarget(format, if (selectedOnly) selected else null)
    }

    fun closeExport() {
        exportTarget = null
    }

    /** The contacts an export takes: the picked ones or the whole log, without those already exported to [format] when [onlyNew]. */
    fun exportCandidates(format: ExportFormat, only: Set<Long>?, onlyNew: Boolean): List<Qso> =
        allQsos.filter { (only == null || it.id in only) && (!onlyNew || !format.isExported(it.adif)) }

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
            else Adif.export(list, out, program = "QSO-LOG", version = APP_VERSION)
        }
    }

    /** Writes the export, then (if asked) marks the exported contacts with the date and time. */
    private fun writeExport(p: PendingExport, file: File, title: String, write: (List<Qso>, java.io.OutputStream) -> Unit) {
        if (p.only != null) clearSelection()
        scope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all().filter { (p.only == null || it.id in p.only) && (!p.onlyNew || !p.format.isExported(it.adif)) }
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

    /** Closes the card without saving; a voice note recorded for an unsaved contact is deleted. */
    fun closeEditor() {
        lookupJob?.cancel() // a lookup for another card must not land in this one
        discardCardRecording()
        val f = form
        if (f.isNew) {
            if (f.audio.isNotBlank()) voice.delete(f.audio)
            if (f.removedAudio.isNotBlank()) voice.delete(f.removedAudio)
        }
        pane = editReturn
    }

    /**
     * "Новый QSO". When the log search has narrowed down to one station (or the query is exactly its callsign),
     * the card opens for that station: its data from the last contact, then refreshed from QRZ.ru.
     */
    fun addQso(audio: String = "") {
        val last = searchedStation()
        if (last == null) newQso(audio) else newQsoFromLog(last, audio)
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
    }

    fun edit(qso: Qso, from: Pane = Pane.Empty) {
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
        pane = Pane.Edit
    }

    fun setBand(band: String) {
        form = form.copy(band = band)
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
        val f = form
        val oldDefault = defaultRst(f.mode)
        form = f.copy(
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
        form = if (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank())) {
            f.copy(call = call, name = "", qth = "", country = "", locator = "", lat = null, lon = null, infoFromQrz = false, adif = f.adif - HamQth.POSITION_FIELD)
        } else f.copy(call = call)
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
        lookup = Lookup.Loading
        if (!hasQrzAccount) {
            lookup = hamqthFallback(call, qrzProblem = null)
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
                } else if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank()))) {
                    form = f.copy(
                        name = info.fullName, qth = info.city, country = info.country,
                        locator = info.locator.ifBlank { info.position?.let { Geo.latLonToLocator(it) }.orEmpty() },
                        lat = info.lat, lon = info.lon, infoFromQrz = true,
                        adif = withQrzExtras(f.adif - HamQth.POSITION_FIELD, info),
                    )
                }
                Lookup.Found(info)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: QrzException) {
            val msg = e.message ?: tr("Ошибка QRZ.ru")
            hamqthFallback(call, qrzProblem = msg) ?: Lookup.Failed(msg)
        } catch (e: Exception) {
            val msg = networkError(e)
            hamqthFallback(call, qrzProblem = msg) ?: Lookup.Failed(msg)
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
        if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank()))) {
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
            } finally {
                refreshing = refreshing - qso.id
            }
            reload()
            say(msg)
        }
    }

    /** Returns an error message, or null when saved. */
    private fun save(): String? {
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
            (!f.infoFromQrz && (l is Lookup.Failed || l is Lookup.Loading) || (l is Lookup.Approx && l.qrzProblem != null))
        val finalQso = qso.copy(pendingLookup = lookupMissed || (!f.isNew && f.pendingLookup && !(f.infoFromQrz && l is Lookup.Found)))
        prefs.lastBand = f.band
        prefs.lastMode = f.mode
        prefs.lastFreq = f.freq
        prefs.lastPower = f.power
        lookupJob?.cancel()
        scope.launch {
            withContext(Dispatchers.IO) { db.save(finalQso) }
            reload()
            val note = if (finalQso.pendingLookup) tr(". Данные QRZ.ru не получены: обновите их кнопкой ⟳ в логе") else ""
            if (f.isNew) newSavedTick++
            say((if (f.isNew) tr("Связь с %s записана", f.call) else tr("Изменения сохранены")) + note)
        }
        pane = editReturn
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

    /** Callsigns from the log that start with what is typed, most recent first: one tap instead of the rest of the call. */
    fun callSuggestions(typed: String): List<String> {
        if (typed.length < 2) return emptyList()
        return allQsos.sortedByDescending { it.timeUtc }.asSequence().map { it.call }.distinct()
            .filter { it.startsWith(typed) && it != typed }.take(3).toList()
    }

    /** Same station, band and mode on the card's UTC day: a repeat (a dupe in a contest). */
    fun dupeOf(f: Form): Qso? {
        if (f.call.length < 3) return null
        return allQsos.filter {
            it.id != f.id && it.call == f.call && it.band.equals(f.band, ignoreCase = true) &&
                it.mode.equals(f.mode, ignoreCase = true) && DATE_FMT.format(utc(it.timeUtc)) == f.date.trim()
        }.maxByOrNull { it.timeUtc }
    }

    fun deleteCurrent() {
        val id = form.id
        if (id == 0L) return
        scope.launch {
            val qso = withContext(Dispatchers.IO) { db.get(id) } ?: return@launch
            pane = editReturn
            delete(qso)
        }
    }


    // ---------- settings ----------

    fun updateSettings(s: StationSettings) {
        val credentialsChanged = s.qrzLogin != settings.qrzLogin || s.qrzPassword != settings.qrzPassword
        val siteChanged = s.qrzSiteEmail != settings.qrzSiteEmail || s.qrzSitePassword != settings.qrzSitePassword
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
