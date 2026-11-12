package ru.r3xed.qsolog

import androidx.compose.runtime.key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import ru.r3xed.qsolog.data.OnlineLog
import ru.r3xed.qsolog.data.Swl
import ru.r3xed.qsolog.data.UiScale
import ru.r3xed.qsolog.ui.QsoTheme
import java.io.File
import java.nio.file.Files

/**
 * The screens changed in 2.9.5, rendered with the demo data for a look during development (not README pictures):
 * ./gradlew :desktop:screenshots -PshotsMain=ru.r3xed.qsolog.Check295Kt -PshotsOut=<folder>
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/check295" }).apply { mkdirs() }
    val data = Files.createTempDirectory("qsolog-check").toFile()
    System.setProperty("qsolog.data", data.absolutePath)
    seed(data)
    // A few contacts uploaded to HAMLOG: the stripe in the list and the table.
    QsoDb(File(data, "qsolog.db")).let { db ->
        db.all().filter { it.call in setOf("R9DEMO", "DL1DEMO", "JA1DEMO") }.forEach { db.save(OnlineLog.HAMLOG.mark(it)) }
    }

    runBlocking {
        withContext(Dispatchers.Main) {
            val state = AppState()
            state.changeLanguage(Lang.RU)
            state.closeWhatsNew()
            val files = Exports({}, {}, {}, {}, {}, { _, _, _, _, _ -> }, {}, { _, _ -> })
            val scene = ImageComposeScene(2048, 1400, Density(2f)) {
                QsoTheme(false, state.uiScale) { key(state.language) { App(state, files) } }
            }
            val t0 = System.nanoTime()
            fun frame() = scene.render(System.nanoTime() - t0)
            suspend fun shot(name: String, waitMs: Long = 1500) {
                repeat((waitMs / 250).toInt()) { frame(); delay(250) }
                File(out, "$name.png").writeBytes(frame().encodeToData(EncodedImageFormat.PNG)!!.bytes)
                println(name)
            }

            shot("01-log-hamlog")
            // Bulk delete: three records picked, the bar with the bin, then the question.
            state.allQsos.take(3).forEach { state.toggleSelected(it.id) }
            shot("02-select-bar", 800)
            state.askDeleteLog()
            shot("03-delete-ask", 800)
            state.logDeleteAsk = null
            state.toggleTableMode()
            shot("04-table-select", 1200)
            state.clearSelection()
            shot("05-table-hamlog", 800)
            state.changeUiScale(UiScale.S130)
            shot("06-table-130", 1200)
            state.toggleTableMode()
            shot("07-log-130", 1200)
            state.changeUiScale(UiScale.S60)
            shot("08-log-60", 1200)
            state.changeUiScale(UiScale.S100)
            state.openSettings()
            shot("09-settings-scale", 1500)

            // The rig panel on the band, then outside any band: the scale stays, grey.
            val rig = fakeRigctld()
            state.changeCat(state.catConfig.copy(enabled = true, rig = ru.r3xed.qsolog.cat.Rigs.RIGCTLD, link = ru.r3xed.qsolog.cat.CatLinkType.NETWORK, host = "127.0.0.1", port = rig.localPort))
            state.pane = Pane.Empty
            state.showRigPanel = true
            shot("10-rig-panel", 3000)
            rigFreq = 15_000_000
            shot("11-rig-out-of-band", 3000)
            state.showRigPanel = false
            rigFreq = null

            // SWL: the station marked as a listener's; its card and the saved report in the log.
            state.updateSettings(state.settings.copy(myCall = "R3A-1234", station = Swl.station(state.settings.station, true)))
            state.openSettings(stations = true)
            shot("12-settings-swl", 1500)
            state.newQso()
            state.setCall("UA9DEMO")
            state.setAdif(Swl.WITH, "R0DEMO")
            shot("13-card-swl", 2000)
            state.trySave()
            state.pane = Pane.Empty
            shot("14-log-swl", 1500)
            state.toggleTableMode()
            shot("15-table-swl", 1200)
        }
    }
    kotlin.system.exitProcess(0)
}
