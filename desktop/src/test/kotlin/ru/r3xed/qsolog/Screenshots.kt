package ru.r3xed.qsolog

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
import ru.r3xed.qsolog.ui.QsoTheme
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
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

    runBlocking {
        withContext(Dispatchers.Main) {
            val state = AppState()
            val files = Exports({}, {}, {}, {}, {}, {}, {})
            var dark by mutableStateOf(false)
            // Sized in pixels: a 1280×860 window at 2× (Retina) scale.
            val big = ImageComposeScene(2560, 1720, Density(2f)) { QsoTheme(dark) { App(state, files) } }
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
                big.sendPointerEvent(PointerEventType.Enter, Offset(1800f, 900f))
                big.sendPointerEvent(PointerEventType.Move, Offset(1800f, 900f))
                repeat(steps) {
                    big.sendPointerEvent(PointerEventType.Scroll, Offset(1800f, 900f), scrollDelta = Offset(0f, step))
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
            scrollRight(68, step = 1f)
            shot("04-card-map", 6000)
            scrollRight(60)
            shot("05-all-fields")

            state.openMap()
            shot("06-map", 8000)

            state.pane = Pane.Empty
            state.sort(SortBy.CALL)
            log().filter { it.call == "4K4DEMO" || it.call == "DL1DEMO" }.forEach { state.toggleSelected(it.id) }
            shot("07-select")
            state.clearSelection()
            state.sort(SortBy.DATE)

            state.pane = Pane.Settings
            shot("08-settings")
            // Scroll the settings column to the bottom: version and project link.
            scrollRight(700)
            shot("09-about")

            dark = true
            state.edit(log().first { it.call == "R9DEMO" })
            shot("10-dark", 3000)
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
    ).forEach { db.save(it) }

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
        "station_MY_RIG" to "IC-7300", "station_MY_ANTENNA" to "Delta", "last_band" to "20m", "last_mode" to "SSB",
    ).forEach { (k, v) -> props.setProperty(k, v) }
    File(dir, "settings.properties").outputStream().use { props.store(it, null) }
}
