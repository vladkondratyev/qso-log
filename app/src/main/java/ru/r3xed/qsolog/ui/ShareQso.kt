package ru.r3xed.qsolog.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ru.r3xed.qsolog.Form
import ru.r3xed.qsolog.data.Geo
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

/** Share sheet with the voice note file and the contact as text (messengers show the text as a caption). */
fun shareVoiceNote(context: Context, file: File, text: String, subject: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        clipData = ClipData.newRawUri(subject, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Отправить запись QSO").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
