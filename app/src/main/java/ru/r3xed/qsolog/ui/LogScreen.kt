package ru.r3xed.qsolog.ui

import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.data.ExportFormat
import androidx.compose.material.icons.filled.Check
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.MenuBook
import ru.r3xed.qsolog.data.ContestMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import ru.r3xed.qsolog.SearchLookup
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import ru.r3xed.qsolog.data.SheetSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.BuildConfig
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.SortBy
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.approxPosition
import ru.r3xed.qsolog.utc

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    vm: AppViewModel,
    hasMicPermission: () -> Boolean,
    requestMicPermission: () -> Unit,
) {
    val x = LocalExtra.current
    BackHandler(enabled = vm.selecting) { vm.clearSelection() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // imePadding: with the keyboard open (search) the list shrinks and "Добавить QSO" stays above the keyboard.
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (vm.selecting) SelectionBar(vm) else Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        QsoLogo()
                        Box(Modifier.weight(1f, fill = false).padding(start = 8.dp, bottom = 6.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                                OneLineText("v" + BuildConfig.VERSION_NAME, maxSize = 14.sp, minSize = 9.sp, color = x.muted)
                            }
                        }
                    }
                    val me = vm.settings.myCall
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        if (vm.contestMode) {
                            // Contest mode is on until the app is closed: say so where the eye goes first.
                            OneLineText(
                                "CONTEST · " + if (vm.contestSentFixed) tr("передаю %s", vm.contestSentText.ifBlank { "—" }) else tr("следующий № %s", ContestMode.serial(vm.contestSerial)),
                                maxSize = 16.sp, minSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                            )
                            // The contest from the book: its name, the tour and the time left, ticking.
                            vm.activeContest?.let { ContestPhaseText(it, x.muted, prefix = it.title, short = true) }
                        } else {
                            OneLineText((if (me.isNotBlank()) "$me · " else "") + tr("записей: %s", vm.total), maxSize = 16.sp, minSize = 11.sp, color = x.muted)
                        }
                    }
                }
                // One round button: the menu with the settings, the dashboard, the map and the reference.
                var menu by remember { mutableStateOf(false) }
                Box {
                    FilledTonalIconButton(onClick = { menu = true }, modifier = Modifier.size(50.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = tr("Меню"), modifier = Modifier.size(27.dp))
                    }
                    // Grouped: the operating mode, the log's views, files, then help and the settings — the rarest last.
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        // The whole row switches contest mode; the menu stays open so the contest book shows up under it.
                        DropdownMenuItem(
                            text = { MenuText("CONTEST MODE", "Ctrl+K", vm.hardKeyboard) },
                            leadingIcon = { Icon(Icons.Filled.Flag, null) },
                            trailingIcon = { Switch(checked = vm.contestMode, onCheckedChange = null) },
                            onClick = { vm.toggleContestMode() },
                        )
                        if (vm.contestMode) {
                            DropdownMenuItem(
                                text = { Text(tr("Справочник контестов"), fontSize = 18.sp) },
                                leadingIcon = { Icon(Icons.Filled.EmojiEvents, null) },
                                onClick = { menu = false; vm.openContests() },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { MenuText(tr("Дашборд"), "Ctrl+D", vm.hardKeyboard) },
                            leadingIcon = { Icon(Icons.Filled.BarChart, null) },
                            onClick = { menu = false; vm.openDashboard() },
                        )
                        DropdownMenuItem(
                            text = { MenuText(tr("Карта QSO"), "Ctrl+M", vm.hardKeyboard) },
                            leadingIcon = { Icon(Icons.Filled.Map, null) },
                            onClick = { menu = false; vm.openMap() },
                        )
                        HistoryMenuItem(vm) { menu = false; vm.openHistory() }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(tr("Экспорт и импорт…"), fontSize = 18.sp) },
                            leadingIcon = { Icon(Icons.Filled.ImportExport, null) },
                            onClick = { menu = false; vm.showTransfer = true },
                        )
                        // Shown once the table is set up in the settings; the sync runs only from here or there.
                        if (SheetSync.isScriptUrl(vm.sheetUrl)) {
                            DropdownMenuItem(
                                text = { MenuText(if (vm.sheetSyncing) tr("Синхронизация…") else tr("Синхронизировать"), "Ctrl+R", vm.hardKeyboard) },
                                leadingIcon = { Icon(Icons.Filled.Sync, null) },
                                enabled = !vm.sheetSyncing,
                                onClick = { menu = false; vm.syncSheet() },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(tr("Справка и калькуляторы"), fontSize = 18.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                            onClick = { menu = false; vm.openReference() },
                        )
                        // The list of keys matters only with a keyboard attached (F1 opens it as well).
                        if (vm.hardKeyboard) {
                            DropdownMenuItem(
                                text = { MenuText(tr("Клавиши"), "F1", true) },
                                leadingIcon = { Icon(Icons.Filled.Keyboard, null) },
                                onClick = { menu = false; vm.showKeys = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { MenuText(tr("Настройки"), "Ctrl+,", vm.hardKeyboard) },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                            onClick = { menu = false; vm.openSettings() },
                        )
                    }
                }
            }

            // Ctrl+F on a physical keyboard puts the cursor here; Enter opens a new card for the station found, Esc clears.
            val searchFocus = remember { FocusRequester() }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            LaunchedEffect(vm.searchFocus) { if (vm.searchFocus > 0) searchFocus.requestFocus() }
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocus)
                    .onFocusChanged { vm.searchFocused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || !e.fromHardware()) return@onPreviewKeyEvent false
                        when {
                            // ↓ from the search to the list: the arrows then walk through the rows.
                            e.key == Key.DirectionDown -> { focusManager.clearFocus(); vm.moveLogCursor(0, to = -1); true }
                            e.key == Key.Escape && vm.query.isEmpty() -> { focusManager.clearFocus(); true }
                            (e.key == Key.Enter || e.key == Key.NumPadEnter) && vm.query.isNotBlank() -> { vm.addQso(); true }
                            e.key == Key.Escape && vm.query.isNotEmpty() -> { vm.search(""); true }
                            else -> false
                        }
                    },
                // One line on narrow phones and with a large system font: smaller than the typed text, never wrapped.
                placeholder = { OneLineText(tr("Поиск: позывной, имя, город"), maxSize = 16.sp, minSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) { Icon(Icons.Filled.Clear, tr("Очистить поиск")) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = x.field, focusedContainerColor = x.field,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )

            // One station found: the bands it was worked on, with the last date on each.
            val station = remember(vm.query, vm.qsos, vm.allQsos) { vm.searchedHistory() }
            if (station != null && station.byBand.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        tr("Диапазоны:"), style = MaterialTheme.typography.bodyMedium, color = x.muted,
                        modifier = Modifier.padding(top = 3.dp, end = 8.dp),
                    )
                    BandChips(station.byBand, ink = MaterialTheme.colorScheme.onSurface, chip = x.field)
                }
            }

            if (vm.qsos.isEmpty()) {
                Box(Modifier.weight(1f)) { EmptyLog(vm) }
            } else {
                SortBar(vm)
                val groups = remember(vm.qsos, vm.sortBy, vm.sortDesc) { groupAndSort(vm.qsos, vm.sortBy, vm.sortDesc) }
                val listState = rememberLazyListState()
                // A new order starts from the top, not from wherever the old one was scrolled to.
                LaunchedEffect(vm.sortBy, vm.sortDesc) { listState.scrollToItem(0) }
                // A just-saved contact is shown: with the date sort it is the first row.
                LaunchedEffect(vm.newSavedTick) { if (vm.newSavedTick > 0) listState.animateScrollToItem(0) }
                // The keyboard's cursor: the list knows the order of the rows, and keeps the outlined one in view.
                LaunchedEffect(groups) { vm.logOrder = groups.flatMap { g -> g.items.map { it.id } } }
                LaunchedEffect(vm.logCursor) {
                    val id = vm.logCursor ?: return@LaunchedEffect
                    var i = 0
                    for (g in groups) {
                        if (g.title != null) i++
                        val k = g.items.indexOfFirst { it.id == id }
                        if (k >= 0) { listState.animateScrollToItem((i + k - 1).coerceAtLeast(0)); break }
                        i += g.items.size
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { g ->
                        if (g.title != null) stickyHeader(key = "h" + g.key) { GroupHeader(g.title, g.items.size) }
                        items(g.items, key = { it.id }) { qso ->
                            LogRow(qso, vm, showDate = vm.sortBy != SortBy.DATE, modifier = Modifier.animateItem(), cursor = qso.id == vm.logCursor)
                        }
                    }
                }
            }

            // At the bottom, under the thumb; holding it records a voice note.
            AddQsoButton(vm, hasMicPermission, requestMicPermission, Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun EmptyLog(vm: AppViewModel) {
    val x = LocalExtra.current
    Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.query.isNotBlank()) {
            Text(tr("В вашем журнале ничего не найдено"), style = MaterialTheme.typography.titleLarge)
            // A callsign-like search goes on to QRZ.ru; a found station opens a new card by itself.
            when (val s = vm.searchLookup) {
                is SearchLookup.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Ищу %s на QRZ.ru…", s.call), style = MaterialTheme.typography.bodyLarge)
                }
                is SearchLookup.NotFound -> Text(tr("На QRZ.ru позывного %s тоже нет.", s.call), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                SearchLookup.NoAccount -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Чтобы искать позывные на QRZ.ru, укажите учётную запись XML API."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
                    TextButton(onClick = { vm.openSettings() }) { Text(tr("Открыть настройки")) }
                }
                is SearchLookup.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::retrySearchLookup) { Text(tr("Повторить")) }
                }
                SearchLookup.Idle -> Text(tr("Проверьте написание или очистите поиск."), style = MaterialTheme.typography.bodyLarge, color = x.muted)
            }
        } else {
            Text(tr("Лог пока пуст"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr("Нажмите «Добавить QSO», чтобы записать первую связь. Если удерживать кнопку, запишется голосовая заметка. Старый лог можно загрузить из CSV в настройках."),
                style = MaterialTheme.typography.bodyLarge, color = x.muted,
            )
        }
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
private fun SortBar(vm: AppViewModel) {
    val x = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SortBy.entries.forEach { by ->
            val selected = vm.sortBy == by
            val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Row(
                Modifier.height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else x.field)
                    .clickable(onClickLabel = tr("Сортировать: %s", by.label)) { vm.sort(by) }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(by.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false)
                if (selected) {
                    Icon(
                        if (vm.sortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                        if (vm.sortDesc) tr("по убыванию") else tr("по возрастанию"),
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

/** A log row. Deleting is only possible from the card ("Удалить" there), never from the list. */
@Composable
private fun LogRow(qso: Qso, vm: AppViewModel, showDate: Boolean, modifier: Modifier = Modifier, cursor: Boolean = false) {
    QsoRow(
        qso,
        cursor = cursor,
        modifier = modifier,
        showDate = showDate,
        selecting = vm.selecting,
        selected = qso.id in vm.selected,
        onClick = { if (vm.selecting) vm.toggleSelected(qso.id) else vm.edit(qso) },
        onLongClick = { vm.toggleSelected(qso.id) },
        refreshing = qso.id in vm.refreshing,
        onRefresh = { vm.refreshLookup(qso) },
    )
}

/** Replaces the log header while records are picked: count, select all, export (ADIF, CSV, contest report), cancel. */
@Composable
private fun SelectionBar(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Close, tr("Отменить выбор"), Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tr("Выбрано: %s", vm.selected.size), style = MaterialTheme.typography.headlineSmall)
            Text(tr("Нажимайте записи, чтобы добавить"), style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
        }
        IconButton(onClick = vm::selectAllShown, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SelectAll, tr("Выбрать все"), Modifier.size(28.dp))
        }
        // Export of the picked records: ADIF, CSV or a contest report (ЕРМАК / Cabrillo), each via its dialog.
        var menu by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { menu = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp)) {
                Icon(Icons.Filled.FileUpload, null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Экспорт"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("ADIF (.adi)", fontSize = 17.sp) }, onClick = { menu = false; vm.openExport(ExportFormat.ADIF, selectedOnly = true) })
                DropdownMenuItem(text = { Text("CSV", fontSize = 17.sp) }, onClick = { menu = false; vm.openExport(ExportFormat.CSV, selectedOnly = true) })
                DropdownMenuItem(text = { Text(tr("ЕРМАК / Cabrillo"), fontSize = 17.sp) }, onClick = { menu = false; vm.openExport(ExportFormat.CONTEST, selectedOnly = true) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QsoRow(
    qso: Qso,
    /** The keyboard's cursor is on this row. */
    cursor: Boolean = false,
    modifier: Modifier = Modifier,
    showDate: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val x = LocalExtra.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
            .border(if (selected || cursor) 2.dp else 1.dp, if (selected || cursor) MaterialTheme.colorScheme.primary else x.line, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = tr("Выбрать запись"),
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    if (selected) tr("Выбрана") else tr("Не выбрана"),
                    Modifier.size(28.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else x.muted,
                )
                Spacer(Modifier.width(10.dp))
            }
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
                if (refreshing) {
                    CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(28.dp), strokeWidth = 3.dp)
                } else {
                    FilledTonalIconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Refresh, tr("Обновить данные с QRZ.ru"), Modifier.size(26.dp))
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
        // One line: long contest codes make the font a little smaller instead of wrapping.
        if (meta.isNotEmpty()) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) { OneLineText(meta, maxSize = 18.sp, minSize = 13.sp) }
        }
        // Who and how far on one line: the distance at the end, so the row does not grow by a line for it.
        val who = listOf(qso.name, qso.qth).filter { it.isNotBlank() }.joinToString(", ").ifBlank { qso.country }
            .ifBlank { if (qso.pendingLookup) tr("Данные QRZ.ru не получены") else "" }
        val km = qso.distanceKm?.let { (if (qso.approxPosition) "≈ " else "") + formatKm(it) }
        if (who.isNotEmpty() || km != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(who, style = MaterialTheme.typography.bodyLarge, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (km != null) Text(km, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/**
 * Text that always stays on one line: the font steps down from [maxSize] to [minSize] until it fits the width
 * (narrow phones, large system font). Only below [minSize] is it cut with an ellipsis.
 */
@Composable
private fun OneLineText(text: String, maxSize: TextUnit, minSize: TextUnit, color: Color = Color.Unspecified) {
    val measurer = rememberTextMeasurer()
    // Measured with the style the text is drawn with (the text field's placeholder style: font, letter spacing).
    val base = LocalTextStyle.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val size = remember(text, width, maxSize, minSize, base) {
            var sp = maxSize.value
            while (sp > minSize.value &&
                measurer.measure(text, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width > width
            ) sp -= 0.5f
            sp.sp
        }
        Text(text, style = base.copy(fontSize = size), color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** A menu line with its key at the end when a keyboard is attached. */
@Composable
private fun MenuText(text: String, key: String, keys: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 18.sp)
        if (keys) Text("   $key", fontFamily = Mono, fontSize = 14.sp, color = LocalExtra.current.muted)
    }
}
