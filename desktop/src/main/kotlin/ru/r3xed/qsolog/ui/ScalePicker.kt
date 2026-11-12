package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.UiScale
import ru.r3xed.qsolog.tr

/**
 * "Масштаб интерфейса" from 50% to 150% by 10%: − and + a step at a time, or the slider. The slider applies the scale
 * when let go, so the window is not laid out anew at every step of the drag.
 */
@Composable
fun ScalePicker(scale: UiScale, onChange: (UiScale) -> Unit) {
    var drag by remember(scale) { mutableStateOf(scale.percent.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = { onChange(UiScale.smaller(scale)) }, enabled = scale != UiScale.STEPS.last()) {
            Icon(Icons.Filled.Remove, tr("Мельче"))
        }
        Text(
            "${Math.round(drag)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        FilledTonalIconButton(onClick = { onChange(UiScale.bigger(scale)) }, enabled = scale != UiScale.STEPS.first()) {
            Icon(Icons.Filled.Add, tr("Крупнее"))
        }
        Slider(
            value = drag,
            onValueChange = { drag = (Math.round(it / 10f) * 10).toFloat() },
            onValueChangeFinished = { onChange(UiScale.of(Math.round(drag))) },
            valueRange = 50f..150f,
            steps = 9,
            modifier = Modifier.weight(1f),
        )
    }
}
