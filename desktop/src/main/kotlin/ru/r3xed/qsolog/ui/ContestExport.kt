package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * Settings of a contest report before the file is saved: format, CONTEST code from the contest rules,
 * CATEGORY-OPERATOR and LOCATION (RDA). [count] is how many records go into the report.
 */
@Composable
fun ContestExportDialog(defaults: Cabrillo.Header, count: Int, onDismiss: () -> Unit, onConfirm: (Cabrillo.Header) -> Unit) {
    var format by remember { mutableStateOf(defaults.format) }
    var contest by remember { mutableStateOf(defaults.contest) }
    var operator by remember { mutableStateOf(defaults.categoryOperator) }
    var location by remember { mutableStateOf(defaults.location) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отчёт для соревнований") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Записей в отчёте: $count", style = MaterialTheme.typography.bodyLarge)
                Options(
                    listOf(Cabrillo.Format.ERMAK to "ЕРМАК", Cabrillo.Format.CABRILLO to "Cabrillo"),
                    format,
                ) { format = it }
                Text(
                    if (format == Cabrillo.Format.ERMAK) "Для российских соревнований (ermak.srr.ru): UTF-8, кириллица в заголовке, файл .txt."
                    else "Международный формат 3.0: только латиница, файл .cbr.",
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                )
                OutlinedTextField(
                    value = contest,
                    onValueChange = { contest = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' } },
                    label = { Text("Код соревнования (CONTEST)") },
                    placeholder = { Text(if (format == Cabrillo.Format.ERMAK) "например RDXC, R3X-CHAMP" else "например CQ-WW-CW") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Категория", style = MaterialTheme.typography.titleSmall)
                Options(
                    listOf("SINGLE-OP" to "Один оператор", "MULTI-OP" to "Несколько", "CHECKLOG" to "Для контроля"),
                    operator,
                ) { operator = it }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.uppercase() },
                    label = { Text(if (format == Cabrillo.Format.ERMAK) "Район RDA (LOCATION)" else "LOCATION") },
                    placeholder = { Text(if (format == Cabrillo.Format.ERMAK) "например KG03" else "секция, штат") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Контрольные номера берутся из полей карточки «Контрольный номер передан/принят»; если переданного нет, " +
                        "ставится порядковый номер 001, 002… Связи идут по времени, как требует формат.",
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(defaults.copy(format = format, contest = contest.trim(), categoryOperator = operator, location = location.trim()))
            }) { Text("Сохранить файл") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
