package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.data.CustomTheme
import ru.r3xed.qsolog.tr

/**
 * "Своя" theme: the light or dark base, the five main colours (background, text, accent, groups, outlines) and the
 * rest under "Дополнительно". A tap on a colour opens [ColorPickerDialog].
 */
@Composable
fun CustomThemeEditor(theme: CustomTheme, onChange: (CustomTheme) -> Unit) {
    var more by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<CustomTheme.Role?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Основа: с какой темы начать"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to tr("Светлая"), true to tr("Тёмная")).forEach { (dark, title) ->
                val on = theme.dark == dark
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else LocalExtra.current.field)
                        .selectable(selected = on, role = Role.RadioButton) { if (!on) onChange(CustomTheme.base(dark)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(tr("Другая основа сбрасывает цвета к этой теме."), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        CustomTheme.BASIC.forEach { r -> ColorLine(r, theme[r]) { picking = r } }
        TextButton(onClick = { more = !more }) { Text(if (more) tr("Скрыть дополнительные цвета") else tr("Дополнительно: ещё %s цветов", CustomTheme.Role.entries.size - CustomTheme.BASIC.size)) }
        if (more) CustomTheme.Role.entries.filter { it !in CustomTheme.BASIC }.forEach { r -> ColorLine(r, theme[r]) { picking = r } }
    }
    picking?.let { r ->
        ColorPickerDialog(tr(r.ru), theme[r], onPick = { onChange(theme.with(r, it)); picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun ColorLine(r: CustomTheme.Role, argb: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalExtra.current.field).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(argb.toInt())).border(1.dp, LocalExtra.current.line, RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Text(tr(r.ru), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(CustomTheme.hex(argb), fontFamily = Mono, color = LocalExtra.current.muted)
    }
}

/** Ready swatches, the hue, saturation and brightness, and the code (#0A5C8A). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var color by remember { mutableStateOf(Color(initial.toInt())) }
    var hex by remember { mutableStateOf(CustomTheme.hex(initial)) }
    val hsv = remember(color) { FloatArray(3).also { rgbToHsv(color, it) } }
    fun set(c: Color) { color = c; hex = CustomTheme.hex(c.toArgb().toLong() and 0xFFFFFFFF) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(color).border(1.dp, LocalExtra.current.line, RoundedCornerShape(10.dp)))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CustomTheme.SWATCHES.forEach { s ->
                        val c = Color(s.toInt())
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(c)
                                .border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.primary else LocalExtra.current.line, RoundedCornerShape(6.dp))
                                .clickable { set(c) },
                        )
                    }
                }
                Text(tr("Оттенок"), style = MaterialTheme.typography.bodySmall)
                Slider(hsv[0], { set(Color.hsv(it, hsv[1], hsv[2])) }, valueRange = 0f..359.9f)
                Text(tr("Насыщенность"), style = MaterialTheme.typography.bodySmall)
                Slider(hsv[1], { set(Color.hsv(hsv[0], it, hsv[2])) }, valueRange = 0f..1f)
                Text(tr("Яркость"), style = MaterialTheme.typography.bodySmall)
                Slider(hsv[2], { set(Color.hsv(hsv[0], hsv[1], it)) }, valueRange = 0f..1f)
                OutlinedTextField(
                    hex, { v -> hex = v.take(9); CustomTheme.parseHex(v)?.let { color = Color(it.toInt()) } },
                    label = { Text(tr("Код цвета")) }, singleLine = true, textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onPick(color.toArgb().toLong() and 0xFFFFFFFF) }) { Text(tr("Готово")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

private fun rgbToHsv(c: Color, out: FloatArray) {
    val r = c.red; val g = c.green; val b = c.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    out[0] = if (h < 0) h + 360f else h
    out[1] = if (max == 0f) 0f else d / max
    out[2] = max
}

/** "Контрастная обводка активного поля": the switch and what it does. */
@Composable
fun FocusRingSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(on, role = Role.Switch) { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(tr("Контрастная обводка активного поля ввода"), style = MaterialTheme.typography.bodyLarge)
            Text(
                tr("Поле с курсором обводится толстой яркой рамкой — видно издалека и при плохом зрении. Цвет рамки — в «Своей» теме, «Дополнительно»."),
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}
