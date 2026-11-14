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
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.data.OnlineLog
import ru.r3xed.qsolog.data.Swl
import ru.r3xed.qsolog.ui.QsoTheme
import ru.r3xed.qsolog.ui.TableColumn
import java.io.File
import java.nio.file.Files

/**
 * The screens changed in 2.9.6, rendered with the demo data for a look during development (not README pictures):
 * ./gradlew :desktop:screenshots -PshotsMain=ru.r3xed.qsolog.Check296Kt -PshotsOut=<folder>
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/check296" }).apply { mkdirs() }
    val data = Files.createTempDirectory("qsolog-check").toFile()
    System.setProperty("qsolog.data", data.absolutePath)
    seed(data)
    // Uploaded to HAMLOG and exported to ADIF: the dots in the list and the table.
    QsoDb(File(data, "qsolog.db")).let { db ->
        db.all().filter { it.call in setOf("R9DEMO", "DL1DEMO", "JA1DEMO") }.forEach { db.save(OnlineLog.HAMLOG.mark(it)) }
        db.all().take(6).forEach { db.save(ExportFormat.ADIF.mark(it)) }
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

            shot("01-log-dots")
            state.toggleTableMode()
            shot("02-table-dots", 1200)
            state.changeTableWidth(TableColumn.NAME, 220)
            state.changeTableWidth(TableColumn.QTH, 240)
            shot("03-table-widths", 1200)
            state.showKeys = true
            shot("04-keys", 1200)
            state.showKeys = false
            state.showPalette = true
            shot("05-palette", 1200)
            state.showPalette = false

            // A listener's station besides the home one: Ctrl+Shift+N switches, the status bar says SWL.
            state.addStation()
            state.updateSettings(state.settings.copy(myCall = "R3A-1234", station = Swl.station(state.settings.station, true)))
            state.nextStation()
            state.nextStation()
            shot("06-table-swl-status", 1500)
            state.toggleTableMode()
            shot("07-log-swl", 1200)

            // The cluster: the arrows on the second spot.
            val node = fakeCluster()
            System.setProperty("qsolog.dxcluster.connect", "127.0.0.1:${node.localPort}")
            state.changeDxConfig(state.dxConfig.copy(login = "UA3DEMO"))
            state.openDxCluster()
            shot("08-dx", 4000)
            state.dxKey("Down"); state.dxKey("Down")
            shot("09-dx-cursor", 800)
            state.stopDxCluster()
            node.close()
        }
    }
    kotlin.system.exitProcess(0)
}
