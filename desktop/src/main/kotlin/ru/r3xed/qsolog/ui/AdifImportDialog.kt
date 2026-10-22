package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.data.Adif
import ru.r3xed.qsolog.tr
import java.nio.charset.Charset

/**
 * Import of an ADIF file: its encoding (UTF-8 first, as ADIF 3 wants; Windows-1251, KOI8-R, CP866 for older Russian
 * logs) and the first contacts read in it, so a wrong one shows as broken letters before anything is written.
 * The same file is used by the phone and the desktop.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdifImportDialog(name: String, bytes: ByteArray, onImport: (Charset) -> Unit, onDismiss: () -> Unit) {
    val x = LocalExtra.current
    var charset by remember { mutableStateOf(Charsets.UTF_8 as Charset) }
    val utf8 = remember(bytes) { Adif.isUtf8(bytes) }
    val preview = remember(bytes, charset) { Adif.preview(bytes, charset) }
    val count = remember(bytes, charset) { Adif.import(bytes, charset).rows.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Импорт из ADIF")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(tr("Кодировка файла"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Adif.ENCODINGS.forEach { (label, cs) -> PickChip(label, cs == charset) { charset = cs } }
                }
                if (!utf8 && charset == Charsets.UTF_8) Text(
                    tr("Файл не в UTF-8: русские буквы прочитаются неверно. Скорее всего, это Windows-1251 — так пишут LogHX, UR5EQF и другие программы."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
                if (preview.isNotEmpty()) {
                    Text(tr("Первые записи"), style = MaterialTheme.typography.titleSmall)
                    preview.forEach { (call, n, qth) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(call, fontFamily = Mono, fontWeight = FontWeight.Bold)
                            Text(listOf(n, qth).filter { it.isNotBlank() }.joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(tr("Если вместо букв — непонятные знаки, выберите другую кодировку."), style = MaterialTheme.typography.bodyMedium, color = x.muted)
                } else {
                    Text(tr("В файле не найдено связей ADIF."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = { onImport(charset) }, enabled = count > 0) { Text(tr("Импортировать (%s)", count)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}
