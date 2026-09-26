package ru.r3xed.qsolog

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
import ru.r3xed.qsolog.data.CallHistory
import ru.r3xed.qsolog.data.Csv
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.HamQth
import ru.r3xed.qsolog.data.approxPosition
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzClient
import ru.r3xed.qsolog.data.QrzException
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.StationSettings
import ru.r3xed.qsolog.data.bandForFreq
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

enum class Pane { Empty, Edit, Settings, Map }

/** Callsign length at which the QRZ.ru / HamQTH lookup starts. */
const val MIN_LOOKUP_LENGTH = 4

enum class ThemeMode(val label: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: DesktopRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

enum class SortBy(val label: String, val defaultDesc: Boolean) {
    DATE("Дата", true),
    DISTANCE("Км", true),
    CALL("Позывной", false),
    BAND("Диапазон", false),
}

/** Desktop counterpart of the Android AppViewModel. */
class AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = QsoDb()
    private val prefs = Settings()
    val voice = VoiceNotes()

    var settings by mutableStateOf(prefs.load()); private set
    private val qrz = QrzClient { settings.qrzLogin to settings.qrzPassword }

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

    fun setBandEnabled(band: String, on: Boolean) {
        enabledBands = if (on) enabledBands + band else enabledBands - band
        prefs.enabledBands = enabledBands
    }

    fun setModeEnabled(mode: String, on: Boolean) {
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
                    if (e is java.net.UnknownHostException) "Нет интернета: не удаётся связаться с GitHub"
                    else "Не удалось проверить: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun dismissUpdate() {
        update = UpdateState.Idle
    }

    /** Where "Назад" in the settings returns: the empty pane, or the card that sent the user there. */
    private var settingsReturn = Pane.Empty

    fun openSettings(from: Pane = Pane.Empty) {
        settingsReturn = from
        pane = Pane.Settings
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
        if (settings.myCall.isBlank()) pane = Pane.Settings
        scope.launch(Dispatchers.IO) { voice.cleanup(keep = db.audioFiles()) }
        scope.launch {
            val pw = withContext(Dispatchers.IO) { prefs.password() }
            if (settings.qrzPassword.isEmpty()) settings = settings.copy(qrzPassword = pw)
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
            val tail = if (left > 0) ". Других связей с ${qso.call}: $left" else ""
            say("Удалена связь с ${qso.call} ${DATE_FMT.format(t)} ${TIME_FMT.format(t)}$tail") { restore(qso) }
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
            say("История QSO удалена: $n записей")
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
            say("Не удалось включить микрофон: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    /** Ends the recording and opens a new contact card with it attached. */
    fun finishRecording() {
        if (recordingSince == null) return
        recordingSince = null
        scope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { voice.stop() }.getOrNull() }
            newQso(audio = name.orEmpty())
            if (name == null) say("Запись слишком короткая, аудио не сохранено")
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
        val f = form
        if (f.isNew) {
            if (f.audio.isNotBlank()) voice.delete(f.audio)
            if (f.removedAudio.isNotBlank()) voice.delete(f.removedAudio)
        }
        pane = editReturn
    }

    fun newQso(audio: String = "") {
        lookupJob?.cancel() // a lookup for another card must not land in this one
        if (pane == Pane.Edit && form.isNew) closeEditor()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        // Mode, band and frequency of the most recent contact in the log: usually the next one is on the same.
        // An empty log falls back to what the last saved card had.
        val latest = allQsos.maxByOrNull { it.timeUtc }
        val mode = latest?.mode?.ifBlank { null } ?: prefs.lastMode
        form = Form(
            date = DATE_FMT.format(now),
            time = TIME_FMT.format(now),
            band = latest?.band?.ifBlank { null } ?: prefs.lastBand,
            mode = mode,
            freq = if (latest != null) latest.freqMhz else prefs.lastFreq,
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
        say("Аудиозапись удалена") {
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
        val band = freq.replace(',', '.').toDoubleOrNull()?.let { bandForFreq(it) }
        form = form.copy(freq = freq, band = band ?: form.band)
    }

    fun setLocator(loc: String) {
        form = form.copy(locator = loc, lat = null, lon = null, infoFromQrz = false, adif = form.adif - HamQth.POSITION_FIELD)
    }

    fun setCall(raw: String) {
        val call = raw.uppercase().filter { it.isLetterOrDigit() || it == '/' }
        if (formError == "Введите позывной") formError = null
        val f = form
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
        if (settings.qrzLogin.isBlank()) {
            lookup = hamqthFallback(call, qrzProblem = null)
                ?: Lookup.Failed("Укажите учётную запись QRZ.ru в настройках", noAccount = true)
            return
        }
        lookup = try {
            val info = qrz.lookup(call) { form.call == call }
            if (info == null) hamqthFallback(call, qrzProblem = null) ?: Lookup.NotFound else {
                val f = form
                if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank()))) {
                    form = f.copy(
                        name = info.fullName, qth = info.city, country = info.country,
                        locator = info.locator.ifBlank { info.position?.let { Geo.latLonToLocator(it) }.orEmpty() },
                        lat = info.lat, lon = info.lon, infoFromQrz = true,
                        adif = f.adif - HamQth.POSITION_FIELD,
                    )
                }
                Lookup.Found(info)
            }
        } catch (e: CancellationException) {
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
        if (!hamqthEnabled) return null
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
        is java.net.UnknownHostException -> "Нет интернета: не удаётся найти api.qrz.ru"
        is java.net.SocketTimeoutException -> "QRZ.ru не ответил вовремя"
        is javax.net.ssl.SSLException -> "Ошибка защищённого соединения: ${e.message}"
        else -> "Нет связи с QRZ.ru: ${e.javaClass.simpleName} ${e.message.orEmpty()}".trim()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: QrzException) {
                e.message ?: "Ошибка QRZ.ru"
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
        val lookupMissed = settings.qrzLogin.isNotBlank() &&
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
            val note = if (finalQso.pendingLookup) ". Данные QRZ.ru не получены: обновите их кнопкой ⟳ в логе" else ""
            if (f.isNew) newSavedTick++
            say((if (f.isNew) "Связь с ${f.call} записана" else "Изменения сохранены") + note)
        }
        pane = editReturn
        return null
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
    }

    fun flushSettings() {
        saveSettingsJob?.cancel()
        prefs.save(settings)
    }

    fun testQrz() {
        scope.launch {
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

    /** Fills my locator from QRZ.ru, or from the QTH address via OpenStreetMap search. */
    fun findMyLocator() {
        scope.launch {
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
                say("Не удалось найти координаты. Введите локатор вручную")
            } else {
                val loc = Geo.latLonToLocator(found)
                updateSettings(settings.copy(myLocator = loc, myQth = qth))
                say("Локатор определён: $loc")
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
    fun exportSelectedAdif(file: File) = exportAdif(file, only = selected).also { clearSelection() }

    fun exportAdif(file: File, only: Set<Long>? = null) {
        scope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all().let { all -> if (only == null) all else all.filter { it.id in only } }
                    file.outputStream().use { Adif.export(list, it, program = "QSO-LOG", version = APP_VERSION) }
                    list.size
                }
                "Экспортировано в ADIF: $count, файл ${file.name}"
            } catch (e: Exception) {
                "Не удалось сохранить файл: ${e.message}"
            }
            say(msg)
        }
    }

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
                    append("Импортировано из ADIF: $added")
                    if (dup > 0) append(", повторов пропущено: $dup")
                    if (bad > 0) append(", записей с ошибками: $bad")
                }
            } catch (e: Exception) {
                "Не удалось прочитать файл: ${e.message}"
            }
            reload()
            say(msg)
        }
    }

    fun exportCsv(file: File) {
        scope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all()
                    file.outputStream().use { Csv.export(list, it) }
                    list.size
                }
                "Экспортировано записей: $count в ${file.name}"
            } catch (e: Exception) {
                "Не удалось сохранить файл: ${e.message}"
            }
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
                    append("Импортировано: $added")
                    if (dup > 0) append(", повторов пропущено: $dup")
                    if (bad > 0) append(", строк с ошибками: $bad")
                }
            } catch (e: Exception) {
                "Не удалось прочитать файл: ${e.message}"
            }
            reload()
            say(msg)
        }
    }
}
