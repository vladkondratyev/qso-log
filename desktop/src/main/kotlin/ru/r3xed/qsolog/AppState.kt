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
import ru.r3xed.qsolog.data.CallHistory
import ru.r3xed.qsolog.data.Csv
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.QrzClient
import ru.r3xed.qsolog.data.QrzException
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.StationSettings
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.data.defaultRst
import ru.r3xed.qsolog.data.withDistance
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

enum class Pane { Empty, Edit, Settings }

/** Desktop counterpart of the Android AppViewModel. */
class AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = QsoDb()
    private val prefs = Settings()

    var settings by mutableStateOf(prefs.load()); private set
    private val qrz = QrzClient { settings.qrzLogin to settings.qrzPassword }

    var pane by mutableStateOf(Pane.Empty)
    var query by mutableStateOf(""); private set
    var qsos by mutableStateOf<List<Qso>>(emptyList()); private set
    var total by mutableStateOf(0); private set

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
        scope.launch {
            val pw = withContext(Dispatchers.IO) { prefs.password() }
            if (settings.qrzPassword.isEmpty()) settings = settings.copy(qrzPassword = pw)
        }
    }

    private fun say(text: String, undo: (() -> Unit)? = null) {
        scope.launch { _messages.send(Message(text, undo)) }
    }

    // ---------- log ----------

    fun reload() {
        scope.launch {
            val (list, count) = withContext(Dispatchers.IO) { db.all(query) to db.count() }
            qsos = list
            total = count
        }
    }

    fun search(q: String) {
        query = q
        reload()
    }

    fun delete(qso: Qso) {
        scope.launch {
            withContext(Dispatchers.IO) { db.delete(qso.id) }
            if (form.id == qso.id) pane = Pane.Empty
            reload()
            say("Связь с ${qso.call} удалена") { restore(qso) }
        }
    }

    private fun restore(qso: Qso) {
        scope.launch {
            withContext(Dispatchers.IO) { db.save(qso.copy(id = 0)) }
            reload()
        }
    }

    // ---------- form ----------

    fun newQso() {
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
            power = prefs.lastPower,
            myCall = settings.myCall,
            myLocator = settings.myLocator,
        )
        history = CallHistory(0, null)
        lookup = Lookup.Idle
        formError = null
        editSession++
        pane = Pane.Edit
    }

    fun edit(qso: Qso) {
        val t = utc(qso.timeUtc)
        form = Form(
            id = qso.id, call = qso.call,
            date = DATE_FMT.format(t), time = TIME_FMT.format(t),
            band = qso.band, mode = qso.mode, freq = qso.freqMhz,
            rstSent = qso.rstSent, rstRcvd = qso.rstRcvd,
            name = qso.name, qth = qso.qth, country = qso.country, locator = qso.locator,
            lat = qso.lat, lon = qso.lon, power = qso.power,
            qslSent = qso.qslSent, qslRcvd = qso.qslRcvd, comment = qso.comment,
            myCall = qso.myCall, myLocator = qso.myLocator, adif = qso.adif,
            distanceKm = qso.distanceKm, bearing = qso.bearing,
            createdAt = qso.createdAt,
        )
        lookup = Lookup.Idle
        formError = null
        editSession++
        loadHistory(qso.call)
        pane = Pane.Edit
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
        lookupJob = scope.launch {
            delay(900) // wait until typing pauses; QRZ.ru allows one request per 3 s
            runLookup(call)
        }
    }

    fun retryLookup() {
        lookupJob?.cancel()
        lookupJob = scope.launch { runLookup(form.call) }
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
        } catch (e: CancellationException) {
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
        scope.launch {
            history = if (call.length < 3) CallHistory(0, null)
            else withContext(Dispatchers.IO) { db.history(call, excludeId = id) }
        }
    }

    fun trySave() {
        formError = save()
    }

    /** Returns an error message, or null when saved. */
    private fun save(): String? {
        val f = form
        if (f.call.length < 3) return "Введите позывной"
        val date = try { LocalDate.parse(f.date.trim(), DATE_FMT) } catch (e: Exception) { return "Дата в формате ДД.ММ.ГГГГ" }
        val time = try { LocalTime.parse(f.time.trim(), TIME_FMT) } catch (e: Exception) { return "Время в формате ЧЧ:ММ" }
        val ts = LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()
        // The record's own station, so editing an imported contact keeps its QTH (and does not take today's settings).
        val me = Geo.locatorToLatLon(f.myLocator) ?: settings.myPosition
        val them = f.position
        val qso = Qso(
            id = f.id, call = f.call, timeUtc = ts,
            band = f.band, mode = f.mode, freqMhz = f.freq.replace(',', '.'),
            rstSent = f.rstSent, rstRcvd = f.rstRcvd,
            name = f.name.trim(), qth = f.qth.trim(), country = f.country.trim(), locator = f.locator.trim(),
            lat = them?.lat, lon = them?.lon,
            distanceKm = if (me != null && them != null) Geo.distanceKm(me, them) else f.distanceKm,
            bearing = if (me != null && them != null) Geo.bearing(me, them) else f.bearing,
            power = f.power.trim(), qslSent = f.qslSent, qslRcvd = f.qslRcvd, comment = f.comment.trim(),
            myCall = f.myCall.ifBlank { settings.myCall }, myLocator = f.myLocator.ifBlank { settings.myLocator },
            adif = f.adif,
            createdAt = f.createdAt, updatedAt = System.currentTimeMillis(),
        )
        prefs.lastBand = f.band
        prefs.lastMode = f.mode
        prefs.lastFreq = f.freq
        prefs.lastPower = f.power
        scope.launch {
            withContext(Dispatchers.IO) { db.save(qso) }
            reload()
            say(if (f.isNew) "Связь с ${f.call} записана" else "Изменения сохранены")
        }
        pane = Pane.Empty
        return null
    }

    fun deleteCurrent() {
        val id = form.id
        if (id == 0L) return
        scope.launch {
            val qso = withContext(Dispatchers.IO) { db.get(id) } ?: return@launch
            pane = Pane.Empty
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
            conn.setRequestProperty("User-Agent", "QSOLog/1.1 (+https://github.com/vladkondratyev/qso-log)")
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

    fun exportAdif(file: File) {
        scope.launch {
            val msg = try {
                val count = withContext(Dispatchers.IO) {
                    val list = db.all()
                    file.outputStream().use { Adif.export(list, it, program = "QSO Log", version = "1.1.0") }
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
                    // Voice notes stay on the phone; only their file names travel in ADIF.
                    val rows = r.rows.map { it.copy(audio = "").withDistance(settings.myPosition) }
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
