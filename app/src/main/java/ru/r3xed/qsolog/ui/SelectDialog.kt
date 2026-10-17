package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.QsoPick
import ru.r3xed.qsolog.tr
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * "Выбрать записи…": picks part of the log by dates (UTC), bands and modes, for an export of just that part.
 * [onPick] gets the ids of the matching contacts; they become the log's selection. The same file is used by the phone
 * and the desktop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectDialog(log: List<Qso>, onPick: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    val x = LocalExtra.current
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var bands by remember { mutableStateOf(emptySet<String>()) }
    var modes by remember { mutableStateOf(emptySet<String>()) }
    val fromDate = QsoPick.parseDate(from)
    val toDate = QsoPick.parseDate(to)
    val fromBad = from.isNotBlank() && fromDate == null
    val toBad = to.isNotBlank() && toDate == null
    val ids = if (fromBad || toBad) emptySet() else QsoPick(fromDate, toDate, bands, modes).ids(log)
    val today = LocalDate.now(ZoneOffset.UTC)
    fun period(a: LocalDate?, b: LocalDate?) {
        from = a?.format(DATE_FMT).orEmpty()
        to = b?.format(DATE_FMT).orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Выбрать записи")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Период (UTC)"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PickChip(tr("Сегодня"), fromDate == today && toDate == today) { period(today, today) }
                    PickChip(tr("7 дней"), fromDate == today.minusDays(6) && toDate == null) { period(today.minusDays(6), null) }
                    PickChip(tr("30 дней"), fromDate == today.minusDays(29) && toDate == null) { period(today.minusDays(29), null) }
                    PickChip(tr("Этот год"), fromDate == today.withDayOfYear(1) && toDate == null) { period(today.withDayOfYear(1), null) }
                    PickChip(tr("Всё время"), from.isBlank() && to.isBlank()) { period(null, null) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(tr("С"), from, fromBad, Modifier.weight(1f)) { from = it }
                    DateField(tr("По"), to, toBad, Modifier.weight(1f)) { to = it }
                }
                val logBands = remember(log) { QsoPick.bandsIn(log) }
                if (logBands.size > 1) {
                    Text(tr("Диапазоны"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        logBands.forEach { b -> PickChip(b, b in bands) { bands = if (b in bands) bands - b else bands + b } }
                    }
                }
                val logModes = remember(log) { QsoPick.modesIn(log) }
                if (logModes.size > 1) {
                    Text(tr("Виды связи"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        logModes.forEach { m -> PickChip(m, m in modes) { modes = if (m in modes) modes - m else modes + m } }
                    }
                }
                Text(
                    tr("Ничего не отмечено — подходят все диапазоны и виды. Выбранные записи можно добавить или убрать нажатием, а потом нажать «Экспорт»."),
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
            }
        },
        confirmButton = { Button(onClick = { onPick(ids) }, enabled = ids.isNotEmpty()) { Text(tr("Выбрать (%s)", ids.size)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

@Composable
private fun DateField(label: String, value: String, bad: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, modifier, singleLine = true, isError = bad,
        label = { Text(label) }, placeholder = { Text(tr("дд.мм.гггг")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/** A chip that is on or off: filled when on. */
@Composable
private fun PickChip(text: String, on: Boolean, onClick: () -> Unit) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(10.dp)
    Text(
        text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primary else x.field)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else x.line, shape)
            .toggleable(on, role = Role.Checkbox) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
