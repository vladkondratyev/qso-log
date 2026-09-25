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
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.CallHistory
import ru.r3xed.qsolog.data.Csv
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzClient
import ru.r3xed.qsolog.data.QrzException
import ru.r3xed.qsolog.data.QrzInfo
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.QsoDb
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

    fun setBandEnabled(band: String, on: Boolean) {
        enabledBands = if (on) enabledBands + band else enabledBands - band
        prefs.enabledBands = enabledBands
    }

    fun setModeEnabled(mode: String, on: Boolean) {
        enabledModes = if (on) enabledModes + mode else enabledModes - mode
        prefs.enabledModes = enabledModes
    }

    fun setBand(band: String) {
        form = form.copy(band = band)
    }

    var sortBy by mutableStateOf(runCatching { SortBy.valueOf(prefs.sortBy) }.getOrDefault(SortBy.DATE)); private set
    var sortDesc by mutableStateOf(prefs.sortDesc); private set

    /** A tap on the current sort flips its direction; a tap on another one selects it with its natural direction. */
    fun sort(by: SortBy) {
        if (by == sortBy) sortDesc = !sortDesc else { sortBy = by; sortDesc = by.defaultDesc }
        prefs.sortBy = by.name
        prefs.sortDesc = sortDesc
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

    init {
        reload()
        if (settings.myCall.isBlank()) screen = Screen.Settings
        viewModelScope.launch(Dispatchers.IO) { voice.cleanup(keep = db.audioFiles()) }
    }

    override fun onCleared() {
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
        newQso(audio = name.orEmpty())
        if (name == null) viewModelScope.launch { _messages.send(Message("Запись слишком короткая, аудио не сохранено")) }
    }

    fun cancelRecording() {
        recordingSince = null
        voice.stopAndDiscard()
    }

    // ---------- form ----------

    fun openMap() {
        screen = Screen.Map
    }

    /** Closes the card without saving; a voice note recorded for an unsaved contact is deleted. */
    fun closeEditor() {
        val f = form
        if (f.isNew) {
            if (f.audio.isNotBlank()) voice.delete(f.audio)
            if (f.removedAudio.isNotBlank()) voice.delete(f.removedAudio)
        }
        screen = editReturn
    }

    fun newQso(audio: String = "") {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val mode = prefs.lastMode
        form = Form(
            date = DATE_FMT.format(now),
            time = TIME_FMT.format(now),
            band = prefs.lastBand,
            mode = mode,
            freq = prefs.lastFreq,
            rstSent = defaultRst(mode),
            rstRcvd = defaultRst(mode),
            power = settings.power.ifBlank { prefs.lastPower },
            audio = audio,
            // "Моя станция" comes from the settings; each record then keeps its own copy.
            myCall = settings.myCall,
            myLocator = settings.myLocator,
            adif = settings.station,
        )
        history = CallHistory(0, null)
        lookup = Lookup.Idle
        editReturn = Screen.Log
        screen = Screen.Edit
    }

    fun edit(qso: Qso, from: Screen = Screen.Log) {
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
            timeOffFollows = qso.adif["TIME_OFF"].isNullOrBlank() ||
                (Adif.displayDate(qso.adif["QSO_DATE_OFF"]) == DATE_FMT.format(t) && Adif.displayTime(qso.adif["TIME_OFF"]) == TIME_FMT.format(t)),
            distanceKm = qso.distanceKm,
            bearing = qso.bearing,
            createdAt = qso.createdAt,
        )
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
        form = form.copy(locator = loc, lat = null, lon = null, infoFromQrz = false)
    }

    fun setCall(raw: String) {
        val call = raw.uppercase().filter { it.isLetterOrDigit() || it == '/' }
        val f = form
        form = if (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank())) {
            f.copy(call = call, name = "", qth = "", country = "", locator = "", lat = null, lon = null, infoFromQrz = false)
        } else f.copy(call = call)
        loadHistory(call)
        lookupJob?.cancel()
        if (call.length < 3) {
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

    private suspend fun runLookup(call: String) {
        if (settings.qrzLogin.isBlank()) {
            lookup = Lookup.Failed("Укажите учётную запись QRZ.ru в настройках")
            return
        }
        lookup = Lookup.Loading
        lookup = try {
            val info = qrz.lookup(call) { form.call == call }
            if (info == null) Lookup.NotFound else {
                val f = form
                if (f.call == call && (f.infoFromQrz || (f.name.isBlank() && f.qth.isBlank() && f.locator.isBlank()))) {
                    form = f.copy(
                        name = info.fullName, qth = info.city, country = info.country,
                        locator = info.locator.ifBlank { info.position?.let { Geo.latLonToLocator(it) }.orEmpty() },
                        lat = info.lat, lon = info.lon, infoFromQrz = true,
                    )
                }
                Lookup.Found(info)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: QrzException) {
            Lookup.Failed(e.message ?: "Ошибка QRZ.ru")
        } catch (e: Exception) {
            Lookup.Failed(networkError(e))
        }
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

    /** Returns an error message, or null when saved. */
    fun save(): String? {
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
        prefs.lastBand = f.band
        prefs.lastMode = f.mode
        prefs.lastFreq = f.freq
        prefs.lastPower = f.power
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.save(qso) }
            reload()
            _messages.send(Message(if (f.isNew) "Связь с ${f.call} записана" else "Изменения сохранены"))
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
