package ru.r3xed.qsolog.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
