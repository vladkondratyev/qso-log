package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.OnlineLog
import ru.r3xed.qsolog.tr

/**
 * Upload to an online logbook: which service, only the contacts not sent there yet (the default) or all of them,
 * and whether to mark the cards. [selected] is the number of records picked in the log, null for the whole log of [total].
 * The same file is used by the phone and the desktop; [lotwNote] and [lotwAction] say how LoTW works on each.
 */
@Composable
fun UploadDialog(
    selected: Int?,
    total: Int,
    count: (OnlineLog, onlyNew: Boolean) -> Int,
    problem: (OnlineLog) -> String?,
    busy: OnlineLog?,
    lotwNote: String,
    lotwAction: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (OnlineLog, onlyNew: Boolean, mark: Boolean) -> Unit,
) {
    val x = LocalExtra.current
    var log by remember { mutableStateOf(OnlineLog.entries.firstOrNull { problem(it) == null && it != OnlineLog.LOTW } ?: OnlineLog.LOTW) }
    var onlyNew by remember { mutableStateOf(true) }
    var mark by remember { mutableStateOf(true) }
    val issue = problem(log)
    val n = count(log, onlyNew)
    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        title = { Text(tr("Выгрузка в онлайн-журнал")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (selected != null) tr("Выбрано записей: %s", selected) else tr("Весь журнал: %s записей", total),
                    style = MaterialTheme.typography.bodyLarge,
                )
                // Two rows of two: the names fit on a phone.
                OnlineLog.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { item ->
                            val on = item == log
                            Box(
                                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(1.5.dp, if (on) MaterialTheme.colorScheme.primary else x.line, RoundedCornerShape(12.dp))
                                    .selectable(on, enabled = busy == null, role = Role.RadioButton) { log = item },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                if (log == OnlineLog.LOTW) Text(lotwNote, style = MaterialTheme.typography.bodyMedium, color = x.muted)
                if (issue != null) {
                    Text(issue, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onOpenSettings) { Text(tr("Открыть настройки")) }
                }
                Column {
                    Choice(tr("Только новые для %s: %s", log.title, count(log, true)), onlyNew, busy == null) { onlyNew = true }
                    Choice(
                        if (selected != null) tr("Все выбранные: %s", count(log, false)) else tr("Весь журнал: %s", count(log, false)),
                        !onlyNew, busy == null,
                    ) { onlyNew = false }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(mark, enabled = busy == null, role = Role.Checkbox) { mark = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(mark, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Поставить отметку в карточках: дата и время выгрузки"), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    tr("Отметка видна в карточке связи. По ней программа в следующий раз предложит только новые связи."),
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(log, onlyNew, mark) }, enabled = busy == null && issue == null && n > 0) {
                if (busy != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Отправляю в %s…", busy.title))
                } else {
                    Text(if (log == OnlineLog.LOTW) "$lotwAction ($n)" else tr("Отправить (%s)", n))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = busy == null) { Text(tr("Отмена")) } },
    )
}

@Composable
private fun Choice(text: String, on: Boolean, enabled: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(on, enabled = enabled, role = Role.RadioButton, onClick = onPick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(on, null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
