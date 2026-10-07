package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.CONTEST_RCVD_ERROR
import ru.r3xed.qsolog.Lookup
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.data.defaultRst
import ru.r3xed.qsolog.data.freqMhz
import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.utc
import kotlin.math.abs

/**
 * The contest-mode card: callsign, two reports and two numbers, nothing else. "Далее" on the keyboard goes from the
 * callsign to the received number, "Готово" there saves and opens the next card. A swipe from right to left saves
 * and goes on, from left to right goes back through the contest contacts to correct them.
 */
@Composable
fun ContestScreen(vm: AppViewModel) {
    val f = vm.form
    val x = LocalExtra.current
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val callFocus = remember { FocusRequester() }
    val rcvdFocus = remember { FocusRequester() }
    val rcvd = f.adif[ContestMode.RCVD].orEmpty()

    // A new card with something typed, or changes to a saved one: closing asks first.
    val typed = if (f.isNew) f.call.isNotBlank() || rcvd.isNotBlank() else vm.hasUnsavedChanges
    val close = { if (typed) confirmClose = true else vm.closeEditor() }
    BackHandler { close() }

    // A missing received number puts the cursor there, any other problem (the callsign) in the callsign.
    val showError = { e: String? ->
        error = e
        if (e == CONTEST_RCVD_ERROR) rcvdFocus.requestFocus() else if (e != null) callFocus.requestFocus()
    }
    val next = { showError(vm.contestSaveAndNext()) }
    val prev = { showError(vm.contestPrev()) }
    val hasPrev = vm.contestHasPrev()

    // Horizontal swipes anywhere on the card, watched before the text fields get them (they would take the drag).
    val onNext by rememberUpdatedState(next)
    val onPrev by rememberUpdatedState(prev)
    var drag by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    val swipe = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                dx = change.position.x - down.position.x
                dy = change.position.y - down.position.y
                drag = if (abs(dx) > abs(dy)) dx else 0f
            } while (change.pressed)
            drag = 0f
            if (abs(dx) > threshold && abs(dx) > 2 * abs(dy)) if (dx < 0) onNext() else onPrev()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding().then(swipe)) {
        // --- header: close, what is being worked, the number sent ---
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Close, tr("Закрыть"), Modifier.size(30.dp))
            }
            Icon(Icons.Filled.Flag, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("CONTEST", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            BandModePicker(vm)
            Spacer(Modifier.weight(1f))
            Text(
                "№ " + f.adif[ContestMode.SENT].orEmpty().ifBlank { "—" },
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, softWrap = false,
            )
        }
        // Where this card is among the contest contacts; the arrows do what the swipes do.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = prev, enabled = hasPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                Text(tr("назад"))
            }
            val pos = vm.contestPosition()
            Text(
                if (pos == null) tr("новая связь") else tr("связь %s из %s", pos.first, pos.second),
                style = MaterialTheme.typography.bodyMedium, color = x.muted,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(onClick = next) {
                Text(if (f.isNew) tr("записать") else tr("вперёд"))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        HorizontalDivider(color = x.line)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .graphicsLayer { translationX = drag * 0.35f }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = f.call,
                onValueChange = { error = null; vm.setCall(it) },
                modifier = Modifier.fillMaxWidth().focusRequester(callFocus),
                label = { Text(tr("Позывной"), fontSize = 16.sp) },
                isError = error != null && error != CONTEST_RCVD_ERROR,
                textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = 1.sp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { rcvdFocus.requestFocus() }),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary),
            )
            if (f.isNew) LaunchedEffect(Unit) { callFocus.requestFocus() }
            StationLine(vm)

            val dbReports = defaultRst(f.mode).startsWith("-")
            val rstKeyboard = if (dbReports) KeyboardType.Text else KeyboardType.Number
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigField(tr("RST передан"), f.rstSent, { vm.update(vm.form.copy(rstSent = it)) }, Modifier.weight(1f), rstKeyboard)
                BigField(tr("RST принят"), f.rstRcvd, { vm.update(vm.form.copy(rstRcvd = it)) }, Modifier.weight(1f), rstKeyboard)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigField(tr("Код передан"), f.adif[ContestMode.SENT].orEmpty(), { vm.setContestField(ContestMode.SENT, it) }, Modifier.weight(1f), KeyboardType.Number)
                BigField(
                    tr("Код принят"), rcvd, { error = null; vm.setContestField(ContestMode.RCVD, it) },
                    Modifier.weight(1f).focusRequester(rcvdFocus), KeyboardType.Number, onDone = next,
                    isError = error == CONTEST_RCVD_ERROR,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
        }

        // --- bottom: one big button, and when the contact is (or will be) logged ---
        HorizontalDivider(color = x.line)
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!f.isNew) {
                    OutlinedIconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Icon(Icons.Filled.DeleteOutline, tr("Удалить связь"), Modifier.size(28.dp)) }
                }
                Button(onClick = next, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (f.isNew) tr("Записать и следующая") else tr("Сохранить и далее"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(28.dp))
                }
            }
            val freq = f.freq.ifBlank { null }?.let { tr("%s МГц", it) }
            val time = if (f.isNew) tr("время UTC — при записи") else "${f.time} UTC · ${f.date}"
            Text(listOfNotNull(time, freq).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = x.muted)
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(tr("Закрыть без сохранения?")) },
            text = { Text(tr("Введённые изменения не сохранятся."), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; vm.closeEditor() }) { Text(tr("Закрыть"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { Button(onClick = { confirmClose = false }) { Text(tr("Продолжить ввод")) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Удалить связь с %s?", f.call)) },
            text = { Text(tr("%s, %s %s. Запись пропадёт из лога.", "${f.date}, ${f.time} UTC", f.band, f.mode), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; vm.deleteCurrent() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(tr("Удалить")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Отмена")) } },
        )
    }
}

/** Band and mode of the card as two small chips; a tap opens the list of the ones switched on in the settings. */
@Composable
private fun BandModePicker(vm: AppViewModel) {
    val f = vm.form
    val x = LocalExtra.current
    var bandMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    val chip = Modifier.clip(RoundedCornerShape(10.dp)).background(x.field)
    Box {
        Text(
            f.band.ifBlank { "—" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            modifier = chip.clickable(onClickLabel = tr("Диапазон")) { bandMenu = true }.padding(horizontal = 10.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = bandMenu, onDismissRequest = { bandMenu = false }) {
            BANDS.filter { it in vm.enabledBands || it == f.band }.forEach { b ->
                DropdownMenuItem(text = { Text(b, fontFamily = Mono, fontSize = 18.sp) }, onClick = {
                    bandMenu = false
                    vm.setBand(b)
                    // The frequency of the old band would be wrong now.
                    if (freqMhz(vm.form.freq)?.let { bandForFreq(it) } != b) vm.update(vm.form.copy(freq = ""))
                })
            }
        }
    }
    Spacer(Modifier.width(6.dp))
    Box {
        Text(
            f.mode.ifBlank { "—" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            modifier = chip.clickable(onClickLabel = tr("Вид связи")) { modeMenu = true }.padding(horizontal = 10.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
            MODES.filter { it in vm.enabledModes || it == f.mode }.forEach { m ->
                DropdownMenuItem(text = { Text(m, fontFamily = Mono, fontSize = 18.sp) }, onClick = { modeMenu = false; vm.setMode(m) })
            }
        }
    }
}

/** One line under the callsign: who it is (from QRZ), and a red mark when it is a repeat on this band and mode. */
@Composable
private fun StationLine(vm: AppViewModel) {
    val f = vm.form
    val x = LocalExtra.current
    if (f.call.length < 3) return
    val dupe = remember(f.call, f.band, f.mode, f.date, f.id, vm.allQsos) { vm.dupeOf(f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val who = listOf(f.name, f.qth.ifBlank { f.country }).filter { it.isNotBlank() }.joinToString(", ")
        Box(Modifier.weight(1f)) {
            when {
                who.isNotBlank() -> Text(who, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                vm.lookup == Lookup.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> Text(f.country, style = MaterialTheme.typography.titleMedium, color = x.muted, maxLines = 1)
            }
        }
        if (dupe != null) {
            val t = utc(dupe.timeUtc)
            Text(
                tr("ПОВТОР %s", TIME_FMT.format(t)),
                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.padding(start = 8.dp)
                    .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * A large mono field that selects its whole value on focus, so typing replaces "599" or the number instead of
 * appending to it. With [onDone] the keyboard shows "Готово" and runs it.
 */
@Composable
private fun BigField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    keyboard: KeyboardType,
    onDone: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    val shown = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        value = shown,
        onValueChange = { tfv = it; if (it.text != value) onChange(it.text.uppercase()) },
        modifier = modifier.onFocusChanged { if (it.isFocused) tfv = TextFieldValue(value, TextRange(0, value.length)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
    )
}
