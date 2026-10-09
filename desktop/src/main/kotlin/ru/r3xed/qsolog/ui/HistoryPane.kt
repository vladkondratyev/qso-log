// Copied from app/…/ui/HistoryScreen.kt by a script (only the map, the window and the state class differ): edit there.
package ru.r3xed.qsolog.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.horizontalScroll
import ru.r3xed.qsolog.data.HistorySort
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.HamQth
import ru.r3xed.qsolog.data.SearchEntry
import ru.r3xed.qsolog.data.SearchSource
import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.utc

/**
 * "История поиска": every station looked up (in the log search or a card), newest first — a cache to look at and to
 * fill a card offline; it never counts as a contact, a station already in the log only shows how many QSOs it has. A tap opens one with the map and the distance,
 * a long press (or the checklist button) starts picking several to delete or edit together.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryPane(vm: AppState) {
    val x = LocalExtra.current
    var picking by remember { mutableStateOf(false) }
    val open = vm.historyOpen
    val selecting = picking || vm.historySelected.isNotEmpty()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (open != null) {
            HistoryEntryView(vm, open, onEdit = { vm.historyEditCalls = listOf(open.call) }, onDelete = { vm.historyDeleteAsk = listOf(open.call) })
            return@Column
        }
        // --- header: the list, or what is picked and what can be done with it ---
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                IconButton(onClick = { picking = false; vm.closeHistory() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Close, tr("Отменить выбор"), Modifier.size(28.dp)) }
                Text(tr("Выбрано: %s", vm.historySelected.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::selectAllHistory) { Icon(Icons.Filled.SelectAll, tr("Выбрать все")) }
                IconButton(onClick = { vm.toggleFavorite(vm.historySelected) }, enabled = vm.historySelected.isNotEmpty()) { Icon(Icons.Filled.StarBorder, tr("В избранное")) }
                IconButton(onClick = { vm.historyEditCalls = vm.historySelected.toList() }, enabled = vm.historySelected.isNotEmpty()) { Icon(Icons.Filled.Edit, tr("Изменить выбранные")) }
                IconButton(onClick = { vm.historyDeleteAsk = vm.historySelected.toList() }, enabled = vm.historySelected.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteOutline, tr("Удалить выбранные"), tint = if (vm.historySelected.isNotEmpty()) MaterialTheme.colorScheme.error else x.muted)
                }
            } else {
                IconButton(onClick = vm::closeHistory, modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(tr("История поиска"), style = MaterialTheme.typography.headlineSmall)
                    Text(tr("записей: %s", vm.historyEntries.size), style = MaterialTheme.typography.bodyMedium, color = x.muted)
                }
                if (vm.historyEntries.isNotEmpty()) {
                    IconButton(onClick = { vm.historyMap = !vm.historyMap }) {
                        Icon(if (vm.historyMap) Icons.AutoMirrored.Filled.List else Icons.Filled.Map, if (vm.historyMap) tr("Списком") else tr("Все на карте"))
                    }
                    IconButton(onClick = { picking = true; vm.historyMap = false }) { Icon(Icons.Filled.Checklist, tr("Выбрать несколько")) }
                }
            }
        }

        // --- the switch: whether looked-up stations are kept at all ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                .background(if (vm.historyOn) MaterialTheme.colorScheme.primaryContainer else x.field)
                .toggleable(value = vm.historyOn, role = Role.Switch) { vm.requestHistoryOn(it) }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Запоминать найденные позывные"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = vm.historyOn, onCheckedChange = null)
        }

        val me = vm.settings.myPosition
        // How many QSOs each station has in the log: shown as a note, the history itself is never counted.
        val inLog = remember(vm.allQsos) { vm.allQsos.groupingBy { it.call }.eachCount() }
        val shown = remember(vm.historyEntries, vm.historyFilter, vm.historySort, me) { vm.historyShown() }
        if (vm.historyEntries.isNotEmpty()) HistoryFilters(vm)
        if (vm.historyEntries.isEmpty()) {
            Text(
                if (vm.historyOn) tr("Пока пусто. Найдите позывной в поиске журнала или наберите его в карточке «Новый QSO»: станция появится здесь вместе с найденными данными. Это только кэш поиска, связью она не считается.")
                else tr("История выключена. Включите её, чтобы программа запоминала все найденные позывные с данными и источником — как кэш поиска, связи из них не появляются. Без интернета карточка заполнится из истории."),
                style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        if (shown.isEmpty()) {
            Text(tr("Ничего не найдено: измените фильтр."), style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        if (vm.historyMap) {
            val markers = shown.mapNotNull { e ->
                e.position?.let { MapMarker(it, if (e.favorite) "★ ${e.call}" else e.call, if (e.favorite) STAR_DARK else Color(0xFF0A5C8A), onClick = { vm.openHistoryEntry(e) }) }
            } + listOfNotNull(me?.let { MapMarker(it, vm.settings.myCall.ifBlank { tr("Я") }, Color(0xFF14202B), home = true) })
            val without = shown.size - markers.count { !it.home }
            if (without > 0) Text(tr("на карте: %s · без координат: %s", markers.count { !it.home }, without), style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp))) {
                MarkerMap(markers, path = null, interactive = true, filledLabels = true, fitHint = tr("Показать все станции"), modifier = Modifier.fillMaxSize())
            }
            return@Column
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(vm.historyCursor) {
            val i = shown.indexOfFirst { it.call == vm.historyCursor }
            if (i >= 0) listState.animateScrollToItem((i - 1).coerceAtLeast(0))
        }
        Text(
            tr("↑ ↓ Enter — открыть · Пробел — отметить · F — избранное · E — изменить · N — новый QSO · M — карта · Delete — удалить"),
            style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.call }) { e ->
                val picked = e.call in vm.historySelected
                val cursor = e.call == vm.historyCursor
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .border(if (picked || cursor) 2.dp else 1.dp, if (picked || cursor) MaterialTheme.colorScheme.primary else x.line, RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = { if (selecting) vm.toggleHistorySelected(e.call) else vm.openHistoryEntry(e) },
                            onLongClick = { vm.toggleHistorySelected(e.call) },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) Checkbox(picked, { vm.toggleHistorySelected(e.call) }, Modifier.padding(end = 6.dp))
                    HistoryRow(e, me, inLog[e.call] ?: 0, Modifier.weight(1f))
                    if (!selecting) IconButton(onClick = { vm.toggleFavorite(listOf(e.call)) }) {
                        Icon(if (e.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (e.favorite) tr("Убрать из избранного") else tr("В избранное"), tint = if (e.favorite) STAR else x.muted)
                    }
                }
            }
        }
    }

    vm.historyDeleteAsk?.let { calls ->
        AlertDialog(
            onDismissRequest = { vm.historyDeleteAsk = null },
            title = { Text(if (calls.size == 1) tr("Удалить %s из истории?", calls[0]) else tr("Удалить из истории записей: %s?", calls.size)) },
            text = { Text(tr("Связи в журнале это не затронет."), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = { vm.historyDeleteAsk = null; picking = false; vm.deleteHistory(calls) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(tr("Удалить")) }
            },
            dismissButton = { TextButton(onClick = { vm.historyDeleteAsk = null }) { Text(tr("Отмена")) } },
        )
    }
    vm.historyEditCalls?.let { calls ->
        HistoryEditDialog(
            single = calls.singleOrNull()?.let { c -> vm.historyEntries.firstOrNull { it.call == c } },
            count = calls.size,
            onDismiss = { vm.historyEditCalls = null },
            onSave = { name, qth, country, locator, note -> vm.historyEditCalls = null; picking = false; vm.editHistory(calls, name, qth, country, locator, note) },
        )
    }
}

/** The star of favourites: the app's amber, readable on both themes. */
private val STAR = Color(0xFFD98B00)
/** Starred labels on the map: dark enough for white text on light tiles. */
private val STAR_DARK = Color(0xFFB07000)

/** One row above the list: order, starred only, source, only with a position, and a text to find. */
@Composable
private fun HistoryFilters(vm: AppState) {
    val x = LocalExtra.current
    val f = vm.historyFilter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Choose(
            tr("Сортировка"), sortTitle(vm.historySort), HistorySort.entries.map { it to sortTitle(it) }, selected = false,
        ) { vm.changeHistorySort(it) }
        Chip(tr("Избранные"), f.favoritesOnly, star = true) { vm.changeHistoryFilter(f.copy(favoritesOnly = !f.favoritesOnly)) }
        Choose(
            tr("Источник"), f.source?.let { sourceTitle(it) } ?: tr("все"),
            listOf<Pair<SearchSource?, String>>(null to tr("все")) + SearchSource.entries.map { it to sourceTitle(it) }, selected = f.source != null,
        ) { vm.changeHistoryFilter(f.copy(source = it)) }
        Chip(tr("С координатами"), f.withPosition) { vm.changeHistoryFilter(f.copy(withPosition = !f.withPosition)) }
    }
    if (vm.historyEntries.size > 5) OutlinedTextField(
        value = f.text, onValueChange = { vm.changeHistoryFilter(f.copy(text = it)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .onFocusChanged { vm.historyTyping = it.isFocused },
        placeholder = { Text(tr("Найти в истории"), style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (f.text.isNotEmpty()) IconButton(onClick = { vm.changeHistoryFilter(f.copy(text = "")) }) { Icon(Icons.Filled.Close, tr("Очистить поиск")) } },
        singleLine = true, textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = x.field, focusedContainerColor = x.field, unfocusedBorderColor = Color.Transparent),
    )
}

private fun sortTitle(s: HistorySort) = when (s) {
    HistorySort.DATE -> tr("сначала новые")
    HistorySort.CALL -> tr("по позывному")
    HistorySort.DISTANCE -> tr("ближе")
    HistorySort.COUNTRY -> tr("по стране")
}

@Composable
private fun Chip(text: String, selected: Boolean, star: Boolean = false, onClick: () -> Unit) {
    val x = LocalExtra.current
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) MaterialTheme.colorScheme.primary else x.field)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // An icon, not "★" in the text: the interface font of the computer has no star.
        if (star) { Icon(Icons.Filled.Star, null, Modifier.size(18.dp), tint = if (selected) fg else STAR); Spacer(Modifier.width(4.dp)) }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = fg)
    }
}

/** A chip with a list: "Источник: все" → one of [options]. */
@Composable
private fun <T> Choose(title: String, value: String, options: List<Pair<T, String>>, selected: Boolean, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Chip("$title: $value ▾", selected) { open = true }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, label) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(label, fontSize = 17.sp) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}

/** One station in the list: callsign, source and date, who and where, and how far. */
@Composable
private fun HistoryRow(e: SearchEntry, me: ru.r3xed.qsolog.data.LatLon?, logged: Int, modifier: Modifier) {
    val x = LocalExtra.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, modifier = Modifier.weight(1f))
            SourceChip(e.source)
        }
        val who = listOf(e.name, e.qth.ifBlank { e.country }).filter { it.isNotBlank() }.joinToString(", ")
        if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val them = e.position
        val approx = e.adif[HamQth.POSITION_FIELD] != null
        Text(
            listOfNotNull(
                if (me != null && them != null) (if (approx) "≈ " else "") + formatKm(Geo.distanceKm(me, them)) else null,
                if (logged > 0) tr("в журнале: %s QSO", logged) else null,
                searchedAt(e.searchedAt),
                e.note.ifBlank { null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Where the data came from, as a small label. */
@Composable
private fun SourceChip(source: SearchSource) {
    val x = LocalExtra.current
    Text(
        sourceTitle(source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer).border(1.dp, x.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

internal fun sourceTitle(s: SearchSource) = if (s == SearchSource.MANUAL) tr("вручную") else if (s == SearchSource.QRZ_RU_SITE) tr("QRZ.ru (сайт)") else s.title

private fun searchedAt(t: Long): String { val d = utc(t); return "${DATE_FMT.format(d)} ${TIME_FMT.format(d)} UTC" }

/** One station: the path on the map with the distance, everything that was found, and what can be done with it. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.HistoryEntryView(vm: AppState, e: SearchEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    val x = LocalExtra.current
    val me = vm.settings.myPosition
    val them = e.position
    val approx = e.adif[HamQth.POSITION_FIELD] != null
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::closeHistory, modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp)) }
        Text(e.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.toggleFavorite(listOf(e.call)) }) {
            Icon(if (e.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (e.favorite) tr("Убрать из избранного") else tr("В избранное"), tint = if (e.favorite) STAR else x.muted)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, tr("Изменить")) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, tr("Удалить из истории"), tint = MaterialTheme.colorScheme.error) }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            me == null -> Text(tr("Укажите свой QTH-локатор в настройках, чтобы видеть расстояние до корреспондента."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
            them == null -> Text(tr("Нет координат корреспондента: впишите его QTH-локатор (✎)."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
            else -> Column(Modifier.fillMaxWidth().border(1.dp, x.line, RoundedCornerShape(16.dp))) {
                Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
                    TileMap(me, them, vm.settings.myCall, e.call, interactive = true, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text((if (approx) "≈ " else "") + formatKm(Geo.distanceKm(me, them)), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    Text(
                        tr("азимут %s°", Geo.bearing(me, them).toInt()) + " · " + tr("обратный %s°", Geo.bearing(them, me).toInt()) + if (approx) tr(" · до центра области") else "",
                        style = MaterialTheme.typography.bodyLarge, color = x.muted,
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val ink = MaterialTheme.colorScheme.onPrimaryContainer
            if (e.name.isNotBlank()) Text(e.name, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = ink)
            val place = listOf(e.qth, e.country).filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotBlank()) Text(place, fontSize = 20.sp, lineHeight = 26.sp, color = ink)
            listOfNotNull(
                e.locator.ifBlank { null }?.let { tr("Локатор") to it },
                e.adif["STATE"]?.let { tr("Область") to it },
                e.adif["CNTY"]?.let { "RDA" to it },
                e.adif["CQZ"]?.let { tr("Зона CQ") to it },
                e.adif["ITUZ"]?.let { tr("Зона ITU") to it },
            ).forEach { (k, v) ->
                Row { Text(k, color = ink.copy(alpha = 0.75f), modifier = Modifier.width(110.dp)); Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, color = ink) }
            }
            if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.padding(top = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Источник:") + " ", style = MaterialTheme.typography.bodyMedium, color = x.muted)
            SourceChip(e.source)
            Spacer(Modifier.width(8.dp))
            Text(searchedAt(e.searchedAt), style = MaterialTheme.typography.bodyMedium, color = x.muted)
        }
        Spacer(Modifier.height(4.dp))
    }
    HorizontalDivider(color = x.line)
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Button(onClick = { vm.newQsoFromHistory(e) }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text(tr("Новый QSO с %s", e.call), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * Editing one entry ([single]: its values in the fields) or [count] entries at once: then the fields start empty and
 * an empty field leaves each entry's own value.
 */
@Composable
private fun HistoryEditDialog(single: SearchEntry?, count: Int, onDismiss: () -> Unit, onSave: (String?, String?, String?, String?, String?) -> Unit) {
    var name by remember { mutableStateOf(single?.name.orEmpty()) }
    var qth by remember { mutableStateOf(single?.qth.orEmpty()) }
    var country by remember { mutableStateOf(single?.country.orEmpty()) }
    var locator by remember { mutableStateOf(single?.locator.orEmpty()) }
    var note by remember { mutableStateOf(single?.note.orEmpty()) }
    val many = single == null
    // One entry: every field as typed; several: only the fields that were filled.
    fun v(s: String) = if (many) s.ifBlank { null } else s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (many) tr("Изменить записей: %s", count) else tr("Изменить %s", single!!.call)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (many) Text(tr("Заполните только то, что нужно поменять у всех выбранных; пустые поля останутся как были."), style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                val f = @Composable { label: String, value: String, set: (String) -> Unit, caps: Boolean ->
                    OutlinedTextField(
                        value, set, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences),
                    )
                }
                f(tr("Имя"), name, { name = it }, false)
                f(tr("QTH (город)"), qth, { qth = it }, false)
                f(tr("Страна"), country, { country = it }, false)
                f(tr("QTH-локатор"), locator, { locator = it }, true)
                f(tr("Заметка"), note, { note = it }, false)
            }
        },
        confirmButton = { Button(onClick = { onSave(v(name), v(qth), v(country), v(locator), v(note)) }) { Text(tr("Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

/** Switching the history on says first what it is for: filling a card from it when there is no internet. */
@Composable
fun HistoryOnDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Включить историю поиска?")) },
        text = {
            Text(
                tr("Программа будет сама запоминать все позывные, которые вы искали в журнале или вводили в карточке: имя, QTH, локатор и откуда они взяты (QRZ.ru, QRZ.com, HamQTH). Это только кэш поиска: связи из него не появляются и в подсчёте QSO он не участвует. Когда нет интернета или сервис не отвечает, история будет использована для автозаполнения карточки новой связи. Записи хранятся только на этом устройстве; удалить их можно в любой момент."),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text(tr("Включить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

/** The menu line: opens the history; the switch at its end turns it on or off right there. */
@Composable
fun HistoryMenuItem(vm: AppState, onOpen: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("История поиска"), fontSize = 18.sp)
                Text("   " + (if (ru.r3xed.qsolog.IS_MAC) "⌘H" else "Ctrl+H"), fontFamily = Mono, fontSize = 14.sp, color = LocalExtra.current.muted)
            }
        },
        leadingIcon = { Icon(Icons.Filled.History, null) },
        trailingIcon = { Switch(checked = vm.historyOn, onCheckedChange = { vm.requestHistoryOn(it) }) },
        onClick = onOpen,
    )
}
