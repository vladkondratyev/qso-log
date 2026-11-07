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
    // Condition layers: MUF of the ionosondes, D-RAP absorption, the aurora, who heard my callsign.
    val layers = vm.mapLayers
    val prop = vm.prop
    androidx.compose.runtime.LaunchedEffect(layers) { if (layers.isNotEmpty()) vm.refreshPropagation(layers.map { ru.r3xed.qsolog.MAP_LAYER_SOURCE.getValue(it) }.toSet()) }
    val rects = remember(layers, prop) {
        buildList {
            if ("drap" in layers) prop.drap.filter { it.value >= 3 }.forEach { add(MapRect(LatLon(it.lat, it.lon), 4.0, 2.0, androidx.compose.ui.graphics.Color(0xFFDC281E).copy(alpha = (0.15f + it.value.toFloat() / 50).coerceAtMost(0.75f)))) }
            if ("aurora" in layers) prop.aurora.forEach { add(MapRect(LatLon(it.lat, it.lon), 3.0, 2.0, androidx.compose.ui.graphics.Color(0xFF1EC85A).copy(alpha = (0.12f + it.value.toFloat() / 120).coerceAtMost(0.75f)))) }
        }
    }
    val segments = remember(layers, prop, myPos) {
        if ("heard" !in layers || myPos == null) emptyList()
        else prop.heardMe.mapNotNull { r -> ru.r3xed.qsolog.data.Geo.locatorToLatLon(r.receiverLocator)?.let { MapSegment(myPos, it, bandColorOf(r.band ?: "")) } }
    }
    val markers = remember(stations, myPos, myCall, spots, layers, prop) {
        buildList {
            if ("muf" in layers) prop.ionosondes.filter { it.confidence >= 25 }.forEach { io ->
                add(MapMarker(io.point, "%.0f".format(io.mufMhz) + " " + tr("МГц"), mufColorOf(io.mufMhz)))
            }
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
        }
        // Layers: cluster spots, the grey line and the conditions (who hears me, MUF, absorption, aurora).
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.FilterChip(selected = vm.mapSpots, onClick = { vm.changeMapSpots(!vm.mapSpots) }, label = { Text(tr("Споты кластера") + if (vm.mapSpots) " · ${vm.dxSpots.size}" else "") })
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            androidx.compose.material3.FilterChip(selected = vm.mapGreyLine, onClick = { vm.changeMapGreyLine(!vm.mapGreyLine) }, label = { Text(tr("Серая зона")) })
            listOf("heard" to tr("Меня слышат"), "muf" to "MUF", "drap" to tr("Поглощение"), "aurora" to tr("Сияние")).forEach { (k, t) ->
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                androidx.compose.material3.FilterChip(selected = k in layers, onClick = { vm.toggleMapLayer(k) }, label = { Text(t) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (stations.isEmpty() && spots.isEmpty() && layers.isEmpty()) {
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
                    shade = shade, rects = rects, segments = segments,
                )
            }
        }
    }
}

/** DX cluster spots on the map: the amber of the "new" marks. */
private val SpotColor = androidx.compose.ui.graphics.Color(0xFFB07000)

/** A band's colour on the map: low bands warm, high bands cool. */
internal fun bandColorOf(band: String) = androidx.compose.ui.graphics.Color(
    when (band) {
        "160m", "80m" -> 0xFFB03A2E
        "60m", "40m" -> 0xFFD9822B
        "30m", "20m" -> 0xFF2E8B57
        "17m", "15m" -> 0xFF1F77B4
        "12m", "10m" -> 0xFF6A3D9A
        else -> 0xFF555555
    },
)

/** MUF from 7 MHz (red) to 30 MHz and up (violet). */
internal fun mufColorOf(mhz: Double) = androidx.compose.ui.graphics.Color(
    when {
        mhz < 10 -> 0xFFB03A2E
        mhz < 15 -> 0xFFD9822B
        mhz < 20 -> 0xFF2E8B57
        mhz < 26 -> 0xFF1F77B4
        else -> 0xFF6A3D9A
    },
)
