package ru.r3xed.qsolog.ui


import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.data.ExportFormat
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Add
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import ru.r3xed.qsolog.data.defaultRst
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.Lookup
import ru.r3xed.qsolog.Screen
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.AdifLabels
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.ui.platform.LocalFocusManager
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.utc


/** The fields of the normal card typed on the app's keypad. */
private enum class KeyTarget { CALL, FREQ, RST_SENT, RST_RCVD }

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditScreen(vm: AppViewModel, hasMicPermission: () -> Boolean, requestMicPermission: () -> Unit) {
    val f = vm.form
    val x = LocalExtra.current
    var showMap by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // A new contact gets the current UTC time on its own: show it as one line, the fields open on ✎.
    var editTime by rememberSaveable { mutableStateOf(!f.isNew) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val callFocus = remember { FocusRequester() }
    val freqFocus = remember { FocusRequester() }
    val rstFocus = remember { FocusRequester() }
    val rstRcvdFocus = remember { FocusRequester() }

    // The app's keypad (settings → "Ввод связи"): callsign, frequency and reports are
    // typed on it, the rest of the card on the system keyboard. No active field — the keypad is hidden.
    val keypad = vm.keypadShown
    val compact = vm.keypad == KeypadMode.COMPACT
    var active by remember(f.id, f.createdAt) { mutableStateOf(if (f.isNew) KeyTarget.CALL else null) }
    var replace by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // A system text field took the focus: while its keyboard is open ours steps aside, then comes back.
    // (It is not switched off here: on a phone the closing system keyboard still counts as open for a moment.)
    val imeVisible = WindowInsets.isImeVisible
    val freqView = remember { BringIntoViewRequester() }
    val rstView = remember { BringIntoViewRequester() }
    fun valueOf(t: KeyTarget) = when (t) {
        KeyTarget.CALL -> vm.form.call
        KeyTarget.FREQ -> vm.form.freq
        KeyTarget.RST_SENT -> vm.form.rstSent
        KeyTarget.RST_RCVD -> vm.form.rstRcvd
    }
    fun setValue(t: KeyTarget, v: String) {
        when (t) {
            KeyTarget.CALL -> { error = null; vm.setCall(v) }
            KeyTarget.FREQ -> vm.setFreq(v)
            KeyTarget.RST_SENT -> vm.update(vm.form.copy(rstSent = v))
            KeyTarget.RST_RCVD -> vm.update(vm.form.copy(rstRcvd = v))
        }
    }
    // A second tap on the active field selects its value (the next key replaces it), as in the contest card.
    fun activate(t: KeyTarget?) {
        focusManager.clearFocus()
        replace = t != null && if (t == active) !replace && valueOf(t).isNotBlank() else t != KeyTarget.CALL && valueOf(t).isNotBlank()
        active = t
    }
    // What is usually typed next: the frequency (if empty), the reports, then the keypad folds away.
    fun nextOf(t: KeyTarget) = when (t) {
        KeyTarget.CALL -> if (vm.form.freq.isBlank()) KeyTarget.FREQ else KeyTarget.RST_SENT
        KeyTarget.FREQ -> KeyTarget.RST_SENT
        KeyTarget.RST_SENT -> KeyTarget.RST_RCVD
        KeyTarget.RST_RCVD -> null
    }
    // Again when the lookup answers: the station card above grows and pushes the field down.
    LaunchedEffect(active, vm.lookup) {
        when (active) {
            KeyTarget.FREQ -> freqView.bringIntoView()
            KeyTarget.RST_SENT, KeyTarget.RST_RCVD -> rstView.bringIntoView()
            else -> {}
        }
    }

    // Closing a card with typed data asks first; an untouched one closes at once.
    val close = { if (vm.hasUnsavedChanges) confirmClose = true else vm.closeEditor() }
    BackHandler { if (showMap) showMap = false else close() }

    val me = vm.myPositionFor(vm.form)
    val them = f.position

    // Show a save problem where it is: the callsign at the top, the date/time fields opened.
    fun showSaveError(e: String?) {
        error = e
        when {
            e == CALL_ERROR -> { scope.launch { scroll.animateScrollTo(0) }; if (keypad) activate(KeyTarget.CALL) else callFocus.requestFocus() }
            e != null -> editTime = true
        }
    }
    val saveIt = { showSaveError(vm.save()) }
    val saveNext = { showSaveError(vm.saveAndNext()) }

    // A physical keyboard (Bluetooth, USB OTG) — a whole outing without touching the screen: Space in the callsign goes
    // on to the frequency or report, Enter logs from any field (a new card: logs and opens the next), "20m", "CW",
    // "14195" in the callsign switch band, mode or frequency, ↑ in an empty callsign opens the contact just logged,
    // Esc wipes a new card (a second Esc closes it). F2–F6, F8, F12 and Alt+1…9 as on the computer.
    var callFocused by remember { mutableStateOf(false) }
    val hardKeys = Modifier.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || !e.fromHardware()) return@onPreviewKeyEvent false
        val plain = !e.isAltPressed && !e.isCtrlPressed && !e.isMetaPressed && !e.isShiftPressed
        val digit = DIGIT_KEYS.indexOf(e.key)
        val form = vm.form
        when {
            (e.key == Key.Enter || e.key == Key.NumPadEnter) && plain -> {
                when {
                    callFocused && vm.runCallCommand() -> {}
                    form.isNew -> saveNext()
                    else -> saveIt()
                }
                true
            }
            e.key == Key.Spacebar && callFocused -> {
                if (!vm.runCallCommand()) { if (vm.form.freq.isBlank()) freqFocus.requestFocus() else rstFocus.requestFocus() }
                true
            }
            e.key == Key.DirectionUp && callFocused && form.isNew && form.call.isEmpty() -> { vm.editLast(); true }
            e.key == Key.Escape -> { if (form.isNew && form.call.isNotBlank()) vm.clearCard() else close(); true }
            e.key == Key.F2 -> { callFocus.requestFocus(); true }
            e.key == Key.F3 -> { freqFocus.requestFocus(); true }
            e.key == Key.F4 -> { rstFocus.requestFocus(); true }
            e.key == Key.F5 -> { rstRcvdFocus.requestFocus(); true }
            e.key == Key.F6 -> { vm.setNow(); true }
            // F7: a voice note for this card, as the microphone button above does.
            e.key == Key.F7 -> {
                if (vm.cardRecordingSince != null) vm.stopCardRecording()
                else if (form.isNew && form.audio.isBlank()) { if (hasMicPermission()) vm.startCardRecording() else requestMicPermission() }
                true
            }
            e.key == Key.F8 -> { if (form.isNew) saveNext(); true }
            e.key == Key.F12 -> { saveIt(); true }
            e.isAltPressed && digit >= 0 && !e.isShiftPressed -> { BANDS.filter { it in vm.enabledBands }.getOrNull(digit)?.let(vm::setBand); true }
            e.isAltPressed && digit >= 0 && e.isShiftPressed -> { MODES.filter { it in vm.enabledModes }.getOrNull(digit)?.let(vm::setMode); true }
            else -> false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding().then(hardKeys)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Close, tr("Закрыть без сохранения"), Modifier.size(30.dp))
            }
            Text(if (f.isNew) tr("Новый QSO") else tr("Запись QSO"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            // A voice note for a card typed by hand: offered once QRZ.ru has found the station, runs until ■ or "Сохранить".
            val since = vm.cardRecordingSince
            if (since != null) CardRecording(since, onStop = vm::stopCardRecording)
            else if (f.isNew && f.audio.isBlank() && vm.lookup is Lookup.Found) {
                IconButton(
                    onClick = { if (hasMicPermission()) vm.startCardRecording() else requestMicPermission() },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Filled.Mic, tr("Записать голосовую заметку"), Modifier.size(28.dp), tint = x.muted.copy(alpha = 0.6f)) }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- callsign ---
            // IntrinsicSize.Min: the play button takes the height of the field; top padding skips the floating label.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (keypad) KeyField(
                    tr("Позывной корреспондента"), f.call, active == KeyTarget.CALL, replace && active == KeyTarget.CALL,
                    error == CALL_ERROR, Modifier.weight(1f), big = true,
                ) { activate(KeyTarget.CALL) }
                else OutlinedTextField(
                    value = f.call,
                    onValueChange = { error = null; vm.setCall(it) },
                    modifier = Modifier.weight(1f).focusRequester(callFocus).onFocusChanged { callFocused = it.isFocused },
                    label = { Text(tr("Позывной корреспондента"), fontSize = 16.sp) },
                    isError = error == CALL_ERROR,
                    supportingText = if (error == CALL_ERROR) { { Text(CALL_ERROR) } } else null,
                    textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 1.sp),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    // "Далее" goes to what is usually typed next: the frequency, or the report if it is already there.
                    keyboardActions = KeyboardActions(onNext = { if (f.freq.isBlank()) freqFocus.requestFocus() else rstFocus.requestFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary),
                )
                if (f.audio.isNotBlank()) {
                    val context = LocalContext.current
                    AudioPlayButton(
                        vm.voice.file(f.audio),
                        onDelete = vm::removeAudio,
                        onShare = {
                            val form = vm.form
                            shareForm(context, form, vm.myPositionFor(form), vm.voice.file(form.audio))
                        },
                        modifier = Modifier.fillMaxHeight().padding(top = 8.dp),
                    )
                }
            }
            if (keypad && error == CALL_ERROR) Text(CALL_ERROR, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            // With a physical keyboard a saved card takes the cursor too, so its keys work at once.
            if (!keypad && (f.isNew || vm.hardKeyboard || vm.openedByKeys)) LaunchedEffect(Unit) { callFocus.requestFocus() }
            // Callsigns from the log that start with what is typed: one tap instead of the rest.
            if (f.isNew) CallSuggestions(vm) { call ->
                vm.setCall(call)
                if (keypad) activate(nextOf(KeyTarget.CALL))
                else if (f.freq.isBlank()) freqFocus.requestFocus() else rstFocus.requestFocus()
            }

            StationCard(vm)
            DupeCard(vm)
            HistoryCard(vm)

            // --- date/time ---
            if (editTime) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(tr("Дата UTC"), f.date, { vm.update(f.copy(date = it)) }, Modifier.weight(1.3f), KeyboardType.Number, mono = true)
                    Field(tr("Время UTC"), f.time, { vm.update(f.copy(time = it)) }, Modifier.weight(1f), KeyboardType.Number, mono = true)
                    IconButton(onClick = vm::setNow, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Refresh, tr("Текущее время")) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
                        .clickable(onClickLabel = tr("Изменить дату и время")) { editTime = true }
                        .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Schedule, null, Modifier.size(22.dp), tint = x.muted)
                    Spacer(Modifier.width(10.dp))
                    // Time first (what changes), then the date; wraps to a second line with a large system font.
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp)) { append(f.time) }
                            withStyle(SpanStyle(fontSize = 14.sp, color = x.muted)) { append(" UTC   ") }
                            withStyle(SpanStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp)) { append(f.date) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::setNow) { Icon(Icons.Filled.Refresh, tr("Текущее время")) }
                    IconButton(onClick = { editTime = true }) { Icon(Icons.Filled.Edit, tr("Изменить дату и время")) }
                }
            }

            // Frequency first: typing it picks the band below by itself.
            if (keypad) KeyField(
                tr("Частота, МГц"), f.freq, active == KeyTarget.FREQ, replace && active == KeyTarget.FREQ, false,
                Modifier.fillMaxWidth().bringIntoViewRequester(freqView),
            ) { activate(KeyTarget.FREQ) }
            else Field(tr("Частота, МГц"), f.freq, vm::setFreq, Modifier.fillMaxWidth().focusRequester(freqFocus), KeyboardType.Decimal, mono = true, onNext = { rstFocus.requestFocus() })

            // One scrolling row each: what is switched on in the settings, plus the record's own value if that one is off.
            // Nothing to choose from (one value on, and the record has it) — the label alone shows it.
            val modes = MODES.filter { it in vm.enabledModes || it == f.mode } + listOfNotNull(f.mode.takeIf { it.isNotBlank() && it !in MODES })
            val bands = BANDS.filter { it in vm.enabledBands || it == f.band } + listOfNotNull(f.band.takeIf { it.isNotBlank() && it !in BANDS })
            Label(tr("Вид связи"), f.mode)
            if (modes != listOf(f.mode)) ChipRow(modes, f.mode, vm::setMode)
            Label(tr("Диапазон"), f.band)
            if (bands != listOf(f.band)) ChipRow(bands, f.band, vm::setBand)

            // Reports: the usual values one tap away, digits keyboard unless the mode reports in dB.
            val quick = quickReports(f.mode)
            val rstKeyboard = if (quick.first().startsWith("-")) KeyboardType.Text else KeyboardType.Number
            Row(Modifier.bringIntoViewRequester(rstView), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (keypad) KeyField(tr("RST отправлен"), f.rstSent, active == KeyTarget.RST_SENT, replace && active == KeyTarget.RST_SENT, false, Modifier.fillMaxWidth()) { activate(KeyTarget.RST_SENT) }
                    else RstField(tr("RST отправлен"), f.rstSent, { vm.update(vm.form.copy(rstSent = it)) }, Modifier.fillMaxWidth().focusRequester(rstFocus), rstKeyboard, onNext = { rstRcvdFocus.requestFocus() })
                    QuickValues(quick, f.rstSent) { vm.update(vm.form.copy(rstSent = it)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (keypad) KeyField(tr("RST принят"), f.rstRcvd, active == KeyTarget.RST_RCVD, replace && active == KeyTarget.RST_RCVD, false, Modifier.fillMaxWidth()) { activate(KeyTarget.RST_RCVD) }
                    else RstField(tr("RST принят"), f.rstRcvd, { vm.update(vm.form.copy(rstRcvd = it)) }, Modifier.fillMaxWidth().focusRequester(rstRcvdFocus), rstKeyboard, onNext = null)
                    QuickValues(quick, f.rstRcvd) { vm.update(vm.form.copy(rstRcvd = it)) }
                }
            }
            // A contest contact: the exchanged numbers right under the reports, as in the contest card.
            val contest = ContestMode.isContest(f.adif)
            if (contest) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(tr("Код передан"), f.adif[ContestMode.SENT].orEmpty(), { vm.setAdif(ContestMode.SENT, it) }, Modifier.weight(1f), KeyboardType.Number, mono = true)
                Field(tr("Код принят"), f.adif[ContestMode.RCVD].orEmpty(), { vm.setAdif(ContestMode.RCVD, it) }, Modifier.weight(1f), KeyboardType.Number, mono = true, last = true)
            }

            // --- distance & map ---
            DistanceCard(vm, onOpenMap = { showMap = true })

            // --- all fields: each group opens on its own, the header says how many fields are filled ---
            Label(tr("Все поля ADIF"))
            val filled = { keys: Collection<String> -> keys.count { !f.adif[it].isNullOrBlank() } }
            // Name, QTH, country and locator are edited in the card at the top; only fields found nowhere else here.
            FieldGroup(tr("Корреспондент"), filled(AdifLabels.THEM.keys)) { AdifFields(vm, AdifLabels.THEM) }
            // End time and receive band/frequency are filled from the main fields, see AdifLabels.DERIVED.
            FieldGroup(
                tr("Связь"),
                filled(AdifLabels.CONTACT.keys) + listOf(f.power.isNotBlank(), f.qslSent, f.qslRcvd, f.comment.isNotBlank()).count { it },
            ) {
                Field(tr("Мощность, Вт"), f.power, { vm.update(f.copy(power = it)) }, Modifier.fillMaxWidth(), KeyboardType.Number, mono = true)
                AdifFields(vm, if (contest) AdifLabels.CONTACT.filterKeys { it != ContestMode.SENT && it != ContestMode.RCVD } else AdifLabels.CONTACT)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(f.qslSent, { vm.update(f.copy(qslSent = it)) })
                    Text(tr("QSL отправлена"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { vm.update(f.copy(qslSent = !f.qslSent)) })
                    Spacer(Modifier.width(16.dp))
                    Checkbox(f.qslRcvd, { vm.update(f.copy(qslRcvd = it)) })
                    Text(tr("QSL получена"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { vm.update(f.copy(qslRcvd = !f.qslRcvd)) })
                }
                OutlinedTextField(
                    value = f.comment, onValueChange = { vm.update(f.copy(comment = it)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text(tr("Комментарий")) },
                    textStyle = MaterialTheme.typography.bodyLarge, minLines = 2, shape = RoundedCornerShape(12.dp),
                )
            }
            FieldGroup(tr("Моя станция"), filled(AdifLabels.MINE.keys) + listOf(f.myCall, f.myLocator).count { it.isNotBlank() }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("Мой позывной"), f.myCall, { vm.update(f.copy(myCall = it.uppercase())) }, Modifier.weight(1f), mono = true)
                    Field(tr("Мой локатор"), f.myLocator, { vm.update(f.copy(myLocator = it)) }, Modifier.weight(1f), mono = true)
                }
                AdifFields(vm, AdifLabels.MINE)
            }
            FieldGroup(tr("Прохождение"), filled(AdifLabels.SPACE_WEATHER.keys)) { AdifFields(vm, AdifLabels.SPACE_WEATHER, perRow = 3) }
            // Anything else the imported log carried, shown under its ADIF name.
            val other = f.adif.keys.filter { it !in AdifLabels.KNOWN && it !in ExportFormat.FIELDS }
            if (other.isNotEmpty()) FieldGroup(tr("Другие поля ADIF"), filled(other)) { AdifFields(vm, other.associateWith { it }) }

            // --- at the very bottom: when this contact went into ADIF, CSV and contest-report files ---
            if (!f.isNew) ExportMarks(f.adif, onClear = vm::clearExportMark)
            Spacer(Modifier.height(8.dp))
        }

        // --- the app's keypad, while one of its fields is active: keys, then "hide" and "to the next field" ---
        val target = active
        if (keypad && target != null && !imeVisible) {
            HorizontalDivider(color = x.line)
            Column(Modifier.padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Keypad(
                    compact = compact,
                    onKey = { c ->
                        val v = if (replace) c else valueOf(target) + c
                        replace = false
                        setValue(target, v.take(if (target == KeyTarget.CALL) 15 else if (target == KeyTarget.FREQ) 12 else 6))
                    },
                    onBackspace = { setValue(target, if (replace) "" else valueOf(target).dropLast(1)); replace = false },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedIconButton(
                        onClick = { activate(null) },
                        modifier = Modifier.size(keypadButtonHeight(compact)),
                        shape = RoundedCornerShape(10.dp),
                    ) { Icon(Icons.Filled.KeyboardHide, tr("Спрятать клавиатуру")) }
                    val next = nextOf(target)
                    OutlinedButton(
                        onClick = { activate(next) },
                        modifier = Modifier.weight(1f).height(keypadButtonHeight(compact)), shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            when (next) {
                                KeyTarget.FREQ -> tr("→ Частота")
                                KeyTarget.RST_SENT -> tr("→ RST отправлен")
                                KeyTarget.RST_RCVD -> tr("→ RST принят")
                                else -> tr("Готово")
                            },
                            fontSize = if (compact) 16.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                    }
                }
            }
        }

        // --- bottom bar ---
        HorizontalDivider(color = x.line)
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            error?.takeIf { it != CALL_ERROR }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!f.isNew) {
                    // Bin icon only; the confirmation dialog still asks before anything is deleted.
                    OutlinedIconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Icon(Icons.Filled.DeleteOutline, tr("Удалить связь"), Modifier.size(28.dp)) }
                    // Saved records only: send the contact to another app, with its voice note if there is one.
                    val context = LocalContext.current
                    FilledTonalIconButton(
                        onClick = {
                            val form = vm.form
                            shareForm(context, form, vm.myPositionFor(form), form.audio.ifBlank { null }?.let { vm.voice.file(it) })
                        },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Icon(Icons.Filled.Share, tr("Отправить QSO в другое приложение"), Modifier.size(28.dp)) }
                }
                // New card: "＋ Следующая" saves and opens the next card on the same band, mode and frequency.
                if (f.isNew) FilledTonalButton(
                    onClick = saveNext,
                    modifier = Modifier.height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Следующая"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = saveIt,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(tr("Сохранить"), fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showMap && me != null && them != null) MapOverlay(vm, onClose = { showMap = false })

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(tr("Закрыть без сохранения?")) },
            text = {
                Text(
                    if (f.isNew && f.audio.isNotBlank()) tr("Введённые данные и голосовая заметка будут потеряны.") else tr("Введённые изменения не сохранятся."),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; vm.closeEditor() }) { Text(tr("Закрыть"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { Button(onClick = { confirmClose = false }) { Text(tr("Продолжить ввод")) } },
        )
    }

    if (confirmDelete) {
        val when_ = "${f.date}, ${f.time} UTC"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Удалить связь с %s?", f.call)) },
            text = { Text(tr("%s, %s %s. Запись пропадёт из лога.", when_, f.band, f.mode), style = MaterialTheme.typography.bodyLarge) },
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

/** Name, city and locator of the other station, in large type. */
@Composable
private fun StationCard(vm: AppViewModel) {
    val f = vm.form
    val x = LocalExtra.current
    val lookup = vm.lookup
    if (f.call.length < 3 && f.name.isBlank()) return
    // The only place to edit name, QTH, country and locator: tap the pencil, the card turns into fields.
    var editing by remember(f.id, f.createdAt) { mutableStateOf(false) }

    val hasInfo = f.name.isNotBlank() || f.qth.isNotBlank() || f.country.isNotBlank() || f.locator.isNotBlank()
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val ink = MaterialTheme.colorScheme.onPrimaryContainer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Корреспондент"), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            IconButton(onClick = { editing = !editing }) {
                Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, if (editing) tr("Готово") else tr("Изменить данные корреспондента"), tint = ink)
            }
        }
        if (editing) {
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(tr("Имя"), f.name, { vm.update(f.copy(name = it, infoFromQrz = false)) }, Modifier.fillMaxWidth())
                Field(tr("QTH (город)"), f.qth, { vm.update(f.copy(qth = it, infoFromQrz = false)) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("Страна"), f.country, { vm.update(f.copy(country = it, infoFromQrz = false)) }, Modifier.weight(1f))
                    Field(tr("QTH-локатор"), f.locator, vm::setLocator, Modifier.weight(1f), mono = true)
                }
            }
        } else if (hasInfo) {
            if (f.name.isNotBlank()) Text(f.name, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = ink)
            val place = listOf(f.qth, f.country).filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotBlank()) Text(place, fontSize = 24.sp, lineHeight = 30.sp, color = ink)
            if (f.locator.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(tr("Локатор"), fontSize = 18.sp, color = ink, modifier = Modifier.padding(end = 10.dp))
                    Text(
                        f.locator, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = ink,
                        modifier = Modifier.border(2.dp, ink.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
            // RDA district from the QRZ.ru site page (ADIF CNTY).
            val rda = f.adif["CNTY"].orEmpty()
            if (rda.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("RDA", fontSize = 18.sp, color = ink, modifier = Modifier.padding(end = 10.dp))
                    Text(
                        rda, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = ink,
                        modifier = Modifier.border(2.dp, ink.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }
        when (lookup) {
            Lookup.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ink)
                Spacer(Modifier.width(10.dp))
                Text(tr("Ищу на QRZ.ru…"), fontSize = 18.sp, color = ink)
            }
            Lookup.NotFound -> if (!hasInfo) Text(tr("На QRZ.ru такого позывного нет"), fontSize = 18.sp, color = ink)
            // Country and region by prefix from HamQTH; say where the data came from and why QRZ.ru did not help.
            // No answer online: filled from the search history, with where and when it was found.
            is Lookup.FromHistory -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tr("Из истории поиска: %s, %s", sourceTitle(lookup.entry.source), DATE_FMT.format(utc(lookup.entry.searchedAt))),
                    fontSize = 16.sp, color = ink.copy(alpha = 0.8f),
                )
                lookup.problem?.let { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p, fontSize = 16.sp, color = ink.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::retryLookup) { Text(tr("Повторить")) }
                    }
                }
            }
            is Lookup.Approx -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("≈ Страна и область по позывному (HamQTH)"), fontSize = 16.sp, color = ink.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lookup.qrzProblem ?: if (!vm.hasQrzAccount) tr("Имя и точный QTH — с учётной записью QRZ.ru") else tr("На QRZ.ru такого позывного нет"),
                        fontSize = 16.sp, color = ink.copy(alpha = 0.8f), modifier = Modifier.weight(1f),
                    )
                    when {
                        lookup.qrzProblem != null -> TextButton(onClick = vm::retryLookup) { Text(tr("Повторить")) }
                        !vm.hasQrzAccount -> TextButton(onClick = { vm.openSettings(from = Screen.Edit) }) { Text(tr("Настройки")) }
                    }
                }
            }
            is Lookup.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lookup.message, fontSize = 17.sp, color = ink, modifier = Modifier.weight(1f))
                // Without an account retrying cannot help: go to the settings, the card waits and looks up on return.
                if (lookup.noAccount) TextButton(onClick = { vm.openSettings(from = Screen.Edit) }) { Text(tr("Настройки")) }
                else TextButton(onClick = vm::retryLookup) { Text(tr("Повторить")) }
            }
            else -> if (!hasInfo && !editing) Text(tr("Данные появятся здесь. Ввести вручную: ✎ справа"), fontSize = 18.sp, color = x.muted)
        }
    }
}

/** Highlights how often and when we last worked this callsign. */
@Composable
private fun HistoryCard(vm: AppViewModel) {
    val h = vm.history
    val x = LocalExtra.current
    if (vm.form.call.length < 3) return
    if (h.count == 0) {
        // For a saved record "no other contacts" is not news; the green note is only for a new one.
        if (!vm.form.isNew) return
        Row(
            Modifier.fillMaxWidth().background(x.okBg, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Новый позывной: связей ещё не было"), color = x.ok, style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val last = h.last!!
    val t = utc(last.timeUtc)
    Row(
        Modifier.fillMaxWidth()
            .background(x.hl, RoundedCornerShape(14.dp))
            .border(2.dp, x.hlBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${h.count}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = x.hlInk)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(if (vm.form.isNew) tr("Уже работали: %s QSO", h.count) else tr("Других QSO: %s", h.count), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = x.hlInk)
            Text(
                tr("Последняя %s %s UTC", DATE_FMT.format(t), TIME_FMT.format(t)),
                fontSize = 17.sp, color = x.hlInk,
            )
            val det = listOf(last.band, last.mode).filter { it.isNotBlank() }.joinToString(" ")
            if (det.isNotBlank()) Text(det, fontSize = 17.sp, color = x.hlInk)
            // The last contact on each band: is this one a new band for the station, and when was it worked there.
            if (h.byBand.size > 1 || (h.byBand.size == 1 && !h.byBand[0].band.equals(vm.form.band, ignoreCase = true))) {
                BandChips(h.byBand, ink = x.hlInk, chip = x.hlBorder.copy(alpha = 0.22f), current = vm.form.band, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun DistanceCard(vm: AppViewModel, onOpenMap: () -> Unit) {
    val x = LocalExtra.current
    val me = vm.myPositionFor(vm.form)
    val them = vm.form.position
    when {
        me == null -> Hint(tr("Укажите свой QTH-локатор в настройках, чтобы видеть расстояние до корреспондента."))
        them == null -> if (vm.form.call.length >= 3) Hint(tr("Нет координат корреспондента. Впишите его QTH-локатор в карточке «Корреспондент» (✎)."))
        else -> {
            val km = Geo.distanceKm(me, them)
            val az = Geo.bearing(me, them)
            Column(Modifier.fillMaxWidth().border(1.dp, x.line, RoundedCornerShape(16.dp))) {
                Box(Modifier.fillMaxWidth().height(170.dp)) {
                    PathMap(me, them, vm.form.myCall.ifBlank { vm.settings.myCall }, vm.form.call, interactive = false, modifier = Modifier.fillMaxSize())
                    // Transparent layer on top: the preview itself does not scroll, a tap opens the full map.
                    Box(Modifier.fillMaxSize().clickable(onClick = onOpenMap))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val approx = vm.form.approxPosition
                        Text((if (approx) "≈ " else "") + formatKm(km), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Text(
                            tr("азимут %s°", az.toInt()) + if (approx) tr(" · до центра области") else "",
                            style = MaterialTheme.typography.bodyLarge, color = x.muted,
                        )
                    }
                    OutlinedButton(onClick = onOpenMap, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Map, null)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Карта"))
                    }
                }
            }
        }
    }
}

@Composable
private fun MapOverlay(vm: AppViewModel, onClose: () -> Unit) {
    val me = vm.myPositionFor(vm.form) ?: return
    val them = vm.form.position ?: return
    val x = LocalExtra.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PathMap(me, them, vm.form.myCall.ifBlank { vm.settings.myCall }, vm.form.call, interactive = true, modifier = Modifier.fillMaxSize())
        Row(
            Modifier.statusBarsPadding().padding(12.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(28.dp))
            }
            Text("${vm.form.myCall.ifBlank { vm.settings.myCall }} → ${vm.form.call}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .border(1.dp, x.line, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val az = Geo.bearing(me, them)
            val back = Geo.bearing(them, me)
            Text((if (vm.form.approxPosition) "≈ " else "") + formatKm(Geo.distanceKm(me, them)), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 36.sp)
            Pair(tr("Азимут на корреспондента"), "${az.toInt()}°")
            Pair(tr("Обратный азимут"), "${back.toInt()}°")
            Pair(tr("Локаторы"), "${vm.form.myLocator.ifBlank { vm.settings.myLocator }} → ${vm.form.locator.ifBlank { Geo.latLonToLocator(them) }}")
        }
    }
}

@Composable
private fun Pair(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = LocalExtra.current.muted, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

fun formatKm(km: Double): String = "%,d ".format(km.toInt()).replace(',', ' ') + tr("км")

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalExtra.current.muted,
        modifier = Modifier.fillMaxWidth().background(LocalExtra.current.field, RoundedCornerShape(12.dp)).padding(14.dp),
    )
}

/** Extra ADIF fields as text inputs, [perRow] to a row. */
@Composable
private fun AdifFields(vm: AppViewModel, labels: Map<String, String>, perRow: Int = 2) {
    labels.entries.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (key, label) ->
                Field(tr(label), vm.form.adif[key].orEmpty(), { vm.setAdif(key, it) }, Modifier.weight(1f))
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Label(text: String, value: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted, letterSpacing = 1.sp)
        // The value the buttons below have written into the form.
        if (value != null) {
            Text(
                "  " + value.ifBlank { tr("не выбран") },
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (value.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val CALL_ERROR get() = tr("Введите позывной")

/** Reports most often given in this mode, offered as one-tap buttons under the RST fields. */
private fun quickReports(mode: String): List<String> = when (defaultRst(mode)) {
    "599" -> listOf("599", "579", "559")
    "-10" -> listOf("-05", "-10", "-15")
    else -> listOf("59", "57", "55")
}

@Composable
private fun QuickValues(values: List<String>, current: String, onPick: (String) -> Unit) {
    val x = LocalExtra.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            val on = v == current
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                    .clickable(onClickLabel = tr("Отчёт %s", v)) { onPick(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Report field that selects its whole value on focus, so typing replaces the default "59" instead of appending to it.
 * [onNext] null: last field, the keyboard shows "Готово".
 */
@Composable
private fun RstField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, keyboard: KeyboardType, onNext: (() -> Unit)?) {
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    // A quick button or a mode change can set the value from outside; then show it with the cursor at the end.
    val shown = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        value = shown,
        onValueChange = { tfv = it; if (it.text != value) onChange(it.text) },
        modifier = modifier.onFocusChanged { if (it.isFocused) tfv = TextFieldValue(value, TextRange(0, value.length)) },
        label = { Text(label) },
        singleLine = true,
        textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (onNext == null) ImeAction.Done else ImeAction.Next),
        keyboardActions = if (onNext != null) KeyboardActions(onNext = { onNext() }) else KeyboardActions.Default,
    )
}

/** Chips in one horizontally scrolling row; the selected one is scrolled into view. */
@Composable
private fun ChipRow(items: List<String>, selected: String, onPick: (String) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected, items) {
        val i = items.indexOf(selected)
        if (i >= 0) state.animateScrollToItem((i - 1).coerceAtLeast(0))
    }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(items) { item -> Chip(item, item == selected) { onPick(item) } }
    }
}

/** One line per export format: when the contact was exported to it, or that it has not been; a mark can be taken off. */
@Composable
private fun ExportMarks(adif: Map<String, String>, onClear: (ExportFormat) -> Unit) {
    val x = LocalExtra.current
    Label(tr("Экспорт"))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field).padding(horizontal = 16.dp, vertical = 6.dp)) {
        ExportFormat.entries.forEach { format ->
            val at = format.exportedAt(adif)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                ExportDot(format, on = at != null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(format.title, style = MaterialTheme.typography.titleMedium)
                    Text(at ?: tr("не экспортировалась"), style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                }
                if (at != null) TextButton(onClick = { onClear(format) }) { Text(tr("Снять")) }
            }
        }
    }
}

/** A group of the "all fields" section: header with the number of filled fields, opens and closes on its own. */
@Composable
private fun FieldGroup(title: String, filledCount: Int, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
            .clickable(onClickLabel = if (open) tr("Свернуть") else tr("Развернуть")) { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            if (filledCount > 0) tr("  · заполнено %s", filledCount) else tr("  · пусто"),
            style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.weight(1f),
        )
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
    }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    capitalize: Boolean = false,
    /** Where "Далее" on the keyboard goes; by default the next field in layout order. */
    onNext: (() -> Unit)? = null,
    /** The last field of a sequence: the keyboard shows "Готово" and closes. */
    last: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        textStyle = if (mono) TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        else MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = if (onNext != null) KeyboardActions(onNext = { onNext() }) else KeyboardActions.Default,
    )
}

/** Red pill in the card's header while a voice note records: pulsing dot, elapsed time, ■. */
@Composable
private fun CardRecording(since: Long, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    val secs = ((now - since) / 1000).coerceAtLeast(0)
    Row(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(28.dp)).background(RecordRed)
            .clickable(onClickLabel = tr("Остановить запись"), onClick = onStop)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Mic, tr("Идёт запись"), Modifier.size(22.dp).alpha(a), tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text("%d:%02d".format(secs / 60, secs % 60), fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Stop, tr("Остановить запись"), Modifier.size(26.dp), tint = Color.White)
    }
}

/** Up to three callsigns from the log that start with what is typed, most recent first. */
@Composable
private fun CallSuggestions(vm: AppViewModel, onPick: (String) -> Unit) {
    val call = vm.form.call
    val list = remember(call, vm.allQsos) { vm.callSuggestions(call) }
    if (list.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Из журнала:"), style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
        list.forEach { c ->
            SuggestionChip(
                onClick = { onPick(c) },
                label = { Text(c, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            )
        }
    }
}

/** Red note when the station was already worked on this band and mode on the card's UTC day (a contest dupe). */
@Composable
private fun DupeCard(vm: AppViewModel) {
    val f = vm.form
    val dupe = remember(f.call, f.band, f.mode, f.date, f.id, vm.allQsos) { vm.dupeOf(f) } ?: return
    val t = utc(dupe.timeUtc)
    val today = DATE_FMT.format(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)) == f.date.trim()
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Повтор"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(12.dp))
        Text(
            tr("%s %s — уже было %s в %s UTC", dupe.band, dupe.mode, if (today) tr("сегодня") else DATE_FMT.format(t), TIME_FMT.format(t)),
            fontSize = 17.sp, color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
