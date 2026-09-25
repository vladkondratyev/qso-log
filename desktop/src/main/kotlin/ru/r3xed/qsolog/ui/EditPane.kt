package ru.r3xed.qsolog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import ru.r3xed.qsolog.AppState
import ru.r3xed.qsolog.SAVE_SHORTCUT
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.Lookup
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.AdifLabels
import ru.r3xed.qsolog.data.BANDS
import ru.r3xed.qsolog.data.Geo
import ru.r3xed.qsolog.data.MODES
import ru.r3xed.qsolog.utc

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditPane(vm: AppState) {
    val f = vm.form
    val x = LocalExtra.current
    var showMap by remember(vm.editSession) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var more by remember(vm.editSession) { mutableStateOf(!f.isNew) }

    val me = vm.myPositionFor(vm.form)
    val them = f.position

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeEditor, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Close, "Закрыть без сохранения (Esc)", Modifier.size(30.dp))
            }
            Text(if (f.isNew) "Новый QSO" else "Запись QSO", style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- callsign ---
            val focus = remember { FocusRequester() }
            // IntrinsicSize.Min: the play button takes the height of the field; top padding skips the floating label.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = f.call,
                    onValueChange = vm::setCall,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    label = { Text("Позывной абонента", fontSize = 16.sp) },
                    textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 1.sp),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary),
                )
                if (f.audio.isNotBlank()) {
                    AudioPlayButton(vm.voice.file(f.audio), onDelete = vm::removeAudio, modifier = Modifier.fillMaxHeight().padding(top = 8.dp))
                }
            }
            LaunchedEffect(vm.editSession) { if (f.isNew) focus.requestFocus() }

            StationCard(vm)
            HistoryCard(vm)

            // --- date/time ---
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Дата UTC", f.date, { vm.update(f.copy(date = it)) }, Modifier.weight(1.3f), KeyboardType.Number, mono = true)
                Field("Время UTC", f.time, { vm.update(f.copy(time = it)) }, Modifier.weight(1f), KeyboardType.Number, mono = true)
                IconButton(onClick = vm::setNow, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Refresh, "Текущее время") }
            }

            // Buttons: what is switched on in the settings, plus the record's own value if that one is switched off.
            Label("Диапазон", f.band)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BANDS.filter { it in vm.enabledBands || it == f.band }.forEach { b -> Chip(b, f.band == b) { vm.setBand(b) } }
                if (f.band.isNotBlank() && f.band !in BANDS) Chip(f.band, true) {}
            }
            Label("Вид связи", f.mode)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MODES.filter { it in vm.enabledModes || it == f.mode }.forEach { m -> Chip(m, f.mode == m) { vm.setMode(m) } }
                if (f.mode.isNotBlank() && f.mode !in MODES) Chip(f.mode, true) {}
            }

            Field("Частота, МГц", f.freq, vm::setFreq, Modifier.fillMaxWidth(), KeyboardType.Decimal, mono = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("RST отправлен", f.rstSent, { vm.update(f.copy(rstSent = it)) }, Modifier.weight(1f), KeyboardType.Text, mono = true)
                Field("RST принят", f.rstRcvd, { vm.update(f.copy(rstRcvd = it)) }, Modifier.weight(1f), KeyboardType.Text, mono = true)
            }

            // --- distance & map ---
            DistanceCard(vm, onOpenMap = { showMap = true })

            // --- more fields ---
            Surface(
                onClick = { more = !more },
                shape = RoundedCornerShape(12.dp),
                color = x.field,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Все поля: абонент, связь, моя станция, ADIF", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
            }
            if (more) {
                // Name, QTH, country and locator are edited in the card at the top; only fields found nowhere else here.
                Group("Абонент")
                AdifFields(vm, AdifLabels.THEM)

                // End time and receive band/frequency are filled from the main fields, see AdifLabels.DERIVED.
                Group("Связь")
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

                Group("Моя станция")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Мой позывной", f.myCall, { vm.update(f.copy(myCall = it.uppercase())) }, Modifier.weight(1f), mono = true)
                    Field("Мой локатор", f.myLocator, { vm.update(f.copy(myLocator = it)) }, Modifier.weight(1f), mono = true)
                }
                AdifFields(vm, AdifLabels.MINE)

                Group("Прохождение")
                AdifFields(vm, AdifLabels.SPACE_WEATHER, perRow = 3)

                // Anything else the imported log carried, shown under its ADIF name.
                val other = f.adif.keys.filter { it !in AdifLabels.KNOWN }
                if (other.isNotEmpty()) {
                    Group("Другие поля ADIF")
                    AdifFields(vm, other.associateWith { it })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // --- bottom bar ---
        HorizontalDivider(color = x.line)
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            vm.formError?.let {
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
                    onClick = vm::trySave,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Сохранить", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(SAVE_SHORTCUT, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                }
            }
        }
    }

    if (showMap && me != null && them != null) MapOverlay(vm, onClose = { showMap = false })

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
private fun StationCard(vm: AppState) {
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
                TextButton(onClick = vm::retryLookup) { Text("Повторить") }
            }
            else -> if (!hasInfo && !editing) Text("Данные появятся здесь. Ввести вручную: ✎ справа", fontSize = 18.sp, color = x.muted)
        }
    }
}

/** Highlights how often and when we last worked this callsign. */
@Composable
private fun HistoryCard(vm: AppState) {
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
private fun DistanceCard(vm: AppState, onOpenMap: () -> Unit) {
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
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    TileMap(me, them, vm.form.myCall.ifBlank { vm.settings.myCall }, vm.form.call, interactive = false, modifier = Modifier.fillMaxSize())
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
private fun MapOverlay(vm: AppState, onClose: () -> Unit) {
    val me = vm.myPositionFor(vm.form) ?: return
    val them = vm.form.position ?: return
    val x = LocalExtra.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TileMap(me, them, vm.form.myCall.ifBlank { vm.settings.myCall }, vm.form.call, interactive = true, modifier = Modifier.fillMaxSize())
        Row(
            Modifier.padding(12.dp)
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
            Modifier.align(Alignment.BottomStart).padding(16.dp).widthIn(max = 460.dp).fillMaxWidth()
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

/** Section heading inside the expanded card. */
@Composable
private fun Group(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** Extra ADIF fields as text inputs, [perRow] to a row. */
@Composable
private fun AdifFields(vm: AppState, labels: Map<String, String>, perRow: Int = 2) {
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
            imeAction = ImeAction.Next,
        ),
    )
}
