package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.cat.CatStatus
import ru.r3xed.qsolog.data.DxStatus
import ru.r3xed.qsolog.tr

/** The bottom line of the window: the radio, the DX cluster, the station in work, today's contacts, the conditions. */
@Composable
fun StatusBar(state: AppState) {
    val x = LocalExtra.current
    LaunchedEffect(Unit) { state.refreshSolar() }
    HorizontalDivider(color = x.line)
    Row(
        Modifier.fillMaxWidth().background(x.field).padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val rig = state.rig
        val cat = state.catStatus
        val (rigText, rigColor) = when {
            rig?.freqHz != null -> listOfNotNull("TRX", rig.freqMhz?.let { "$it MHz" }, rig.radioMode, if (rig.tx == true) "TX" else null).joinToString(" ") to x.ok
            state.catConfig.enabled && cat is CatStatus.Failed -> "TRX: " + tr("нет связи") to MaterialTheme.colorScheme.error
            state.catConfig.enabled -> "TRX: …" to x.muted
            else -> "TRX: " + tr("выкл.") to x.muted
        }
        // TRX off: a click turns CAT on when the radio is set up, else opens its settings; on — the radio panel.
        StatusItem(rigText, rigColor) {
            val c = state.catConfig
            when {
                state.rig != null || (c.enabled && cat !is CatStatus.Failed) -> state.showRigPanel = true
                c.enabled -> state.openSettings(page = ru.r3xed.qsolog.SettingsPage.HARDWARE)
                c.device.isNotBlank() || c.link != ru.r3xed.qsolog.cat.CatLinkType.USB -> { state.changeCat(c.copy(enabled = true)); state.say(tr("CAT включён: %s", c.rig)) }
                else -> state.openSettings(page = ru.r3xed.qsolog.SettingsPage.HARDWARE)
            }
        }
        val (dxText, dxColor) = when (val d = state.dxStatus) {
            is DxStatus.Connected -> tr("Кластер: на связи · спотов %s", state.dxSpots.size) to x.ok
            is DxStatus.Connecting -> tr("Кластер: подключение…") to x.muted
            is DxStatus.Failed -> tr("Кластер: ошибка") to MaterialTheme.colorScheme.error
            DxStatus.Off -> tr("Кластер: выкл.") to x.muted
        }
        StatusItem(dxText + if (state.dxAlerts.enabled) " · 🔔" else "", dxColor, state::openDxCluster)
        state.activation?.let { a -> StatusItem(tr("Активация %s · %s", a.title, state.activationCount), MaterialTheme.colorScheme.tertiary) {} }
        Spacer(Modifier.weight(1f))
        val today = remember(state.allQsos) {
            val day = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
            state.allQsos.count { java.time.Instant.ofEpochMilli(it.timeUtc).atZone(java.time.ZoneOffset.UTC).toLocalDate() == day }
        }
        state.solar?.let { s -> StatusItem(s.short, x.muted, state::openPropagation) }
        StatusItem(tr("Сегодня: %s QSO", today), x.muted, state::openDashboard)
        // The station in work: a click — the list of one's stations to switch (and SWL marked, as in the log).
        var stations by remember { mutableStateOf(false) }
        Box {
            val swl = ru.r3xed.qsolog.data.Swl.isSwl(state.settings.station)
            StatusItem(state.settings.myCall.ifBlank { "—" } + (if (swl) " · SWL" else "") + " ▾", if (swl) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface) { stations = true }
            StationMenu(state, stations) { stations = false }
        }
    }
}

@Composable
private fun StatusItem(text: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** A command of "⌘K": its name, its key ("" — none) and what it does. */
class PaletteCommand(val name: String, val key: String = "", val run: () -> Unit)

/** "⌘K" / Ctrl+K: type a few letters of a command, ↑↓ to choose, Enter to run, Esc to close. */
@Composable
fun CommandPalette(commands: List<PaletteCommand>, onClose: () -> Unit) {
    val x = LocalExtra.current
    var q by remember { mutableStateOf("") }
    var cursor by remember { mutableIntStateOf(0) }
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    val hits = commands.filter { c -> words.all { it in c.name.lowercase() } }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(cursor) { if (hits.isNotEmpty()) list.animateScrollToItem(cursor.coerceIn(0, hits.lastIndex)) }
    fun run(i: Int) { hits.getOrNull(i)?.let { onClose(); it.run() } }
    Dialog(onDismissRequest = onClose) {
        // The field takes the cursor once the dialog is on the screen.
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { focus.requestFocus() } }
        Surface(Modifier.width(560.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it; cursor = 0 },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { cursor = (cursor + 1).coerceAtMost((hits.size - 1).coerceAtLeast(0)); true }
                            Key.DirectionUp -> { cursor = (cursor - 1).coerceAtLeast(0); true }
                            Key.Enter, Key.NumPadEnter -> { run(cursor); true }
                            Key.Escape -> { onClose(); true }
                            else -> false
                        }
                    },
                    placeholder = { Text(tr("Команда: экспорт, кластер, дипломы, масштаб…")) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 8.dp), state = list) {
                    itemsIndexed(hits) { i, c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (i == cursor) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { run(i) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.name, fontSize = 16.sp, fontWeight = if (i == cursor) FontWeight.Bold else null, modifier = Modifier.weight(1f))
                            // The key of the command: run it without the palette next time.
                            if (c.key.isNotBlank()) Text(c.key, fontSize = 14.sp, fontFamily = Mono, color = x.muted, maxLines = 1)
                        }
                    }
                }
                if (hits.isEmpty()) Text(tr("Ничего не найдено"), color = x.muted, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
