package ru.r3xed.qsolog

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import ru.r3xed.qsolog.ui.PhotoViewer
import ru.r3xed.qsolog.ui.QsoTheme
import java.io.File

/** The photo viewer of the card, opened and zoomed with the wheel: ./gradlew :desktop:screenshots -PshotsMain=ru.r3xed.qsolog.CheckPhotoKt -PshotsOut=<folder> */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/checkphoto" }).apply { mkdirs() }
    val img = org.jetbrains.skia.Image.makeFromEncoded(File("../docs/icon.png").readBytes()).toComposeImageBitmap()
    val scene = ImageComposeScene(1600, 1100, Density(2f)) { QsoTheme(false) { PhotoViewer(img) {} } }
    var t = 0L
    fun frame() = scene.render(t).also { t += 100_000_000 }
    repeat(5) { frame() }
    File(out, "1-open.png").writeBytes(frame().encodeToData(EncodedImageFormat.PNG)!!.bytes)
    repeat(6) { scene.sendPointerEvent(PointerEventType.Scroll, Offset(500f, 400f), scrollDelta = Offset(0f, -1f)); frame() }
    File(out, "2-zoomed.png").writeBytes(frame().encodeToData(EncodedImageFormat.PNG)!!.bytes)
    println("ok")
    kotlin.system.exitProcess(0)
}
