package ru.r3xed.qsolog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.CONTEST_RCVD_ERROR
import ru.r3xed.qsolog.Lookup
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.ContestMode
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.data.Qso
import ru.r3xed.qsolog.data.bandForFreq
import ru.r3xed.qsolog.data.freqMhz
import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.utc

/** The fields of the contest card; Enter moves between them. */
private enum class Target { CALL, SENT, RCVD, RST }

/**
 * The contest-mode card: callsign, the two numbers and the reports, nothing else. Keyboard first: Enter goes from the
 * callsign to the received number and there logs the contact and opens the next card; Page Up / Page Down (or the
 * arrows in the header) go through the contest contacts to correct them.
 */
@Composable
fun ContestPane(vm: AppState) {
    val f = vm.form
    val x = LocalExtra.current
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val callFocus = remember { FocusRequester() }
    val rcvdFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(Target.CALL) }
    val rcvd = f.adif[ContestMode.RCVD].orEmpty()
    val sent = f.adif[ContestMode.SENT].orEmpty()

    // A missing received number puts the cursor there, any other problem (the callsign) in the callsign.
    val showError = { e: String? ->
        error = e
        when {
            e == null -> {}
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

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val plain = !e.isAltPressed && !e.isCtrlPressed && !e.isMetaPressed && !e.isShiftPressed
            when {
                // Enter logs from any field ("20m", "CW", "14025" in the callsign switch band, mode or frequency);
                // Space in the callsign goes on to the received number; ↑ in an empty callsign — the previous contact.
                (e.key == Key.Enter || e.key == Key.NumPadEnter) && plain -> {
                    if (focused == Target.CALL && vm.runCallCommand()) {} else next()
                    true
                }
                e.key == Key.Spacebar && focused == Target.CALL -> {
                    if (!vm.runCallCommand()) rcvdFocus.requestFocus()
                    true
                }
                e.key == Key.DirectionUp && focused == Target.CALL && f.isNew && f.call.isEmpty() -> {
                    if (hasPrev) prev()
                    true
                }
                // F12 records the contact from any field, as "Сохранить" does in the usual card.
                e.key == Key.F12 -> {
                    next()
                    true
                }
                e.key == Key.PageUp || (e.isAltPressed && e.key == Key.DirectionLeft) -> {
                    if (hasPrev) prev()
                    true
                }
                e.key == Key.PageDown || (e.isAltPressed && e.key == Key.DirectionRight) -> {
                    next()
                    true
                }
                else -> false
            }
        },
    ) {
        // --- header: close, what is being worked, the rate ---
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::requestCloseContest, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Close, tr("Закрыть (Esc)"), Modifier.size(30.dp))
            }
            Icon(Icons.Filled.Flag, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("CONTEST", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            BandModePicker(vm)
            Spacer(Modifier.weight(1f))
            ContestRate(vm)
        }
        // Where this card is among the contest contacts; the arrows do what Page Up / Page Down do.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = prev, enabled = hasPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                Text(tr("назад") + " (PgUp)")
            }
            val pos = vm.contestPosition()
            // Just logged: said here instead of a message.
            val saved = vm.contestLastSaved?.takeIf { f.isNew && f.call.isBlank() }
            Text(
                when {
                    saved != null -> tr("✓ %s записана", saved)
                    pos == null -> tr("новая связь")
                    else -> tr("связь %s из %s", pos.first, pos.second)
                },
                style = MaterialTheme.typography.bodyLarge, color = if (saved != null) x.ok else x.muted,
                fontWeight = if (saved != null) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            TextButton(onClick = next) {
                Text((if (f.isNew) tr("записать") else tr("вперёд")) + " (PgDn)")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        HorizontalDivider(color = x.line)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val callError = error != null && error != CONTEST_RCVD_ERROR
            OutlinedTextField(
                value = f.call,
                onValueChange = { error = null; vm.setCall(it) },
                modifier = Modifier.fillMaxWidth().focusRequester(callFocus).onFocusChanged { if (it.isFocused) focused = Target.CALL },
                label = { Text(tr("Позывной"), fontSize = 16.sp) },
                isError = callError,
                textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 1.sp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary),
            )
            LaunchedEffect(Unit) { callFocus.requestFocus() }
            // Calls from the log that start with what is typed: one click instead of the rest.
            if (f.isNew) {
                val suggestions = remember(f.call, vm.allQsos) { vm.callSuggestions(f.call) }
                if (suggestions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { c ->
                            Text(
                                c, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(x.field)
                                    .clickable(onClickLabel = tr("Подставить позывной")) { error = null; vm.setCall(c); rcvdFocus.requestFocus() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            StationLine(vm)

            // The numbers first: they change with every contact. Any letters and digits: 015, MO69, EU, 16.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigField(
                    tr("Код передан"), sent, { vm.setAdif(ContestMode.SENT, it) },
                    Modifier.weight(1f).onFocusChanged { if (it.isFocused) focused = Target.SENT },
                )
                BigField(
                    tr("Код принят"), rcvd, { error = null; vm.setAdif(ContestMode.RCVD, it) },
                    Modifier.weight(1f).focusRequester(rcvdFocus).onFocusChanged { if (it.isFocused) focused = Target.RCVD },
                    isError = error == CONTEST_RCVD_ERROR,
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigField(
                        tr("RST передан"), f.rstSent, { vm.update(vm.form.copy(rstSent = it)) },
                        Modifier.weight(1f).onFocusChanged { if (it.isFocused) focused = Target.RST },
                    )
                    BigField(
                        tr("RST принят"), f.rstRcvd, { vm.update(vm.form.copy(rstRcvd = it)) },
                        Modifier.weight(1f).onFocusChanged { if (it.isFocused) focused = Target.RST },
                    )
                    IconButton(onClick = { vm.changeContestRstShown(false) }) { Icon(Icons.Filled.ExpandLess, tr("Свернуть RST")) }
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

        // --- bottom: the big button, and when the contact is (or will be) logged ---
        HorizontalDivider(color = x.line)
        Column(Modifier.padding(horizontal = 32.dp, vertical = 12.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                val saveLabel = if (f.isNew) tr("Записать и следующая") else tr("Сохранить и далее")
                Button(
                    onClick = next, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(16.dp),
                    colors = if (dupeArmed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                    else ButtonDefaults.buttonColors(),
                ) {
                    Text(if (dupeArmed) tr("Записать повтор") else saveLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(28.dp))
                }
            }
            TimeLine(f.isNew, "${f.time} UTC · ${f.date}", f.freq.ifBlank { null }?.let { tr("%s МГц", it) })
            Text(
                tr("Enter — из позывного в принятый код, там — записать. PgUp / PgDn — предыдущая и следующая связь контеста. Esc — закрыть."),
                style = MaterialTheme.typography.bodyMedium, color = x.muted,
            )
        }
    }

    if (vm.confirmClose) {
        AlertDialog(
            onDismissRequest = { vm.confirmClose = false },
            title = { Text(tr("Закрыть без сохранения?")) },
            text = { Text(tr("Введённые изменения не сохранятся."), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { vm.confirmClose = false; vm.closeEditor() }) { Text(tr("Закрыть"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { Button(onClick = { vm.confirmClose = false }) { Text(tr("Продолжить ввод")) } },
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
private fun ContestRate(vm: AppState) {
    val x = LocalExtra.current
    val now = utcNow() / 60_000 * 60_000 // once a minute is enough
    val (total, hour) = remember(now, vm.allQsos) { vm.contestCounts(now + 59_999) }
    Column(horizontalAlignment = Alignment.End) {
        Text("$total QSO", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, softWrap = false)
        Text(tr("%s за час", hour), style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 1, softWrap = false)
    }
}

/** Under the button: a ticking UTC clock on a new card (its time is taken on logging), the logged time otherwise. */
@Composable
private fun TimeLine(isNew: Boolean, logged: String, freq: String?) {
    val x = LocalExtra.current
    val time = if (isNew) TIME_SEC_FMT.format(utc(utcNow())) + " UTC" else logged
    Text(listOfNotNull(time, freq).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = x.muted, fontFamily = if (isNew) Mono else null)
}

private val TIME_SEC_FMT: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

/** The last contest contacts under the card: proof they were logged, and a click opens one to correct it. */
@Composable
private fun RecentContacts(vm: AppState, onOpen: (Qso) -> Unit) {
    val x = LocalExtra.current
    val recent = remember(vm.allQsos) { vm.contestRecent(6) }
    if (recent.isEmpty()) return
    Column {
        Text(tr("Последние связи"), style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(bottom = 2.dp))
        recent.forEach { q ->
            val open = q.id == vm.form.id
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (open) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable(onClickLabel = tr("Открыть связь")) { onOpen(q) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(TIME_FMT.format(utc(q.timeUtc)), fontFamily = Mono, fontSize = 16.sp, color = x.muted)
                Spacer(Modifier.width(14.dp))
                Text(q.call, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${q.band} ${q.mode}", fontFamily = Mono, fontSize = 16.sp, color = x.muted)
                Spacer(Modifier.width(14.dp))
                Text(
                    "${q.adif[ContestMode.SENT].orEmpty()} / ${q.adif[ContestMode.RCVD].orEmpty()}",
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
                )
            }
        }
    }
}

/** Band and mode of the card as two small chips; a click opens the list of the ones switched on in the settings. */
@Composable
private fun BandModePicker(vm: AppState) {
    val f = vm.form
    val x = LocalExtra.current
    var bandMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    val chip = Modifier.clip(RoundedCornerShape(10.dp)).background(x.field)
    Box {
        Text(
            f.band.ifBlank { "—" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = chip.clickable(onClickLabel = tr("Диапазон")) { bandMenu = true }.padding(horizontal = 12.dp, vertical = 6.dp),
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
            f.mode.ifBlank { "—" }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = chip.clickable(onClickLabel = tr("Вид связи")) { modeMenu = true }.padding(horizontal = 12.dp, vertical = 6.dp),
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
private fun StationLine(vm: AppState) {
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

/** A large mono field that selects its whole value on focus, so typing replaces "599" or the number instead of appending to it. */
@Composable
private fun BigField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, isError: Boolean = false) {
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    val shown = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        value = shown,
        onValueChange = { tfv = it; if (it.text != value) onChange(it.text.uppercase().filter { c -> !c.isWhitespace() }.take(12)) },
        modifier = modifier.onFocusChanged { if (it.isFocused) tfv = TextFieldValue(value, TextRange(0, value.length)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false),
    )
}
