package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import ru.r3xed.qsolog.utc

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    vm: AppViewModel,
    hasMicPermission: () -> Boolean,
    requestMicPermission: () -> Unit,
    onExportSelected: () -> Unit,
) {
    val x = LocalExtra.current
    BackHandler(enabled = vm.selecting) { vm.clearSelection() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (vm.selecting) SelectionBar(vm, onExportSelected) else Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        QsoLogo()
                        Text(
                            "v" + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelMedium,
                            color = x.muted,
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                        )
                    }
                    val me = vm.settings.myCall
                    Text(
                        (if (me.isNotBlank()) "$me · " else "") + "записей: ${vm.total}",
                        style = MaterialTheme.typography.bodyMedium, color = x.muted,
                    )
                }
                FilledTonalIconButton(onClick = vm::openMap, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Map, contentDescription = "Карта QSO", modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(10.dp))
                FilledTonalIconButton(onClick = { vm.openSettings() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", modifier = Modifier.size(30.dp))
                }
            }

            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Поиск: позывной, имя, город", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) { Icon(Icons.Filled.Clear, "Очистить поиск") }
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

            if (vm.qsos.isEmpty()) {
                Box(Modifier.weight(1f)) { EmptyLog(vm) }
            } else {
                SortBar(vm)
                val groups = remember(vm.qsos, vm.sortBy, vm.sortDesc) { groupAndSort(vm.qsos, vm.sortBy, vm.sortDesc) }
                val listState = rememberLazyListState()
                // A new order starts from the top, not from wherever the old one was scrolled to.
                LaunchedEffect(vm.sortBy, vm.sortDesc) { listState.scrollToItem(0) }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { g ->
                        if (g.title != null) stickyHeader(key = "h" + g.key) { GroupHeader(g.title, g.items.size) }
                        items(g.items, key = { it.id }) { qso ->
                            LogRow(qso, vm, showDate = vm.sortBy != SortBy.DATE, modifier = Modifier.animateItem())
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
            Text("Ничего не найдено", style = MaterialTheme.typography.titleLarge)
            Text("Проверьте написание или очистите поиск.", style = MaterialTheme.typography.bodyLarge, color = x.muted)
        } else {
            Text("Лог пока пуст", style = MaterialTheme.typography.titleLarge)
            Text(
                "Нажмите «Добавить QSO», чтобы записать первую связь. Если удерживать кнопку, запишется голосовая заметка. Старый лог можно загрузить из CSV в настройках.",
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
        today -> "Сегодня, ${DATE_FMT.format(day)}"
        today.minusDays(1) -> "Вчера, ${DATE_FMT.format(day)}"
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
                if (unknown.isNotEmpty()) Group("nodist", "Расстояние неизвестно", unknown) else null,
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
            (if (desc) order.reversed() else order).map { Group("b$it", it.ifBlank { "Без диапазона" }, byBand.getValue(it)) }
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
                    .clickable(onClickLabel = "Сортировать: ${by.label}") { vm.sort(by) }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(by.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false)
                if (selected) {
                    Icon(
                        if (vm.sortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                        if (vm.sortDesc) "по убыванию" else "по возрастанию",
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
private fun LogRow(qso: Qso, vm: AppViewModel, showDate: Boolean, modifier: Modifier = Modifier) {
    QsoRow(
        qso,
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

/** Replaces the log header while records are picked: count, select all, export to ADIF, cancel. */
@Composable
private fun SelectionBar(vm: AppViewModel, onExport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Close, "Отменить выбор", Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Выбрано: ${vm.selected.size}", style = MaterialTheme.typography.headlineSmall)
            Text("Нажимайте записи, чтобы добавить", style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
        }
        IconButton(onClick = vm::selectAllShown, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.SelectAll, "Выбрать все", Modifier.size(28.dp))
        }
        Button(onClick = onExport, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(52.dp)) {
            Icon(Icons.Filled.FileUpload, null)
            Spacer(Modifier.width(6.dp))
            Text("ADIF", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QsoRow(
    qso: Qso,
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
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else x.line, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = "Выбрать запись",
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
                    if (selected) "Выбрана" else "Не выбрана",
                    Modifier.size(28.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else x.muted,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(qso.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (qso.audio.isNotBlank()) {
                Icon(Icons.Filled.Mic, "Есть голосовая заметка", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            // Saved while QRZ.ru was unreachable: fetch the station data again.
            if (qso.pendingLookup && !selecting) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(28.dp), strokeWidth = 3.dp)
                } else {
                    FilledTonalIconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Refresh, "Обновить данные с QRZ.ru", Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
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
            qso.distanceKm?.let { formatKm(it) },
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        val who = listOf(qso.name, qso.qth).filter { it.isNotBlank() }.joinToString(", ")
        if (who.isNotEmpty()) Text(who, style = MaterialTheme.typography.bodyLarge, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else if (qso.pendingLookup) Text("Данные QRZ.ru не получены", style = MaterialTheme.typography.bodyLarge, color = x.muted, maxLines = 1)
    }
}
