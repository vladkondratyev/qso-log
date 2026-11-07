package ru.r3xed.qsolog.ui

import ru.r3xed.qsolog.tr
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
    // Layers: DX cluster spots (at the country's centre) and the grey line, redrawn every minute.
    val spots = if (vm.mapSpots) vm.dxSpots else emptyList()
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(vm.mapGreyLine) { while (vm.mapGreyLine) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
    val shade = if (vm.mapGreyLine) androidx.compose.runtime.remember(now) { ru.r3xed.qsolog.data.Sun.night(now) } else null
    val markers = remember(stations, myPos, myCall, spots) {
        buildList {
            spots.distinctBy { it.call }.forEach { s ->
                val e = ru.r3xed.qsolog.data.Cty.lookup(s.call)?.entity ?: return@forEach
                add(MapMarker(LatLon(e.lat, e.lon), s.call + " " + (s.band ?: ""), SpotColor, onClick = vm::openDxCluster))
            }
            stations.forEach { (q, count) ->
                val label = if (count > 1) "${q.call} ×$count" else q.call
                add(MapMarker(LatLon(q.lat!!, q.lon!!), label, StationColor, onClick = { vm.edit(q, from = Pane.Map) }))
            }
            // My station last, so it is drawn on top of the stations around it.
            myPos?.let { add(MapMarker(it, myCall.ifBlank { tr("Я") }, MyColor, home = true)) }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pane = Pane.Empty }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад (Esc)"), Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Карта QSO"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    tr("позывных на карте: %s", stations.size) + if (withoutPosition > 0) tr(" · без координат: %s", withoutPosition) else "",
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
            }
            androidx.compose.material3.FilterChip(selected = vm.mapSpots, onClick = { vm.changeMapSpots(!vm.mapSpots) }, label = { Text(tr("Споты кластера") + if (vm.mapSpots) " · ${vm.dxSpots.size}" else "") })
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            androidx.compose.material3.FilterChip(selected = vm.mapGreyLine, onClick = { vm.changeMapGreyLine(!vm.mapGreyLine) }, label = { Text(tr("Серая зона")) }, modifier = Modifier.padding(end = 12.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (stations.isEmpty() && spots.isEmpty()) {
                Text(
                    tr("В логе пока нет связей с известным QTH. Точки появятся, когда QRZ.ru вернёт координаты абонента или вы впишете его локатор."),
                    style = MaterialTheme.typography.bodyLarge, color = x.muted,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                MarkerMap(
                    markers, path = null, interactive = true, filledLabels = true, fitHint = tr("Показать все станции"),
                    modifier = Modifier.fillMaxSize(),
                    camera = vm.mapCamera, onCamera = { vm.mapCamera = it },
                    shade = shade,
                )
            }
        }
    }
}

/** DX cluster spots on the map: the amber of the "new" marks. */
private val SpotColor = androidx.compose.ui.graphics.Color(0xFFB07000)
