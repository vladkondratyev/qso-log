package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.utc


@Composable
fun EditScreen(vm: AppViewModel) {
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

    // Closing a card with typed data asks first; an untouched one closes at once.
    val close = { if (vm.hasUnsavedChanges) confirmClose = true else vm.closeEditor() }
    BackHandler { if (showMap) showMap = false else close() }

    val me = vm.myPositionFor(vm.form)
    val them = f.position

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Close, "Закрыть без сохранения", Modifier.size(30.dp))
            }
            Text(if (f.isNew) "Новый QSO" else "Запись QSO", style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- callsign ---
            // IntrinsicSize.Min: the play button takes the height of the field; top padding skips the floating label.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = f.call,
                    onValueChange = { error = null; vm.setCall(it) },
                    modifier = Modifier.weight(1f).focusRequester(callFocus),
                    label = { Text("Позывной абонента", fontSize = 16.sp) },
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
                    AudioPlayButton(vm.voice.file(f.audio), onDelete = vm::removeAudio, modifier = Modifier.fillMaxHeight().padding(top = 8.dp))
                }
            }
            if (f.isNew) LaunchedEffect(Unit) { callFocus.requestFocus() }

            StationCard(vm)
            HistoryCard(vm)

            // --- date/time ---
            if (editTime) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field("Дата UTC", f.date, { vm.update(f.copy(date = it)) }, Modifier.weight(1.3f), KeyboardType.Number, mono = true)
                    Field("Время UTC", f.time, { vm.update(f.copy(time = it)) }, Modifier.weight(1f), KeyboardType.Number, mono = true)
                    IconButton(onClick = vm::setNow, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Refresh, "Текущее время") }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
                        .clickable(onClickLabel = "Изменить дату и время") { editTime = true }
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
                    IconButton(onClick = vm::setNow) { Icon(Icons.Filled.Refresh, "Текущее время") }
                    IconButton(onClick = { editTime = true }) { Icon(Icons.Filled.Edit, "Изменить дату и время") }
                }
            }

            // Frequency first: typing it picks the band below by itself.
            Field("Частота, МГц", f.freq, vm::setFreq, Modifier.fillMaxWidth().focusRequester(freqFocus), KeyboardType.Decimal, mono = true, onNext = { rstFocus.requestFocus() })

            // One scrolling row each: what is switched on in the settings, plus the record's own value if that one is off.
            Label("Вид связи", f.mode)
            ChipRow(MODES.filter { it in vm.enabledModes || it == f.mode } + listOfNotNull(f.mode.takeIf { it.isNotBlank() && it !in MODES }), f.mode, vm::setMode)
            Label("Диапазон", f.band)
            ChipRow(BANDS.filter { it in vm.enabledBands || it == f.band } + listOfNotNull(f.band.takeIf { it.isNotBlank() && it !in BANDS }), f.band, vm::setBand)

            // Reports: the usual values one tap away, digits keyboard unless the mode reports in dB.
            val quick = quickReports(f.mode)
            val rstKeyboard = if (quick.first().startsWith("-")) KeyboardType.Text else KeyboardType.Number
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RstField("RST отправлен", f.rstSent, { vm.update(vm.form.copy(rstSent = it)) }, Modifier.fillMaxWidth().focusRequester(rstFocus), rstKeyboard, onNext = { rstRcvdFocus.requestFocus() })
                    QuickValues(quick, f.rstSent) { vm.update(vm.form.copy(rstSent = it)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RstField("RST принят", f.rstRcvd, { vm.update(vm.form.copy(rstRcvd = it)) }, Modifier.fillMaxWidth().focusRequester(rstRcvdFocus), rstKeyboard, onNext = null)
                    QuickValues(quick, f.rstRcvd) { vm.update(vm.form.copy(rstRcvd = it)) }
                }
            }

            // --- distance & map ---
            DistanceCard(vm, onOpenMap = { showMap = true })

            // --- all fields: each group opens on its own, the header says how many fields are filled ---
            Label("Все поля ADIF")
            val filled = { keys: Collection<String> -> keys.count { !f.adif[it].isNullOrBlank() } }
            // Name, QTH, country and locator are edited in the card at the top; only fields found nowhere else here.
            FieldGroup("Абонент", filled(AdifLabels.THEM.keys)) { AdifFields(vm, AdifLabels.THEM) }
            // End time and receive band/frequency are filled from the main fields, see AdifLabels.DERIVED.
            FieldGroup(
                "Связь",
                filled(AdifLabels.CONTACT.keys) + listOf(f.power.isNotBlank(), f.qslSent, f.qslRcvd, f.comment.isNotBlank()).count { it },
            ) {
                Field("Мощность, Вт", f.power, { vm.update(f.copy(power = it)) }, Modifier.fillMaxWidth(), KeyboardType.Number, mono = true)
                AdifFields(vm, AdifLabels.CONTACT)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(f.qslSent, { vm.update(f.copy(qslSent = it)) })
                    Text("QSL отправлена", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { vm.update(f.copy(qslSent = !f.qslSent)) })
                    Spacer(Modifier.width(16.dp))
                    Checkbox(f.qslRcvd, { vm.update(f.copy(qslRcvd = it)) })
                    Text("QSL получена", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { vm.update(f.copy(qslRcvd = !f.qslRcvd)) })
                }
                OutlinedTextField(
                    value = f.comment, onValueChange = { vm.update(f.copy(comment = it)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Комментарий") },
                    textStyle = MaterialTheme.typography.bodyLarge, minLines = 2, shape = RoundedCornerShape(12.dp),
                )
            }
            FieldGroup("Моя станция", filled(AdifLabels.MINE.keys) + listOf(f.myCall, f.myLocator).count { it.isNotBlank() }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Мой позывной", f.myCall, { vm.update(f.copy(myCall = it.uppercase())) }, Modifier.weight(1f), mono = true)
                    Field("Мой локатор", f.myLocator, { vm.update(f.copy(myLocator = it)) }, Modifier.weight(1f), mono = true)
                }
                AdifFields(vm, AdifLabels.MINE)
            }
            FieldGroup("Прохождение", filled(AdifLabels.SPACE_WEATHER.keys)) { AdifFields(vm, AdifLabels.SPACE_WEATHER, perRow = 3) }
            // Anything else the imported log carried, shown under its ADIF name.
            val other = f.adif.keys.filter { it !in AdifLabels.KNOWN }
            if (other.isNotEmpty()) FieldGroup("Другие поля ADIF", filled(other)) { AdifFields(vm, other.associateWith { it }) }
            Spacer(Modifier.height(8.dp))
        }

        // --- bottom bar ---
        HorizontalDivider(color = x.line)
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            error?.takeIf { it != CALL_ERROR }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!f.isNew) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Удалить", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
                Button(
                    onClick = {
                        error = vm.save()
                        when {
                            // Show the problem where it is: the callsign at the top, the date/time fields opened.
                            error == CALL_ERROR -> { scope.launch { scroll.animateScrollTo(0) }; callFocus.requestFocus() }
                            error != null -> editTime = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Сохранить", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showMap && me != null && them != null) MapOverlay(vm, onClose = { showMap = false })

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Закрыть без сохранения?") },
            text = {
                Text(
                    if (f.isNew && f.audio.isNotBlank()) "Введённые данные и голосовая заметка будут потеряны." else "Введённые изменения не сохранятся.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; vm.closeEditor() }) { Text("Закрыть", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { Button(onClick = { confirmClose = false }) { Text("Продолжить ввод") } },
        )
    }

    if (confirmDelete) {
        val when_ = "${f.date}, ${f.time} UTC"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить связь с ${f.call}?") },
            text = { Text("$when_, ${f.band} ${f.mode}. Запись пропадёт из лога.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; vm.deleteCurrent() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
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

    val hasInfo = f.name.isNotBlank() || f.qth.isNotBlank() || f.locator.isNotBlank()
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val ink = MaterialTheme.colorScheme.onPrimaryContainer
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Абонент", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            IconButton(onClick = { editing = !editing }) {
                Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, if (editing) "Готово" else "Изменить данные абонента", tint = ink)
            }
        }
        if (editing) {
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Имя", f.name, { vm.update(f.copy(name = it, infoFromQrz = false)) }, Modifier.fillMaxWidth())
                Field("QTH (город)", f.qth, { vm.update(f.copy(qth = it, infoFromQrz = false)) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Страна", f.country, { vm.update(f.copy(country = it, infoFromQrz = false)) }, Modifier.weight(1f))
                    Field("QTH-локатор", f.locator, vm::setLocator, Modifier.weight(1f), mono = true)
                }
            }
        } else if (hasInfo) {
            if (f.name.isNotBlank()) Text(f.name, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = ink)
            val place = listOf(f.qth, f.country).filter { it.isNotBlank() }.joinToString(", ")
            if (place.isNotBlank()) Text(place, fontSize = 24.sp, lineHeight = 30.sp, color = ink)
            if (f.locator.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Локатор", fontSize = 18.sp, color = ink, modifier = Modifier.padding(end = 10.dp))
                    Text(
                        f.locator, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = ink,
                        modifier = Modifier.border(2.dp, ink.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }
        when (lookup) {
            Lookup.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ink)
                Spacer(Modifier.width(10.dp))
                Text("Ищу на QRZ.ru…", fontSize = 18.sp, color = ink)
            }
            Lookup.NotFound -> if (!hasInfo) Text("На QRZ.ru такого позывного нет", fontSize = 18.sp, color = ink)
            is Lookup.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lookup.message, fontSize = 17.sp, color = ink, modifier = Modifier.weight(1f))
                // Without an account retrying cannot help: go to the settings, the card waits and looks up on return.
                if (lookup.noAccount) TextButton(onClick = { vm.openSettings(from = Screen.Edit) }) { Text("Настройки") }
                else TextButton(onClick = vm::retryLookup) { Text("Повторить") }
            }
            else -> if (!hasInfo && !editing) Text("Данные появятся здесь. Ввести вручную: ✎ справа", fontSize = 18.sp, color = x.muted)
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
            Text("Новый позывной: связей ещё не было", color = x.ok, style = MaterialTheme.typography.titleMedium)
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
            Text(if (vm.form.isNew) "Уже работали: ${h.count} QSO" else "Других QSO: ${h.count}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = x.hlInk)
            Text(
                "Последняя ${DATE_FMT.format(t)} ${TIME_FMT.format(t)} UTC",
                fontSize = 17.sp, color = x.hlInk,
            )
            val det = listOf(last.band, last.mode).filter { it.isNotBlank() }.joinToString(" ")
            if (det.isNotBlank()) Text(det, fontSize = 17.sp, color = x.hlInk)
        }
    }
}

@Composable
private fun DistanceCard(vm: AppViewModel, onOpenMap: () -> Unit) {
    val x = LocalExtra.current
    val me = vm.myPositionFor(vm.form)
    val them = vm.form.position
    when {
        me == null -> Hint("Укажите свой QTH-локатор в настройках, чтобы видеть расстояние до абонента.")
        them == null -> if (vm.form.call.length >= 3) Hint("Нет координат абонента. Впишите его QTH-локатор в карточке «Абонент» (✎).")
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
                        Text(formatKm(km), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Text("азимут ${az.toInt()}°", style = MaterialTheme.typography.bodyLarge, color = x.muted)
                    }
                    OutlinedButton(onClick = onOpenMap, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Map, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Карта")
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", Modifier.size(28.dp))
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
            Text(formatKm(Geo.distanceKm(me, them)), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 36.sp)
            Pair("Азимут на абонента", "${az.toInt()}°")
            Pair("Обратный азимут", "${back.toInt()}°")
            Pair("Локаторы", "${vm.form.myLocator.ifBlank { vm.settings.myLocator }} → ${vm.form.locator.ifBlank { Geo.latLonToLocator(them) }}")
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

fun formatKm(km: Double): String = "%,d км".format(km.toInt()).replace(',', ' ')

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
                Field(label, vm.form.adif[key].orEmpty(), { vm.setAdif(key, it) }, Modifier.weight(1f))
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
                "  " + value.ifBlank { "не выбран" },
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (value.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val CALL_ERROR = "Введите позывной"

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
                    .clickable(onClickLabel = "Отчёт $v") { onPick(v) },
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

/** A group of the "all fields" section: header with the number of filled fields, opens and closes on its own. */
@Composable
private fun FieldGroup(title: String, filledCount: Int, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(x.field)
            .clickable(onClickLabel = if (open) "Свернуть" else "Развернуть") { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            if (filledCount > 0) "  · заполнено $filledCount" else "  · пусто",
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
