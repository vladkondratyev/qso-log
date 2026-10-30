package ru.r3xed.qsolog.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

/** The locator taken from the phone's location: the value, how long ago, and a button to ask again. */
@Composable
internal fun AutoLocatorLine(locator: String, fixAt: Long, onRefresh: () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("QTH-локатор"), style = MaterialTheme.typography.labelMedium, color = x.muted)
            Text(locator.ifBlank { "—" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            val min = if (fixAt == 0L) null else (System.currentTimeMillis() - fixAt) / 60_000
            Text(
                when {
                    min == null -> tr("по геолокации: ещё не определён в этот запуск")
                    min < 1 -> tr("по геолокации: только что")
                    else -> tr("по геолокации: %s мин назад", min)
                },
                style = MaterialTheme.typography.bodyMedium, color = x.ok,
            )
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.MyLocation, tr("Определить сейчас")) }
    }
}

/** "Определять локатор по геолокации": asks for the location permission the first time it is turned on. */
@Composable
internal fun AutoLocatorSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x = LocalExtra.current
    val ctx = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { denied = false; onChange(true) } else denied = true
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .toggleable(value = on, role = Role.Checkbox) { want ->
                if (!want) onChange(false)
                else if (listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) onChange(true)
                else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Определять локатор по геолокации"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("При запуске, при выборе станции и при открытии карточки QSO (не чаще раза в 10 минут). Локатор показывается рядом с позывным под логотипом."), style = MaterialTheme.typography.bodyMedium, color = x.muted)
        }
    }
    if (denied) Text(tr("Без разрешения на геолокацию локатор определить нельзя. Разрешить можно в настройках Android."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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
