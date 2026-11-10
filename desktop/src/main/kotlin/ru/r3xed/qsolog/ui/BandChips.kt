package ru.r3xed.qsolog.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.utc
import java.time.format.DateTimeFormatter

private val SHORT = DateTimeFormatter.ofPattern("dd.MM.yy")

/**
 * The bands a station was worked on, compact: "40m 12.09.26" per band, wrapping as needed. [current] (the band of
 * the open card) gets a border.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BandChips(byBand: List<Qso>, ink: Color, chip: Color, current: String = "", modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        byBand.forEach { q ->
            val here = q.band.equals(current, ignoreCase = true)
            val shape = RoundedCornerShape(8.dp)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(q.band) }
                    append(" " + SHORT.format(utc(q.timeUtc)))
                },
                fontFamily = Mono, fontSize = 14.sp, color = ink, maxLines = 1,
                modifier = Modifier.background(chip, shape)
                    .then(if (here) Modifier.border(1.5.dp, ink, shape) else Modifier)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Previous QSOs as a table, as in N1MM: a row per band worked (and the card's [band]), a column per mode group, the
 * number of contacts in each cell. The card's band and mode group are outlined: a number there is a repeat.
 */
@Composable
fun BandModeGrid(h: ru.r3xed.qsolog.data.CallHistory, band: String, mode: String, ink: Color, chip: Color, modifier: Modifier = Modifier) {
    val rows = h.grid(band)
    if (rows.isEmpty()) return
    val group = ru.r3xed.qsolog.data.ModeGroup.of(mode)
    val shape = RoundedCornerShape(6.dp)
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        @Composable
        fun cell(text: String, w: Int, bold: Boolean = false, fill: Color? = null, outline: Boolean = false) = Text(
            text, color = ink, fontSize = 14.sp, fontFamily = Mono, maxLines = 1,
            fontWeight = if (bold) FontWeight.Bold else null,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(w.dp)
                .then(if (fill != null) Modifier.background(fill, shape) else Modifier)
                .then(if (outline) Modifier.border(2.dp, ink, shape) else Modifier)
                .padding(vertical = 1.dp),
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            cell("", 54)
            ru.r3xed.qsolog.data.ModeGroup.entries.forEach { cell(it.title, 46, bold = true) }
        }
        rows.forEach { (b, counts) ->
            val here = b.equals(band, ignoreCase = true)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                cell(b, 54, bold = here)
                ru.r3xed.qsolog.data.ModeGroup.entries.forEach { g ->
                    val n = counts[g] ?: 0
                    cell(if (n > 0) "$n" else "·", 46, bold = n > 0, fill = if (n > 0) chip else null, outline = here && g == group)
                }
            }
        }
    }
}
