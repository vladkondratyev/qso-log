package ru.r3xed.qsolog.ui

import ru.r3xed.qsolog.Form
import ru.r3xed.qsolog.data.Geo
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** The contact as a few lines of text, to go along with a shared voice note. */
fun qsoText(f: Form, myPosition: ru.r3xed.qsolog.data.LatLon?): String = buildList {
    add("QSO с ${f.call}")
    add("${f.date} ${f.time} UTC")
    add(
        listOf(f.band, f.freq.ifBlank { null }?.let { "$it МГц" }, f.mode).filterNotNull().filter { it.isNotBlank() }
            .joinToString(" · ") + if (f.rstSent.isNotBlank() || f.rstRcvd.isNotBlank()) " · RST ${f.rstSent}/${f.rstRcvd}" else "",
    )
    val who = listOf(f.name, f.qth, f.country).filter { it.isNotBlank() }.joinToString(", ")
    if (who.isNotBlank()) add(who + if (f.locator.isNotBlank()) " · ${f.locator}" else "")
    val them = f.position
    if (myPosition != null && them != null) {
        add((if (f.approxPosition) "≈ " else "") + formatKm(Geo.distanceKm(myPosition, them)) + ", азимут ${Geo.bearing(myPosition, them).toInt()}°")
    }
    if (f.comment.isNotBlank()) add(f.comment)
    add("— ${f.myCall.ifBlank { "QSO-LOG" }}, QSO-LOG")
}.filter { it.isNotBlank() }.joinToString("\n")

/**
 * Desktop counterpart of Android's share sheet: saves the voice note where the user picks, puts the contact as text
 * into a .txt file next to it and into the clipboard, so it can be pasted into a messenger or an e-mail.
 */
fun exportVoiceNote(file: File, text: String, f: Form, say: (String) -> Unit) {
    val base = "QSO_${f.call.replace('/', '-')}_${f.date.replace('.', '-')}_${f.time.replace(":", "")}"
    val dialog = FileDialog(null as Frame?, "Сохранить запись QSO", FileDialog.SAVE)
    dialog.file = "$base.wav"
    dialog.isVisible = true
    val name = dialog.file ?: return
    val dir = dialog.directory ?: return
    val target = File(dir, if (name.lowercase().endsWith(".wav")) name else "$name.wav")
    try {
        file.copyTo(target, overwrite = true)
        File(target.parentFile, target.nameWithoutExtension + ".txt").writeText(text + "\n")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        say("Запись сохранена: ${target.name}. Параметры связи — в ${target.nameWithoutExtension}.txt и в буфере обмена")
    } catch (e: Exception) {
        say("Не удалось сохранить запись: ${e.message}")
    }
}

/**
 * "Отправить" in a saved card. With a voice note: the note, a .txt and the clipboard, as above.
 * Without one: the contact as text in the clipboard, ready to paste into a messenger or an e-mail.
 */
fun shareForm(f: Form, myPosition: ru.r3xed.qsolog.data.LatLon?, audio: File?, say: (String) -> Unit) {
    val text = qsoText(f, myPosition)
    if (audio != null && audio.exists()) exportVoiceNote(audio, text, f, say)
    else {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        say("Связь с ${f.call} скопирована в буфер обмена: вставьте её в мессенджер или письмо")
    }
}
