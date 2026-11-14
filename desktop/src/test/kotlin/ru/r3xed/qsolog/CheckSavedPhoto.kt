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
import ru.r3xed.qsolog.data.QsoPhotos
import ru.r3xed.qsolog.ui.QsoTheme
import java.io.File
import java.nio.file.Files

/**
 * A saved contact and a search history entry with the photo kept on this device:
 * ./gradlew :desktop:screenshots -PshotsMain=ru.r3xed.qsolog.CheckSavedPhotoKt -PshotsOut=<folder>
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/checksavedphoto" }).apply { mkdirs() }
    val data = Files.createTempDirectory("qsolog-check").toFile()
    System.setProperty("qsolog.data", data.absolutePath)
    seed(data)
    seedHistory(data)
    val pic = File("../docs/icon.png").readBytes()
    val db = QsoDb(File(data, "qsolog.db"))
    val q = db.all().first { it.call == "DL1DEMO" }
    val uid = q.uid.ifBlank { error("no uid") }
    QsoPhotos(File(data, "photos")).save(uid, pic)
    QsoPhotos(File(File(data, "photos"), "history")).save("VK2DEMO", pic)

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
            repeat(10) { frame(); delay(250) } // the log loads
            state.edit(state.allQsos.first { it.call == "DL1DEMO" })
            shot("1-saved-card", 2500)
            state.pane = Pane.Empty
            state.changeHistoryOn(true)
            state.openHistory()
            state.openHistoryEntry(state.historyEntries.first { it.call == "VK2DEMO" })
            shot("2-history-entry", 3000)
        }
    }
    kotlin.system.exitProcess(0)
}
