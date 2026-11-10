package ru.r3xed.qsolog.ui

import androidx.compose.material.icons.filled.Tune
import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material.icons.filled.KeyboardHide
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.MenuBook
import ru.r3xed.qsolog.data.ContestMode
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import ru.r3xed.qsolog.SearchLookup
import ru.r3xed.qsolog.data.KeypadMode
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import ru.r3xed.qsolog.data.SheetSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.graphics.compositeOver
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
    // "Своя клавиатура" is on: the search is typed on it too. [searchKeys] — its keypad is open under the list;
    // [searchSystem] — this time the system keyboard was asked for (Cyrillic names and towns).
    var searchKeys by remember { mutableStateOf(false) }
    var searchSystem by remember { mutableStateOf(false) }
    var searchCaret by remember { mutableStateOf<Int?>(null) }
    val ownSearch = vm.keypadShown && !searchSystem
    BackHandler(enabled = ownSearch && searchKeys && !vm.selecting) { searchKeys = false }
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
                            StationLine(vm, x.muted)
                        }
                    }
                }
                // One round button: the menu with the settings, the dashboard, the map and the reference.
                var menu by remember { mutableStateOf(false) }
                Box {
                    FilledTonalIconButton(onClick = { menu = true }, modifier = Modifier.size(50.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = tr("Меню"), modifier = Modifier.size(27.dp))
                    }
                    // Grouped: the log's views, files, help, the contest mode, then the keys and the settings.
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        // Кластер, карта и статистика — on the bottom bar; here the rest of the log's views.
                        DropdownMenuItem(
                            text = { Text(tr("Дипломы"), fontSize = 18.sp) },
                            leadingIcon = { Icon(Icons.Filled.EmojiEvents, null) },
                            onClick = { menu = false; vm.openAwards() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Прохождение"), fontSize = 18.sp) },
                            leadingIcon = { Icon(Icons.Filled.Public, null) },
                            onClick = { menu = false; vm.openPropagation() },
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
                                text = { MenuText(if (vm.sheetSyncing) tr("Синхронизация…") else tr("Синхронизировать"), KeyMap.label(KeyAction.SYNC, false), vm.hardKeyboard) },
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
                        HorizontalDivider()
                        // The whole row switches contest mode; the menu stays open so the contest book shows up under it.
                        DropdownMenuItem(
                            text = { MenuText("CONTEST MODE", KeyMap.label(KeyAction.CONTEST_MODE, false), vm.hardKeyboard) },
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
                        // The list of keys matters only with a keyboard attached (F1 opens it as well).
                        if (vm.hardKeyboard) {
                            DropdownMenuItem(
                                text = { MenuText(tr("Клавиши"), KeyMap.label(KeyAction.KEYS_HELP, false), true) },
                                leadingIcon = { Icon(Icons.Filled.Keyboard, null) },
                                onClick = { menu = false; vm.showKeys = true },
                            )
                        }
                        if (vm.catConfig.enabled || vm.rig != null) DropdownMenuItem(
                            text = { MenuText(tr("Пульт трансивера"), KeyMap.label(KeyAction.RIG_PANEL, false), vm.hardKeyboard) },
                            leadingIcon = { Icon(Icons.Filled.Tune, null) },
                            onClick = { menu = false; vm.showRigPanel = true },
                        )
                        DropdownMenuItem(
                            text = { MenuText(tr("Настройки"), KeyMap.label(KeyAction.SETTINGS, false), vm.hardKeyboard) },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                            onClick = { menu = false; vm.openSettings() },
                        )
                    }
                }
            }

            // "Выезд": the activation in progress, its count and the button that ends it.
            ActivationBanner(vm)

            // Ctrl+F on a physical keyboard puts the cursor here; Enter opens a new card for the station found, Esc clears.
            val searchFocus = remember { FocusRequester() }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            LaunchedEffect(vm.searchFocus) { if (vm.searchFocus > 0 && !ownSearch) searchFocus.requestFocus() }
            LaunchedEffect(searchSystem) { if (searchSystem) searchFocus.requestFocus() }
            if (ownSearch) KeyField(
                tr("Поиск: позывной, имя, город"), vm.query, active = searchKeys, selected = false, isError = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                caret = searchCaret, onClear = { vm.search(""); searchCaret = null },
            ) { searchKeys = true; searchCaret = null }
            else OutlinedTextField(
                value = vm.query,
                onValueChange = vm::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocus)
                    .onFocusChanged { vm.searchFocused = it.isFocused; if (!it.isFocused && searchSystem && vm.query.isBlank()) searchSystem = false }
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
                    // ⌫ wipes the whole query.
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) { Icon(Icons.AutoMirrored.Filled.Backspace, tr("Очистить поиск"), tint = x.muted) }
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

            // The transceiver (CAT, or the dial WSJT-X / JTDX reports): a tap opens its panel.
            RigBadge(vm, Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp))

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

            if (vm.qsos.isEmpty() && (vm.stationFilter != null || vm.logFilter.active) && vm.query.isBlank()) {
                // The "Станция" filter or "Фильтр" left nothing: the chips stay, so the filter can be changed back.
                SortBar(vm)
                Text(
                    if (vm.logFilter.active) tr("Нет связей для фильтра: %s.", vm.logFilter.describe())
                    else tr("Связей с позывным %s в журнале нет.", vm.stationFilter.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.padding(24.dp),
                )
                Spacer(Modifier.weight(1f))
            } else if (vm.qsos.isEmpty()) {
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

            if (ownSearch && searchKeys && !vm.selecting) {
                // The app's keypad for the search: keys, then "hide", "system keyboard" and "new QSO" for the typed call.
                val compact = vm.keypad == KeypadMode.COMPACT
                HorizontalDivider(color = x.line)
                Column(Modifier.padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                    Keypad(
                        compact = compact,
                        onKey = { c -> val (v, at) = typeAt(vm.query, searchCaret, c, 30); searchCaret = at; vm.search(v) },
                        onBackspace = { val (v, at) = backspaceAt(vm.query, searchCaret); searchCaret = at; vm.search(v) },
                        onClear = { vm.search(""); searchCaret = null },
                        onCaret = { steps -> searchCaret = moveCaret(vm.query, searchCaret, steps) },
                        holdMs = vm.keypadHoldMs,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        OutlinedIconButton(onClick = { searchKeys = false }, modifier = Modifier.size(keypadButtonHeight(compact)), shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Filled.KeyboardHide, tr("Спрятать клавиатуру"))
                        }
                        OutlinedIconButton(onClick = { searchKeys = false; searchSystem = true }, modifier = Modifier.size(keypadButtonHeight(compact)), shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Filled.Keyboard, tr("Системная клавиатура: имя, город по-русски"))
                        }
                        Button(
                            onClick = {
                                // A station of the log opens with its data; a callsign not in the log goes into the card as typed.
                                val typed = vm.query.trim().uppercase()
                                searchKeys = false
                                vm.addQso()
                                if (vm.form.call.isBlank() && Regex("[A-Z0-9/]{3,}").matches(typed)) vm.setCall(typed)
                            },
                            enabled = vm.query.isNotBlank(),
                            modifier = Modifier.weight(1f).height(keypadButtonHeight(compact)), shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) { Text(tr("Новая связь"), fontSize = if (compact) 16.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                }
            } else {
                // At the bottom, under the thumb; holding it records a voice note.
                AddQsoButton(vm, hasMicPermission, requestMicPermission, Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp))
            }
        }
    }

    // Ctrl+D or Delete on the log: one dialog for the cursor's row or the picked records; Enter deletes.
    vm.logDeleteAsk?.let { list ->
        val one = list.singleOrNull()
        AlertDialog(
            onDismissRequest = { vm.logDeleteAsk = null },
            title = { Text(if (one != null) tr("Удалить связь с %s?", one.call) else tr("Удалить выбранные связи: %s?", list.size)) },
            text = {
                val t = one?.let { utc(it.timeUtc) }
                Text(
                    (if (one != null && t != null) tr("%s, %s %s. Запись пропадёт из лога.", "${DATE_FMT.format(t)}, ${TIME_FMT.format(t)} UTC", one.band, one.mode) + "\n\n" else "") +
                        tr("Enter — удалить, Esc — отмена"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.enterKey(vm::confirmDeleteLog),
                )
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmDeleteLog,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(tr("Удалить")) }
            },
            dismissButton = { TextButton(onClick = { vm.logDeleteAsk = null }) { Text(tr("Отмена")) } },
        )
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

/**
 * Under the logo: the callsign in work (with the locator when it comes from the location) and the count. A tap shows
 * the station's settings (locator, QTH, power, station fields) and, with several stations, the list to switch.
 */
@Composable
private fun StationLine(vm: AppViewModel, color: androidx.compose.ui.graphics.Color) {
    val s = vm.settings
    val many = vm.stations.size > 1
    val loc = s.myLocator.takeIf { vm.activeStation.autoLocator && it.isNotBlank() }
    val who = listOfNotNull(s.myCall.ifBlank { null }, loc).joinToString(" · ")
    val count = if ((vm.stationFilter != null || vm.logFilter.active) && vm.query.isBlank()) tr("записей: %s из %s", vm.qsos.size, vm.total) else tr("записей: %s", vm.total)
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.clickable(onClickLabel = if (many) tr("Выбрать станцию") else tr("Моя станция")) { open = true }) {
            OneLineText((if (who.isNotBlank()) "$who ▾ · " else "") + count, maxSize = 16.sp, minSize = 11.sp, color = color)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val muted = LocalExtra.current.muted
            vm.stations.forEach { st ->
                val active = st.id == vm.activeStationId
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(st.call.ifBlank { "—" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (active) {
                                // The station in work: all its settings, one per line.
                                ru.r3xed.qsolog.data.MyStations.details(st, tr("по геолокации")).forEach { (k, v) ->
                                    Text("$k: $v", fontSize = 14.sp, color = muted)
                                }
                            } else {
                                val d = ru.r3xed.qsolog.data.MyStations.describe(st, tr("локатор по геолокации"))
                                if (d.isNotBlank()) Text(d, fontSize = 14.sp, color = muted)
                            }
                        }
                    },
                    leadingIcon = { if (active) Icon(Icons.Filled.Check, tr("В работе")) else Spacer(Modifier.size(24.dp)) },
                    onClick = { open = false; if (active) vm.openSettings(stations = true) else vm.switchStation(st.id) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (many) tr("Настроить станции…") else tr("Настроить станцию…"), fontSize = 17.sp) },
                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                onClick = { open = false; vm.openSettings(stations = true) },
            )
        }
    }
}

/** "Станция": with two or more callsigns of one's own, the log shows the contacts of one of them or all. */
@Composable
private fun StationFilterChip(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val selected = vm.stationFilter != null
    Box {
        // The station in work is already named under the logo: here only the icon, filled while the filter is on.
        IconPill(Icons.Filled.CellTower, null, selected, description = tr("Фильтр по своему позывному") + (vm.stationFilter?.let { ": $it" } ?: "")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val counts = remember(vm.allQsos) { vm.allQsos.groupingBy { it.myCall.uppercase() }.eachCount() }
            DropdownMenuItem(text = { Text(tr("Все станции") + "  " + vm.allQsos.size, fontSize = 17.sp) }, onClick = { open = false; vm.changeStationFilter(null) })
            vm.stationCalls.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c + "  " + (counts[c] ?: 0), fontFamily = Mono, fontSize = 17.sp, fontWeight = if (c == vm.stationFilter) FontWeight.Bold else null) },
                    onClick = { open = false; vm.changeStationFilter(c) },
                )
            }
        }
    }
}

/** Four compact sort chips sized to their labels; with a large system font the row scrolls sideways instead of cutting words. */
@Composable
private fun SortBar(vm: AppViewModel) {
    // One compact row: the sorts as icons (the chosen one with its name and arrow), then the station and the filter.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortBy.entries.forEach { by ->
            val selected = vm.sortBy == by
            IconPill(
                sortIcon(by), if (selected) by.label else null, selected,
                trailing = if (selected) (if (vm.sortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward) else null,
                description = tr("Сортировать: %s", by.label),
            ) { vm.sort(by) }
        }
        Spacer(Modifier.width(4.dp))
        if (vm.stationCalls.size > 1) StationFilterChip(vm)
        LogFilterChip(vm)
    }
}

private fun sortIcon(by: SortBy) = when (by) {
    SortBy.DATE -> Icons.Filled.Event
    SortBy.DISTANCE -> Icons.Filled.Straighten
    SortBy.CALL -> Icons.Filled.SortByAlpha
    SortBy.BAND -> Icons.Filled.Waves
}

/** A round pill with an icon; the [label] shows only when the pill is chosen (or the filter is on). */
@Composable
internal fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?, selected: Boolean,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null, description: String, onClick: () -> Unit,
) {
    val x = LocalExtra.current
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else x.field)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = if (label == null) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (label == null) description else null, Modifier.size(20.dp), tint = fg)
        if (label != null) Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 5.dp).widthIn(max = 150.dp), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, null, Modifier.padding(start = 2.dp).size(15.dp), tint = fg)
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
        // Three buttons share the row with it: a small label and the count, which always fits.
        Column(Modifier.weight(1f)) {
            Text(tr("выбрано"), style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, maxLines = 1)
            Text("${vm.selected.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        IconButton(onClick = { vm.showSelect = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.FilterList, tr("Выбрать по датам, диапазонам, видам"), Modifier.size(28.dp))
        }
        IconButton(onClick = vm::selectAllShown, modifier = Modifier.size(48.dp)) {
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
                DropdownMenuItem(text = { Text("LoTW · eQSL · Club Log · QRZ.com · HAMLOG", fontSize = 17.sp) }, onClick = { menu = false; vm.openUpload(selectedOnly = true) })
                HorizontalDivider()
                val ctx = androidx.compose.ui.platform.LocalContext.current
                DropdownMenuItem(
                    text = { Text(tr("Печать QSL-наклеек"), fontSize = 17.sp) },
                    leadingIcon = { Icon(Icons.Filled.Print, null) },
                    onClick = { menu = false; printHtml(ctx, vm.qslLabelsHtml(), "QSL") },
                )
                DropdownMenuItem(
                    text = { Text(tr("Отметить: QSL отправлены"), fontSize = 17.sp) },
                    leadingIcon = { Icon(Icons.Filled.MarkEmailRead, null) },
                    onClick = { menu = false; vm.markQslSent() },
                )
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
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    // Confirmed in LoTW: the row turns light green, as in the logs of RUMlog and SDR-Control.
                    ru.r3xed.qsolog.data.Lotw.isReceived(qso.adif) -> x.ok.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)
                    else -> MaterialTheme.colorScheme.surface
                },
                shape,
            )
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
            ConfirmedMark(qso.adif)
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


/** The activation in progress ("Выезд"): its reference, how many contacts, and "Завершить" (with the ADIF export). */
@Composable
private fun ActivationBanner(vm: AppViewModel) {
    val a = vm.activation ?: return
    var ask by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Flag, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(
            tr("Активация %s · связей: %s", a.title, vm.activationCount), fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        TextButton(onClick = { ask = true }) { Text(tr("Завершить")) }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(tr("Завершить активацию %s?", a.title)) },
        text = { Text(tr("Связей в активации: %s. Их можно сразу выгрузить в ADIF — для POTA, WWFF, SOTA или RDA.", vm.activationCount)) },
        confirmButton = { Button(onClick = { ask = false; vm.stopActivation(export = true) }) { Text(tr("Завершить и выгрузить ADIF")) } },
        dismissButton = {
            Row {
                TextButton(onClick = { ask = false }) { Text(tr("Отмена")) }
                TextButton(onClick = { ask = false; vm.stopActivation(export = false) }) { Text(tr("Просто завершить")) }
            }
        },
    )
}

/**
 * "Фильтр": not yet in LoTW / eQSL / Club Log / QRZ.com, the paper QSL queue, a band or a mode; and "Мои фильтры" —
 * the search, the station and the filter saved under a name.
 */
@Composable
private fun LogFilterChip(vm: AppViewModel) {
    val x = LocalExtra.current
    var open by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val f = vm.logFilter
    val selected = f.active
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box {
        IconPill(Icons.Filled.FilterList, if (selected) f.describe() else null, selected, description = tr("Фильтр журнала")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (selected) DropdownMenuItem(text = { Text(tr("Сбросить фильтр"), fontSize = 17.sp) }, leadingIcon = { Icon(Icons.Filled.Close, null) }, onClick = { open = false; vm.changeLogFilter(ru.r3xed.qsolog.data.LogFilter()) })
            if (vm.savedFilters.isNotEmpty()) {
                Text(tr("Мои фильтры"), style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                vm.savedFilters.forEach { sf ->
                    DropdownMenuItem(
                        text = { Text(sf.name, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                        onClick = { open = false; vm.applySavedFilter(sf) },
                        trailingIcon = { IconButton(onClick = { vm.deleteSavedFilter(sf.name) }) { Icon(Icons.Filled.Delete, tr("Удалить фильтр")) } },
                    )
                }
                HorizontalDivider()
            }
            Text(tr("Не отправлено"), style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            ru.r3xed.qsolog.data.OnlineLog.entries.forEach { log ->
                DropdownMenuItem(
                    text = { Text(log.title, fontSize = 17.sp, fontWeight = if (f.unsent == log) FontWeight.Bold else null) },
                    onClick = { open = false; vm.changeLogFilter(f.copy(unsent = if (f.unsent == log) null else log)) },
                )
            }
            HorizontalDivider()
            ru.r3xed.qsolog.data.QslQueue.entries.forEach { qq ->
                DropdownMenuItem(
                    text = { Text(qq.title, fontSize = 17.sp, fontWeight = if (f.qsl == qq) FontWeight.Bold else null) },
                    onClick = { open = false; vm.changeLogFilter(f.copy(qsl = if (f.qsl == qq) null else qq)) },
                )
            }
            HorizontalDivider()
            val bands = remember(vm.allQsos) { ru.r3xed.qsolog.data.BANDS.filter { b -> vm.allQsos.any { it.band.equals(b, true) } } }
            val modes = remember(vm.allQsos) { ru.r3xed.qsolog.data.MODES.filter { m -> vm.allQsos.any { it.mode.equals(m, true) } } }
            Text(tr("Диапазон"), style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bands.forEach { b -> FilterPill(b, f.band == b) { vm.changeLogFilter(f.copy(band = if (f.band == b) null else b)) } }
            }
            Text(tr("Вид связи"), style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { m -> FilterPill(m, f.mode == m) { vm.changeLogFilter(f.copy(mode = if (f.mode == m) null else m)) } }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            DropdownMenuItem(
                text = { Text(tr("Сохранить как «Мой фильтр»…"), fontSize = 17.sp) },
                leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null) },
                onClick = { open = false; saving = true },
            )
        }
    }
    if (saving) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(tr("Сохранить фильтр")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Сохраняются поиск, станция и фильтр: %s", listOfNotNull(vm.query.ifBlank { null }, vm.stationFilter, f.describe().ifBlank { null }).joinToString(" · ").ifBlank { tr("ничего не выбрано") }))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Название")) }, singleLine = true)
                }
            },
            confirmButton = { Button(onClick = { saving = false; vm.saveCurrentFilter(name) }, enabled = name.isNotBlank()) { Text(tr("Сохранить")) } },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(tr("Отмена")) } },
        )
    }
}

@Composable
private fun FilterPill(text: String, on: Boolean, onClick: () -> Unit) {
    val x = LocalExtra.current
    Text(
        text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp,
        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) MaterialTheme.colorScheme.primary else x.field)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Android's print dialog for an HTML page (QSL labels); the WebView lives until the page is handed over. */
internal fun printHtml(context: android.content.Context, html: String, job: String) {
    val wv = android.webkit.WebView(context)
    printing = wv
    wv.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView, url: String?) {
            val pm = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
            pm.print(job, view.createPrintDocumentAdapter(job), android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).build())
            printing = null
        }
    }
    wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private var printing: android.webkit.WebView? = null
