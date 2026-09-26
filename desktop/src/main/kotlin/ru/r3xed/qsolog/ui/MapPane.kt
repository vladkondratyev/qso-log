package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.Pane
import ru.r3xed.qsolog.data.LatLon

// Map tiles stay light in the dark theme too, so labels keep dark fills with white text.
private val StationColor = Color(0xFF0A5C8A)
private val MyColor = Color(0xFF14202B)

/** Map of every station in the log, one point per callsign. Clicking a point opens the latest contact with it. */
@Composable
fun MapPane(vm: AppState) {
    val x = LocalExtra.current
    val all = vm.allQsos
    val stations = remember(all) {
        all.filter { it.lat != null && it.lon != null }
            .groupBy { it.call }
            .map { (_, list) -> list.maxBy { it.timeUtc } to list.size }
    }
    val withoutPosition = remember(all) { all.filter { it.lat == null || it.lon == null }.map { it.call }.distinct().size }
    val myPos = vm.settings.myPosition
    val myCall = vm.settings.myCall
    val markers = remember(stations, myPos, myCall) {
        buildList {
            stations.forEach { (q, count) ->
                val label = if (count > 1) "${q.call} ×$count" else q.call
                add(MapMarker(LatLon(q.lat!!, q.lon!!), label, StationColor, onClick = { vm.edit(q, from = Pane.Map) }))
            }
            // My station last, so it is drawn on top of the stations around it.
            myPos?.let { add(MapMarker(it, myCall.ifBlank { "Я" }, MyColor, home = true)) }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pane = Pane.Empty }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад (Esc)", Modifier.size(30.dp))
            }
            Column {
                Text("Карта QSO", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "позывных на карте: ${stations.size}" + if (withoutPosition > 0) " · без координат: $withoutPosition" else "",
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (stations.isEmpty()) {
                Text(
                    "В логе пока нет связей с известным QTH. Точки появятся, когда QRZ.ru вернёт координаты абонента или вы впишете его локатор.",
                    style = MaterialTheme.typography.bodyLarge, color = x.muted,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                MarkerMap(
                    markers, path = null, interactive = true, filledLabels = true, fitHint = "Показать все станции",
                    modifier = Modifier.fillMaxSize(),
                    camera = vm.mapCamera, onCamera = { vm.mapCamera = it },
                )
            }
        }
    }
}
