package ru.r3xed.qsolog.ui

import ru.r3xed.qsolog.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.Cabrillo
import ru.r3xed.qsolog.data.Contest
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import ru.r3xed.qsolog.data.ExportFormat

/**
 * Settings of a contest report before the file is saved: format, CONTEST code from the contest rules,
 * CATEGORY-OPERATOR and LOCATION (RDA); then, as for every export, only new contacts or all and the export mark.
 * [selected] is the number of records picked in the log, null for the whole log.
 */
@Composable
fun ContestExportDialog(
    defaults: Cabrillo.Header,
    selected: Int?,
    /** Some of the contacts were entered in contest mode: offer to take only them (on by default). */
    hasContest: Boolean,
    /** Contests of the book with contacts here, and how many: the report takes one of them or all contest contacts. */
    contests: List<Pair<Contest, Int>> = emptyList(),
    initialContest: String? = null,
    count: (onlyNew: Boolean, contestOnly: Boolean, contestRef: String?) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (Cabrillo.Header, onlyNew: Boolean, mark: Boolean, contestOnly: Boolean, contestRef: String?) -> Unit,
) {
    var format by remember { mutableStateOf(defaults.format) }
    var contest by remember { mutableStateOf(defaults.contest) }
    var operator by remember { mutableStateOf(defaults.categoryOperator) }
    var location by remember { mutableStateOf(defaults.location) }
    var onlyNew by remember { mutableStateOf(true) }
    var mark by remember { mutableStateOf(true) }
    var contestOnly by remember { mutableStateOf(hasContest) }
    var ref by remember { mutableStateOf(initialContest) }
    val n = { onlyNew: Boolean -> count(onlyNew, contestOnly, ref.takeIf { contestOnly }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Отчёт для соревнований")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Options(
                    listOf(Cabrillo.Format.ERMAK to tr("ЕРМАК"), Cabrillo.Format.CABRILLO to "Cabrillo"),
                    format,
                ) { format = it }
                Text(
                    if (format == Cabrillo.Format.ERMAK) tr("Для российских соревнований (ermak.srr.ru): UTF-8, кириллица в заголовке, файл .txt.")
                    else tr("Международный формат 3.0: только латиница, файл .cbr."),
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                )
                OutlinedTextField(
                    value = contest,
                    onValueChange = { contest = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' } },
                    label = { Text(tr("Код соревнования (CONTEST)")) },
                    placeholder = { Text(if (format == Cabrillo.Format.ERMAK) tr("например RDXC, R3X-CHAMP") else tr("например CQ-WW-CW")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("Категория"), style = MaterialTheme.typography.titleSmall)
                Options(
                    listOf("SINGLE-OP" to tr("Один оператор"), "MULTI-OP" to tr("Несколько"), "CHECKLOG" to tr("Для контроля")),
                    operator,
                ) { operator = it }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.uppercase() },
                    label = { Text(if (format == Cabrillo.Format.ERMAK) tr("Район RDA (LOCATION)") else "LOCATION") },
                    placeholder = { Text(if (format == Cabrillo.Format.ERMAK) tr("например KG03") else tr("секция, штат")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr("Контрольные номера берутся из полей карточки «Контрольный номер передан/принят»; если переданного нет, ставится порядковый номер 001, 002… Связи идут по времени, как требует формат."),
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                )
                // Ordinary contacts have no numbers: the report would give them made-up "sent" ones.
                if (hasContest) {
                    Row(Modifier.fillMaxWidth().toggleable(contestOnly, role = Role.Checkbox) { contestOnly = it }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(contestOnly, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Только связи контест-режима (с флажком)"), style = MaterialTheme.typography.bodyLarge)
                    }
                    // The contests from the book: the report of one of them, its CONTEST code filled in.
                    if (contestOnly && contests.isNotEmpty()) {
                        Text(tr("Соревнование"), style = MaterialTheme.typography.titleSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ContestChoice(tr("Все контест-связи"), ref == null) { ref = null }
                            contests.forEach { (c, k) ->
                                ContestChoice("${c.title} ($k)", ref == c.id) {
                                    ref = c.id
                                    if (c.code.isNotBlank()) contest = c.code.uppercase()
                                }
                            }
                        }
                    }
                }
                ExportChoices(ExportFormat.CONTEST, selected, n, onlyNew, { onlyNew = it }, mark, { mark = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(defaults.copy(format = format, contest = contest.trim(), categoryOperator = operator, location = location.trim()), onlyNew, mark, contestOnly, ref.takeIf { contestOnly }) },
                enabled = n(onlyNew) > 0,
            ) { Text(tr("Сохранить файл (%s)", n(onlyNew))) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

/** One line of a single choice: a radio button and the text. */
@Composable
private fun ContestChoice(text: String, on: Boolean, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(on, role = Role.RadioButton, onClick = onPick).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(on, null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A row of equal buttons, the chosen one filled. */
@Composable
private fun <T> Options(items: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val x = LocalExtra.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                    .clickable { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
