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
import ru.r3xed.qsolog.data.Contest
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.ui.QsoTheme
import java.io.File
import java.nio.file.Files

/**
 * 3.0.x: "Загрузить фото" in a saved card, the wider log (dragged divider), the stars and the contest participants:
 * ./gradlew :desktop:screenshots -PshotsMain=ru.r3xed.qsolog.Check30Kt -PshotsOut=<folder>
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/check30" }).apply { mkdirs() }
    val data = Files.createTempDirectory("qsolog-check").toFile()
    System.setProperty("qsolog.data", data.absolutePath)
    seed(data)
    File(data, "settings.properties").appendText("\nqrz_login=demo\n")
    // Two contest contacts of an earlier year and one now: the list shows three stations, one of them worked.
    val db = QsoDb(File(data, "qsolog.db"))
    val now = System.currentTimeMillis()
    fun c(call: String, t: Long, sent: String, rcvd: String, band: String) = ru.r3xed.qsolog.data.Qso(
        call = call, timeUtc = t, band = band, mode = "CW", rstSent = "599", rstRcvd = "599",
        adif = mapOf(ContestMode.FIELD to "Y", ContestMode.SENT to sent, ContestMode.RCVD to rcvd, Contest.CODE to "RDAC"),
    )
    db.insert(c("RA3DEMO", now - 400L * 86_400_000, "KG-15", "MO-05", "40m"))
    db.insert(c("RK9DEMO", now - 400L * 86_400_000, "KG-15", "SV-30", "40m"))
    db.insert(c("UA1DEMO", now - 60_000, "KG-15", "LO-02", "40m"))

    runBlocking {
        withContext(Dispatchers.Main) {
            val state = AppState()
            state.changeLanguage(Lang.RU)
            state.closeWhatsNew()
            val files = Exports({}, {}, {}, {}, {}, { _, _, _, _, _ -> }, {}, { _, _ -> })
            val scene = ImageComposeScene(2560, 1600, Density(2f)) {
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
            state.changeLogPaneWidth(560, keep = false)
            state.edit(state.allQsos.first { it.call == "DL1DEMO" })
            shot("1-saved-card-get-photo", 2500)
            state.pane = Pane.Empty
            state.toggleContestMode()
            state.addQso()
            state.setCall("A")
            shot("2-contest-participants", 2500)
            state.setCall("")
            shot("3-contest-participants-all", 1500)
        }
    }
    kotlin.system.exitProcess(0)
}
