package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalView
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
import ru.r3xed.qsolog.data.KeypadMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.data.defaultRst
import ru.r3xed.qsolog.data.freqMhz
import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.utc
import kotlin.math.abs

/** The fields of the contest card the app's keypad can type into. */
private enum class Target { CALL, SENT, RCVD, RST_SENT, RST_RCVD }

/**
 * The contest-mode card: callsign, the two numbers and the reports, nothing else. A swipe from right to left saves
 * and goes on, from left to right goes back through the contest contacts to correct them. Typing is done on the
 * app's own keypad (big keys, digits and Latin letters, the system keyboard stays closed) or, if switched off in the
 * settings, on the system keyboard: "Далее" goes from the callsign to the received number, "Готово" there saves.
 */
@Composable
fun ContestScreen(vm: AppViewModel) {
    val f = vm.form
    val x = LocalExtra.current
    val keypad = vm.keypadShown
    val compact = vm.keypad == KeypadMode.COMPACT
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val callFocus = remember { FocusRequester() }
    val rcvdFocus = remember { FocusRequester() }
    val rcvd = f.adif[ContestMode.RCVD].orEmpty()
    val sent = f.adif[ContestMode.SENT].orEmpty()

    // The keypad's field. A tap on a filled number or report selects it: the next key replaces it, as on a keyboard.
    var active by remember { mutableStateOf(if (f.isNew) Target.CALL else Target.RCVD) }
    var replace by remember { mutableStateOf(!f.isNew) }
    fun valueOf(t: Target) = when (t) {
        Target.CALL -> vm.form.call
        Target.SENT -> vm.form.adif[ContestMode.SENT].orEmpty()
        Target.RCVD -> vm.form.adif[ContestMode.RCVD].orEmpty()
        Target.RST_SENT -> vm.form.rstSent
        Target.RST_RCVD -> vm.form.rstRcvd
    }
    fun setValue(t: Target, v: String) {
        error = null
        when (t) {
            Target.CALL -> vm.setCall(v)
            Target.SENT -> vm.setContestField(ContestMode.SENT, v)
            Target.RCVD -> vm.setContestField(ContestMode.RCVD, v)
            Target.RST_SENT -> vm.update(vm.form.copy(rstSent = v))
            Target.RST_RCVD -> vm.update(vm.form.copy(rstRcvd = v))
        }
    }
    // A second tap on the active field selects its value (then ⌫ clears it, a key replaces it), a third deselects.
    fun activate(t: Target) {
        replace = if (t == active) !replace && valueOf(t).isNotBlank() else t != Target.CALL && valueOf(t).isNotBlank()
        active = t
    }

    // A new card with something typed, or changes to a saved one: closing asks first.
    val typed = if (f.isNew) f.call.isNotBlank() || rcvd.isNotBlank() else vm.hasUnsavedChanges
    val close = { if (typed) confirmClose = true else vm.closeEditor() }
    BackHandler { close() }

    // A missing received number puts the cursor there, any other problem (the callsign) in the callsign.
    val showError = { e: String? ->
        error = e
        when {
            e == null -> {}
            keypad -> { active = if (e == CONTEST_RCVD_ERROR) Target.RCVD else Target.CALL; replace = false }
            e == CONTEST_RCVD_ERROR -> rcvdFocus.requestFocus()
            else -> callFocus.requestFocus()
        }
    }
    // A repeat on this band and mode is logged only on a second press: the first one turns the button red.
    val dupe = remember(f.call, f.band, f.mode, f.date, f.id, vm.allQsos) { if (f.isNew) vm.dupeOf(f) else null }
    var dupeArmed by remember(f.call, f.band, f.mode) { mutableStateOf(false) }
    val next = {
        if (dupe != null && !dupeArmed && rcvd.isNotBlank()) {
            dupeArmed = true
            error = null
        } else {
            showError(vm.contestSaveAndNext())
        }
    }
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

    // A physical keyboard: Space in the callsign goes on to the received code, Enter logs from any field ("20m", "CW",
    // "14025" in the callsign switch band, mode or frequency), ↑ in an empty callsign or PgUp — the previous contact,
    // PgDn — the next one, Esc wipes the new card (a second Esc closes it), F12 logs.
    var callFocused by remember { mutableStateOf(false) }
    val hardKeys = Modifier.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || !e.fromHardware()) return@onPreviewKeyEvent false
        val plain = !e.isAltPressed && !e.isCtrlPressed && !e.isMetaPressed && !e.isShiftPressed
        val form = vm.form
        when {
            // Enter in the callsign goes on to the received code (never logs); in the other fields it logs.
            (e.key == Key.Enter || e.key == Key.NumPadEnter) && plain -> {
                if (callFocused) { if (!vm.runCallCommand()) rcvdFocus.requestFocus() } else next()
                true
            }
            e.key == Key.Spacebar && callFocused -> { if (!vm.runCallCommand()) rcvdFocus.requestFocus(); true }
            e.key == Key.DirectionUp && callFocused && form.isNew && form.call.isEmpty() -> { prev(); true }
            e.key == Key.PageUp || (e.isAltPressed && e.key == Key.DirectionLeft) -> { prev(); true }
            e.key == Key.PageDown || (e.isAltPressed && e.key == Key.DirectionRight) -> { next(); true }
            e.key == Key.F12 -> { next(); true }
            e.key == Key.Escape -> {
                if (form.isNew && (form.call.isNotBlank() || !form.adif[ContestMode.RCVD].isNullOrBlank())) vm.clearCard() else close()
                true
            }
            else -> false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding().then(hardKeys)) {
      Column(Modifier.weight(1f).then(swipe)) {
        // --- header: close, what is being worked, what is sent ---
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, tr("Закрыть"), Modifier.size(30.dp))
            }
            Icon(Icons.Filled.Flag, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("CONTEST", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            BandModePicker(vm)
            Spacer(Modifier.weight(1f))
            ContestRate(vm)
        }
        // Where this card is among the contest contacts; the arrows do what the swipes do.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = prev, enabled = hasPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                Text(tr("назад"))
            }
            val pos = vm.contestPosition()
            // Just logged: said here instead of a message over the header.
            val saved = vm.contestLastSaved?.takeIf { f.isNew && f.call.isBlank() }
            Text(
                when {
                    saved != null -> tr("✓ %s записана", saved)
                    pos == null -> tr("новая связь")
                    else -> tr("связь %s из %s", pos.first, pos.second)
                },
                style = MaterialTheme.typography.bodyMedium, color = if (saved != null) x.ok else x.muted,
                fontWeight = if (saved != null) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
            val callError = error != null && error != CONTEST_RCVD_ERROR
            if (keypad) {
                KeyField(tr("Позывной"), f.call, active == Target.CALL, false, callError, Modifier.fillMaxWidth(), big = true) { activate(Target.CALL) }
            } else {
                OutlinedTextField(
                    value = f.call,
                    onValueChange = { error = null; vm.setCall(it) },
                    modifier = Modifier.fillMaxWidth().focusRequester(callFocus).onFocusChanged { callFocused = it.isFocused },
                    label = { Text(tr("Позывной"), fontSize = 16.sp) },
                    isError = callError,
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
                LaunchedEffect(Unit) { callFocus.requestFocus() }
            }
            // Calls from the log that start with what is typed: one tap instead of the rest.
            if (f.isNew) {
                val suggestions = remember(f.call, vm.allQsos) { vm.callSuggestions(f.call) }
                if (suggestions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { c ->
                            Text(
                                c, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(x.field)
                                    .clickable(onClickLabel = tr("Подставить позывной")) { error = null; vm.setCall(c) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            StationLine(vm)

            // The numbers first: they change with every contact. Any letters and digits: 015, MO69, EU, 16.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (keypad) {
                    KeyField(tr("Код передан"), sent, active == Target.SENT, replace, false, Modifier.weight(1f)) { activate(Target.SENT) }
                    KeyField(tr("Код принят"), rcvd, active == Target.RCVD, replace, error == CONTEST_RCVD_ERROR, Modifier.weight(1f)) { activate(Target.RCVD) }
                } else {
                    BigField(tr("Код передан"), sent, { vm.setContestField(ContestMode.SENT, it) }, Modifier.weight(1f), KeyboardType.Ascii)
                    BigField(
                        tr("Код принят"), rcvd, { error = null; vm.setContestField(ContestMode.RCVD, it) },
                        Modifier.weight(1f).focusRequester(rcvdFocus), KeyboardType.Ascii, onDone = next,
                        isError = error == CONTEST_RCVD_ERROR,
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
            if (dupeArmed && dupe != null) {
                Text(
                    tr("Уже была связь на %s %s в %s. Нажмите ещё раз, чтобы записать повтор.", dupe.band, dupe.mode, TIME_FMT.format(utc(dupe.timeUtc))),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium,
                )
            }

            // The reports are 59 / 599 nearly always: they can be folded into one line.
            if (vm.contestRstShown) {
                val dbReports = defaultRst(f.mode).startsWith("-")
                val rstKeyboard = if (dbReports) KeyboardType.Text else KeyboardType.Number
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (keypad) {
                        KeyField(tr("RST передан"), f.rstSent, active == Target.RST_SENT, replace, false, Modifier.weight(1f)) { activate(Target.RST_SENT) }
                        KeyField(tr("RST принят"), f.rstRcvd, active == Target.RST_RCVD, replace, false, Modifier.weight(1f)) { activate(Target.RST_RCVD) }
                    } else {
                        BigField(tr("RST передан"), f.rstSent, { vm.update(vm.form.copy(rstSent = it)) }, Modifier.weight(1f), rstKeyboard)
                        BigField(tr("RST принят"), f.rstRcvd, { vm.update(vm.form.copy(rstRcvd = it)) }, Modifier.weight(1f), rstKeyboard)
                    }
                    IconButton(onClick = {
                        vm.changeContestRstShown(false)
                        if (active == Target.RST_SENT || active == Target.RST_RCVD) activate(Target.CALL)
                    }) { Icon(Icons.Filled.ExpandLess, tr("Свернуть RST")) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = tr("Показать RST")) { vm.changeContestRstShown(true) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("RST  ", style = MaterialTheme.typography.bodyLarge, color = x.muted)
                    Text("${f.rstSent.ifBlank { "—" }} / ${f.rstRcvd.ifBlank { "—" }}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    Text(tr("изменить"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            RecentContacts(vm) { showError(vm.contestOpen(it)) }
        }
      }

        // --- bottom: the keypad or one big button, and when the contact is (or will be) logged ---
        HorizontalDivider(color = x.line)
        Column(Modifier.navigationBarsPadding().padding(horizontal = if (keypad) 6.dp else 16.dp, vertical = if (keypad) 6.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val deleteButton = @Composable { size: Int ->
                OutlinedIconButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.size(size.dp),
                    shape = RoundedCornerShape(if (keypad) 10.dp else 16.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Filled.DeleteOutline, tr("Удалить связь"), Modifier.size(28.dp)) }
            }
            val saveLabel = if (f.isNew) tr("Записать и следующая") else tr("Сохранить и далее")
            if (keypad) {
                Keypad(
                    compact = compact,
                    onKey = { c ->
                        val v = if (replace) c else valueOf(active) + c
                        replace = false
                        setValue(active, v.take(if (active == Target.CALL) 15 else 12))
                    },
                    onBackspace = { setValue(active, if (replace) "" else valueOf(active).dropLast(1)); replace = false },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!f.isNew) deleteButton(keypadButtonHeight(compact).value.toInt())
                    // From the callsign to the received number and back: the two fields typed in every contact.
                    val toRcvd = active == Target.CALL
                    OutlinedButton(
                        onClick = { activate(if (toRcvd) Target.RCVD else Target.CALL) },
                        modifier = Modifier.weight(1f).height(keypadButtonHeight(compact)), shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(if (toRcvd) tr("→ Код") else tr("→ Позывной"), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = next, modifier = Modifier.weight(if (f.isNew) 1.6f else 1.3f).height(keypadButtonHeight(compact)), shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        colors = if (dupeArmed) dupeColors() else ButtonDefaults.buttonColors(),
                    ) {
                        Text(if (dupeArmed) tr("Записать повтор") else if (f.isNew) tr("Записать") else tr("Далее"), fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(26.dp))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!f.isNew) deleteButton(60)
                    Button(onClick = next, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(16.dp), colors = if (dupeArmed) dupeColors() else ButtonDefaults.buttonColors()) {
                        Text(if (dupeArmed) tr("Записать повтор") else saveLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(28.dp))
                    }
                }
            }
            TimeLine(f.isNew, "${f.time} UTC · ${f.date}", f.freq.ifBlank { null }?.let { tr("%s МГц", it) }, Modifier.padding(horizontal = if (keypad) 10.dp else 0.dp))
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

/** The save button when the contact is a repeat and one more press logs it anyway. */
@Composable
private fun dupeColors() = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)

/** The current UTC time, ticking every second. */
@Composable
private fun utcNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Header: contest contacts so far and in the last hour — the rate. */
@Composable
private fun ContestRate(vm: AppViewModel) {
    val x = LocalExtra.current
    val now = utcNow() / 60_000 * 60_000 // once a minute is enough
    val (total, hour) = remember(now, vm.allQsos) { vm.contestCounts(now + 59_999) }
    Column(horizontalAlignment = Alignment.End) {
        Text("$total QSO", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, softWrap = false)
        Text(tr("%s за час", hour), style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, softWrap = false)
    }
}

/** Under the buttons: a ticking UTC clock on a new card (its time is taken on logging), the logged time otherwise. */
@Composable
private fun TimeLine(isNew: Boolean, logged: String, freq: String?, modifier: Modifier) {
    val x = LocalExtra.current
    val time = if (isNew) TIME_SEC_FMT.format(utc(utcNow())) + " UTC" else logged
    Text(listOfNotNull(time, freq).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = x.muted, fontFamily = if (isNew) Mono else null, modifier = modifier)
}

private val TIME_SEC_FMT: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

/** The last contest contacts under the card: proof they were logged, and a tap opens one to correct it. */
@Composable
private fun RecentContacts(vm: AppViewModel, onOpen: (ru.r3xed.qsolog.data.Qso) -> Unit) {
    val x = LocalExtra.current
    val recent = remember(vm.allQsos) { vm.contestRecent(4) }
    if (recent.isEmpty()) return
    Column {
        Text(tr("Последние связи"), style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(bottom = 2.dp))
        recent.forEach { q ->
            val open = q.id == vm.form.id
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (open) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(onClickLabel = tr("Открыть связь")) { onOpen(q) }
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(TIME_FMT.format(utc(q.timeUtc)), fontFamily = Mono, fontSize = 15.sp, color = x.muted)
                Spacer(Modifier.width(10.dp))
                Text(q.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(q.band, fontFamily = Mono, fontSize = 15.sp, color = x.muted)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${q.adif[ContestMode.SENT].orEmpty()} / ${q.adif[ContestMode.RCVD].orEmpty()}",
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                )
            }
        }
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
