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

/**
 * 2.9.7: the rig panel's line under the frequency with S3, S8 and S9+40 — the hint must stay in place.
 * ./gradlew :desktop:screenshots -PshotsMain=ru.r3xed.qsolog.Check297Kt -PshotsOut=<folder>
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/check297" }).apply { mkdirs() }
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
            suspend fun shot(name: String, waitMs: Long = 1500) {
                repeat((waitMs / 250).toInt()) { frame(); delay(250) }
                File(out, "$name.png").writeBytes(frame().encodeToData(EncodedImageFormat.PNG)!!.bytes)
                println(name)
            }
            val rig = fakeRigctld()
            state.changeCat(state.catConfig.copy(enabled = true, rig = ru.r3xed.qsolog.cat.Rigs.RIGCTLD, link = ru.r3xed.qsolog.cat.CatLinkType.NETWORK, host = "127.0.0.1", port = rig.localPort))
            state.pane = Pane.Empty
            state.showRigPanel = true
            rigStrength = -36
            shot("01-rig-s3", 3000)
            rigStrength = -6
            shot("02-rig-s8", 2500)
            rigStrength = 40
            shot("03-rig-s9-40", 2500)
            state.showRigPanel = false
            rig.close()
        }
    }
    System.exit(0)
}
