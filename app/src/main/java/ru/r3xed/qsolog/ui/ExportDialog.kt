package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.tr

/**
 * Before an ADIF or CSV file is saved: only the contacts not exported to this format yet (the default) or all of them,
 * and whether to mark the exported cards. [selected] is the number of records picked in the log, null for the whole log.
 * For ADIF also the encoding: [utf8] true — UTF-8, false — Windows-1251 (LogHX, UR5EQF).
 * The same file is used by the phone and the desktop.
 */
@Composable
fun ExportDialog(
    format: ExportFormat,
    selected: Int?,
    count: (onlyNew: Boolean) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (onlyNew: Boolean, mark: Boolean) -> Unit,
    utf8: Boolean = true,
    onUtf8: (Boolean) -> Unit = {},
) {
    var onlyNew by remember { mutableStateOf(true) }
    var mark by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Экспорт в %s", format.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExportChoices(format, selected, count, onlyNew, { onlyNew = it }, mark, { mark = it })
                if (format == ExportFormat.ADIF) {
                    Column {
                        Text(tr("Кодировка файла"), style = MaterialTheme.typography.titleSmall)
                        Choice(tr("UTF-8 — для большинства программ и сайтов"), utf8) { onUtf8(true) }
                        Choice(tr("Windows-1251 — для LogHX и UR5EQF"), !utf8) { onUtf8(false) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(onlyNew, mark) }, enabled = count(onlyNew) > 0) { Text(tr("Сохранить файл (%s)", count(onlyNew))) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

/** Which contacts go into the file and whether their cards get the export mark; also used by the contest-report dialog. */
@Composable
fun ExportChoices(
    format: ExportFormat,
    selected: Int?,
    count: (onlyNew: Boolean) -> Int,
    onlyNew: Boolean,
    onOnlyNew: (Boolean) -> Unit,
    mark: Boolean,
    onMark: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected != null) Text(tr("Выбрано записей: %s", selected), style = MaterialTheme.typography.bodyLarge)
        Column {
            Choice(tr("Только новые для %s: %s", format.title, count(true)), onlyNew) { onOnlyNew(true) }
            Choice(if (selected != null) tr("Все выбранные: %s", count(false)) else tr("Весь журнал: %s", count(false)), !onlyNew) { onOnlyNew(false) }
        }
        Row(Modifier.fillMaxWidth().toggleable(mark, role = Role.Checkbox, onValueChange = onMark), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(mark, null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Поставить отметку об экспорте в карточках (дата и время)"), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            tr("«Новые» — связи без отметки об экспорте в %s. Отметка видна внизу карточки связи.", format.title),
            style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
        )
    }
}

/** Colour of each format's dot: in the log row and next to the mark in the card; readable on both themes. */
fun exportColor(format: ExportFormat): Color = when (format) {
    ExportFormat.ADIF -> Color(0xFF2E86DE)
    ExportFormat.CSV -> Color(0xFF2EA043)
    ExportFormat.CONTEST -> Color(0xFFE8890C)
}

/** A small dot of [format]'s colour; hollow when the contact has not been exported to it. */
@Composable
fun ExportDot(format: ExportFormat, on: Boolean = true, size: Dp = 12.dp) {
    val c = exportColor(format)
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (on) Modifier.background(c) else Modifier.border(1.5.dp, c.copy(alpha = 0.5f), CircleShape)),
    )
}

/** The dots of the formats a contact was exported to, for the log row; nothing when it has not been exported. */
@Composable
fun ExportDots(adif: Map<String, String>) {
    val done = ExportFormat.entries.filter { it.isExported(adif) }
    if (done.isEmpty()) return
    Row(
        Modifier.padding(end = 8.dp).semantics { contentDescription = tr("Экспорт: %s", done.joinToString(", ") { it.title }) },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        done.forEach { ExportDot(it, size = 8.dp) }
    }
}

@Composable
private fun Choice(text: String, on: Boolean, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(on, role = Role.RadioButton, onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(on, null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
