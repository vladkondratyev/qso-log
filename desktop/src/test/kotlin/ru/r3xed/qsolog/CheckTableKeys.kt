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
import ru.r3xed.qsolog.ui.QsoTheme
import java.io.File
import java.nio.file.Files

/** The table log: a row opened, then ↓ ↓ through the table's order: -PshotsMain=ru.r3xed.qsolog.CheckTableKeysKt */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/checktablekeys" }).apply { mkdirs() }
    val data = Files.createTempDirectory("qsolog-check").toFile()
    System.setProperty("qsolog.data", data.absolutePath)
    seed(data)
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
            suspend fun wait(ms: Long) = repeat((ms / 250).toInt()) { frame(); delay(250) }
            if (!state.tableMode) state.toggleTableMode()
            wait(2000)
            val order = state.logOrder
            val first = state.allQsos.first { it.id == order[3] }
            // As a click on the 4th row does.
            state.logCursor = first.id
            state.edit(first)
            wait(1500)
            println("card has focus after a click: ${state.cardHasFocus}")
            state.stepTableCard(1)
            wait(800)
            state.stepTableCard(1)
            wait(1500)
            val now = state.form.call
            val want = state.allQsos.first { it.id == order[5] }.call
            println("opened ${first.call}, after ↓↓: $now, expected $want — " + if (now == want) "OK" else "WRONG")
            File(out, "table-step.png").writeBytes(frame().encodeToData(EncodedImageFormat.PNG)!!.bytes)
            // Esc, then ↓ on the table: the cursor goes on from that row.
            state.closeEditor()
            state.pane = Pane.Empty
            state.moveLogCursor(1)
            println("cursor after Esc and ↓: " + state.allQsos.first { it.id == state.logCursor }.call + ", expected " + state.allQsos.first { it.id == order[6] }.call)
        }
    }
    kotlin.system.exitProcess(0)
}
