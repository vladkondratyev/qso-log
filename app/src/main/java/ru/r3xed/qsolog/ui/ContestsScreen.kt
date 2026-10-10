package ru.r3xed.qsolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.DATE_FMT
import ru.r3xed.qsolog.TIME_FMT
import ru.r3xed.qsolog.data.Contest
import ru.r3xed.qsolog.tr
import ru.r3xed.qsolog.utc
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The contest book (⋮ menu while contest mode is on): contests with their CONTEST code, time and tours. The chosen
 * one gets the contacts of the contest card, each with its tour; the report then takes exactly them.
 */
@Composable
fun ContestsScreen(vm: AppViewModel) {
    val x = LocalExtra.current
    BackHandler { vm.closeContests() }
    var editing by remember { mutableStateOf<Contest?>(null) }
    var deleting by remember { mutableStateOf<Contest?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeContests, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад"), Modifier.size(30.dp))
            }
            Text(tr("Справочник контестов"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { editing = Contest() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Add, tr("Новое соревнование"), Modifier.size(30.dp))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                tr("Связи контест-режима записываются в выбранное соревнование: его код попадает в отчёт ЕРМАК/Cabrillo, номер тура — в каждую связь."),
                style = MaterialTheme.typography.bodyMedium, color = x.muted,
            )
            ContestRow(selected = vm.activeContest == null, onSelect = { vm.activateContest(null) }) {
                Text(tr("Без соревнования"), style = MaterialTheme.typography.titleMedium)
                Text(tr("Связи получают только флажок контеста"), style = MaterialTheme.typography.bodyMedium, color = x.muted)
            }
            vm.contests.forEach { c ->
                val count = remember(c.id, vm.allQsos) { vm.contestCount(c) }
                ContestRow(selected = vm.activeContest?.id == c.id, onSelect = { vm.activateContest(c.id) }, onEdit = { editing = c }, onDelete = { deleting = c }) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    contestDetails(c, count).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = x.muted) }
                    ContestPhaseText(c, MaterialTheme.colorScheme.primary, maxLines = 2)
                }
            }
            if (vm.contests.isEmpty()) {
                FilledTonalButton(onClick = { editing = Contest() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Добавить соревнование"))
                }
            }
        }
    }

    editing?.let { c ->
        ContestEditDialog(c, isNew = vm.contests.none { it.id == c.id }, onDismiss = { editing = null }) { vm.saveContest(it); editing = null }
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("Удалить «%s»?", c.title)) },
            text = { Text(tr("Связи останутся в журнале с флажком контеста и кодом соревнования."), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteContest(c.id); deleting = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(tr("Удалить")) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Отмена")) } },
        )
    }
}

/** A card of the list: a radio button to make it the active contest, the details, edit and delete. */
@Composable
private fun ContestRow(selected: Boolean, onSelect: () -> Unit, onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else x.field)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null, Modifier.padding(horizontal = 8.dp))
        Column(Modifier.weight(1f)) { content() }
        if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, tr("Изменить")) }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, tr("Удалить")) }
    }
}

/** Code, time and tours of [c] in short lines. */
private fun contestDetails(c: Contest, count: Int): List<String> = listOfNotNull(
    c.code.ifBlank { null }?.let { "CONTEST: $it" },
    c.start.let { s ->
        val e = c.finish
        when {
            s != null && e != null -> "${stamp(s)} – ${stamp(e)} UTC"
            s != null -> tr("с %s UTC", stamp(s))
            e != null -> tr("до %s UTC", stamp(e))
            else -> null
        }
    },
    if (c.hasTours) tr("Туры: %s по %s мин", c.tourCount, c.tourMinutes) + if (c.dupesPerTour) tr(" · повторы обнуляются") else "" else null,
    tr("QSO: %s", count),
)

private fun stamp(ms: Long): String = utc(ms).let { "${DATE_FMT.format(it)} ${TIME_FMT.format(it)}" }

/** The current time, ticking every second. */
@Composable
private fun ticking(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Where the contest is now: "тур 3 из 12 · до конца тура 04:12", "до начала 1:05:09"; [short] for the log's header:
 * "тур 3/12 · 04:12". Nothing for a contest without time.
 */
fun contestPhase(c: Contest, now: Long, short: Boolean = false): String? = when (val p = c.phase(now)) {
    is Contest.Phase.Before -> if (short) tr("старт через %s", Contest.clock(p.left)) else tr("до начала %s", Contest.clock(p.left))
    is Contest.Phase.Tour ->
        if (short) tr("тур %s/%s · %s", p.n, p.count, Contest.clock(p.left))
        else tr("тур %s из %s · до конца тура %s", p.n, p.count, Contest.clock(p.left))
    is Contest.Phase.Running -> p.left?.let { if (short) tr("осталось %s", Contest.clock(it)) else tr("до конца %s", Contest.clock(it)) }
    Contest.Phase.Over -> tr("закончилось")
}

/** [contestPhase] as a line that ticks every second. */
@Composable
fun ContestPhaseText(c: Contest, color: Color, prefix: String = "", maxLines: Int = 1, short: Boolean = false) {
    val text = contestPhase(c, ticking(), short) ?: if (prefix.isEmpty()) return else null
    Text(
        listOfNotNull(prefix.ifEmpty { null }, text).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = color,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** The tour running now; changes (and recomposes the caller) only when a new tour starts. */
@Composable
fun rememberTour(c: Contest?): Int? = produceState(c?.tourAt(System.currentTimeMillis()), c) {
    while (c != null) {
        delay(1000 - System.currentTimeMillis() % 1000)
        value = c.tourAt(System.currentTimeMillis())
    }
}.value

/** Adds or changes a contest: name, code, start and end (UTC), tours. */
@Composable
private fun ContestEditDialog(c: Contest, isNew: Boolean, onDismiss: () -> Unit, onSave: (Contest) -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var code by remember { mutableStateOf(c.code) }
    var startDate by remember { mutableStateOf(c.start?.let { DATE_FMT.format(utc(it)) }.orEmpty()) }
    var startTime by remember { mutableStateOf(c.start?.let { TIME_FMT.format(utc(it)) }.orEmpty()) }
    var endDate by remember { mutableStateOf(c.end?.let { DATE_FMT.format(utc(it)) }.orEmpty()) }
    var endTime by remember { mutableStateOf(c.end?.let { TIME_FMT.format(utc(it)) }.orEmpty()) }
    var tourMinutes by remember { mutableStateOf(c.tourMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var tourCount by remember { mutableStateOf(c.tourCount.takeIf { it > 0 }?.toString().orEmpty()) }
    var dupesPerTour by remember { mutableStateOf(c.dupesPerTour) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(): Contest? {
        val start = when (val t = moment(startDate, startTime)) {
            Bad -> { error = tr("Начало: дата ДД.ММ.ГГГГ и время ЧЧ:ММ"); return null }
            else -> t as Long?
        }
        val end = when (val t = moment(endDate, endTime)) {
            Bad -> { error = tr("Конец: дата ДД.ММ.ГГГГ и время ЧЧ:ММ"); return null }
            else -> t as Long?
        }
        if (start != null && end != null && end <= start) { error = tr("Конец должен быть позже начала"); return null }
        val minutes = tourMinutes.trim().toIntOrNull() ?: 0
        val count = tourCount.trim().toIntOrNull() ?: 0
        if ((minutes > 0 || count > 0) && (minutes <= 0 || count <= 0 || start == null)) {
            error = tr("Для туров нужны начало, длительность и количество"); return null
        }
        if (name.isBlank() && code.isBlank()) { error = tr("Укажите название или код"); return null }
        return c.copy(
            name = name.trim(), code = code.trim().uppercase(), start = start, end = end,
            tourMinutes = minutes, tourCount = count, dupesPerTour = dupesPerTour && minutes > 0,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) tr("Новое соревнование") else tr("Соревнование")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text(tr("Название")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }; error = null },
                    label = { Text(tr("Код соревнования (CONTEST)")) },
                    placeholder = { Text(tr("например RDXC, R3X-CHAMP")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("Начало и конец, UTC"), style = MaterialTheme.typography.titleSmall)
                DateTimeRow(tr("Начало"), startDate, { startDate = it; error = null }, startTime, { startTime = it; error = null })
                DateTimeRow(tr("Конец"), endDate, { endDate = it; error = null }, endTime, { endTime = it; error = null })
                Text(tr("Туры (для минитестов)"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("Тур, мин"), tourMinutes, Modifier.weight(1f)) { tourMinutes = it; error = null }
                    NumberField(tr("Туров"), tourCount, Modifier.weight(1f)) { tourCount = it; error = null }
                }
                Row(Modifier.fillMaxWidth().toggleable(dupesPerTour, role = Role.Checkbox) { dupesPerTour = it }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(dupesPerTour, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Обнулять повторы с новым туром"), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    tr("С турами конец можно не указывать: он считается по последнему туру. В каждом туре с той же станцией можно снова работать, если повторы обнуляются."),
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { Button(onClick = { build()?.let(onSave) }) { Text(tr("Сохранить")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

@Composable
private fun DateTimeRow(label: String, date: String, onDate: (String) -> Unit, time: String, onTime: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            date, { v -> onDate(v.filter { it.isDigit() || it == '.' }.take(10)) },
            label = { Text(label) }, placeholder = { Text(tr("ДД.ММ.ГГГГ")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.5f),
        )
        OutlinedTextField(
            time, { v -> onTime(v.filter { it.isDigit() || it == ':' }.take(5)) },
            label = { Text(tr("Время")) }, placeholder = { Text(tr("ЧЧ:ММ")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() }.take(4)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

/** A date and time typed in the dialog that do not parse. */
private object Bad

/** Epoch ms UTC of [date] and [time] (00:00 when only the date); null when both are empty, [Bad] when wrong. */
private fun moment(date: String, time: String): Any? {
    if (date.isBlank() && time.isBlank()) return null
    val d = runCatching { LocalDate.parse(date.trim(), DATE_FMT) }.getOrNull() ?: return Bad
    val t = if (time.isBlank()) LocalTime.MIDNIGHT else runCatching { LocalTime.parse(time.trim().let { if (it.length == 4 && ':' !in it) it.take(2) + ":" + it.drop(2) else it }, TIME_FMT) }.getOrNull() ?: return Bad
    return d.atTime(t).toInstant(ZoneOffset.UTC).toEpochMilli()
}
