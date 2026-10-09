package ru.r3xed.qsolog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * Chart colours in the app's own palette (QSO-LOG blue #0A5C8A first, then amber, green, violet, rose), validated
 * with the dataviz palette checker against the app's surfaces (#FFFFFF, #10171E): categorical slots in fixed order,
 * a recessive grey for "other", and a one-hue ramp of the app's blue for magnitude. Amber is below 3:1 on white and
 * green/amber are close for protanopes on the dark surface, so every chart shows its values as text (legend with numbers).
 */
object Viz {
    private val seriesLight = listOf(Color(0xFF0A5C8A), Color(0xFFD98B00), Color(0xFF1E7B45), Color(0xFF7B61C9), Color(0xFFC0507A))
    private val seriesDark = listOf(Color(0xFF3A9AD4), Color(0xFFC08A00), Color(0xFF2F9E62), Color(0xFF8C76DA), Color(0xFFC95C88))
    private val rampLight = listOf(0xFFD3E8F5, 0xFFA9D2EC, 0xFF7AB8DE, 0xFF4A9ACB, 0xFF1F7BB0, 0xFF0A5C8A, 0xFF06324D).map(::Color)
    // On the dark surface "more" is lighter: the same blue ramp read the other way.
    private val rampDark = listOf(0xFF12405E, 0xFF175A82, 0xFF1F74A6, 0xFF3A8FC2, 0xFF6BBEEF, 0xFF9FD4F4, 0xFFD3E8F5).map(::Color)

    @Composable
    fun dark() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable
    fun series(i: Int): Color = (if (dark()) seriesDark else seriesLight)[i % seriesLight.size]

    @Composable
    fun accent(): Color = series(0)

    @Composable
    fun other(): Color = if (dark()) Color(0xFF5F6D7B) else Color(0xFF9AA6B2)

    @Composable
    fun ramp(): List<Color> = if (dark()) rampDark else rampLight

    const val SLOTS = 5
}

/** Round axis steps: 1, 2, 5 × 10^n. */
fun niceStep(max: Int, ticks: Int = 4): Int {
    if (max <= ticks) return 1
    val raw = max.toDouble() / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * mag >= raw } * mag
    return step.toInt().coerceAtLeast(1)
}

/** A small swatch + text: the identity of a series never rests on colour alone. */
@Composable
fun LegendItem(color: Color, text: String, value: String? = null) {
    val x = LocalExtra.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 14.dp, bottom = 4.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (value != null) {
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = x.muted)
        }
    }
}

/**
 * Horizontal bars, one series in one colour: label · bar · value at the tip. Every value is written out, so the bars
 * need no axis. [labelWidth] keeps the bars aligned.
 */
@Composable
fun BarList(items: List<Pair<String, Int>>, color: Color, labelWidth: Int = 96, mono: Boolean = false) {
    val x = LocalExtra.current
    val max = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    // Room for the widest value plus the gap before it.
    val valueRoom = (12 + 10 * "$max".length).dp
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label, modifier = Modifier.width(labelWidth.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                )
                // The longest bar leaves room for its value: the number never falls off the end.
                BoxWithConstraints(Modifier.weight(1f)) {
                    val barMax = (maxWidth - valueRoom).coerceAtLeast(0.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n > 0) Box(
                            Modifier.width((barMax * (n.toFloat() / max)).coerceAtLeast(3.dp)).height(18.dp)
                                .background(color, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("$n", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        if (items.isEmpty()) Text("—", color = x.muted)
    }
}

enum class TimeForm { COLUMNS, LINE, CUMULATIVE, STACKED }

/**
 * Contacts over time on one y-axis. [values] per column; with [stacks] each column is split into segments
 * (colours by [stackColors], a 2 px surface gap between them). A tap selects a column and [onSelect] reports it.
 * [xLabel] formats the column for the axis (first, middle, last).
 */
@Composable
fun TimeChart(
    values: List<Int>,
    form: TimeForm,
    xLabel: (Int) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    stacks: List<List<Int>>? = null,
    stackColors: List<Color> = emptyList(),
) {
    val x = LocalExtra.current
    val accent = Viz.accent()
    val surface = MaterialTheme.colorScheme.surface
    val ink = x.muted
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = ink)
    val n = values.size
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val step = niceStep(max)
    val top = (ceil(max.toDouble() / step) * step).toInt().coerceAtLeast(step)
    Canvas(
        Modifier.fillMaxWidth().height(210.dp).pointerInput(n, form) {
            detectTapGestures { p ->
                val left = 40.dp.toPx()
                val w = size.width - left
                if (n == 0 || p.x < left) { onSelect(null); return@detectTapGestures }
                val i = ((p.x - left) / (w / n)).toInt().coerceIn(0, n - 1)
                onSelect(if (i == selected) null else i)
            }
        },
    ) {
        val left = 40.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val plotTop = 8.dp.toPx()
        val w = size.width - left
        val h = bottom - plotTop
        fun yOf(v: Int) = bottom - h * v / top
        // Hairline grid and round ticks.
        var t = 0
        while (t <= top) {
            val y = yOf(t)
            drawLine(x.line, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
            val tl = measurer.measure("$t", labelStyle)
            drawText(tl, topLeft = Offset(left - tl.size.width - 6.dp.toPx(), y - tl.size.height / 2f))
            t += step
        }
        if (n == 0) return@Canvas
        val slot = w / n
        // x labels: first, middle, last (never one per column).
        listOf(0, n / 2, n - 1).distinct().forEach { i ->
            val tl = measurer.measure(xLabel(i), labelStyle)
            val cx = left + slot * (i + 0.5f)
            val lx = (cx - tl.size.width / 2f).coerceIn(left, size.width - tl.size.width)
            drawText(tl, topLeft = Offset(lx, bottom + 4.dp.toPx()))
        }
        selected?.let { i ->
            // Selection: a soft band behind the column / a crosshair on the line.
            drawRect(x.field, Offset(left + slot * i, plotTop), Size(slot, h))
        }
        when (form) {
            TimeForm.COLUMNS, TimeForm.STACKED -> {
                val bw = (slot * 0.7f).coerceAtMost(24.dp.toPx()).coerceAtLeast(1f)
                val r = 4.dp.toPx().coerceAtMost(bw / 2)
                for (i in 0 until n) {
                    val cx = left + slot * (i + 0.5f)
                    if (form == TimeForm.STACKED && stacks != null) {
                        var y = bottom
                        val parts = stacks[i]
                        val lastIdx = parts.indexOfLast { it > 0 }
                        parts.forEachIndexed { k, v ->
                            if (v <= 0) return@forEachIndexed
                            val segH = h * v / top
                            val gap = if (k == lastIdx) 0f else 2.dp.toPx()
                            val segTop = y - segH
                            if (k == lastIdx) roundTop(stackColors[k], cx - bw / 2, segTop, bw, segH, r)
                            else drawRect(stackColors[k], Offset(cx - bw / 2, segTop + gap), Size(bw, (segH - gap).coerceAtLeast(0f)))
                            y = segTop
                        }
                    } else if (values[i] > 0) {
                        val y = yOf(values[i])
                        roundTop(accent, cx - bw / 2, y, bw, bottom - y, r)
                    }
                }
            }
            TimeForm.LINE, TimeForm.CUMULATIVE -> {
                val path = Path()
                val area = Path()
                for (i in 0 until n) {
                    val px = left + slot * (i + 0.5f)
                    val py = yOf(values[i])
                    if (i == 0) { path.moveTo(px, py); area.moveTo(px, bottom); area.lineTo(px, py) } else { path.lineTo(px, py); area.lineTo(px, py) }
                }
                area.lineTo(left + slot * (n - 0.5f), bottom); area.close()
                drawPath(area, accent.copy(alpha = 0.10f))
                drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // End dot (and the selected one) with a surface ring.
                listOfNotNull(n - 1, selected).distinct().forEach { i ->
                    val c = Offset(left + slot * (i + 0.5f), yOf(values[i]))
                    drawCircle(surface, 6.dp.toPx(), c)
                    drawCircle(accent, 4.dp.toPx(), c)
                }
            }
        }
        drawLine(ink.copy(alpha = 0.5f), Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1f)
    }
}

/** A column with a 4 px rounded data end and a square foot on the baseline. */
private fun DrawScope.roundTop(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
    if (h <= 0f) return
    val rr = r.coerceAtMost(h)
    val path = Path().apply {
        addRoundRect(RoundRect(x, y, x + w, y + h, topLeftCornerRadius = CornerRadius(rr), topRightCornerRadius = CornerRadius(rr)))
    }
    drawPath(path, color)
}

/**
 * Part-to-whole ring for a few classes (≤ 6 with "other"), 2 px surface gaps between segments; the legend beside it
 * carries every value and share, so nothing depends on the angle or the colour alone.
 */
@Composable
fun Donut(items: List<Pair<String, Int>>, colors: List<Color>, center: String) {
    val total = items.sumOf { it.second }.coerceAtLeast(1)
    val surface = MaterialTheme.colorScheme.surface
    var picked by remember(items) { mutableStateOf<Int?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(150.dp).pointerInput(items) {
                    detectTapGestures { p ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        if (hypot(p.x - c.x, p.y - c.y) > size.width / 2f) { picked = null; return@detectTapGestures }
                        val a = (atan2(p.y - c.y, p.x - c.x) * 180 / PI + 90 + 360) % 360
                        var acc = 0.0
                        picked = items.indices.firstOrNull { i -> acc += items[i].second * 360.0 / total; a < acc }
                    }
                },
            ) {
                val stroke = 26.dp.toPx()
                val d = size.minDimension - stroke
                var start = -90f
                items.forEachIndexed { i, (_, v) ->
                    val sweep = v * 360f / total
                    val w = if (picked == i) stroke + 6.dp.toPx() else stroke
                    drawArc(colors[i], start, sweep, false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(w))
                    // The surface gap: a thin surface line at each segment's start.
                    if (items.size > 1) drawArc(surface, start - 0.8f, 1.6f, false, Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(w + 2))
                    start += sweep
                }
            }
            val p = picked
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (p == null) center else "${items[p].second}", style = MaterialTheme.typography.titleMedium)
                if (p != null) Text("${items[p].second * 100 / total}%", style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            items.forEachIndexed { i, (label, v) -> LegendItem(colors[i], label, "$v · ${v * 100 / total}%") }
        }
    }
}

/**
 * Weekday × UTC hour heat map on the one-hue ramp (more is darker on light, lighter on dark); empty cells stay the
 * field colour. A tap names the cell and its count; the scale under the grid gives the range.
 */
@Composable
fun HeatMap(grid: Array<IntArray>, dayNames: List<String>, onPick: (Int, Int, Int) -> Unit) {
    val x = LocalExtra.current
    val ramp = Viz.ramp()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = x.muted)
    val max = grid.maxOf { it.maxOrNull() ?: 0 }.coerceAtLeast(1)
    fun colorOf(v: Int) = if (v == 0) x.field else ramp[((v - 1) * ramp.size / max).coerceIn(0, ramp.size - 1)]
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(200.dp).pointerInput(grid) {
                detectTapGestures { p ->
                    val left = 30.dp.toPx()
                    val cw = (size.width - left) / 24f
                    val ch = (size.height - 18.dp.toPx()) / 7f
                    val col = ((p.x - left) / cw).toInt()
                    val row = (p.y / ch).toInt()
                    if (col in 0..23 && row in 0..6) onPick(row, col, grid[row][col])
                }
            },
        ) {
            val left = 30.dp.toPx()
            val cw = (size.width - left) / 24f
            val ch = (size.height - 18.dp.toPx()) / 7f
            val gap = 2.dp.toPx()
            for (r in 0 until 7) {
                val tl = measurer.measure(dayNames[r], labelStyle)
                drawText(tl, topLeft = Offset(0f, r * ch + (ch - tl.size.height) / 2f))
                for (c in 0 until 24) {
                    drawRoundRect(
                        colorOf(grid[r][c]), Offset(left + c * cw + gap / 2, r * ch + gap / 2), Size(cw - gap, ch - gap),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            listOf(0, 6, 12, 18, 23).forEach { c ->
                val tl = measurer.measure("$c", labelStyle)
                drawText(tl, topLeft = Offset(left + c * cw + (cw - tl.size.width) / 2f, 7 * ch + 2.dp.toPx()))
            }
        }
        // Scale: 0 … max.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("0", style = MaterialTheme.typography.bodySmall, color = x.muted)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(14.dp).background(x.field, RoundedCornerShape(2.dp)))
            ramp.forEach { Box(Modifier.padding(start = 2.dp).size(14.dp).background(it, RoundedCornerShape(2.dp))) }
            Spacer(Modifier.width(6.dp))
            Text("$max QSO", style = MaterialTheme.typography.bodySmall, color = x.muted)
        }
    }
}

/** A headline number: label, value, an optional line under it. */
@Composable
fun StatTile(label: String, value: String, note: String? = null, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    Column(
        modifier.background(x.field, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp).widthIn(min = 120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
