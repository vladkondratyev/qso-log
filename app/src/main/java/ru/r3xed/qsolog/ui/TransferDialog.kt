package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.tr

/**
 * "Экспорт и импорт…" from the ⋮ menu: every file format in one place — what used to be six buttons deep in the
 * settings. [onExport] opens the format's own export dialog; the imports open the file picker.
 */
@Composable
fun TransferDialog(
    total: Int,
    onExport: (ExportFormat) -> Unit,
    onImportAdif: () -> Unit,
    onImportCsv: () -> Unit,
    onImportContest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val x = LocalExtra.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.ImportExport, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(tr("Экспорт и импорт")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Экспорт журнала (записей: %s)", total), style = MaterialTheme.typography.titleSmall)
                Choice("ADIF", tr("для LogHX, UR5EQF, QRZ.com, LoTW"), Icons.Filled.FileUpload) { onExport(ExportFormat.ADIF) }
                Choice("CSV", tr("таблица для Excel и Google Таблиц"), Icons.Filled.FileUpload) { onExport(ExportFormat.CSV) }
                Choice(tr("ЕРМАК / Cabrillo"), tr("отчёт для соревнований"), Icons.Filled.FileUpload) { onExport(ExportFormat.CONTEST) }
                Text(tr("Импорт в журнал"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Choice("ADIF", tr("файл .adi или .adif"), Icons.Filled.FileDownload, onImportAdif)
                Choice("CSV", tr("таблица с разделителем «;»"), Icons.Filled.FileDownload, onImportCsv)
                Choice(tr("ЕРМАК / Cabrillo"), tr("отчёт .txt или .cbr"), Icons.Filled.FileDownload, onImportContest)
                Text(
                    tr("Чтобы выгрузить только часть журнала, выберите записи долгим нажатием и нажмите «Экспорт». Повторы при импорте пропускаются."),
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Закрыть")) } },
    )
}

/** One format: a wide button with the format's name and what it is for. */
@Composable
private fun Choice(title: String, note: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, style = MaterialTheme.typography.titleSmall)
            Text(note, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
    }
}
