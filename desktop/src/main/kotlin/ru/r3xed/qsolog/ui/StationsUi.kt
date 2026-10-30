package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.MyStation
import ru.r3xed.qsolog.data.MyStations
import ru.r3xed.qsolog.tr

/** "Мои станции": one line per station, the one in work outlined; a tap puts another to work. */
@Composable
internal fun StationList(stations: List<MyStation>, activeId: String, onPick: (String) -> Unit, onAdd: () -> Unit) {
    val x = LocalExtra.current
    if (stations.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stations.forEach { st ->
                val on = st.id == activeId
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else x.field)
                        .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else x.field, shape)
                        .clickable(onClickLabel = tr("Взять в работу")) { onPick(st.id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(st.call.ifBlank { "—" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        val d = MyStations.describe(st, tr("локатор по геолокации"))
                        if (d.isNotBlank()) Text(d, style = MaterialTheme.typography.bodyMedium, color = x.muted)
                    }
                    if (on) Text(tr("в работе"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    TextButton(onClick = onAdd) {
        Icon(Icons.Filled.Add, null)
        Text(tr("Добавить станцию"), fontSize = 17.sp, modifier = Modifier.padding(start = 6.dp))
    }
    if (stations.size < 2) Note(tr("Несколько станций — дом, выезд, клубная: у каждой свой позывной, локатор и мощность. Переключаются нажатием на свой позывной под логотипом."))
}

/** "Удалить станцию": asks once more; the contacts made with it stay in the log. */
@Composable
internal fun StationDelete(st: MyStation, onDelete: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    if (!ask) {
        TextButton(onClick = { ask = true }) {
            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
            Text(tr("Удалить станцию %s", st.title.ifBlank { st.call }), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Удалить? Связи с ней останутся в журнале."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { ask = false }) { Text(tr("Нет")) }
            TextButton(onClick = { ask = false; onDelete() }) { Text(tr("Удалить"), color = MaterialTheme.colorScheme.error) }
        }
    }
}
