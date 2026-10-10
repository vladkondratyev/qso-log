package ru.r3xed.qsolog

import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.ui.QsoTheme
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders README screenshots of the desktop app with made-up data (callsigns *DEMO), without opening a window.
 * Run: ./gradlew :desktop:screenshots  →  docs/screenshots/desktop-*.png
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "docs/screenshots" }).apply { mkdirs() }
    val data = Files.createTempDirectory("qsolog-demo").toFile()
    System.setProperty("qsolog.data", data.absolutePath)
    seed(data)
    seedHistory(data)

    runBlocking {
        withContext(Dispatchers.Main) {
            val state = AppState()
            // Russian shots first (the README is in Russian), a few English ones at the end.
            state.changeLanguage(Lang.RU)
            // "Что нового" opens on the first start of a new version: its own shot below, not over every one.
            state.closeWhatsNew()
            val files = Exports({}, {}, {}, {}, {}, { _, _, _, _, _ -> }, {}, { _, _ -> })
            var dark by mutableStateOf(false)
            // Sized in pixels: the default 1024×700 window at 2× (Retina) scale.
            val big = ImageComposeScene(2048, 1400, Density(2f)) { QsoTheme(dark) { key(state.language) { App(state, files) } } }
            // Frames need a clock, or animations (floating field labels, colours) never move past their first frame.
            val t0 = System.nanoTime()
            fun frame() = big.render(System.nanoTime() - t0)

            suspend fun shot(name: String, waitMs: Long = 1500) {
                // Let coroutines (database, tiles) finish and the UI settle.
                repeat((waitMs / 250).toInt()) {
                    frame()
                    delay(250)
                }
                val img = frame()
                File(out, "desktop-$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                println("desktop-$name.png")
            }

            // Mouse wheel over the right pane.
            fun scrollRight(steps: Int, step: Float = 20f) {
                big.sendPointerEvent(PointerEventType.Enter, Offset(1500f, 760f))
                big.sendPointerEvent(PointerEventType.Move, Offset(1500f, 760f))
                repeat(steps) {
                    big.sendPointerEvent(PointerEventType.Scroll, Offset(1500f, 760f), scrollDelta = Offset(0f, step))
                    frame()
                }
            }

            val log = { state.allQsos }
            shot("01-log")

            state.newQso(audio = "demo_new.wav") // deleted again when this unsaved card closes
            state.setCall("R9DEMO")
            shot("02-new-qso")
            state.closeEditor()

            state.edit(log().first { it.call == "R9DEMO" })
            shot("03-card", 1000)
            // The whole card fits now: the same view once the map tiles are in.
            shot("04-card-map", 6000)
            state.showKeys = true
            shot("18-keys", 500)
            state.showKeys = false
            scrollRight(12)
            shot("05-all-fields")
            // The very bottom of the card: when the contact went into ADIF, CSV and contest-report files.
            scrollRight(400)
            shot("12-export-marks")

            state.openMap()
            shot("06-map", 8000)

            state.pane = Pane.Empty
            state.sort(SortBy.CALL)
            log().filter { it.call == "4K4DEMO" || it.call == "DL1DEMO" }.forEach { state.toggleSelected(it.id) }
            shot("07-select")
            state.clearSelection()
            state.sort(SortBy.DATE)

            state.openExport(ExportFormat.ADIF, selectedOnly = false)
            shot("13-export-dialog")
            state.closeExport()

            // The search narrowed to one station: the bands it was worked on.
            state.search("R9DEMO")
            shot("14-search-bands", 1000)
            state.search("")

            // Contest mode: the simplified card, a callsign typed, the received number waiting.
            state.changeContestMode(true)
            // A minitest from the contest book, in its third tour: the card shows the tour and the time left.
            val start = System.currentTimeMillis() / 60_000 * 60_000 - 23 * 60_000
            state.saveContest(ru.r3xed.qsolog.data.Contest(name = "Минитест DEMO", code = "MINI-DEMO", start = start, tourMinutes = 10, tourCount = 12, dupesPerTour = true))
            state.saveContest(ru.r3xed.qsolog.data.Contest(name = "Кубок DEMO", code = "CUP-DEMO", start = start + 7 * 86_400_000L, end = start + 7 * 86_400_000L + 4 * 3_600_000L))
            state.activateContest(state.contests.first().id)
            state.addQso()
            state.setCall("SP3DEMO")
            state.setAdif(ru.r3xed.qsolog.data.ContestMode.RCVD, "MO69")
            shot("15-contest", 2000)
            state.closeEditor()
            state.openContests()
            shot("22-contests", 1000)
            state.changeContestMode(false)

            state.openDashboard()
            shot("16-dashboard", 2000)
            scrollRight(60)
            shot("17-dashboard-more", 1500)
            state.pane = Pane.Empty

            // Search history: stations looked up but not logged, one starred; the list, all on the map, one entry.
            state.changeHistoryOn(true)
            state.openHistory()
            shot("19-history", 1000)
            state.historyMap = true
            shot("20-history-map", 6000)
            state.openHistoryEntry(state.historyEntries.first { it.call == "VK2DEMO" })
            shot("21-history-entry", 6000)
            state.pane = Pane.Empty

            state.pane = Pane.Settings
            shot("08-settings")
            // Scroll the settings column to the bottom: version and project link.
            scrollRight(700)
            shot("09-about")
            state.showWhatsNew = true
            shot("23-whats-new", 1000)
            state.closeWhatsNew()

            dark = true
            state.edit(log().first { it.call == "R9DEMO" })
            shot("10-dark", 3000)

            dark = false
            state.closeEditor()
            state.changeLanguage(Lang.EN)
            shot("en-01-log")
            state.newQso(audio = "demo_new.wav")
            state.setCall("R9DEMO")
            shot("en-02-new-qso")
            state.closeEditor()
            state.pane = Pane.Settings
            shot("en-08-settings")
            big.close()
        }
    }
    data.deleteRecursively()
    kotlin.system.exitProcess(0)
}

private val MY = LatLon(55.77, 37.62) // KO85ts, Moscow

private fun seed(dir: File) {
    val today = LocalDate.now(ZoneOffset.UTC)
    fun at(daysAgo: Long, hhmm: String) = today.minusDays(daysAgo).atTime(LocalTime.parse(hhmm)).toInstant(ZoneOffset.UTC).toEpochMilli()
    fun qso(call: String, t: Long, band: String, mode: String, freq: String, rst: Pair<String, String>, name: String, qth: String, country: String, pos: LatLon?, audio: String = "", pending: Boolean = false) =
        Qso(
            call = call, timeUtc = t, band = band, mode = mode, freqMhz = freq, rstSent = rst.first, rstRcvd = rst.second,
            name = name, qth = qth, country = country, locator = pos?.let { Geo.latLonToLocator(it) }.orEmpty(),
            lat = pos?.lat, lon = pos?.lon,
            distanceKm = pos?.let { Geo.distanceKm(MY, it) }, bearing = pos?.let { Geo.bearing(MY, it) },
            power = "100", myCall = "UA3DEMO", myLocator = "KO85ts", audio = audio, pendingLookup = pending,
            adif = mapOf("MY_RIG" to "IC-7300", "MY_ANTENNA" to "Delta"),
        )
    val ssb = "59" to "59"
    val cw = "599" to "579"
    val db = QsoDb(File(dir, "qsolog.db"))
    listOf(
        qso("R5DEMO", at(0, "21:43"), "20m", "SSB", "", ssb, "", "", "", null, pending = true),
        qso("R9DEMO", at(0, "13:11"), "20m", "SSB", "14.180", ssb, "Сергей", "Екатеринбург", "Россия", LatLon(56.84, 60.61), audio = "demo_note.wav"),
        qso("DL1DEMO", at(0, "10:51"), "40m", "CW", "7.012", cw, "Klaus", "Hamburg", "Germany", LatLon(53.55, 9.99)),
        qso("R3DEMO", at(0, "09:40"), "40m", "SSB", "7.128", ssb, "Александр", "Кубинка", "Россия", LatLon(55.57, 36.70)),
        qso("JA1DEMO", at(1, "20:10"), "20m", "SSB", "14.2595", ssb, "Hiro", "Tokyo", "Japan", LatLon(35.68, 139.69)),
        qso("R9DEMO", at(2, "18:05"), "40m", "CW", "7.021", "599" to "599", "Сергей", "Екатеринбург", "Россия", LatLon(56.84, 60.61)),
        qso("OH2DEMO", at(3, "07:15"), "15m", "FT8", "21.074", "-08" to "-12", "Mikko", "Helsinki", "Finland", LatLon(60.17, 24.94)),
        qso("4K4DEMO", at(4, "20:39"), "20m", "SSB", "14.220", ssb, "Elvin", "Ganja", "Azerbaijan", LatLon(40.68, 46.36)),
        qso("EA5DEMO", at(5, "18:22"), "15m", "CW", "21.025", "599" to "599", "Pepe", "Valencia", "Spain", LatLon(39.47, -0.38)),
        qso("LY2DEMO", at(6, "16:48"), "20m", "SSB", "14.195", ssb, "Tomas", "Vilnius", "Lithuania", LatLon(54.69, 25.28)),
        qso("R6DEMO", at(7, "12:30"), "80m", "SSB", "3.650", "59" to "57", "Олег", "Ростов-на-Дону", "Россия", LatLon(47.23, 39.72)),
        qso("R0DEMO", at(8, "06:02"), "20m", "CW", "14.025", "579" to "559", "Павел", "Владивосток", "Россия", LatLon(43.12, 131.89)),
    ).plus(
        // A short contest this morning: flagged, with the numbers sent and received.
        listOf("UR5DEMO" to "MO69", "OK1DEMO" to "017", "UA9DEMO" to "SV12", "YL2DEMO" to "021").mapIndexed { i, (call, rcvd) ->
            qso(call, at(0, "08:%02d".format(5 + i * 7)), "15m", "CW", "21.0${15 + i * 4}", "599" to "599", "", "", "", null)
                .let { it.copy(adif = it.adif + mapOf("APP_QSOLOG_CONTEST" to "Y", "STX_STRING" to "%03d".format(i + 1), "SRX_STRING" to rcvd)) }
        },
    ).map { q ->
        // Older contacts were already exported: ADIF a day later, CSV and a contest report for some of them.
        val day = LocalDateTime.ofEpochSecond(q.timeUtc / 1000, 0, ZoneOffset.UTC).plusDays(1).withHour(9).withMinute(30)
        var m = q
        if (q.timeUtc < at(0, "00:00")) m = ExportFormat.ADIF.mark(m, day)
        if (q.timeUtc < at(3, "00:00")) m = ExportFormat.CSV.mark(m, day)
        if (q.call == "R9DEMO" && q.timeUtc > at(0, "00:00")) m = ExportFormat.ADIF.mark(ExportFormat.CONTEST.mark(m, day.minusDays(1).withHour(14)), day.minusDays(1).withHour(14))
        m
    }.forEach { db.save(it) }

    // A made-up voice note: a short two-tone beep, 4 seconds.
    val samples = 64_000
    val pcm = ByteArray(samples * 2)
    for (i in 0 until samples) {
        val f = if ((i / 8000) % 2 == 0) 660.0 else 880.0
        val v = (sin(2 * PI * f * i / 16_000) * 6000).toInt()
        pcm[2 * i] = (v and 0xFF).toByte()
        pcm[2 * i + 1] = (v shr 8).toByte()
    }
    val audio = File(dir, "audio").apply { mkdirs() }
    VoiceNotes.writeWav(pcm, File(audio, "demo_note.wav"))
    VoiceNotes.writeWav(pcm, File(audio, "demo_new.wav"))

    val props = java.util.Properties()
    mapOf(
        "my_call" to "UA3DEMO", "my_locator" to "KO85ts", "my_qth" to "Москва", "my_power" to "100",
        "station_MY_RIG" to "IC-7300", "station_MY_ANTENNA" to "Delta", "last_band" to "20m", "last_mode" to "SSB", "contest_serial" to "5",
    ).forEach { (k, v) -> props.setProperty(k, v) }
    File(dir, "settings.properties").outputStream().use { props.store(it, null) }
}

/** Made-up stations for the search history screenshots (fictional callsigns, places approximate). */
private fun seedHistory(dir: File) {
    val h = ru.r3xed.qsolog.data.SearchHistory(File(dir, "search_history.json"))
    val now = System.currentTimeMillis()
    val hour = 3_600_000L
    listOf(
        ru.r3xed.qsolog.data.SearchEntry("VK2DEMO", name = "Jack", qth = "Sydney", country = "Australia", locator = "QF56od", source = ru.r3xed.qsolog.data.SearchSource.QRZ_COM, searchedAt = now - 2 * hour, favorite = true, adif = mapOf("CQZ" to "30", "ITUZ" to "59")),
        ru.r3xed.qsolog.data.SearchEntry("UA0DEMO", name = "Олег", qth = "Хабаровск", country = "Россия", locator = "PN78lk", source = ru.r3xed.qsolog.data.SearchSource.QRZ_RU, searchedAt = now - hour, adif = mapOf("STATE" to "HK", "CNTY" to "HK-01")),
        ru.r3xed.qsolog.data.SearchEntry("EA8DEMO", country = "Canary Islands", qth = "Canary Islands", lat = 28.3, lon = -15.6, source = ru.r3xed.qsolog.data.SearchSource.HAMQTH, searchedAt = now - 3 * hour, adif = mapOf(ru.r3xed.qsolog.data.HamQth.POSITION_FIELD to ru.r3xed.qsolog.data.HamQth.POSITION_REGION)),
        ru.r3xed.qsolog.data.SearchEntry("W1DEMO", name = "John", qth = "Boston", country = "United States", locator = "FN42kj", source = ru.r3xed.qsolog.data.SearchSource.QRZ_COM, searchedAt = now - 26 * hour, note = "QSL via LoTW"),
        ru.r3xed.qsolog.data.SearchEntry("R2DEMO", name = "Пётр", qth = "Калининград", country = "Россия", locator = "KO04gq", source = ru.r3xed.qsolog.data.SearchSource.QRZ_RU_SITE, searchedAt = now - 30 * hour),
    ).forEach(h::put)
}
