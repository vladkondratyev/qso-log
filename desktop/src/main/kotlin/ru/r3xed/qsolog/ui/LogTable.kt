package ru.r3xed.qsolog.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.Pane
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.approxPosition
import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.utc
import java.time.format.DateTimeFormatter

private val TABLE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TABLE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun bandOrder(b: String) = BANDS.indexOf(b.lowercase()).let { if (it < 0) Int.MAX_VALUE else it }

/**
 * A column of the table log: its header, width, what a row shows and how it sorts. [group] — the rows can be grouped
 * by it. The number (No.) is the contact's place in the log, the oldest first.
 */
enum class TableColumn(private val ru: String, val width: Int, val group: Boolean = false) {
    NO("№", 56), // no-tr
    MY_CALL("Мой позывной", 110, group = true), // no-tr
    DATE("Дата", 100, group = true), // no-tr
    TIME("Время", 60), // no-tr
    CALL("Позывной", 120), // no-tr
    BAND("Диапазон", 76, group = true), // no-tr
    FREQ("Частота", 90), // no-tr
    MODE("Вид", 64, group = true), // no-tr
    RST_SENT("RST отпр.", 70), // no-tr
    RST_RCVD("RST прин.", 70), // no-tr
    NAME("Имя", 120), // no-tr
    QTH("QTH", 140), // no-tr
    COUNTRY("Страна", 130, group = true), // no-tr
    LOCATOR("Локатор", 80), // no-tr
    DISTANCE("Расстояние", 96), // no-tr
    POWER("Мощность", 76), // no-tr
    QSL_SENT("QSL отпр.", 72), // no-tr
    QSL_RCVD("QSL прин.", 72), // no-tr
    IOTA("IOTA", 72), // no-tr
    RDA("RDA", 72), // no-tr
    STATE("Область", 110, group = true), // no-tr
    CQZ("CQ", 44), // no-tr
    ITUZ("ITU", 44), // no-tr
    SENT_NR("№ отпр.", 70), // no-tr
    RCVD_NR("№ прин.", 70), // no-tr
    COMMENT("Комментарий", 220), // no-tr
    ;

    val title: String get() = if (this == NO || this == QTH || this == IOTA || this == RDA || this == CQZ || this == ITUZ) ru else tr(ru)

    /** What the cell shows; [no] — the row's number in the log. */
    fun text(q: Qso, no: Int): String = when (this) {
        NO -> no.toString()
        MY_CALL -> q.myCall
        DATE -> TABLE_DATE.format(utc(q.timeUtc))
        TIME -> TABLE_TIME.format(utc(q.timeUtc))
        CALL -> q.call
        BAND -> q.band
        FREQ -> q.freqMhz
        MODE -> q.mode
        RST_SENT -> q.rstSent
        RST_RCVD -> q.rstRcvd
        NAME -> q.name
        QTH -> q.qth
        COUNTRY -> q.country
        LOCATOR -> q.locator
        DISTANCE -> q.distanceKm?.let { (if (q.approxPosition) "≈ " else "") + formatKm(it) }.orEmpty()
        POWER -> q.power
        QSL_SENT -> if (q.qslSent) "✓" else ""
        QSL_RCVD -> if (q.qslRcvd) "✓" else ""
        IOTA -> q.adif["IOTA"].orEmpty()
        RDA -> q.adif["CNTY"].orEmpty()
        STATE -> q.adif["STATE"].orEmpty()
        CQZ -> q.adif["CQZ"].orEmpty()
        ITUZ -> q.adif["ITUZ"].orEmpty()
        SENT_NR -> q.adif[ContestMode.SENT].orEmpty()
        RCVD_NR -> q.adif[ContestMode.RCVD].orEmpty()
        COMMENT -> q.comment
    }

    /** The order of the rows by this column (the earlier contact first among equals). */
    val comparator: Comparator<Pair<Qso, Int>>
        get() = when (this) {
            NO, DATE, TIME -> compareBy { it.first.timeUtc }
            BAND -> compareBy<Pair<Qso, Int>> { bandOrder(it.first.band) }.thenBy { it.first.timeUtc }
            FREQ -> compareBy<Pair<Qso, Int>> { it.first.freqMhz.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.first.timeUtc }
            DISTANCE -> compareBy<Pair<Qso, Int>> { it.first.distanceKm ?: Double.MAX_VALUE }.thenBy { it.first.timeUtc }
            CQZ, ITUZ, SENT_NR, RCVD_NR -> compareBy<Pair<Qso, Int>> { text(it.first, it.second).toIntOrNull() ?: Int.MAX_VALUE }.thenBy { text(it.first, it.second) }
            else -> compareBy<Pair<Qso, Int>, String>(String.CASE_INSENSITIVE_ORDER) { text(it.first, it.second) }.thenBy { it.first.timeUtc }
        }

    companion object {
        /** As in the classic loggers: number, date and time, the call, band and mode, the reports, who and where. */
        val DEFAULT = listOf(NO, DATE, TIME, CALL, BAND, FREQ, MODE, RST_SENT, RST_RCVD, NAME, QTH, COUNTRY, COMMENT)

        fun decode(s: String): List<TableColumn> =
            s.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }.distinct().ifEmpty { DEFAULT }
    }
}

/**
 * «Журнал таблицей» on the computer: the whole log as a compact table over the window — the columns of one's choice,
 * a click on a header sorts (again — the other way), above it the search, the filters and the grouping. A new or
 * opened contact comes up as a card over the table ([TableCardOverlay]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogTable(state: AppState, shortcut: String, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    var columnsDialog by remember { mutableStateOf(false) }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        // --- the toolbar: search, station, filter, grouping, columns, a new QSO ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(state.searchFocus) { if (state.searchFocus > 0) searchFocus.requestFocus() }
            OutlinedTextField(
                value = state.query,
                onValueChange = state::search,
                modifier = Modifier.widthIn(min = 220.dp, max = 360.dp).focusRequester(searchFocus)
                    .onFocusChanged { state.searchFocused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            (e.key == Key.Enter || e.key == Key.NumPadEnter) && state.query.isNotBlank() -> { state.addQso(); true }
                            e.key == Key.Escape && state.query.isNotEmpty() -> { state.search(""); true }
                            else -> false
                        }
                    },
                placeholder = { Text(tr("Поиск: позывной, имя, город"), maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { state.search("") }) { Icon(Icons.AutoMirrored.Filled.Backspace, tr("Очистить поиск"), tint = x.muted) } },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = x.field, focusedContainerColor = x.field, unfocusedBorderColor = Color.Transparent),
            )
            if (state.stationCalls.size > 1) StationFilterChip(state)
            LogFilterChip(state)
            GroupChooser(state)
            OutlinedButton(onClick = { columnsDialog = true }) {
                Icon(Icons.Filled.ViewColumn, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Колонки"))
            }
            Spacer(Modifier.weight(1f))
            Text(tr("записей: %s", state.qsos.size), color = x.muted, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { state.addQso() }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Новое QSO") + if (shortcut.isNotBlank()) "  $shortcut" else "")
            }
        }
        ActivationBanner(state)
        HorizontalDivider(color = x.line)

        val cols = state.tableColumns
        // Numbers count from the oldest contact of the log, so they stay the same whatever the order.
        val numbered = remember(state.allQsos) {
            state.allQsos.sortedBy { it.timeUtc }.withIndex().associate { (i, q) -> q.id to i + 1 }
        }
        val rows = remember(state.qsos, numbered, state.tableSort, state.tableSortDesc) {
            val pairs = state.qsos.map { it to (numbered[it.id] ?: 0) }
            val sorted = pairs.sortedWith(state.tableSort.comparator)
            if (state.tableSortDesc) sorted.reversed() else sorted
        }
        val groups: List<Pair<String?, List<Pair<Qso, Int>>>> = remember(rows, state.tableGroup) {
            val g = state.tableGroup ?: return@remember listOf(null to rows)
            val by = rows.groupBy { g.text(it.first, it.second).ifBlank { "—" } }
            val keys = when (g) {
                TableColumn.BAND -> by.keys.sortedBy { bandOrder(it) }
                TableColumn.DATE -> by.keys.sortedDescending()
                else -> by.keys.sorted()
            }
            keys.map { it to by.getValue(it) }
        }
        val total = cols.sumOf { it.width } + 16
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth.value >= total
            val h = rememberScrollState()
            Column(Modifier.fillMaxSize().then(if (wide) Modifier else Modifier.horizontalScroll(h))) {
                val width = if (wide) Modifier.fillMaxWidth() else Modifier.width(total.dp)
                // The header: a click sorts by the column, again — the other way round.
                Row(width.background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    cols.forEach { c ->
                        val on = state.tableSort == c
                        Row(
                            cellWidth(c, wide).height(34.dp).clickable(onClickLabel = tr("Сортировать: %s", c.title)) { state.sortTable(c) }.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.title, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (on) Icon(if (state.tableSortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                val list = rememberLazyListState()
                LaunchedEffect(state.newSavedTick) { if (state.newSavedTick > 0) list.scrollToItem(0) }
                LazyColumn(width.weight(1f), state = list) {
                    groups.forEach { (title, items) ->
                        if (title != null) stickyHeader(key = "g$title") {
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("  · " + items.size, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        items(items, key = { it.first.id }) { (q, no) ->
                            TableRow(state, q, no, cols, wide)
                        }
                    }
                }
            }
        }
    }
    if (columnsDialog) ColumnsDialog(state.tableColumns, state::changeTableColumns) { columnsDialog = false }
}

/** A cell's width: its own, or — when the window is wider than the columns need — a share of the room in proportion. */
private fun androidx.compose.foundation.layout.RowScope.cellWidth(c: TableColumn, wide: Boolean): Modifier =
    if (wide) Modifier.weight(c.width.toFloat()) else Modifier.width(c.width.dp)

@Composable
private fun TableRow(state: AppState, q: Qso, no: Int, cols: List<TableColumn>, wide: Boolean) {
    val x = LocalExtra.current
    val open = (state.pane == Pane.Edit || state.pane == Pane.Contest) && state.form.id == q.id
    val picked = q.id in state.selected
    val zebra = no % 2 == 0
    val bg = when {
        open || picked || q.id == state.logCursor -> MaterialTheme.colorScheme.primaryContainer
        zebra -> x.field
        else -> MaterialTheme.colorScheme.background
    }
    val ink = if (open || picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().background(bg).clickable { if (state.selecting) state.toggleSelected(q.id) else state.edit(q) }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cols.forEach { c ->
            Text(
                c.text(q, no), color = if (c == TableColumn.NO) x.muted else ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp, fontFamily = if (c == TableColumn.CALL || c == TableColumn.FREQ || c == TableColumn.LOCATOR) Mono else null,
                fontWeight = if (c == TableColumn.CALL) FontWeight.Bold else null,
                modifier = cellWidth(c, wide).padding(horizontal = 4.dp, vertical = 5.dp),
            )
        }
    }
}

/** «Группировать»: none, or a column with groups (band, mode, date, station, country, region). */
@Composable
private fun GroupChooser(state: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Icon(Icons.Filled.Workspaces, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(state.tableGroup?.let { tr("Группы: %s", it.title) } ?: tr("Без групп"), maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(tr("Без групп")) }, onClick = { open = false; state.changeTableGroup(null) })
            TableColumn.entries.filter { it.group }.forEach { c ->
                DropdownMenuItem(text = { Text(c.title) }, onClick = { open = false; state.changeTableGroup(c) })
            }
        }
    }
}

/** «Колонки»: which columns the table shows, in the order of the list. */
@Composable
private fun ColumnsDialog(chosen: List<TableColumn>, onChange: (List<TableColumn>) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Колонки таблицы")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                TableColumn.entries.forEach { c ->
                    val on = c in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            // Kept in the order of the list, whatever order they were ticked in.
                            val next = if (on) chosen - c else TableColumn.entries.filter { it in chosen || it == c }
                            if (next.isNotEmpty()) onChange(next)
                        }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(on, null)
                        Spacer(Modifier.width(8.dp))
                        Text(c.title)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text(tr("Готово")) } },
        dismissButton = { TextButton(onClick = { onChange(TableColumn.DEFAULT) }) { Text(tr("По умолчанию")) } },
    )
}

/**
 * The card over the table: a new or opened contact in a compact window in the middle, the table dimmed behind it.
 * Esc and the card's own buttons close it, as usual.
 */
@Composable
fun TableCardOverlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = 760.dp).fillMaxWidth(0.9f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp, shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.background,
        ) { content() }
    }
}
