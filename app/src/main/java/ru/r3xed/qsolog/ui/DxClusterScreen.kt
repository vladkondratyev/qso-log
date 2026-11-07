package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Cty
import ru.r3xed.qsolog.data.DxCluster
import ru.r3xed.qsolog.data.DxLogIndex
import ru.r3xed.qsolog.data.DxMark
import ru.r3xed.qsolog.data.DxSpot
import ru.r3xed.qsolog.data.DxStatus
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.LatLon
import ru.r3xed.qsolog.tr

/**
 * The DX cluster: spots, newest first, marked against the log (a new country in yellow, worked ones pale). A tap opens
 * the new card for the station and tunes the radio; a long press only opens the card.
 */
@Composable
fun DxClusterScreen(vm: AppViewModel) {
    val x = LocalExtra.current
    BackHandler { vm.closeDxCluster() }
    DisposableEffect(Unit) {
        vm.dxShown()
        onDispose { vm.dxHidden() }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val all = vm.allQsos
    val index by produceState<DxLogIndex?>(null, all) { value = withContext(Dispatchers.Default) { DxLogIndex(all) } }
    val keepMs = vm.dxConfig.keepMinutes * 60_000L
    val spots = vm.dxSpots.filter { it.timeUtc >= now - keepMs }
    val marks = remember(spots, index) { index?.let { ix -> spots.associateWith { ix.mark(it) } } ?: emptyMap() }
    val shown = spots.filter { s ->
        (vm.dxBand == null || s.band == vm.dxBand) &&
            (vm.dxGroups.isEmpty() || DxCluster.group(s.mode) in vm.dxGroups) &&
            (!vm.dxOnlyNew || marks[s] in setOf(DxMark.NEW_COUNTRY, DxMark.NEW_REGION, DxMark.NEW_BAND, DxMark.NEW_MODE))
    }
    var spotDialog by remember { mutableStateOf(false) }
    var commandDialog by remember { mutableStateOf(false) }
    var settingsDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeDxCluster, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(tr("DX-кластер"), style = MaterialTheme.typography.headlineSmall)
                DxStatusLine(vm.dxStatus, spots.size)
            }
            IconButton(onClick = { settingsDialog = true }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Settings, tr("Настройки кластера"), Modifier.size(26.dp))
            }
        }
        // Bands that have spots (and the one picked), then the mode groups and "only new".
        val bands = remember(spots, vm.dxBand) { BANDS.filter { b -> b == vm.dxBand || spots.any { it.band == b } } }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DxPill(tr("Все"), vm.dxBand == null) { vm.dxBand = null }
            bands.forEach { b -> DxPill(b.removeSuffix("m"), vm.dxBand == b) { vm.dxBand = if (vm.dxBand == b) null else b } }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("CW", "SSB", "DIGI").forEach { g ->
                DxPill(g, g in vm.dxGroups) { vm.dxGroups = if (g in vm.dxGroups) vm.dxGroups - g else vm.dxGroups + g }
            }
            DxPill(tr("Только новые"), vm.dxOnlyNew) { vm.dxOnlyNew = !vm.dxOnlyNew }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val st = vm.dxStatus
                    when {
                        st is DxStatus.Connecting || (st is DxStatus.Connected && spots.isEmpty()) -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(tr("Жду споты…"), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                        }
                        st is DxStatus.Failed -> {
                            Text(st.message, style = MaterialTheme.typography.bodyLarge)
                            Text(tr("Программа подключится снова сама. Сервер и позывной для входа — в настройках кластера."), style = MaterialTheme.typography.bodyMedium, color = x.muted)
                            OutlinedButton(onClick = { settingsDialog = true }) { Text(tr("Настройки кластера")) }
                        }
                        spots.isNotEmpty() -> Text(tr("Для этих фильтров спотов нет. Снимите фильтр диапазона или вида связи."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                        else -> Text(tr("Спотов пока нет."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.call + "|" + it.khz + "|" + it.timeUtc }) { s ->
                        DxSpotRow(s, marks[s] ?: DxMark.NONE, now, vm.settings.myPosition, onTap = { vm.workSpot(s) }, onHold = { vm.workSpot(s, tune = false) })
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val connected = vm.dxStatus is DxStatus.Connected
            OutlinedButton(onClick = { spotDialog = true }, enabled = connected, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text(tr("Свой спот"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { commandDialog = true }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text(tr("Команда"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (spotDialog) DxSpotDialog(vm) { spotDialog = false }
    if (commandDialog) DxCommandDialog(vm) { commandDialog = false }
    if (settingsDialog) {
        AlertDialog(
            onDismissRequest = { settingsDialog = false },
            title = { Text(tr("Настройки кластера")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DxClusterSettings(vm.dxConfig, vm.settings.myCall, vm::changeDxConfig)
                }
            },
            confirmButton = { TextButton(onClick = { settingsDialog = false }) { Text(tr("Готово")) } },
        )
    }
}

@Composable
private fun DxStatusLine(st: DxStatus, count: Int) {
    val x = LocalExtra.current
    val (dot, text) = when (st) {
        is DxStatus.Connected -> x.ok to (st.server + " · " + tr("спотов: %s", count))
        is DxStatus.Connecting -> x.muted to tr("подключаюсь к %s…", st.server)
        is DxStatus.Failed -> MaterialTheme.colorScheme.error to tr("нет связи с кластером")
        DxStatus.Off -> x.muted to tr("не подключён")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DxPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val x = LocalExtra.current
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else x.field)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** "1 мин", "2 ч": how long ago the station was spotted. */
internal fun spotAge(timeUtc: Long, now: Long): String {
    val min = ((now - timeUtc) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> tr("сейчас")
        min < 60 -> tr("%s мин", min)
        else -> tr("%s ч", min / 60)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DxSpotRow(s: DxSpot, mark: DxMark, now: Long, me: LatLon?, onTap: () -> Unit, onHold: () -> Unit) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(14.dp)
    val newCountry = mark == DxMark.NEW_COUNTRY
    val ink = if (newCountry) x.hlInk else MaterialTheme.colorScheme.onSurface
    val sub = if (newCountry) x.hlInk else x.muted
    val entity = remember(s.call) { Cty.lookup(s.call)?.entity }
    Column(
        Modifier.fillMaxWidth().alpha(if (mark == DxMark.WORKED) 0.55f else 1f)
            .background(if (newCountry) x.hl else MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, if (newCountry) x.hlBorder else x.line, shape)
            .combinedClickable(onClick = onTap, onLongClick = onHold, onLongClickLabel = tr("Открыть карточку без перестройки трансивера"))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format(java.util.Locale.ROOT, "%.1f", s.khz) + " · " + s.mode,
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (newCountry) x.hlInk else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(spotAge(s.timeUtc, now), fontFamily = Mono, fontSize = 13.sp, color = sub)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            DxTag(mark, s)
        }
        val km = if (me != null && entity != null) formatKm(Geo.distanceKm(me, LatLon(entity.lat, entity.lon))) else null
        val line = listOfNotNull(entity?.name, km?.let { "≈ $it" }, s.comment.ifBlank { null }, tr("от %s", s.spotter)).joinToString(" · ")
        Text(line, style = MaterialTheme.typography.bodyMedium, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DxTag(mark: DxMark, s: DxSpot) {
    val x = LocalExtra.current
    val (text, bg, fg) = when (mark) {
        DxMark.NEW_COUNTRY -> Triple(tr("новая страна"), x.hl, x.hlInk)
        DxMark.NEW_REGION -> Triple(tr("новая область"), x.hl, x.hlInk)
        DxMark.NEW_BAND -> Triple(tr("новый на %s", s.band.orEmpty()), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        DxMark.NEW_MODE -> Triple(tr("новый вид"), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        DxMark.WORKED -> Triple(tr("✓ в журнале"), x.okBg, x.ok)
        DxMark.NONE -> return
    }
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg)
            .then(if (mark == DxMark.NEW_COUNTRY) Modifier.border(1.dp, x.hlBorder, RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** "Свой спот": the station, its frequency (the radio's, if CAT is on) and a comment go to the cluster. */
@Composable
private fun DxSpotDialog(vm: AppViewModel, onClose: () -> Unit) {
    var call by remember { mutableStateOf("") }
    var khz by remember { mutableStateOf(vm.rig?.freqHz?.let { String.format(java.util.Locale.ROOT, "%.1f", it / 1000.0) }.orEmpty()) }
    var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Свой спот")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(call, { call = it.uppercase().trim() }, label = { Text(tr("Позывной")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(khz, { khz = it }, label = { Text(tr("Частота, кГц")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(comment, { comment = it }, label = { Text(tr("Комментарий (UP 2, 599…)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(tr("Спот увидят все, кто подключён к кластеру. Отправляйте только станции, которые слышите сами."), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (vm.dxSpot(call, khz, comment)) onClose() else error = tr("Проверьте позывной и частоту (например 14025.0)")
            }) { Text(tr("Отправить")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Отмена")) } },
    )
}

/** "Команда": what the node has said and a line to send it a command (sh/dx 50, set/filter…). */
@Composable
private fun DxCommandDialog(vm: AppViewModel, onClose: () -> Unit) {
    var line by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(vm.dxLines.size) { scroll.scrollTo(scroll.maxValue) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Команда кластеру")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(10.dp)).background(LocalExtra.current.field)
                        .verticalScroll(scroll).padding(10.dp),
                ) {
                    if (vm.dxLines.isEmpty()) Text(tr("Сервер пока ничего не прислал."), fontSize = 13.sp, color = LocalExtra.current.muted)
                    vm.dxLines.forEach { Text(it, fontFamily = Mono, fontSize = 12.sp) }
                }
                OutlinedTextField(line, { line = it }, label = { Text(tr("Команда, например sh/dx 50")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (vm.dxSend(line)) line = "" }, enabled = line.isNotBlank() && vm.dxStatus is DxStatus.Connected) { Text(tr("Отправить")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Закрыть")) } },
    )
}
