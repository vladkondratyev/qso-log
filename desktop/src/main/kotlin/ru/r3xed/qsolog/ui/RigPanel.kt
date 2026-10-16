package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.Pane
import ru.r3xed.qsolog.cat.BandPlan
import ru.r3xed.qsolog.cat.CatFormat
import ru.r3xed.qsolog.cat.CatModes
import ru.r3xed.qsolog.cat.CatStatus
import ru.r3xed.qsolog.cat.Rigs
import ru.r3xed.qsolog.cat.describe
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.data.freqMhz
import ru.r3xed.qsolog.tr

/** A dialog of the panel's own: a card over the dimmed window, Esc or a click outside closes it. */
@Composable
private fun DialogWindowless(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { content() }
    }
}

/** Modes the panel offers: what a radio can be switched to. */
internal val RIG_MODES = listOf("CW", "SSB", "FT8", "FT4", "RTTY", "AM", "FM")

/** Tuning steps of the panel, Hz. */
internal val TUNE_STEPS = listOf(10, 100, 1000, 10_000)

internal fun stepLabel(hz: Int): String = if (hz >= 1000) tr("%s кГц", hz / 1000) else tr("%s Гц", hz)

/**
 * The transceiver panel, over the window: the frequency (dragged sideways or turned by the mouse wheel like a dial, or ±
 * by the step), a frequency typed in kHz, the bands and the modes. Opened by a click on the TRX line or by its key.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun RigPanel(vm: AppState) {
    val x = LocalExtra.current
    DialogWindowless(onDismiss = { vm.showRigPanel = false }) {
        Column(
            Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val rig = vm.catRig
            val st = vm.catStatus
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(10.dp).clip(CircleShape).background(if (rig != null) x.ok else if (st is CatStatus.Failed) MaterialTheme.colorScheme.error else x.muted))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Пульт трансивера"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (vm.catConfig.enabled) Rigs.title(vm.catConfig.model) + if (rig == null) " · " + st.describe() else "" else tr("CAT выключен"),
                        style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 2,
                    )
                }
                FilledTonalIconButton(onClick = {
                    vm.showRigPanel = false
                    vm.openSettings(vm.pane.takeIf { it == Pane.Edit || it == Pane.Contest } ?: Pane.Empty)
                }) { Icon(Icons.Filled.Settings, tr("Настройки CAT")) }
            }
            if (rig == null) {
                val digi = vm.rig
                Text(
                    if (digi != null) tr("Частоту %s сообщает %s. Управлять трансивером оттуда нельзя: он подключён к той программе. Чтобы управлять из QSO-LOG, подключите CAT здесь — например, через Hamlib rigctld, к которому подключён и %s.", CatFormat.khz(digi.freqHz ?: 0), vm.rigSource, vm.rigSource)
                    else tr("Трансивер не отвечает. Включите CAT в настройках, выберите модель и подключение — тогда здесь можно менять частоту, диапазон и вид связи."),
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }
            val hz = rig.freqHz ?: 0L
            // While the radio has not yet answered a command, the panel shows where it was sent.
            var target by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(target) { if (target != null) { delay(1500); target = null } }
            val shown = target ?: hz
            fun tune(to: Long) {
                if (to <= 0) return
                target = to
                vm.rigTune(to)
            }
            val step = vm.tuneStep
            val density = LocalDensity.current
            val perStep = with(density) { 14.dp.toPx() }
            var dragged by remember { mutableStateOf(0f) }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(x.field)
                    // The mouse wheel turns the dial too: a notch is a step.
                    .onPointerEvent(PointerEventType.Scroll) { ev ->
                        val dy = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) {
                            val base = target ?: vm.catRig?.freqHz ?: return@onPointerEvent
                            tune((base / step + (if (dy < 0) 1 else -1)) * step)
                        }
                    }
                    .pointerInput(step) {
                        detectHorizontalDragGestures(onDragEnd = { dragged = 0f }) { change, dx ->
                            change.consume()
                            dragged += dx
                            val steps = (dragged / perStep).toInt()
                            if (steps != 0) {
                                dragged -= steps * perStep
                                val base = target ?: vm.catRig?.freqHz ?: return@detectHorizontalDragGestures
                                tune((base / step + steps) * step)
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(CatFormat.khz(shown), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, maxLines = 1)
                    Text(
                        listOfNotNull(tr("кГц"), rig.radioMode, rig.sDb?.let { CatFormat.sMeter(it) }).joinToString(" · ") + " · " + tr("тяните мышью влево-вправо или крутите колесо"),
                        style = MaterialTheme.typography.bodyMedium, color = x.muted, textAlign = TextAlign.Center,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { tune((shown / step - 1) * step) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Remove, tr("Ниже на шаг")) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TUNE_STEPS.forEach { s ->
                        PanelChip(stepLabel(s), s == step, Modifier.weight(1f), small = true) { vm.changeTuneStep(s) }
                    }
                }
                FilledTonalIconButton(onClick = { tune((shown / step + 1) * step) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Add, tr("Выше на шаг")) }
            }
            var typed by remember { mutableStateOf("") }
            val typedHz = freqMhz(typed)?.takeIf { bandForFreq(it) != null }?.let { Math.round(it * 1_000_000) }
            fun go() {
                typedHz?.let { tune(it); typed = "" }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typed, onValueChange = { v -> typed = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                    singleLine = true,
                    label = { Text(tr("Частота: 14195 или 7.074")) },
                    textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { go(); true } else false
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                Button(onClick = ::go, enabled = typedHz != null, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(12.dp)) { Text(tr("Перейти"), fontSize = 17.sp) }
            }
            val band = bandForFreq(shown / 1e6)
            Text(tr("Диапазон"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BANDS.filter { it in vm.enabledBands && it in BandPlan.bands }.forEach { b ->
                    PanelChip(b, b == band) { vm.rigBand(b)?.let { target = it } }
                }
            }
            val mode = rig.radioMode?.let { CatModes.toLog(it, vm.form.mode) }
            Text(tr("Вид"), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RIG_MODES.forEach { m ->
                    PanelChip(m, m == mode || (m == "FT8" && mode == "FT8")) { vm.rigMode(m)?.let { target = it } }
                }
            }
            Text(
                tr("FT8 и FT4 переводят трансивер в режим данных (USB-D) и на частоту вида на этом диапазоне. Диапазон возвращает трансивер туда, где он был на нём в последний раз. Кнопки диапазона и вида в новой карточке QSO тоже перестраивают трансивер — это выключается в настройках CAT."),
                style = MaterialTheme.typography.bodyMedium, color = x.muted,
            )
        }
    }
}

@Composable
private fun PanelChip(text: String, on: Boolean, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(46.dp).clip(shape)
            .background(if (on) MaterialTheme.colorScheme.primary else x.field)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else x.line, shape)
            .clickable(onClick = onClick).padding(horizontal = if (small) 2.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (small) 13.sp else 16.sp, maxLines = 1,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}
