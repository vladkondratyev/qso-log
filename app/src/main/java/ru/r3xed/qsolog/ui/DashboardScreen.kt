package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.StatBucket
import ru.r3xed.qsolog.data.StatFilter
import ru.r3xed.qsolog.data.CountryStat
import androidx.compose.ui.text.style.TextOverflow
import ru.r3xed.qsolog.data.StatPeriod
import ru.r3xed.qsolog.data.StatSummary
import ru.r3xed.qsolog.data.Stats
import ru.r3xed.qsolog.data.TimePoint
import ru.r3xed.qsolog.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the dashboard shows, counted off the main thread. */
private class DashData(
    val list: List<Qso>,
    val summary: StatSummary,
    val bucket: StatBucket,
    val timeline: List<TimePoint>,
    /** Bands that get their own colour in the stacked chart (the log's biggest, so colours do not move with filters). */
    val stackKeys: List<String>,
    val bands: List<Pair<String, Int>>,
    val modes: List<Pair<String, Int>>,
    val modeKeys: List<String>,
    val bandKeys: List<String>,
    val calls: List<Pair<String, Int>>,
    val countries: List<CountryStat>,
    val continents: List<Pair<String, Int>>,
    val weekHour: Array<IntArray>,
    val distance: IntArray,
)

private fun compute(all: List<Qso>, f: StatFilter, bucket: StatBucket): DashData {
    val list = Stats.filter(all, f)
    // Colour order comes from the whole log, not the filtered part: a band keeps its colour whatever is picked.
    val bandRank = Stats.counts(all) { it.band.lowercase() }.map { it.first }
    val modeRank = Stats.counts(all) { it.mode.uppercase() }.map { it.first }
    val stackKeys = bandRank.take(4)
    val (from, to) = Stats.span(list, f.period)
    val b = Stats.resolve(bucket, from, to)
    return DashData(
        list = list,
        summary = Stats.summary(list),
        bucket = b,
        timeline = Stats.timeline(list, b, from, to, split = { it.band.lowercase() }, keep = stackKeys.toSet()),
        stackKeys = stackKeys,
        bands = Stats.bandCounts(list),
        modes = Stats.counts(list) { it.mode.uppercase() },
        modeKeys = modeRank.take(Viz.SLOTS - 1),
        bandKeys = bandRank.take(Viz.SLOTS - 1),
        calls = Stats.top(Stats.counts(list) { it.call.uppercase() }, 10).filter { it.first != Stats.OTHER },
        countries = Stats.countryStats(list),
        continents = Stats.counts(list) { Stats.continent(it) },
        weekHour = Stats.weekHour(list),
        distance = Stats.distanceBins(list),
    )
}

/** Shares for a ring: the fixed [keys] in their colour order, everything else as "other"; empty classes left out. */
private fun shares(counts: List<Pair<String, Int>>, keys: List<String>): List<Pair<String, Int>> {
    val m = counts.toMap()
    val known = keys.map { it to (m[it] ?: 0) }
    val rest = counts.filter { it.first !in keys }.sumOf { it.second }
    return known + (Stats.OTHER to rest)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val x = LocalExtra.current
    BackHandler { vm.closeDashboard() }
    val f = vm.dashFilter
    val bucket = vm.dashBucket
    val all = vm.allQsos
    val data by produceState<DashData?>(null, all, f, bucket) {
        value = withContext(Dispatchers.Default) { compute(all, f, bucket) }
    }
    val hasContest = remember(all) { all.any { ContestMode.isContest(it.adif) } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeDashboard, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp))
            }
            Text(tr("Дашборд"), style = MaterialTheme.typography.headlineSmall)
        }

        // --- the parameters: one row, above every chart ---
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatPeriod.entries.forEach { p ->
                Pill(periodLabel(p), selected = f.period == p) { vm.changeDashFilter(f.copy(period = p)) }
            }
            val bands = remember(all) { BANDS.filter { b -> all.any { it.band.equals(b, true) } } }
            val modes = remember(all) { MODES.filter { m -> all.any { it.mode.equals(m, true) } } }
            PickPill(tr("Диапазон"), f.band, bands) { vm.changeDashFilter(f.copy(band = it)) }
            PickPill(tr("Вид"), f.mode, modes) { vm.changeDashFilter(f.copy(mode = it)) }
            if (hasContest || f.contestOnly) Pill(tr("Контест"), selected = f.contestOnly, icon = true) { vm.changeDashFilter(f.copy(contestOnly = !f.contestOnly)) }
        }

        val d = data
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        if (d.list.isEmpty()) {
            Text(
                if (all.isEmpty()) tr("Журнал пока пуст: графики появятся после первых связей.") else tr("Нет связей для этих условий. Выберите другой период или снимите фильтр."),
                style = MaterialTheme.typography.bodyLarge, color = x.muted, modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Summary(d.summary) }
            item { TimeCard(vm, d) }
            item { ShareCard(tr("Диапазоны"), d.bands, d.bandKeys, mono = true) }
            item { ShareCard(tr("Виды связи"), d.modes, d.modeKeys, mono = true) }
            item { HourCard(d) }
            item {
                ChartCard(tr("Чаще всего"), tr("10 позывных с наибольшим числом связей")) {
                    BarList(d.calls, Viz.accent(), labelWidth = 120, mono = true)
                }
            }
            item { CountryCard(d.countries) }
            item {
                ChartCard(tr("Континенты"), null) {
                    BarList(d.continents.map { continentName(it.first) to it.second }, Viz.accent(), labelWidth = 150)
                }
            }
            item { DistanceCard(d) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(s: StatSummary) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        val m = Modifier.weight(1f)
        StatTile("QSO", fmt(s.total), modifier = m)
        StatTile(tr("Позывных"), fmt(s.calls), modifier = m)
        StatTile(tr("Стран DXCC"), fmt(s.countries), tr("континентов: %s", s.continents), modifier = m)
        StatTile(tr("Активных дней"), fmt(s.activeDays), tr("≈ %s в день", String.format(Locale.ROOT, "%.1f", s.perActiveDay)), modifier = m)
        s.bestDay?.let { (day, n) -> StatTile(tr("Рекорд дня"), "$n QSO", DATE_FMT.format(day), modifier = m) }
        s.farthest?.let { q -> StatTile(tr("Самая дальняя"), formatKm(q.distanceKm!!), q.call, modifier = m) }
    }
}

@Composable
private fun TimeCard(vm: AppViewModel, d: DashData) {
    var form by rememberSaveable { mutableStateOf(TimeForm.COLUMNS) }
    var selected by remember(d) { mutableStateOf<Int?>(null) }
    val points = if (form == TimeForm.CUMULATIVE) Stats.cumulative(d.timeline) else d.timeline
    val stackColors = d.stackKeys.indices.map { Viz.series(it) } + Viz.other()
    val label = { i: Int -> dateLabel(points[i].start, d.bucket) }
    ChartCard(
        tr("Связи по времени"),
        when (d.bucket) {
            StatBucket.DAY -> tr("по дням, UTC")
            StatBucket.WEEK -> tr("по неделям, с понедельника")
            else -> tr("по месяцам")
        },
    ) {
        Choice(
            listOf(TimeForm.COLUMNS to tr("Столбцы"), TimeForm.LINE to tr("Линия"), TimeForm.CUMULATIVE to tr("Итог"), TimeForm.STACKED to tr("Диапазоны")),
            form,
        ) { form = it; selected = null }
        Spacer(Modifier.height(4.dp))
        Choice(
            listOf(StatBucket.AUTO to tr("Авто"), StatBucket.DAY to tr("День"), StatBucket.WEEK to tr("Неделя"), StatBucket.MONTH to tr("Месяц")),
            vm.dashBucket,
        ) { vm.changeDashBucket(it) }
        Spacer(Modifier.height(8.dp))
        // The tooltip line: the tapped column, or the hint.
        val sel = selected?.takeIf { it < points.size }
        Text(
            if (sel == null) tr("Нажмите на график, чтобы увидеть число") else buildString {
                append(label(sel)).append(": ").append(points[sel].count).append(" QSO")
                if (form == TimeForm.STACKED) {
                    val parts = (d.stackKeys + Stats.OTHER).mapNotNull { k -> points[sel].byKey[k]?.let { keyLabel(k) + " " + it } }
                    if (parts.isNotEmpty()) append(" · ").append(parts.joinToString(", "))
                }
            },
            style = MaterialTheme.typography.bodyMedium, fontWeight = if (sel == null) FontWeight.Normal else FontWeight.Bold,
            color = if (sel == null) LocalExtra.current.muted else MaterialTheme.colorScheme.onSurface,
        )
        TimeChart(
            values = points.map { it.count },
            form = form,
            xLabel = label,
            selected = sel,
            onSelect = { selected = it },
            stacks = if (form == TimeForm.STACKED) points.map { p -> (d.stackKeys + Stats.OTHER).map { p.byKey[it] ?: 0 } } else null,
            stackColors = stackColors,
        )
        if (form == TimeForm.STACKED) {
            Spacer(Modifier.height(6.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow {
                (d.stackKeys + Stats.OTHER).forEachIndexed { i, k ->
                    val n = points.sumOf { it.byKey[k] ?: 0 }
                    if (n > 0) LegendItem(stackColors[i], keyLabel(k), "$n")
                }
            }
        }
    }
}

/** Bands or modes: bars (every class, by frequency for bands) or a ring of the biggest classes plus "other". */
@Composable
private fun ShareCard(title: String, counts: List<Pair<String, Int>>, keys: List<String>, mono: Boolean) {
    var ring by rememberSaveable(title) { mutableStateOf(false) }
    ChartCard(title, null) {
        Choice(listOf(false to tr("Столбцы"), true to tr("Доли")), ring) { ring = it }
        Spacer(Modifier.height(10.dp))
        if (ring) {
            val items = shares(counts, keys).filter { it.second > 0 }
            val colors = items.map { (k, _) -> if (k == Stats.OTHER) Viz.other() else Viz.series(keys.indexOf(k)) }
            Donut(items.map { keyLabel(it.first) to it.second }, colors, "${counts.sumOf { it.second }}")
        } else {
            BarList(counts, Viz.accent(), labelWidth = 80, mono = mono)
        }
    }
}

@Composable
private fun HourCard(d: DashData) {
    var picked by remember(d) { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    val days = listOf(tr("Пн"), tr("Вт"), tr("Ср"), tr("Чт"), tr("Пт"), tr("Сб"), tr("Вс"))
    ChartCard(tr("Когда вы в эфире"), tr("день недели и час UTC")) {
        val p = picked
        Text(
            if (p == null) tr("Нажмите на клетку, чтобы увидеть число")
            else tr("%s, %s–%s UTC: %s QSO", days[p.first], "%02d".format(p.second), "%02d".format((p.second + 1) % 24), p.third),
            style = MaterialTheme.typography.bodyMedium, fontWeight = if (p == null) FontWeight.Normal else FontWeight.Bold,
            color = if (p == null) LocalExtra.current.muted else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        HeatMap(d.weekHour, days) { r, c, n -> picked = Triple(r, c, n) }
    }
}

@Composable
private fun DistanceCard(d: DashData) {
    val known = d.distance.sum()
    ChartCard(tr("Расстояние"), tr("связей с известным расстоянием: %s из %s", known, d.list.size)) {
        val b = Stats.DISTANCE_BOUNDS
        val labels = b.indices.map { i -> if (i == 0) "< ${b[0]}" else "${fmt(b[i - 1])}–${fmt(b[i])}" } + "> ${fmt(b.last())}"
        // Ordered bins on one series: one colour, in their own order (not sorted by count).
        BarList(labels.zip(d.distance.toList()).map { (l, n) -> "$l " + tr("км") to n }, Viz.accent(), labelWidth = 160)
    }
}

/** How the country list is ordered. */
private enum class CountrySort { QSOS, NAME, RECENT }

/**
 * Every country of the filtered log: contacts with a bar, different callsigns, continent, bands worked and the last
 * contact. The first 10 are shown, the rest open with one tap.
 */
@Composable
private fun CountryCard(stats: List<CountryStat>) {
    val x = LocalExtra.current
    var sort by remember { mutableStateOf(CountrySort.QSOS) }
    var all by remember { mutableStateOf(false) }
    val sorted = remember(stats, sort) {
        when (sort) {
            CountrySort.QSOS -> stats
            CountrySort.NAME -> stats.sortedBy { it.country }
            CountrySort.RECENT -> stats.sortedByDescending { it.last }
        }
    }
    val max = stats.maxOfOrNull { it.qsos }?.coerceAtLeast(1) ?: 1
    val color = Viz.accent()
    ChartCard(tr("Страны"), tr("стран DXCC: %s · по префиксу позывного", stats.size)) {
        Choice(listOf(CountrySort.QSOS to tr("Больше связей"), CountrySort.NAME to tr("А–Я"), CountrySort.RECENT to tr("Недавние")), sort) { sort = it }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (if (all) sorted else sorted.take(10)).forEach { c ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.country, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${c.qsos} QSO", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                    // The bar shows the share at a glance; the number above says it exactly.
                    Box(Modifier.padding(vertical = 3.dp).fillMaxWidth(c.qsos.toFloat() / max).height(6.dp).background(color, RoundedCornerShape(3.dp)))
                    Text(
                        listOfNotNull(
                            c.continent?.let { continentName(it) },
                            tr("позывных: %s", c.calls),
                            c.bands.joinToString(" "),
                            tr("последняя %s", DATE_FMT.format(java.time.Instant.ofEpochMilli(c.last).atOffset(java.time.ZoneOffset.UTC).toLocalDate())),
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = x.muted,
                    )
                }
            }
            if (sorted.size > 10) {
                Text(
                    if (all) tr("Свернуть") else tr("Показать все: %s", sorted.size),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

/** A card with a title, a line under it and the chart. */
@Composable
private fun ChartCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, shape).border(1.dp, x.line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = x.muted)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A row of small toggle buttons for a chart's form. */
@Composable
private fun <T> Choice(items: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (v, label) -> Pill(label, selected = v == selected, small = true) { onPick(v) } }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, small: Boolean = false, icon: Boolean = false, onClick: () -> Unit) {
    val x = LocalExtra.current
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.height(if (small) 34.dp else 42.dp).clip(RoundedCornerShape(21.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else x.field)
            .clickable(onClick = onClick).padding(horizontal = if (small) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) { Icon(Icons.Filled.Flag, null, Modifier.size(18.dp), tint = fg); Spacer(Modifier.width(4.dp)) }
        Text(text, fontSize = if (small) 14.sp else 15.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** A filter pill with a list: "Диапазон: все" → one of [options] or all. */
@Composable
private fun PickPill(title: String, value: String?, options: List<String>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        val x = LocalExtra.current
        val selected = value != null
        val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Row(
            Modifier.height(42.dp).clip(RoundedCornerShape(21.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else x.field)
                .clickable { open = true }.padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$title: " + (value ?: tr("все")), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null, tint = fg)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(tr("все"), fontSize = 17.sp) }, onClick = { open = false; onPick(null) })
            options.forEach { o -> DropdownMenuItem(text = { Text(o, fontFamily = Mono, fontSize = 17.sp) }, onClick = { open = false; onPick(o) }) }
        }
    }
}

private fun periodLabel(p: StatPeriod) = when (p) {
    StatPeriod.WEEK -> tr("7 дней")
    StatPeriod.MONTH -> tr("30 дней")
    StatPeriod.YEAR -> tr("Год")
    StatPeriod.ALL -> tr("Всё время")
}

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.yyyy")

private fun dateLabel(d: LocalDate, b: StatBucket) = if (b == StatBucket.MONTH) MONTH_FMT.format(d) else DAY_FMT.format(d)

private fun keyLabel(k: String) = if (k == Stats.OTHER) tr("Другие") else k

private fun fmt(n: Int) = "%,d".format(n).replace(',', ' ')

private fun continentName(code: String) = when (code) {
    "EU" -> tr("Европа")
    "AS" -> tr("Азия")
    "NA" -> tr("Северная Америка")
    "SA" -> tr("Южная Америка")
    "AF" -> tr("Африка")
    "OC" -> tr("Океания")
    "AN" -> tr("Антарктида")
    else -> code
}
