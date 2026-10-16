package ru.r3xed.qsolog.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.cat.BandScale
import ru.r3xed.qsolog.cat.BandUse
import ru.r3xed.qsolog.cat.CatFormat
import ru.r3xed.qsolog.cat.RigMeterScale
import ru.r3xed.qsolog.cat.RigState
import ru.r3xed.qsolog.tr

// The same file is in the phone and the computer app: the meters and the band scale of the transceiver panel.

private val METER_OK = Color(0xFF2E9E57)
private val METER_HOT = Color(0xFFD9473A)
private val NEEDLE = Color(0xFFD9473A)

/** The colour of a part of a band on the scale. */
fun bandUseColor(u: BandUse): Color = when (u) {
    BandUse.CW -> Color(0xFF4C7BD9)
    BandUse.DIGITAL -> Color(0xFF9B59D0)
    BandUse.BEACON -> Color(0xFFE0A030)
    BandUse.PHONE -> Color(0xFF3FAF6A)
    BandUse.FM -> Color(0xFF6E8CA0)
}

fun bandUseTitle(u: BandUse): String = when (u) {
    BandUse.CW -> "CW"
    BandUse.DIGITAL -> tr("цифра")
    BandUse.BEACON -> tr("маяки")
    BandUse.PHONE -> "SSB"
    BandUse.FM -> "FM"
}

/**
 * The radio's meters: the S-meter on receive; output power, SWR and ALC on transmit (what the radio reports).
 * Nothing when the radio gives no meter at all.
 */
@Composable
fun RigMeters(s: RigState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.tx == true) {
            MeterBar(
                "Po", (s.powerPct ?: 0) / 100f, s.powerPct?.let { "$it%" } ?: "TX", warnFrom = 1.01f,
                labels = listOf(0f to "0", 0.25f to "25", 0.5f to "50", 0.75f to "75", 1f to "100"),
            )
            s.swr?.let { swr ->
                MeterBar(
                    "SWR", RigMeterScale.swrPosition(swr), CatFormat.swr(swr), warnFrom = RigMeterScale.swrPosition(2.0),
                    labels = RigMeterScale.SWR_LABELS.map { (v, l) -> RigMeterScale.swrPosition(v) to l },
                )
            }
            s.alcPct?.let { alc ->
                // The ALC zone is the first half of the scale: past it the signal is overdriven.
                MeterBar("ALC", alc / 200f, "$alc%", warnFrom = 0.5f, labels = listOf(0f to "0", 0.5f to tr("зона"), 1f to ""))
            }
        } else s.sDb?.let { db ->
            MeterBar(
                "S", RigMeterScale.sPosition(db), CatFormat.sMeter(db), warnFrom = 0.6f,
                labels = RigMeterScale.S_LABELS.map { (v, l) -> RigMeterScale.sPosition(v) to l },
            )
        }
    }
}

/** One meter: its name, a bar green up to [warnFrom] and red past it, the scale's labels, the reading in figures. */
@Composable
private fun MeterBar(name: String, fraction: Float, value: String, warnFrom: Float, labels: List<Pair<Float, String>>) {
    val x = LocalExtra.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(180), label = "meter")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = x.muted, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(x.line, size = size, cornerRadius = r)
                val warnX = size.width * warnFrom.coerceAtMost(1f)
                val fillX = size.width * f
                drawRoundRect(METER_OK, size = Size(minOf(fillX, warnX), size.height), cornerRadius = r)
                if (fillX > warnX) drawRect(METER_HOT, topLeft = Offset(warnX, 0f), size = Size(fillX - warnX, size.height))
                labels.forEach { (p, _) -> drawRect(x.muted, topLeft = Offset(size.width * p - 0.5f, 0f), size = Size(1f, size.height * 0.35f)) }
            }
            ScaleLabels(labels)
        }
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp).width(64.dp))
    }
}

/** Labels at their places along a scale, each centred on its point (the ends kept inside). */
@Composable
private fun ScaleLabels(labels: List<Pair<Float, String>>, size: Int = 11) {
    val x = LocalExtra.current
    // The height is the text's own: a fixed one cut the labels off at some font sizes.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        labels.forEach { (p, l) ->
            if (l.isEmpty()) return@forEach
            val half = (l.length * size * 0.32f).dp
            val at = (w * p - half).coerceIn(0.dp, maxOf(0.dp, w - half * 2))
            Text(l, fontSize = size.sp, color = x.muted, fontFamily = Mono, maxLines = 1, modifier = Modifier.offset(x = at))
        }
    }
}

/**
 * The band the radio is on: its parts in colour (CW, digital, beacons, SSB, FM), the FT8 and FT4 frequencies, the
 * radio's frequency as a red needle. A tap or a drag along it tunes the radio there (to whole kHz).
 * [ticks] — about how many frequency labels fit.
 */
@Composable
fun BandScaleBar(hz: Long, onTune: (Long) -> Unit, modifier: Modifier = Modifier, ticks: Int = 4) {
    val scale = BandScale.of(hz) ?: return
    val x = LocalExtra.current
    val pos by animateFloatAsState(scale.position(hz), tween(150), label = "needle")
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(scale.band, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            // The legend: only the kinds this band has.
            scale.parts.map { it.use }.distinct().forEach { u ->
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(bandUseColor(u)))
                Text(" " + bandUseTitle(u) + "  ", fontSize = 11.sp, color = x.muted)
            }
        }
        // FT8 and FT4 close together (14.074 and 14.080) share one label.
        val marks = scale.marks.fold(emptyList<Pair<Float, String>>()) { acc, m ->
            val p = scale.position(m.hz)
            val last = acc.lastOrNull()
            if (last != null && p - last.first < 0.06f) acc.dropLast(1) + (last.first to last.second + "·" + m.label) else acc + (p to m.label)
        }
        ScaleLabels(marks)
        Canvas(
            Modifier.fillMaxWidth().height(30.dp)
                .pointerInput(scale) { detectTapGestures { o -> onTune(scale.freqAt(o.x / size.width)) } }
                .pointerInput(scale) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onTune(scale.freqAt(change.position.x / size.width))
                    }
                },
        ) {
            val barTop = size.height * 0.2f
            val barH = size.height * 0.6f
            drawRoundRect(x.line, topLeft = Offset(0f, barTop), size = Size(size.width, barH), cornerRadius = CornerRadius(4f))
            scale.parts.forEach { p ->
                val l = size.width * scale.position(p.fromHz)
                val r = size.width * scale.position(p.toHz)
                drawRect(bandUseColor(p.use).copy(alpha = 0.75f), topLeft = Offset(l, barTop), size = Size(r - l, barH))
            }
            scale.marks.forEach { m ->
                val mx = size.width * scale.position(m.hz)
                drawRect(Color.White.copy(alpha = 0.9f), topLeft = Offset(mx - 1f, barTop), size = Size(2f, barH))
            }
            val nx = size.width * pos
            drawRect(NEEDLE, topLeft = Offset(nx - 2.5f, 0f), size = Size(5f, size.height))
        }
        ScaleLabels(scale.ticks(ticks).map { scale.position(it) to CatFormat.mhz(it) })
    }
}
