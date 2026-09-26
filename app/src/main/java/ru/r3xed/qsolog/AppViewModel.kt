package ru.r3xed.qsolog

import android.app.Application
import android.location.Geocoder
import android.net.Uri
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
import ru.r3xed.qsolog.data.Cabrillo
import ru.r3xed.qsolog.data.Csv
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.HamQth
import ru.r3xed.qsolog.data.approxPosition
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzClient
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
import ru.r3xed.qsolog.data.defaultRst
import ru.r3xed.qsolog.data.withDistance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ThemeMode(val label: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

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

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Failed(val message: String) : UpdateState
}

enum class SortBy(val label: String, val defaultDesc: Boolean) {
    DATE("Дата", true),
    DISTANCE("Км", true),
    CALL("Позывной", false),
    BAND("Диапазон", false),
}

sealed interface Screen {
    data object Log : Screen
    data object Edit : Screen
    data object Settings : Screen
    data object Map : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = QsoDb(app)
    private val prefs = Settings(app)
    val voice = VoiceNotes(app)

    var settings by mutableStateOf(prefs.load()); private set
    private val qrz = QrzClient { settings.qrzLogin to settings.qrzPassword }

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
            say("Должен быть включён хотя бы один диапазон")
            return
        }
        enabledBands = if (on) enabledBands + band else enabledBands - band
        prefs.enabledBands = enabledBands
    }

    fun setModeEnabled(mode: String, on: Boolean) {
        if (!on && enabledModes.count { it in MODES && it != mode } == 0) {
            say("Должен быть включён хотя бы один вид связи")
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

    /** Header chosen in the dialog, waiting for the file picker. */
    private var pendingContest: Pair<Cabrillo.Header, Set<Long>?>? = null

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

    /** Remembers the dialog's choice and returns the file name to offer. */
    fun prepareContest(h: Cabrillo.Header): String {
        prefs.contestFormat = h.format.name
        prefs.contestCode = h.contest
        prefs.contestOperator = h.categoryOperator
        pendingContest = h to contestTarget?.only
        contestTarget = null
        val code = h.contest.uppercase().ifBlank { "LOG" }.replace('/', '-')
        return "${h.callsign.ifBlank { "log" }.replace('/', '-')}_$code.${h.format.extension}"
    }

    fun exportContest(uri: Uri) {
        val (h, only) = pendingContest ?: return
        pendingContest = null
        if (only != null) clearSelection()
        viewModelScope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all().let { all -> if (only == null) all else all.filter { it.id in only } }
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use {
                        it.write(Cabrillo.export(list, h).toByteArray(Cabrillo.charset(h.format)))
                    }
                    list.size
                }
                "Экспортировано в ${h.format.title}: $count"
            } catch (e: Exception) {
                "Не удалось сохранить файл: ${e.message}"
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
                    append("Импортировано из отчёта")
                    if (r.contest.isNotBlank()) append(" ${r.contest}")
                    append(": $added")
                    if (dup > 0) append(", повторов пропущено: $dup")
                    if (r.skipped > 0) append(", строк с ошибками: ${r.skipped}")
                }
            } catch (e: Exception) {
                "Не удалось прочитать файл: ${e.message}"
            }
            reload()
            _messages.send(Message(msg))
        }
    }

    /** Bumped when a new contact is saved; the log scrolls to the top to show it. */
    var newSavedTick by mutableStateOf(0); private set

    var hamqthEnabled by mutableStateOf(prefs.hamqthEnabled); private set

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
                    if (e is java.net.UnknownHostException) "Нет интернета: не удаётся связаться с GitHub"
                    else "Не удалось проверить: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun dismissUpdate() {
        update = UpdateState.Idle
    }

    /** Where "Назад" in the settings returns: the log, or the card that sent the user there. */
    private var settingsReturn: Screen = Screen.Log

    fun openSettings(from: Screen = Screen.Log) {
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

    val hasUnsavedChanges: Boolean
        get() = formOriginal.let { it != null && form != it }

    init {
        prefs.launchCount = prefs.launchCount + 1
        showRecordHint = prefs.launchCount <= 5
        reload()
        if (settings.myCall.isBlank()) screen = Screen.Settings
        viewModelScope.launch(Dispatchers.IO) { voice.cleanup(keep = db.audioFiles()) }
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
            if (settings.qrzLogin.isBlank()) {
                searchLookup = SearchLookup.NoAccount
                return@launch
            }
            searchLookup = SearchLookup.Searching(call)
            searchLookup = try {
                val info = qrz.lookup(call) { query.trim().uppercase() == call }
                if (info == null) SearchLookup.NotFound(call) else {
                    newQsoFor(call, info)
                    SearchLookup.Idle
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: QrzException) {
                SearchLookup.Failed(e.message ?: "Ошибка QRZ.ru")
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
            val tail = if (left > 0) ". Других связей с ${qso.call}: $left" else ""
            _messages.send(Message("Удалена связь с ${qso.call} ${DATE_FMT.format(t)} ${TIME_FMT.format(t)}$tail") { restore(qso) })
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
            _messages.send(Message("История QSO удалена: $n записей"))
        }
    }

    private fun restore(qso: Qso) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.save(qso.copy(id = 0)) }
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
            viewModelScope.launch { _messages.send(Message("Не удалось включить микрофон: ${e.message ?: e.javaClass.simpleName}")) }
            false
        }
    }

    /** Ends the recording and opens a new contact card with it attached. */
    fun finishRecording() {
        if (recordingSince == null) return
        recordingSince = null
        val name = voice.stop()
        addQso(audio = name.orEmpty())
        if (name == null) viewModelScope.launch { _messages.send(Message("Запись слишком короткая, аудио не сохранено")) }
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
            say("Не удалось включить микрофон: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Stops the card recording and attaches it to the form. */
    fun stopCardRecording() {
        if (cardRecordingSince == null) return
        cardRecordingSince = null
        val name = voice.stop()
        if (name == null) say("Запись слишком короткая, аудио не сохранено")
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

    /** Closes the card without saving; a voice note recorded for an unsaved contact is deleted. */
    fun closeEditor() {
        lookupJob?.cancel() // a lookup for another card must not land in this one
        discardCardRecording()
        val f = form
        if (f.isNew) {
            if (f.audio.isNotBlank()) voice.delete(f.audio)
            if (f.removedAudio.isNotBlank()) voice.delete(f.removedAudio)
        }
        screen = editReturn
    }

    /**
     * "Добавить QSO". When the log search has narrowed down to one station (or the query is exactly its callsign),
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
        if (settings.qrzLogin.isNotBlank()) lookupJob = viewModelScope.launch { runLookup(last.call) }
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
        editReturn = Screen.Log
        screen = Screen.Edit
    }

    fun edit(qso: Qso, from: Screen = Screen.Log) {
        lookupJob?.cancel() // a lookup for another card must not land in this one
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
        screen = Screen.Edit
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
        val band = freq.replace(',', '.').toDoubleOrNull()?.let { bandForFreq(it) }
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
        if (settings.qrzLogin.isBlank()) {
            lookup = hamqthFallback(call, qrzProblem = null)
                ?: Lookup.Failed("Укажите учётную запись QRZ.ru в настройках", noAccount = true)
            return
        }
        lookup = try {
            val info = qrz.lookup(call) { form.call == call }
            if (info == null) hamqthFallback(call, qrzProblem = null) ?: Lookup.NotFound else {
                val f = form
                if (f.call == call && cardFromLog) {
                    // Filled from the log: QRZ.ru's values win, but what it leaves blank keeps the log's.
                    val pos = info.position
                    form = f.copy(
                        name = info.fullName.ifBlank { f.name }, qth = info.city.ifBlank { f.qth }, country = info.country.ifBlank { f.country },
                        locator = info.locator.ifBlank { pos?.let { Geo.latLonToLocator(it) } ?: f.locator },
                        lat = if (pos != null) info.lat else f.lat, lon = if (pos != null) info.lon else f.lon,
                        adif = if (pos != null || info.locator.isNotBlank()) f.adif - HamQth.POSITION_FIELD else f.adif,
                    )
                } else if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank()))) {
                    form = f.copy(
                        name = info.fullName, qth = info.city, country = info.country,
                        locator = info.locator.ifBlank { info.position?.let { Geo.latLonToLocator(it) }.orEmpty() },
                        lat = info.lat, lon = info.lon, infoFromQrz = true,
                        adif = f.adif - HamQth.POSITION_FIELD,
                    )
                }
                Lookup.Found(info)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: QrzException) {
            val msg = e.message ?: "Ошибка QRZ.ru"
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
        is java.net.UnknownHostException -> "Нет интернета: не удаётся найти api.qrz.ru"
        is java.net.SocketTimeoutException -> "QRZ.ru не ответил вовремя"
        is javax.net.ssl.SSLException -> "Ошибка защищённого соединения: ${e.message}"
        else -> "Нет связи с QRZ.ru: ${e.javaClass.simpleName} ${e.message.orEmpty()}".trim()
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

    /** Detaches the voice note. The file itself goes when the card is saved, so closing without saving keeps it. */
    fun removeAudio() {
        val f = form
        if (f.audio.isBlank()) return
        form = f.copy(audio = "", removedAudio = f.audio)
        viewModelScope.launch {
            _messages.send(Message("Аудиозапись удалена") {
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
                val info = qrz.lookup(qso.call)
                if (info == null) {
                    withContext(Dispatchers.IO) { db.save(qso.copy(pendingLookup = false)) }
                    "На QRZ.ru позывного ${qso.call} нет"
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
                        adif = if (approx) qso.adif - HamQth.POSITION_FIELD else qso.adif,
                        pendingLookup = false,
                        updatedAt = System.currentTimeMillis(),
                    )
                    withContext(Dispatchers.IO) { db.save(updated) }
                    "Данные ${qso.call} получены с QRZ.ru"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: QrzException) {
                e.message ?: "Ошибка QRZ.ru"
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
    fun save(): String? {
        stopCardRecording()
        val f = form
        if (f.call.length < 3) return "Введите позывной"
        val date = try { LocalDate.parse(f.date.trim(), DATE_FMT) } catch (e: Exception) { return "Дата в формате ДД.ММ.ГГГГ" }
        val time = try { LocalTime.parse(f.time.trim(), TIME_FMT) } catch (e: Exception) { return "Время в формате ЧЧ:ММ" }
        val ts = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()
        val freq = f.freq.replace(',', '.').trim()
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
        val lookupMissed = settings.qrzLogin.isNotBlank() &&
            (!f.infoFromQrz && (l is Lookup.Failed || l is Lookup.Loading) || (l is Lookup.Approx && l.qrzProblem != null))
        val finalQso = qso.copy(pendingLookup = lookupMissed || (!f.isNew && f.pendingLookup && !(f.infoFromQrz && l is Lookup.Found)))
        prefs.lastBand = f.band
        prefs.lastMode = f.mode
        prefs.lastFreq = f.freq
        prefs.lastPower = f.power
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.save(finalQso) }
            reload()
            if (f.isNew) newSavedTick++
            val note = if (finalQso.pendingLookup) ". Данные QRZ.ru не получены: обновите их кнопкой ⟳ в логе" else ""
            _messages.send(Message((if (f.isNew) "Связь с ${f.call} записана" else "Изменения сохранены") + note))
        }
        screen = editReturn
        return null
    }

    fun deleteCurrent() {
        val id = form.id
        if (id == 0L) return
        viewModelScope.launch {
            val qso = withContext(Dispatchers.IO) { db.get(id) } ?: return@launch
            screen = editReturn
            delete(qso)
        }
    }

    // ---------- settings ----------

    fun updateSettings(s: StationSettings) {
        val credentialsChanged = s.qrzLogin != settings.qrzLogin || s.qrzPassword != settings.qrzPassword
        settings = s
        prefs.save(s)
        if (credentialsChanged) {
            qrz.reset()
            qrzOk = null
            qrzStatus = null
        }
    }

    fun testQrz() {
        viewModelScope.launch {
            qrzOk = null
            qrzStatus = "Проверяю…"
            qrz.reset()
            try {
                qrz.login()
                qrzOk = true
                qrzStatus = "Подключено"
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
            if (s.myCall.isNotBlank() && s.qrzLogin.isNotBlank()) {
                try {
                    qrz.lookup(s.myCall)?.let { info ->
                        pos = info.position
                        if (qth.isBlank()) qth = info.city
                    }
                } catch (_: Exception) {
                }
            }
            if (pos == null && qth.isNotBlank()) pos = geocode(qth)
            val found = pos
            if (found == null) {
                _messages.send(Message("Не удалось найти координаты. Введите локатор вручную"))
            } else {
                updateSettings(settings.copy(myLocator = Geo.latLonToLocator(found), myQth = qth))
                _messages.send(Message("Локатор определён: ${Geo.latLonToLocator(found)}"))
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

    fun csvFileName(): String {
        val call = settings.myCall.ifBlank { "log" }.replace('/', '-')
        return "qso_${call}_${LocalDate.now()}.csv"
    }

    fun adifFileName(): String {
        val call = settings.myCall.ifBlank { "log" }.replace('/', '-')
        return "${call}_${LocalDate.now()}.adi"
    }

    fun selectedAdifFileName(): String {
        val call = settings.myCall.ifBlank { "log" }.replace('/', '-')
        return "${call}_${selected.size}qso_${LocalDate.now()}.adi"
    }

    /** Exports only the records picked in the log, then leaves selection mode. */
    fun exportSelectedAdif(uri: Uri) = exportAdif(uri, only = selected).also { clearSelection() }

    fun exportAdif(uri: Uri, only: Set<Long>? = null) {
        viewModelScope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all().let { all -> if (only == null) all else all.filter { it.id in only } }
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use {
                        Adif.export(list, it, program = "QSO-LOG", version = BuildConfig.VERSION_NAME)
                    }
                    list.size
                }
                "Экспортировано в ADIF: $count"
            } catch (e: Exception) {
                "Не удалось сохранить файл: ${e.message}"
            }
            _messages.send(Message(msg))
        }
    }

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
                    append("Импортировано из ADIF: $added")
                    if (dup > 0) append(", повторов пропущено: $dup")
                    if (bad > 0) append(", записей с ошибками: $bad")
                }
            } catch (e: Exception) {
                "Не удалось прочитать файл: ${e.message}"
            }
            reload()
            _messages.send(Message(msg))
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all()
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { Csv.export(list, it) }
                    list.size
                }
                "Экспортировано записей: $count"
            } catch (e: Exception) {
                "Не удалось сохранить файл: ${e.message}"
            }
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
                    append("Импортировано: $added")
                    if (dup > 0) append(", повторов пропущено: $dup")
                    if (bad > 0) append(", строк с ошибками: $bad")
                }
            } catch (e: Exception) {
                "Не удалось прочитать файл: ${e.message}"
            }
            reload()
            _messages.send(Message(msg))
        }
    }
}
