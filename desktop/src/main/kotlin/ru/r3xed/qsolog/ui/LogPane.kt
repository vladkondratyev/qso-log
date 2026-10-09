package ru.r3xed.qsolog.ui

import ru.r3xed.qsolog.tr
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.r3xed.qsolog.SearchLookup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.MenuBook
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.SheetSync
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.APP_VERSION
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.IS_MAC
import ru.r3xed.qsolog.Pane
import ru.r3xed.qsolog.data.ExportFormat
import ru.r3xed.qsolog.SortBy
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.utc
import ru.r3xed.qsolog.data.approxPosition
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogPane(state: AppState, shortcut: String, onExportSelected: () -> Unit, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        if (state.selecting) SelectionBar(state, onExportSelected) else Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    QsoLogo()
                    Text(
                        "v$APP_VERSION",
                        style = MaterialTheme.typography.labelMedium,
                        color = x.muted,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    )
                }
                val me = state.settings.myCall
                if (state.contestMode) {
                    // Contest mode is on until the app is closed: say so where the eye goes first.
                    Text(
                        "CONTEST · " + if (state.contestSentFixed) tr("передаю %s", state.contestSentText.ifBlank { "—" }) else tr("следующий № %s", ContestMode.serial(state.contestSerial)),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    )
                } else {
                    Text((if (me.isNotBlank()) "$me · " else "") + tr("записей: %s", state.total), style = MaterialTheme.typography.bodyMedium, color = x.muted)
                }
            }
            // One round button: the menu with the settings, the dashboard, the map and the reference.
            var menu by remember { mutableStateOf(false) }
            Box {
                Tip(tr("Меню")) {
                    FilledTonalIconButton(onClick = { menu = true }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = tr("Меню"), modifier = Modifier.size(28.dp))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { MenuText(tr("Настройки"), (if (IS_MAC) "⌘" else "Ctrl+") + ",") },
                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        onClick = { menu = false; state.openSettings() },
                    )
                    // Shown once the table is set up in the settings; the sync runs only from here or there.
                    if (SheetSync.isScriptUrl(state.sheetUrl)) {
                        DropdownMenuItem(
                            text = { MenuText(if (state.sheetSyncing) tr("Синхронизация…") else tr("Синхронизировать"), (if (IS_MAC) "⌘" else "Ctrl+") + "R") },
                            leadingIcon = { Icon(Icons.Filled.Sync, null) },
                            enabled = !state.sheetSyncing,
                            onClick = { menu = false; state.syncSheet() },
                        )
                    }
                    DropdownMenuItem(
                        text = { MenuText(tr("Дашборд"), (if (IS_MAC) "⌘" else "Ctrl+") + "D") },
                        leadingIcon = { Icon(Icons.Filled.BarChart, null) },
                        onClick = { menu = false; state.openDashboard() },
                    )
                    DropdownMenuItem(
                        text = { MenuText(tr("Карта QSO"), (if (IS_MAC) "⌘" else "Ctrl+") + "M") },
                        leadingIcon = { Icon(Icons.Filled.Map, null) },
                        onClick = { menu = false; state.openMap() },
                    )
                    HistoryMenuItem(state) { menu = false; state.openHistory() }
                    DropdownMenuItem(
                        text = { Text(tr("Справка и калькуляторы"), fontSize = 17.sp) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                        onClick = { menu = false; state.openReference() },
                    )
                }
            }
        }

        // Ctrl+F (⌘F) puts the cursor here; Enter opens a new card for the station found, Esc clears the search.
        val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        LaunchedEffect(state.searchFocus) { if (state.searchFocus > 0) searchFocus.requestFocus() }
        OutlinedTextField(
            value = state.query,
            onValueChange = state::search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .focusRequester(searchFocus)
                .onFocusChanged { state.searchFocused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        // ↓ from the search to the list: the arrows then walk through the rows.
                        e.key == Key.DirectionDown -> { focusManager.clearFocus(); state.moveLogCursor(0, to = -1); true }
                        (e.key == Key.Enter || e.key == Key.NumPadEnter) && state.query.isNotBlank() -> { state.addQso(); true }
                        e.key == Key.Escape && state.query.isNotEmpty() -> { state.search(""); true }
                        else -> false
                    }
                },
            placeholder = { Text(tr("Поиск: позывной, имя, город"), style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { state.search("") }) { Icon(Icons.Filled.Clear, tr("Очистить поиск")) }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = x.field, focusedContainerColor = x.field,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        // One station found: the bands it was worked on, with the last date on each.
        val station = remember(state.query, state.qsos, state.allQsos) { state.searchedHistory() }
        if (station != null && station.byBand.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                Text(
                    tr("Диапазоны:"), style = MaterialTheme.typography.bodyMedium, color = x.muted,
                    modifier = Modifier.padding(top = 3.dp, end = 8.dp),
                )
                BandChips(station.byBand, ink = MaterialTheme.colorScheme.onSurface, chip = x.field)
            }
        }

        if (state.qsos.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.query.isNotBlank()) {
                    Text(tr("В вашем журнале ничего не найдено"), style = MaterialTheme.typography.titleLarge)
                    // A callsign-like search goes on to QRZ.ru; a found station opens a new card by itself.
                    when (val s = state.searchLookup) {
                        is SearchLookup.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(tr("Ищу %s на QRZ.ru…", s.call), style = MaterialTheme.typography.bodyLarge)
                        }
                        is SearchLookup.NotFound -> Text(tr("На QRZ.ru позывного %s тоже нет.", s.call), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                        SearchLookup.NoAccount -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("Чтобы искать позывные на QRZ.ru, укажите учётную запись XML API."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                            TextButton(onClick = { state.openSettings() }) { Text(tr("Открыть настройки")) }
                        }
                        is SearchLookup.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.message, style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.weight(1f))
                            TextButton(onClick = state::retrySearchLookup) { Text(tr("Повторить")) }
                        }
                        SearchLookup.Idle -> Text(tr("Проверьте написание или очистите поиск."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                    }
                } else {
                    Text(tr("Лог пока пуст"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        tr("Нажмите «Добавить QSO», чтобы записать первую связь. Если удерживать кнопку, запишется голосовая заметка. Старый лог можно загрузить из ADIF или CSV в меню «Файл»."),
                        style = MaterialTheme.typography.bodyLarge, color = x.muted,
                    )
                }
            }
        } else {
            SortBar(state)
            val groups = remember(state.qsos, state.sortBy, state.sortDesc) { groupAndSort(state.qsos, state.sortBy, state.sortDesc) }
            val listState = rememberLazyListState()
            // A new order starts from the top, not from wherever the old one was scrolled to.
            LaunchedEffect(state.sortBy, state.sortDesc) { listState.scrollToItem(0) }
            // A just-saved contact is shown: with the date sort it is the first row.
            LaunchedEffect(state.newSavedTick) { if (state.newSavedTick > 0) listState.animateScrollToItem(0) }
            // The keyboard's cursor: the list knows the order of the rows, and keeps the outlined one in view.
            LaunchedEffect(groups) { state.logOrder = groups.flatMap { g -> g.items.map { it.id } } }
            LaunchedEffect(state.logCursor) {
                val id = state.logCursor ?: return@LaunchedEffect
                var i = 0
                for (g in groups) {
                    if (g.title != null) i++
                    val k = g.items.indexOfFirst { it.id == id }
                    if (k >= 0) { listState.animateScrollToItem((i + k - 1).coerceAtLeast(0)); break }
                    i += g.items.size
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(end = 8.dp),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { g ->
                        if (g.title != null) stickyHeader(key = "h" + g.key) { GroupHeader(g.title, g.items.size) }
                        items(g.items, key = { it.id }) { qso ->
                            QsoRow(qso, state, showDate = state.sortBy != SortBy.DATE)
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
            }
        }

        // At the bottom, like on the phone; holding it records a voice note.
        AddQsoButton(state, shortcut, Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp))
    }
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

private class Group(val key: String, val title: String?, val items: List<Qso>)

private fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now(ZoneOffset.UTC)
    return when (day) {
        today -> tr("Сегодня, %s", DATE_FMT.format(day))
        today.minusDays(1) -> tr("Вчера, %s", DATE_FMT.format(day))
        else -> DATE_FMT.format(day)
    }
}

/** The log as sections for the chosen sort. The list arrives newest first. */
private fun groupAndSort(list: List<Qso>, by: SortBy, desc: Boolean): List<Group> {
    val newestFirst = list.sortedByDescending { it.timeUtc }
    return when (by) {
        SortBy.DATE -> {
            val byDay = (if (desc) newestFirst else newestFirst.reversed()).groupBy { utc(it.timeUtc).toLocalDate() }
            byDay.map { (day, items) -> Group("d$day", dayTitle(day), items) }
        }
        SortBy.DISTANCE -> {
            // Contacts without a distance go last in both directions.
            val known = newestFirst.filter { it.distanceKm != null }.let { l -> if (desc) l.sortedByDescending { it.distanceKm } else l.sortedBy { it.distanceKm } }
            val unknown = newestFirst.filter { it.distanceKm == null }
            listOfNotNull(
                Group("dist", null, known),
                if (unknown.isNotEmpty()) Group("nodist", tr("Расстояние неизвестно"), unknown) else null,
            )
        }
        SortBy.CALL -> {
            val byCall = newestFirst.groupBy { it.call }
            val calls = if (desc) byCall.keys.sortedDescending() else byCall.keys.sorted()
            calls.map { Group("c$it", it, byCall.getValue(it)) }
        }
        SortBy.BAND -> {
            val byBand = newestFirst.groupBy { it.band }
            // Known bands in frequency order, anything else after them.
            val order = byBand.keys.sortedWith(compareBy({ BANDS.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
            (if (desc) order.reversed() else order).map { Group("b$it", it.ifBlank { tr("Без диапазона") }, byBand.getValue(it)) }
        }
    }
}

/** Four compact sort chips sized to their labels; with a large system font the row scrolls sideways instead of cutting words. */
@Composable
private fun SortBar(state: AppState) {
    val x = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SortBy.entries.forEach { by ->
            val selected = state.sortBy == by
            val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Row(
                Modifier.height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else x.field)
                    .clickable(onClickLabel = tr("Сортировать: %s", by.label)) { state.sort(by) }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(by.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false)
                if (selected) {
                    Icon(
                        if (state.sortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                        if (state.sortDesc) tr("по убыванию") else tr("по возрастанию"),
                        Modifier.size(16.dp),
                        tint = fg,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Text("$count QSO", style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted)
    }
}

/** Replaces the log header while records are picked: count, select all, export to ADIF, cancel. */
@Composable
private fun SelectionBar(state: AppState, onExport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = state::clearSelection, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Close, tr("Отменить выбор (Esc)"), Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tr("Выбрано: %s", state.selected.size), style = MaterialTheme.typography.headlineSmall)
            Text(tr("Щёлкайте записи, чтобы добавить"), style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
        }
        Tip(tr("Выбрать все")) {
            IconButton(onClick = state::selectAllShown, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SelectAll, tr("Выбрать все"), Modifier.size(28.dp))
            }
        }
        // Export of the picked records: ADIF, or a contest report (ЕРМАК / Cabrillo) via its settings dialog.
        var menu by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { menu = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp)) {
                Icon(Icons.Filled.FileUpload, null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Экспорт"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("ADIF (.adi)", fontSize = 17.sp) }, onClick = { menu = false; onExport() })
                DropdownMenuItem(text = { Text("CSV", fontSize = 17.sp) }, onClick = { menu = false; state.openExport(ExportFormat.CSV, selectedOnly = true) })
                DropdownMenuItem(text = { Text(tr("ЕРМАК / Cabrillo"), fontSize = 17.sp) }, onClick = { menu = false; state.openExport(ExportFormat.CONTEST, selectedOnly = true) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun QsoRow(qso: Qso, state: AppState, showDate: Boolean) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(14.dp)
    val selecting = state.selecting
    val picked = qso.id in state.selected
    val open = !selecting && (state.pane == Pane.Edit || state.pane == Pane.Contest) && state.form.id == qso.id
    val highlighted = picked || open || qso.id == state.logCursor
    val window = LocalWindowInfo.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) MaterialTheme.colorScheme.primary else x.line, shape)
            .combinedClickable(
                onClick = {
                    // ⌘-click (macOS) or Ctrl-click picks records, like a long press.
                    val mods = window.keyboardModifiers
                    val multi = if (IS_MAC) mods.isMetaPressed else mods.isCtrlPressed
                    if (selecting || multi) state.toggleSelected(qso.id) else state.edit(qso)
                },
                onLongClickLabel = tr("Выбрать запись"),
                onLongClick = { state.toggleSelected(qso.id) },
            )
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (picked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                if (picked) tr("Выбрана") else tr("Не выбрана"),
                Modifier.size(28.dp),
                tint = if (picked) MaterialTheme.colorScheme.primary else x.muted,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(qso.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ContestMode.isContest(qso.adif)) {
                    Icon(Icons.Filled.Flag, tr("Связь в контест-режиме"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                if (qso.audio.isNotBlank()) {
                    Icon(Icons.Filled.Mic, tr("Есть голосовая заметка"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                // Saved while QRZ.ru was unreachable: fetch the station data again.
                if (qso.pendingLookup && !selecting) {
                    if (qso.id in state.refreshing) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(26.dp), strokeWidth = 3.dp)
                    } else {
                        Tip(tr("Обновить данные с QRZ.ru")) {
                            FilledTonalIconButton(onClick = { state.refreshLookup(qso) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Refresh, tr("Обновить данные с QRZ.ru"), Modifier.size(24.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                // Exported to ADIF / CSV / a contest report: a small dot of each format's colour.
            ExportDots(qso.adif)
            // Outside the date sort there are no day headers, so the row carries its own date.
                val t = utc(qso.timeUtc)
                Text(
                    (if (showDate) SHORT_DATE.format(t) + " " else "") + TIME_FMT.format(t) + " UTC",
                    fontFamily = Mono, fontSize = 16.sp, color = x.muted,
                )
            }
            val meta = listOfNotNull(
                qso.freqMhz.ifBlank { null } ?: qso.band.ifBlank { null },
                qso.mode.ifBlank { null },
                if (qso.rstSent.isNotBlank() || qso.rstRcvd.isNotBlank()) "${qso.rstSent} / ${qso.rstRcvd}" else null,
                // Contest numbers: sent / received.
                if (ContestMode.isContest(qso.adif)) "№ ${qso.adif[ContestMode.SENT].orEmpty()} / ${qso.adif[ContestMode.RCVD].orEmpty().ifBlank { "—" }}" else null,
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Who and how far on one line: the distance at the end, so the row does not grow by a line for it.
            val who = listOf(qso.name, qso.qth).filter { it.isNotBlank() }.joinToString(", ").ifBlank { qso.country }
                .ifBlank { if (qso.pendingLookup) tr("Данные QRZ.ru не получены") else "" }
            val km = qso.distanceKm?.let { (if (qso.approxPosition) "≈ " else "") + formatKm(it) }
            if (who.isNotEmpty() || km != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(who, style = MaterialTheme.typography.bodyLarge, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (km != null) Text(km, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 10.dp, end = 8.dp))
                }
            }
        }
        // Deleting is only possible from the card ("Удалить" there), never from the list.
        Spacer(Modifier.width(10.dp))
    }
}

/** Tooltip on hover, for icon-only buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipArea(tooltip = {
        Text(text, Modifier.background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(6.dp)).padding(8.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
    }) { content() }
}

/** A menu line with its key at the end. */
@Composable
private fun MenuText(text: String, key: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 17.sp)
        Text("   $key", fontFamily = Mono, fontSize = 14.sp, color = LocalExtra.current.muted)
    }
}
