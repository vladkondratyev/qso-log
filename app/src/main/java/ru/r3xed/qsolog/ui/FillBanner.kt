package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.tr

/** «Дозаполнить данные корреспондентов» is running: how far, and a button to stop it. Nothing when it is not. */
@Composable
fun FillBanner(vm: AppViewModel) {
    val (done, all) = vm.fillProgress ?: return
    Column(Modifier.fillMaxWidth().background(LocalExtra.current.field).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Дозаполнение данных корреспондентов: %s из %s", done, all), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::stopFillMissing) { Text(tr("Стоп")) }
        }
        LinearProgressIndicator(progress = { if (all == 0) 0f else done.toFloat() / all }, modifier = Modifier.fillMaxWidth())
    }
}
