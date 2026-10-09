package ru.r3xed.qsolog

import android.app.Application
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.data.Adif
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.CallHistory
import ru.r3xed.qsolog.data.CallCommand
import ru.r3xed.qsolog.data.SearchEntry
import ru.r3xed.qsolog.data.SearchHistory
import ru.r3xed.qsolog.data.SearchSource
import ru.r3xed.qsolog.data.HistoryFilter
import ru.r3xed.qsolog.data.HistorySort
import ru.r3xed.qsolog.data.HistoryView
import ru.r3xed.qsolog.data.HISTORY_FIELDS
import ru.r3xed.qsolog.data.Cabrillo
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.data.Csv
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.HamQth
import ru.r3xed.qsolog.data.approxPosition
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzClient
import ru.r3xed.qsolog.data.QrzSite
import ru.r3xed.qsolog.data.QrzCom
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.KeypadMode
import ru.r3xed.qsolog.data.SyncState
import ru.r3xed.qsolog.data.SheetSync
import ru.r3xed.qsolog.data.QrzException
import ru.r3xed.qsolog.data.QrzInfo
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.QsoDb
import ru.r3xed.qsolog.data.Release
import ru.r3xed.qsolog.data.UpdateChecker
import ru.r3xed.qsolog.data.Settings
import ru.r3xed.qsolog.data.StationSettings
import ru.r3xed.qsolog.data.VoiceNotes
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.data.freqMhz
import ru.r3xed.qsolog.data.normalizeFreq
import ru.r3xed.qsolog.data.defaultRst
import ru.r3xed.qsolog.data.withDistance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ThemeMode(private val ru: String) {
    SYSTEM("Как в системе"), // no-tr
    LIGHT("Светлая"), // no-tr
    DARK("Тёмная"), // no-tr
    ;

    val label: String get() = tr(ru)
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

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
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

sealed interface Screen {
    data object Log : Screen
    data object Edit : Screen
    data object Settings : Screen
    data object Map : Screen
    data object Welcome : Screen
    data object Reference : Screen
    /** The simplified contact card of the contest mode. */
    data object Contest : Screen
    /** Charts of the log: bands, modes, days, stations… */
    data object Dashboard : Screen
    /** Stations looked up but not logged. */
    data object History : Screen
}

/** User-Agent for the online logbooks: they ask programs to name themselves. */

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = QsoDb(app)
    private val prefs = Settings(app)

    /** Interface language, set before anything builds a text. Changing it re-creates the screen (key in the root). */
    var language by mutableStateOf(I18n.fromCode(prefs.language).also { I18n.chosen = it }); private set

    fun changeLanguage(l: Lang) {
        I18n.chosen = l
        prefs.language = l.code
        language = l
    }
    val voice = VoiceNotes(app)

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
        if (settings.qrzLogin.isNotBlank()) ask { qrz.lookup(call, stillWanted) }?.let { return qrzSite.withRda(it).copy(source = SearchSource.QRZ_RU.key) }
        // The QRZ.ru site is the same database as its XML API: asked only when the API is not set or failed.
        if (settings.qrzSiteEmail.isNotBlank() && !notFound) ask { qrzSite.lookup(call) }?.let { return it.copy(source = SearchSource.QRZ_RU_SITE.key) }
        if (settings.qrzComLogin.isNotBlank()) ask { qrzCom.lookup(call) }?.let { return it.copy(source = SearchSource.QRZ_COM.key) }
        if (notFound) return null
        throw problem ?: QrzException(403, tr("Укажите учётную запись QRZ.ru в настройках"))
    }

    /** Region and RDA (QRZ.ru site), region and zones (QRZ.com) into ADIF STATE / CNTY / CQZ / ITUZ unless the card has them. */
    private fun withQrzExtras(adif: Map<String, String>, info: QrzInfo): Map<String, String> =
        adif + listOf("STATE" to info.region, "CNTY" to info.rda, "CQZ" to info.cqZone, "ITUZ" to info.ituZone)
            .filter { (k, v) -> v.isNotBlank() && adif[k].isNullOrBlank() }

    var screen by mutableStateOf<Screen>(Screen.Log)
    var query by mutableStateOf(""); private set
    var qsos by mutableStateOf<List<Qso>>(emptyList()); private set
    /** The whole log regardless of the search, for the map. */
    var allQsos by mutableStateOf<List<Qso>>(emptyList()); private set
    var total by mutableStateOf(0); private set

    /** Records picked with a long press in the log; non-empty means the log is in selection mode. */
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

    fun setBand(band: String) {
        form = form.copy(band = band)
    }

    // The log always opens by date, newest on top; another sort lasts only until the app is closed.
    var sortBy by mutableStateOf(SortBy.DATE); private set
    var sortDesc by mutableStateOf(true); private set

    /** A tap on the current sort flips its direction; a tap on another one selects it with its natural direction. */
    fun sort(by: SortBy) {
        if (by == sortBy) sortDesc = !sortDesc else { sortBy = by; sortDesc = by.defaultDesc }
    }

    /** Last centre and zoom of the stations map (lat, lon, zoom), kept while the app runs. */
    var mapPosition: Triple<Double, Double, Double>? = null

    /** Where closing the contact card returns to: the log or the map. */
    private var editReturn: Screen = Screen.Log

    /** Recording started by a long press on "Добавить QSO"; the value is the start time. */
    var recordingSince by mutableStateOf<Long?>(null); private set

    var form by mutableStateOf(Form()); private set
    var history by mutableStateOf(CallHistory(0, null)); private set
    var lookup by mutableStateOf<Lookup>(Lookup.Idle); private set
    var qrzStatus by mutableStateOf<String?>(null); private set
    var qrzOk by mutableStateOf<Boolean?>(null); private set

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    class Message(val text: String, val undo: (() -> Unit)? = null)

    private var lookupJob: Job? = null

    /** The "hold to record" line under the add button: only for the first few starts, then the mic icon is enough. */
    val showRecordHint: Boolean

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
        createdBy = "QSO-LOG " + BuildConfig.VERSION_NAME,
    )

    /** Contacts entered in contest mode among [only] (or the whole log): the report dialog then offers to take only them. */
    fun hasContestQsos(only: Set<Long>?): Boolean = allQsos.any { (only == null || it.id in only) && ContestMode.isContest(it.adif) }

    /** Remembers the dialog's choice and returns the file name to offer. */
    fun prepareContest(h: Cabrillo.Header, onlyNew: Boolean, mark: Boolean, contestOnly: Boolean): String {
        prefs.contestFormat = h.format.name
        prefs.contestCode = h.contest
        prefs.contestOperator = h.categoryOperator
        pendingContest = h to PendingExport(ExportFormat.CONTEST, contestTarget?.only, onlyNew, mark, contestOnly)
        contestTarget = null
        val code = h.contest.uppercase().ifBlank { "LOG" }.replace('/', '-')
        return "${h.callsign.ifBlank { "log" }.replace('/', '-')}_$code.${h.format.extension}"
    }

    fun exportContest(uri: Uri) {
        val (h, p) = pendingContest ?: return
        pendingContest = null
        writeExport(p, uri, h.format.title) { list, out -> out.write(Cabrillo.export(list, h).toByteArray(Cabrillo.charset(h.format))) }
    }

    // ---------- export marks: ADIF, CSV, ЕРМАК / Cabrillo ----------

    /** The ADIF / CSV export dialog is open: for [ExportTarget.only] records picked in the log, or the whole log when null. */
    var exportTarget by mutableStateOf<ExportTarget?>(null); private set

    /** Choices of an export dialog, waiting for the file picker. */
    class PendingExport(val format: ExportFormat, val only: Set<Long>?, val onlyNew: Boolean, val mark: Boolean, val contestOnly: Boolean = false)

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
    fun exportCandidates(format: ExportFormat, only: Set<Long>?, onlyNew: Boolean, contestOnly: Boolean = false): List<Qso> =
        allQsos.filter { takes(it, only, onlyNew, format, contestOnly) }

    private fun takes(q: Qso, only: Set<Long>?, onlyNew: Boolean, format: ExportFormat, contestOnly: Boolean) =
        (only == null || q.id in only) && (!onlyNew || !format.isExported(q.adif)) && (!contestOnly || ContestMode.isContest(q.adif))

    /** Remembers the dialog's choice and returns the file name to offer. */
    fun prepareExport(onlyNew: Boolean, mark: Boolean): String {
        val t = exportTarget ?: return "log"
        pendingExport = PendingExport(t.format, t.only, onlyNew, mark)
        exportTarget = null
        val call = settings.myCall.ifBlank { "log" }.replace('/', '-')
        val n = exportCandidates(t.format, t.only, onlyNew).size
        return when (t.format) {
            ExportFormat.CSV -> "qso_${call}_${LocalDate.now()}.csv"
            else -> if (t.only != null || onlyNew) "${call}_${n}qso_${LocalDate.now()}.adi" else "${call}_${LocalDate.now()}.adi"
        }
    }

    /** Saves the ADIF or CSV file chosen in [prepareExport]. */
    fun exportFile(uri: Uri) {
        val p = pendingExport ?: return
        pendingExport = null
        writeExport(p, uri, p.format.title) { list, out ->
            if (p.format == ExportFormat.CSV) Csv.export(list, out)
            else Adif.export(list, out, if (adifUtf8) Charsets.UTF_8 else Adif.WINDOWS_1251, program = "QSO-LOG", version = BuildConfig.VERSION_NAME)
        }
    }

    /** Writes the export, then (if asked) marks the exported contacts with the date and time. */
    private fun writeExport(p: PendingExport, uri: Uri, title: String, write: (List<Qso>, java.io.OutputStream) -> Unit) {
        if (p.only != null) clearSelection()
        viewModelScope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all().filter { takes(it, p.only, p.onlyNew, p.format, p.contestOnly) }
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { write(list, it) }
                    if (p.mark) list.forEach { db.save(p.format.mark(it)) }
                    list.size
                }
                reload()
                tr("Экспортировано в %s: %s", title, count) + if (p.mark && count > 0) tr(", отметка поставлена") else ""
            } catch (e: Exception) {
                tr("Не удалось сохранить файл: %s", e.message)
            }
            _messages.send(Message(msg))
        }
    }

    fun importContest(uri: Uri) {
        viewModelScope.launch {
            val msg = try {
                val (added, dup, r) = withContext(Dispatchers.IO) {
                    val r = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { Cabrillo.import(it) }
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
            _messages.send(Message(msg))
        }
    }

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
        viewModelScope.launch {
            update = try {
                val r = withContext(Dispatchers.IO) { UpdateChecker.latest() }
                if (UpdateChecker.isNewer(r.version, BuildConfig.VERSION_NAME)) UpdateState.Available(r) else UpdateState.UpToDate
            } catch (e: kotlinx.coroutines.CancellationException) {
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

    /** Where "Назад" in the settings returns: the log, or the card that sent the user there. */
    private var settingsReturn: Screen = Screen.Log

    /** The reference screen (bands, Morse, spelling alphabets, calculators): opened from the log's ⋮ menu. */
    fun openReference() {
        screen = Screen.Reference
    }

    fun closeReference() {
        screen = Screen.Log
    }

    val settingsScroll = ScrollState(0)

    /** Leaves the first-start setup (done or "Настроить позже"); it is not shown again. */
    fun finishWelcome() {
        prefs.welcomeDone = true
        screen = Screen.Log
    }

    fun openSettings(from: Screen = Screen.Log) {
        // A fresh visit starts at the top.
        viewModelScope.launch { settingsScroll.scrollTo(0) }
        settingsReturn = from
        screen = Screen.Settings
    }

    fun closeSettings() {
        screen = settingsReturn
        settingsReturn = Screen.Log
        // Coming back to a card after entering the QRZ.ru account: look the callsign up now.
        if (screen == Screen.Edit && form.call.length >= MIN_LOOKUP_LENGTH && (lookup as? Lookup.Failed)?.noAccount == true) retryLookup()
    }

    /** The card as it was opened, to tell whether closing it would lose something. */
    private var formOriginal: Form? = null

    /** Bumped each time a card is opened, so the card's own UI state (fields, focus) starts fresh. */
    var editSession by mutableStateOf(0); private set

    val hasUnsavedChanges: Boolean
        get() = formOriginal.let { it != null && form != it }

    init {
        prefs.launchCount = prefs.launchCount + 1
        showRecordHint = prefs.launchCount <= 5
        reload()
        // First start: a short three-step setup; later, without a callsign, the settings as before.
        if (settings.myCall.isBlank()) screen = if (prefs.welcomeDone) Screen.Settings else Screen.Welcome
        viewModelScope.launch(Dispatchers.IO) { voice.cleanup(keep = db.audioFiles()) }
        // Keys and passwords of the online logbooks the app no longer uploads to.
        viewModelScope.launch(Dispatchers.IO) { prefs.forgetOnlineLogAccounts() }
    }

    override fun onCleared() {
        cardRecordingSince = null
        voice.stopAndDiscard()
    }

    // ---------- log ----------

    fun reload() {
        viewModelScope.launch {
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
        searchLookupJob = viewModelScope.launch {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
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
        // Hide the row at once, so the list never shows a record the snackbar calls deleted.
        qsos = qsos.filter { it.id != qso.id }
        allQsos = allQsos.filter { it.id != qso.id }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.delete(qso.id) }
            reload()
            val t = utc(qso.timeUtc)
            // Date and time say which contact went: the log may hold several with the same callsign.
            val left = allQsos.count { it.call == qso.call }
            val tail = if (left > 0) tr(". Других связей с %s: %s", qso.call, left) else ""
            _messages.send(Message(tr("Удалена связь с %s %s %s%s", qso.call, DATE_FMT.format(t), TIME_FMT.format(t), tail)) { restore(qso) })
        }
    }

    /** Deletes the whole log and all voice notes. The settings screen asks for confirmation first. */
    fun deleteAll() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                val n = db.deleteAll()
                voice.dir.listFiles()?.forEach { it.delete() }
                n
            }
            reload()
            _messages.send(Message(tr("История QSO удалена: %s записей", n)))
        }
    }

    private fun restore(qso: Qso) {
        viewModelScope.launch {
            // A fresh change time: a deletion already sent to the table must not win over the undo.
            withContext(Dispatchers.IO) { db.save(qso.copy(id = 0, updatedAt = System.currentTimeMillis())) }
            reload()
        }
    }

    fun say(text: String) {
        viewModelScope.launch { _messages.send(Message(text)) }
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
            viewModelScope.launch { _messages.send(Message(tr("Не удалось включить микрофон: %s", e.message ?: e.javaClass.simpleName))) }
            false
        }
    }

    /** Ends the recording and opens a new contact card with it attached. */
    fun finishRecording() {
        if (recordingSince == null) return
        recordingSince = null
        val name = voice.stop()
        addQso(audio = name.orEmpty())
        if (name == null) viewModelScope.launch { _messages.send(Message(tr("Запись слишком короткая, аудио не сохранено"))) }
    }

    fun cancelRecording() {
        recordingSince = null
        voice.stopAndDiscard()
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
        val name = voice.stop()
        if (name == null) say(tr("Запись слишком короткая, аудио не сохранено"))
        else form = form.copy(audio = name)
    }

    /** Leaving the card without saving: the recording goes with it. */
    private fun discardCardRecording() {
        if (cardRecordingSince == null) return
        cardRecordingSince = null
        voice.stopAndDiscard()
    }

    // ---------- form ----------

    fun openMap() {
        screen = Screen.Map
    }

    /** Dashboard parameters, kept while the app runs. */
    var dashFilter by mutableStateOf(ru.r3xed.qsolog.data.StatFilter()); private set
    var dashBucket by mutableStateOf(ru.r3xed.qsolog.data.StatBucket.AUTO); private set

    fun changeDashFilter(f: ru.r3xed.qsolog.data.StatFilter) {
        dashFilter = f
    }

    fun changeDashBucket(b: ru.r3xed.qsolog.data.StatBucket) {
        dashBucket = b
    }

    fun openDashboard() {
        screen = Screen.Dashboard
    }

    fun closeDashboard() {
        screen = Screen.Log
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
        screen = editReturn
        returnToNewCard(f.isNew)
    }

    /**
     * "Добавить QSO". When the log search has narrowed down to one station (or the query is exactly its callsign),
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
        if (hasQrzAccount) lookupJob = viewModelScope.launch { runLookup(last.call) }
    }

    fun newQso(audio: String = "") {
        lookupJob?.cancel() // a lookup for another card must not land in this one
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
        editSession++
        editReturn = Screen.Log
        screen = Screen.Edit
    }

    fun edit(qso: Qso, from: Screen = Screen.Log) {
        openedByKeys = false
        lookupJob?.cancel() // a lookup for another card must not land in this one
        cardFromLog = false
        editSession++
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
            myCall = qso.myCall,
            myLocator = qso.myLocator,
            adif = qso.adif - AdifLabels.TIME_OFF,
            dateOff = Adif.displayDate(qso.adif["QSO_DATE_OFF"]),
            timeOff = Adif.displayTime(qso.adif["TIME_OFF"]),
            bandRxFollows = qso.adif["BAND_RX"].let { it.isNullOrBlank() || it.equals(qso.band, ignoreCase = true) },
            freqRxFollows = qso.adif["FREQ_RX"].let { it.isNullOrBlank() || it.toDoubleOrNull() == qso.freqMhz.toDoubleOrNull() },
            pendingLookup = qso.pendingLookup,
            timeOffFollows = qso.adif["TIME_OFF"].isNullOrBlank() ||
                (Adif.displayDate(qso.adif["QSO_DATE_OFF"]) == DATE_FMT.format(t) && Adif.displayTime(qso.adif["TIME_OFF"]) == TIME_FMT.format(t)),
            distanceKm = qso.distanceKm,
            bearing = qso.bearing,
            createdAt = qso.createdAt,
        )
        formOriginal = form
        lookup = Lookup.Idle
        loadHistory(qso.call)
        editReturn = from
        // In contest mode a contest contact opens in the contest card, where swipes go through the others.
        screen = if (contestMode && ContestMode.isContest(qso.adif)) Screen.Contest else Screen.Edit
    }

    fun update(f: Form) {
        form = f
    }

    fun setNow() {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        form = form.copy(date = DATE_FMT.format(now), time = TIME_FMT.format(now))
    }

    fun setMode(mode: String) {
        val f = form
        // Swap the default report only if the user has not typed their own.
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
        lookupJob = viewModelScope.launch {
            delay(900) // wait until typing pauses; QRZ.ru allows one request per 3 s
            runLookup(call)
        }
    }

    fun retryLookup() {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch { runLookup(form.call) }
    }

    /**
     * QRZ.ru first (full data: name, QTH, locator). Without an account, when it does not know the callsign or fails,
     * HamQTH's free prefix search fills country, region, zones and an approximate position, if switched on.
     */
    private suspend fun runLookup(call: String) {
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
        } catch (e: kotlinx.coroutines.CancellationException) {
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
        } catch (e: kotlinx.coroutines.CancellationException) {
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
        viewModelScope.launch {
            history = if (call.length < 3) CallHistory(0, null)
            else withContext(Dispatchers.IO) { db.history(call, excludeId = id) }
        }
    }

    /** The station a record was made from: its own locator, or the one from the settings. */
    fun myPositionFor(f: Form): LatLon? = Geo.locatorToLatLon(f.myLocator) ?: settings.myPosition

    fun setAdif(key: String, value: String) {
        form = form.copy(adif = form.adif + (key to value))
    }

    /** Takes the [format] export mark off the open card (saved with the card), so the next "only new" export takes it. */
    fun clearExportMark(format: ExportFormat) {
        form = form.copy(adif = format.unmarkFields(form.adif))
    }

    /** Detaches the voice note. The file itself goes when the card is saved, so closing without saving keeps it. */
    fun removeAudio() {
        val f = form
        if (f.audio.isBlank()) return
        form = f.copy(audio = "", removedAudio = f.audio)
        viewModelScope.launch {
            _messages.send(Message(tr("Аудиозапись удалена")) {
                if (form.audio.isBlank() && form.removedAudio == f.audio) form = form.copy(audio = f.audio, removedAudio = "")
            })
        }
    }

    /** Records whose QRZ.ru data is being fetched again right now (spinner instead of the button). */
    var refreshing by mutableStateOf<Set<Long>>(emptySet()); private set

    /** Retries the QRZ.ru lookup for a contact saved offline; fills only fields that are still empty. */
    fun refreshLookup(qso: Qso) {
        if (qso.id in refreshing) return
        refreshing = refreshing + qso.id
        viewModelScope.launch {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: QrzException) {
                e.message ?: tr("Ошибка QRZ.ru")
            } catch (e: Exception) {
                networkError(e)
            } finally {
                refreshing = refreshing - qso.id
            }
            reload()
            _messages.send(Message(msg))
        }
    }

    /** Returns an error message, or null when saved. */
    /** Saves the card. [quiet]: no "Связь записана" message (the contest card says it in its own line). */
    fun save(quiet: Boolean = false): String? {
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
        saveJob = viewModelScope.launch {
            lastSavedId = withContext(Dispatchers.IO) { db.save(finalQso) }
            forgetSearch(finalQso.call)
            reload()
            if (f.isNew) newSavedTick++
            val note = if (finalQso.pendingLookup) tr(". Данные QRZ.ru не получены: обновите их кнопкой ⟳ в логе") else ""
            if (!quiet) _messages.send(Message((if (f.isNew) tr("Связь с %s записана", f.call) else tr("Изменения сохранены")) + note))
        }
        screen = editReturn
        returnToNewCard(f.isNew)
        return null
    }

    /**
     * "＋ Следующая": saves the card and opens a new one on the same band, mode and frequency with the cursor in the
     * callsign — for pile-ups and contests. Returns the error like [save].
     */
    fun saveAndNext(): String? {
        val f = form
        val err = save()
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
    private val searchHistory = SearchHistory(java.io.File(app.filesDir, "search_history.json"))
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
        screen = Screen.History
    }

    /** Back: from an entry to the list, from a selection to the plain list, from the list to the log. */
    fun closeHistory() {
        when {
            historyOpen != null -> historyOpen = null
            historyMap -> historyMap = false
            historySelected.isNotEmpty() -> historySelected = emptySet()
            else -> screen = Screen.Log
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
        if (screen != Screen.Edit) return // the contest card fills only the callsign
        form = form.copy(
            call = e.call, name = e.name, qth = e.qth, country = e.country, locator = e.locator,
            lat = e.lat, lon = e.lon, infoFromQrz = true,
            adif = form.adif + e.adif.filter { (k, v) -> v.isNotBlank() && form.adif[k].isNullOrBlank() },
        )
        formOriginal = form
        cardFromLog = true
        editReturn = Screen.History
        loadHistory(e.call)
        lookup = Lookup.FromHistory(e)
        if (hasQrzAccount) lookupJob = viewModelScope.launch { runLookup(e.call) }
    }

    /** The card was filled from the history: the station's data as it was found earlier. */
    private fun historyFallback(call: String, problem: String?): Lookup? {
        if (!historyOn) return null
        val e = searchHistory.get(call) ?: return null
        val f = form
        if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank()))) {
            form = f.copy(
                name = e.name, qth = e.qth, country = e.country, locator = e.locator, lat = e.lat, lon = e.lon, infoFromQrz = true,
                adif = f.adif + e.adif.filter { (k, v) -> v.isNotBlank() && f.adif[k].isNullOrBlank() },
            )
        }
        return Lookup.FromHistory(e, problem)
    }

    /** A new card closed or wiped without saving: what was found about the station goes to the history. */
    private fun rememberSearch() {
        if (!historyOn) return
        val f = form
        if (!f.isNew || f.call.length < 3 || allQsos.any { it.call == f.call }) return
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

    /** The station is in the log now: the history no longer needs it. */
    private fun forgetSearch(call: String) {
        if (searchHistory.get(call) == null) return
        searchHistory.delete(listOf(call))
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

    /** A physical keyboard is attached: the app's on-screen keypad steps aside for the text fields. */
    var hardKeyboard by mutableStateOf(false)

    /** The app's keypad is on in the settings and no physical keyboard is attached. */
    val keypadShown: Boolean
        get() = keypad != KeypadMode.OFF && !hardKeyboard

    /** F1 / Ctrl+/: the list of keys is open. */
    var showKeys by mutableStateOf(false)

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
        if (screen == Screen.Contest) { contestPrev(); return }
        if (hasUnsavedChanges) return
        val fromNew = screen == Screen.Edit && form.isNew
        val job = saveJob
        viewModelScope.launch {
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
            is CallCommand.Freq -> setFreq(c.freq)
        }
        // A fresh card stays "untouched": closing it does not ask.
        if (untouched) formOriginal = form
        return true
    }

    /** Esc on a new card with something typed: a clean card on the same band, mode and frequency. */
    fun clearCard() {
        val f = form
        if (screen == Screen.Contest) {
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
        viewModelScope.launch {
            val qso = withContext(Dispatchers.IO) { db.get(id) } ?: return@launch
            // In the contest card the work goes on: the waiting draft or a new card instead of the log.
            if (screen == Screen.Contest) contestDraftOrNew() else screen = editReturn
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
        viewModelScope.launch {
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
                _messages.send(Message(tr("Журнал синхронизирован: отправлено %s, получено %s", res.sent, res.received)))
            }.onFailure { e ->
                sheetOk = false
                sheetStatus = tr("Не удалось синхронизировать в %s: %s", time, e.message ?: e.javaClass.simpleName)
                _messages.send(Message(sheetStatus))
            }
            prefs.sheetStatus = sheetStatus
        }
    }

    // ---------- contest mode ----------

    /**
     * Contest mode: "Добавить QSO" opens the simplified card (callsign, reports, numbers) and swipes go through the
     * contest contacts. Off at every start of the app, switched on in the settings.
     */
    var contestMode by mutableStateOf(false); private set

    /** The number the next contest contact sends; goes up by one with each saved contact. */
    var contestSerial by mutableStateOf(prefs.contestSerial); private set

    fun changeContestMode(on: Boolean) {
        contestMode = on
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

    /** The app's own keyboard in both cards, remembered between starts. */
    var keypad by mutableStateOf(prefs.keypad); private set

    fun changeKeypad(mode: KeypadMode) {
        keypad = mode
        prefs.keypad = mode
    }

    fun changeContestRstShown(on: Boolean) {
        contestRstShown = on
        prefs.contestRstShown = on
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
    fun contestCounts(now: Long): kotlin.Pair<Int, Int> {
        val list = contestQsos()
        return list.size to list.count { it.timeUtc in (now - 3_600_000L)..now }
    }

    /** A new contest card that was typed in and left by swiping back; it comes back at the end of the swipes. */
    private var contestDraft: Form? = null

    /** Contest contacts in the order they were made: the swipes go through them. */
    private fun contestQsos(): List<Qso> =
        allQsos.filter { ContestMode.isContest(it.adif) }.sortedWith(compareBy({ it.timeUtc }, { it.createdAt }, { it.id }))

    /** Position of the open contest card: "№ in the contest / of how many"; null for a new one. */
    fun contestPosition(): kotlin.Pair<Int, Int>? {
        val list = contestQsos()
        val i = list.indexOfFirst { it.id == form.id }
        return if (form.isNew || i < 0) null else (i + 1) to list.size
    }

    /** Whether there is an older contest contact to swipe back to. */
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
            adif = f.adif + (ContestMode.FIELD to "Y") + (ContestMode.SENT to contestSentNext()),
        )
        formOriginal = form
        screen = Screen.Contest
    }

    /**
     * "Записать и следующая" (and a swipe forward on a new card): saves the contact with the time of saving, moves the
     * number on and opens the next card on the same band and mode. Returns the error like [save].
     */
    fun contestSaveAndNext(): String? {
        val f = form
        if (!f.isNew) return contestNext()
        contestError(f)?.let { return it }
        val now = LocalDateTime.now(ZoneOffset.UTC)
        form = f.copy(date = DATE_FMT.format(now), time = TIME_FMT.format(now))
        val err = save(quiet = true)
        if (err != null) {
            screen = Screen.Contest
            return err
        }
        contestLastSaved = f.call
        contestWork = f
        if (!contestSentFixed) changeContestSerial(ContestMode.nextSerial(f.adif[ContestMode.SENT].orEmpty(), contestSerial))
        contestDraft = null
        newContestQso()
        return null
    }

    /** Swipe back: the previous contest contact. Changes to a saved one are saved; a typed new card waits as a draft. */
    fun contestPrev(): String? {
        val list = contestQsos()
        val f = form
        val target = if (f.isNew) list.lastOrNull() else list.getOrNull(list.indexOfFirst { it.id == f.id } - 1)
        return if (target == null) null else contestOpen(target)
    }

    /** Opens [target] in the contest card, as a swipe back does: the open card is saved or kept as a draft first. */
    fun contestOpen(target: Qso): String? {
        val f = form
        if (!f.isNew && f.id == target.id) return null
        contestLastSaved = null
        if (f.isNew) {
            contestWork = f
            contestDraft = f.takeIf { it.call.isNotBlank() || !it.adif[ContestMode.RCVD].isNullOrBlank() }
        } else if (hasUnsavedChanges) {
            contestError(f)?.let { return it }
            save()?.let { screen = Screen.Contest; return it }
        }
        edit(target)
        screen = Screen.Contest
        return null
    }

    /** Swipe forward from a saved contact: the next one, and after the last — the draft or a new card. */
    private fun contestNext(): String? {
        val list = contestQsos()
        val f = form
        if (hasUnsavedChanges) {
            contestError(f)?.let { return it }
            save()?.let { screen = Screen.Contest; return it }
        }
        val next = list.getOrNull(list.indexOfFirst { it.id == f.id } + 1)
        if (next != null) edit(next) else contestDraftOrNew()
        screen = Screen.Contest
        return null
    }

    /** After the last contest contact: the card typed in and left by a swipe back, or a new one. */
    private fun contestDraftOrNew() {
        val draft = contestDraft
        newContestQso()
        if (draft == null) return
        contestDraft = null
        form = draft.copy(adif = draft.adif + (ContestMode.SENT to (draft.adif[ContestMode.SENT] ?: contestSentNext())))
        loadHistory(draft.call)
        // The lookup may have been cut short by the swipe back.
        if (draft.call.length >= MIN_LOOKUP_LENGTH && !draft.infoFromQrz && draft.name.isBlank()) retryLookup()
    }

    /**
     * A contest contact needs the number the other station gave: without it the report line ends after the RST and
     * the judges' software does not count the contact. (The callsign is checked by [save].)
     */
    private fun contestError(f: Form): String? =
        if (f.call.length >= 3 && f.adif[ContestMode.RCVD].isNullOrBlank()) CONTEST_RCVD_ERROR else null

    fun setContestField(key: String, value: String) {
        form = form.copy(adif = form.adif + (key to value))
    }

    // ---------- settings ----------

    fun updateSettings(s: StationSettings) {
        val credentialsChanged = s.qrzLogin != settings.qrzLogin || s.qrzPassword != settings.qrzPassword
        val siteChanged = s.qrzSiteEmail != settings.qrzSiteEmail || s.qrzSitePassword != settings.qrzSitePassword
        val comChanged = s.qrzComLogin != settings.qrzComLogin || s.qrzComPassword != settings.qrzComPassword
        settings = s
        prefs.save(s)
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
        viewModelScope.launch {
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

    /** Result of "Проверить вход" for the site account. */
    var qrzSiteStatus by mutableStateOf<String?>(null); private set
    var qrzSiteOk by mutableStateOf<Boolean?>(null); private set

    fun testQrzSite() {
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    /** Fills my locator from QRZ.ru, or from the QTH address via the system geocoder. */
    fun findMyLocator() {
        viewModelScope.launch {
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
                _messages.send(Message(tr("Не удалось найти координаты. Введите локатор вручную")))
            } else {
                updateSettings(settings.copy(myLocator = Geo.latLonToLocator(found), myQth = qth))
                _messages.send(Message(tr("Локатор определён: %s", Geo.latLonToLocator(found))))
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocode(address: String): LatLon? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            Geocoder(getApplication(), Locale("ru")).getFromLocationName(address, 1)
                ?.firstOrNull()?.let { LatLon(it.latitude, it.longitude) }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- CSV ----------

    fun importAdif(uri: Uri) {
        viewModelScope.launch {
            val msg = try {
                val (added, dup, bad) = withContext(Dispatchers.IO) {
                    val r = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { Adif.import(it) }
                    // A voice note name only means something if the file is on this phone.
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
            _messages.send(Message(msg))
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            val msg = try {
                val (added, dup, bad) = withContext(Dispatchers.IO) {
                    val r = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { Csv.import(it) }
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
            _messages.send(Message(msg))
        }
    }
}
