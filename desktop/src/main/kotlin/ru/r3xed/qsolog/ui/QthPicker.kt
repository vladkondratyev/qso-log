package ru.r3xed.qsolog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toComposeImageBitmap
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
        Surface(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(40.dp), shape = RoundedCornerShape(20.dp)) {
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
                    val primary = MaterialTheme.colorScheme.primary
                    val markers = remember(point, name) { listOfNotNull(point?.let { MapMarker(it, name.ifBlank { Geo.latLonToLocator(it) }, primary) }) }
                    // The view moves only when a found place is chosen; a click just moves the marker.
                    val fit = remember(flyTo) { listOf(flyTo?.first ?: start ?: LatLon(55.75, 37.6)) }
                    MarkerMap(
                        markers, null, interactive = true, filledLabels = true, fitHint = tr("Показать точку"),
                        modifier = Modifier.fillMaxSize(), fitTo = fit, onTapMap = { point = it },
                    )
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

/** The station's photo from QRZ.ru / QRZ.com while the card is filled: loaded from the web, kept nowhere. */
@Composable
fun CallPhoto(url: String, modifier: Modifier = Modifier) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8_000; c.readTimeout = 12_000
                c.setRequestProperty("User-Agent", "QSO-LOG")
                try {
                    val bytes = c.inputStream.use { it.readBytes() }
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bmp ?: return
    var big by remember { mutableStateOf(false) }
    Image(
        b, tr("Фото корреспондента"),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = tr("Увеличить фото")) { big = true },
        contentScale = ContentScale.Crop,
    )
    if (big) Dialog(onDismissRequest = { big = false }) {
        Image(
            b, tr("Фото корреспондента"),
            modifier = Modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(16.dp)).clickable { big = false },
            contentScale = ContentScale.Fit,
        )
    }
}
