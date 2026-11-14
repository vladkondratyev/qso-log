package ru.r3xed.qsolog.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.data.Place
import ru.r3xed.qsolog.tr

/**
 * "QTH на карте": when the callbooks gave no QTH, the town is searched by name (OpenStreetMap) and the point picked
 * on the map; [onPick] gets the town's name for QTH and the point (its 6-character locator goes into the card).
 */
@Composable
fun QthPickerDialog(
    initial: String,
    start: LatLon?,
    search: suspend (String) -> List<Place>,
    onPick: (name: String, point: LatLon) -> Unit,
    onDismiss: () -> Unit,
) {
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initial) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(initial) }
    var point by remember { mutableStateOf<LatLon?>(null) }
    // Bumped when a found place is chosen: the map flies there.
    var flyTo by remember { mutableStateOf<Pair<LatLon, Int>?>(null) }

    fun runSearch() {
        val q = query.trim()
        if (q.length < 2 || busy) return
        busy = true; problem = null
        scope.launch {
            try {
                results = search(q)
                if (results.isEmpty()) problem = tr("Ничего не найдено: попробуйте добавить область или страну")
            } catch (e: Exception) {
                problem = tr("Поиск не удался: %s", e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { if (initial.trim().length >= 2) runSearch() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("QTH на карте"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, tr("Закрыть")) }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("Населённый пункт")) },
                    singleLine = true,
                    trailingIcon = {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = ::runSearch) { Icon(Icons.Filled.Search, tr("Найти")) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    shape = RoundedCornerShape(12.dp),
                )
                problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                if (results.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(12.dp)).background(x.field)) {
                    items(results) { p ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                name = p.name; point = p.point; flyTo = p.point to ((flyTo?.second ?: 0) + 1)
                            }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(p.name + "  " + p.locator, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(p.full, style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 2)
                        }
                        HorizontalDivider(color = x.line)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                    PickMap(point, start, flyTo, onTap = { point = it }, modifier = Modifier.fillMaxSize())
                }
                Text(
                    point?.let { tr("Локатор %s · %s", Geo.latLonToLocator(it), name.ifBlank { tr("без названия") }) }
                        ?: tr("Найдите населённый пункт или нажмите на карту"),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(tr("Отмена")) }
                    Button(onClick = { point?.let { onPick(name.ifBlank { query.trim() }, it) } }, enabled = point != null) { Text(tr("Применить")) }
                }
            }
        }
    }
}

/** The osmdroid map of the picker: a tap puts the marker; a chosen search result moves the view to it. */
@Composable
private fun PickMap(point: LatLon?, start: LatLon?, flyTo: Pair<LatLon, Int>?, onTap: (LatLon) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            fillViewport()
            // Centred once it has its real size: before that (and in a dialog that is laid out more than once)
            // osmdroid puts the centre elsewhere.
            val c = GeoPoint(start?.lat ?: 55.75, start?.lon ?: 37.6)
            var centred = false
            addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                if (!centred && r - l > 0 && b - t > 0) {
                    centred = true
                    v.post { controller.setZoom(if (start != null) 6.0 else 4.0); controller.setCenter(c) }
                }
            }
        }
    }
    val marker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); setInfoWindow(null) } }
    val tap = remember { arrayOf(onTap) }
    tap[0] = onTap
    DisposableEffect(map) {
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { tap[0](LatLon(p.latitude, p.longitude)); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    LaunchedEffect(flyTo) {
        flyTo?.first?.let { p -> map.controller.setZoom(11.0); map.controller.setCenter(GeoPoint(p.lat, p.lon)) }
    }
    AndroidView(factory = { map }, modifier = modifier.clipToBounds(), update = { v ->
        v.overlays.remove(marker)
        if (point != null) {
            marker.position = GeoPoint(point.lat, point.lon)
            v.overlays.add(marker)
        }
        v.invalidate()
    })
}

/** The station's photo from QRZ.ru / QRZ.com while the card is filled: loaded from the web, kept nowhere. */
@Composable
fun CallPhoto(url: String, modifier: Modifier = Modifier) {
    // The file as it came, for the full-screen view; the card's thumbnail is decoded small.
    val bytes by produceState<ByteArray?>(null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8_000; c.readTimeout = 12_000
                c.setRequestProperty("User-Agent", "QSO-LOG")
                try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
            } catch (e: Exception) {
                null
            }
        }
    }
    val data = bytes ?: return
    val thumb = remember(data) { decodePhoto(data, 480) } ?: return
    var big by remember { mutableStateOf(false) }
    Image(
        thumb, tr("Фото корреспондента"),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = tr("Открыть фото на весь экран")) { big = true },
        contentScale = ContentScale.Crop,
    )
    if (big) PhotoViewer(data) { big = false }
}

/** [bytes] decoded no smaller than [min] px on the short side (halving while it stays above it). */
private fun decodePhoto(bytes: ByteArray, min: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var sample = 1
    while (o.outWidth / (sample * 2) >= min && o.outHeight / (sample * 2) >= min) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/**
 * The photo over the whole screen: two fingers zoom and move it, a double tap zooms in (or back), a tap or Back
 * closes it.
 */
@Composable
private fun PhotoViewer(bytes: ByteArray, onClose: () -> Unit) {
    val img = remember(bytes) { decodePhoto(bytes, 1600) } ?: return
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val s = (scale * zoom).coerceIn(1f, 8f)
                        scale = s
                        offset = if (s == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { if (scale > 1f) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero } else scale = 2.5f },
                        onTap = { onClose() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                img, tr("Фото корреспондента"),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
                contentScale = ContentScale.Fit,
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 8.dp).size(56.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)) {
                Icon(Icons.Filled.Close, tr("Закрыть"), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}
