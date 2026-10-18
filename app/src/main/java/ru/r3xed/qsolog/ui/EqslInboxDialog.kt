package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.EqslInbox
import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.utc

// The same file is in the phone and the computer app.

/**
 * The eQSL inbox: how many contacts its cards confirmed (they are marked already) and the cards with no contact
 * in the log, all picked, to be added.
 */
@Composable
fun EqslInboxDialog(state: EqslInbox, onOpenSettings: () -> Unit, onAdd: (List<ru.r3xed.qsolog.data.Qso>) -> Unit, onDismiss: () -> Unit) {
    val x = LocalExtra.current
    val missing = (state as? EqslInbox.Done)?.match?.missing.orEmpty()
    var picked by remember(missing) { mutableStateOf(missing.indices.toSet()) }
    AlertDialog(
        onDismissRequest = { if (state != EqslInbox.Loading) onDismiss() },
        icon = { Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(tr("Карточки eQSL")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    EqslInbox.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Загружаю ящик eQSL…"), style = MaterialTheme.typography.bodyLarge)
                    }
                    is EqslInbox.Failed -> {
                        Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        if (state.settings) TextButton(onClick = onOpenSettings) { Text(tr("Открыть настройки")) }
                    }
                    is EqslInbox.Done -> {
                        val m = state.match
                        Text(tr("Подтверждено новых связей: %s", m.confirmed.size), style = MaterialTheme.typography.bodyLarge)
                        if (m.already > 0) Text(tr("Уже были подтверждены: %s", m.already), style = MaterialTheme.typography.bodyLarge)
                        if (m.confirmed.isNotEmpty()) Text(
                            tr("В карточках этих связей стоит отметка «eQSL получена» с датой карточки."),
                            style = MaterialTheme.typography.bodyMedium, color = x.muted,
                        )
                        if (missing.isNotEmpty()) {
                            Text(tr("Карточки без связи в журнале: %s", missing.size), style = MaterialTheme.typography.titleSmall)
                            Text(
                                tr("Связи нет в журнале или у неё другое время (больше 30 минут), диапазон или вид. Отмеченные карточки будут добавлены как новые связи."),
                                style = MaterialTheme.typography.bodyMedium, color = x.muted,
                            )
                            // A first download can bring years of cards: all of them on or off at once.
                            TextButton(onClick = { picked = if (picked.size == missing.size) emptySet() else missing.indices.toSet() }) {
                                Text(if (picked.size == missing.size) tr("Снять все") else tr("Отметить все"))
                            }
                            missing.forEachIndexed { i, q ->
                                val t = utc(q.timeUtc)
                                Row(
                                    Modifier.fillMaxWidth().toggleable(i in picked, role = Role.Checkbox) { picked = if (it) picked + i else picked - i },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(i in picked, null)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(q.call, style = MaterialTheme.typography.titleMedium, fontFamily = Mono)
                                        Text(
                                            "${DATE_FMT.format(t)} ${TIME_FMT.format(t)} UTC · ${q.band} · ${q.mode}",
                                            style = MaterialTheme.typography.bodyMedium, color = x.muted,
                                        )
                                    }
                                }
                            }
                        } else if (m.confirmed.isEmpty() && m.already == 0) {
                            Text(tr("Новых карточек нет"), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (missing.isNotEmpty()) Button(onClick = { onAdd(missing.filterIndexed { i, _ -> i in picked }) }, enabled = picked.isNotEmpty()) {
                Text(tr("Добавить в журнал (%s)", picked.size))
            } else TextButton(onClick = onDismiss, enabled = state != EqslInbox.Loading) { Text(tr("Закрыть")) }
        },
        dismissButton = if (missing.isNotEmpty()) ({ TextButton(onClick = onDismiss) { Text(tr("Не добавлять")) } }) else null,
    )
}
