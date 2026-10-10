// The same file as app/…/ui/MorseTrainer.kt (copied): edit both.
package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import ru.r3xed.qsolog.morse.MorseKeyMap
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.data.Reference
import ru.r3xed.qsolog.morse.KeyType
import ru.r3xed.qsolog.morse.MorseCode
import ru.r3xed.qsolog.morse.MorseKeyBus
import ru.r3xed.qsolog.morse.MorseKeySession
import ru.r3xed.qsolog.morse.MorseLesson
import ru.r3xed.qsolog.morse.MorseSettings
import ru.r3xed.qsolog.morse.MorseStore
import ru.r3xed.qsolog.tr

/** The trainer's common settings: alphabet, speed and how many Koch characters are learned. */
@Composable
private fun MorseSettingsBlock(s: MorseSettings, onChange: (MorseSettings) -> Unit, sending: Boolean) {
    if (sending) Chips(listOf(tr("Прямой ключ"), tr("Ямбический A"), tr("Ямбический B")), s.type.ordinal) { onChange(s.copy(type = KeyType.entries[it])) }
    Chips(listOf(tr("Кириллица"), tr("Латиница")), if (s.cyrillic) 0 else 1) { onChange(s.copy(cyrillic = it == 0)) }
    Stepper(tr("Скорость, слов в минуту"), s.wpm, 5, 40) { onChange(s.copy(wpm = it, gapWpm = s.gapWpm.coerceAtMost(it))) }
    if (!sending) Stepper(tr("Скорость с учётом пауз, слов в минуту"), s.gapWpm, 3, s.wpm) { onChange(s.copy(gapWpm = it)) }
    val koch = MorseCode.koch(s.cyrillic)
    Stepper(tr("Знаков по Коху"), s.level.coerceAtMost(koch.length), 2, koch.length) { onChange(s.copy(level = it)) }
    Text(
        buildAnnotatedString {
            val n = s.level.coerceAtMost(koch.length)
            append(koch.take(n - 1).toList().joinToString(" "))
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append(" " + koch[n - 1]) }
        },
        fontFamily = Mono, fontSize = 16.sp,
    )
}

/** − value + */
@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) { Icon(Icons.Filled.Remove, tr("Меньше")) }
        Text("$value", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(36.dp))
        IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Icon(Icons.Filled.Add, tr("Больше")) }
    }
}

/**
 * Sending practice: a group to key, what was heard, right in green and wrong in red. The key is on the screen (one pad
 * for a straight key, two for paddles) or external: a USB or Bluetooth adapter that sends keys (see the note below).
 */
@Composable
fun MorseSendTrainer(store: MorseStore) {
    val x = LocalExtra.current
    var s by remember { mutableStateOf(store.load()) }
    val change = { n: MorseSettings -> s = n; store.save(n) }
    val session = remember(s.wpm, s.type, s.cyrillic) { MorseKeySession(s.wpm, s.type, s.cyrillic) }
    var task by remember(s.cyrillic, s.level) { mutableStateOf(MorseLesson.group(s.cyrillic, s.level)) }
    var heard by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("") }
    var speed by remember { mutableIntStateOf(0) }
    // Which keys of an external key's adapter mean dot, dash and straight key; kept between starts.
    var map by remember { mutableStateOf(MorseKeyMap.load(store, defaultMorseKeyMap())) }
    var connect by remember { mutableStateOf(false) }
    DisposableEffect(session) {
        val tone = MorseSidetone(session).also { it.start() }
        MorseKeyBus.session = session
        onDispose {
            tone.stop()
            if (MorseKeyBus.session === session) MorseKeyBus.session = null
        }
    }
    DisposableEffect(map) {
        MorseKeyBus.keyMap = map
        onDispose { MorseKeyBus.keyMap = null }
    }
    LaunchedEffect(session, task) {
        session.clear()
        while (true) {
            session.read(System.nanoTime() / 1_000_000) { d -> heard = d.text; pending = d.pending; speed = d.wpm }
            // The whole group keyed and the word ended: the next one, after a moment to see the result.
            if (heard.filter { !it.isWhitespace() }.length >= task.length && heard.endsWith(" ")) {
                delay(1500)
                task = MorseLesson.group(s.cyrillic, s.level)
                break
            }
            delay(40)
        }
    }

    MorseSettingsBlock(s, change, sending = true)
    Text(tr("Передайте:"), style = MaterialTheme.typography.bodyMedium, color = x.muted)
    Text(task.toList().joinToString(" "), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 2.sp)
    Text(tr("Принято:"), style = MaterialTheme.typography.bodyMedium, color = x.muted)
    val got = heard.filter { !it.isWhitespace() }
    Text(
        buildAnnotatedString {
            got.forEachIndexed { i, c ->
                withStyle(SpanStyle(color = if (task.getOrNull(i) == c) x.ok else MaterialTheme.colorScheme.error)) { append("$c ") }
            }
            if (pending.isNotEmpty()) withStyle(SpanStyle(color = x.muted)) { append(pending.replace('.', '·').replace('-', '−')) }
        },
        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 2.sp, modifier = Modifier.height(48.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (right, _) = MorseLesson.check(task, got)
        Text(
            listOfNotNull(
                if (got.isNotEmpty()) tr("верно %s из %s", right, task.length) else null,
                if (got.isNotEmpty() && s.type == KeyType.STRAIGHT) tr("ваша скорость ≈ %s сл/мин", speed) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { task = MorseLesson.group(s.cyrillic, s.level) }) { Icon(Icons.Filled.Refresh, tr("Другое задание")) }
    }
    if (connect) MorseKeyConnect(map, onMap = { m -> map = m; m.save(store) }, onClose = { connect = false })
    else OutlinedButton(onClick = { connect = true }, shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Filled.Cable, null)
        Spacer(Modifier.width(8.dp))
        Text(tr("Подключить ключ"))
    }
    // The key on the screen: one pad, or a dot pad and a dash pad side by side.
    if (s.type == KeyType.STRAIGHT) KeyPad(tr("Ключ"), Modifier.fillMaxWidth()) { session.press(0, it) }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KeyPad("·", Modifier.weight(1f)) { session.press(1, it) }
        KeyPad("−", Modifier.weight(1f)) { session.press(2, it) }
    }
}

/**
 * Connecting a real key: how (USB or Bluetooth adapter, or a headset button on a phone), what the device sees, a live
 * check of what arrives and how it is understood, and assigning any key of the adapter to dot, dash or straight key.
 */
@Composable
private fun MorseKeyConnect(map: MorseKeyMap, onMap: (MorseKeyMap) -> Unit, onClose: () -> Unit) {
    val x = LocalExtra.current
    val c = MaterialTheme.colorScheme
    var last by remember { mutableStateOf<String?>(null) }
    var held by remember { mutableStateOf(setOf<String>()) }
    var learning by remember { mutableStateOf<Int?>(null) }
    var devices by remember { mutableStateOf(connectedKeyDevices()) }
    val currentMap by rememberUpdatedState(map)
    val currentOnMap by rememberUpdatedState(onMap)
    DisposableEffect(Unit) {
        MorseKeyBus.seen = { id, down -> last = id; held = if (down) held + id else held - id }
        onDispose { MorseKeyBus.seen = null; MorseKeyBus.learn = null }
    }
    LaunchedEffect(learning) {
        val role = learning
        MorseKeyBus.learn = if (role == null) null else { id -> currentOnMap(currentMap.assign(id, role)); learning = null }
    }
    if (MORSE_DEVICE_LIST) LaunchedEffect(Unit) { while (true) { devices = connectedKeyDevices(); delay(2000) } }
    val roleName = { r: Int -> when (r) { 1 -> tr("точка"); 2 -> tr("тире"); 0 -> tr("прямой ключ"); else -> tr("не назначена") } }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.field).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Подключение ключа"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Закрыть")) }
        }
        Text(tr("1. Как подключить"), fontWeight = FontWeight.Bold)
        ConnectWay(
            tr("USB-переходник"),
            tr("Плата с гнездом 3,5 мм, которая передаёт нажатия ключа как клавиатура: готовая (например, Vail Adapter) или самодельная на Seeed XIAO, Arduino Pro Micro. Вставьте штекер ключа в переходник, а переходник — в компьютер или в телефон через переходник OTG (USB-C ↔ USB-A)."),
        )
        ConnectWay(
            tr("Bluetooth-переходник"),
            tr("Плата на ESP32, которая показывается как Bluetooth-клавиатура. Включите её и соедините в настройках Bluetooth, как обычную клавиатуру. Задержка немного больше, чем по проводу."),
        )
        if (MORSE_HEADSET_KEYS) ConnectWay(
            tr("Кнопки гарнитуры"),
            tr("Без электроники, только пайка: штекер 3,5 мм с четырьмя контактами (TRRS, стандарт CTIA: гильза — микрофон, второе кольцо — земля) или переходник USB-C → 3,5 мм с микрофоном. Прямой ключ — между микрофоном и землёй (кнопка гарнитуры). Манипулятор: точка — через резистор 240 Ом (громкость +), тире — через 470 Ом (громкость −). Не на всех телефонах эти кнопки доходят до программы — проверьте ниже."),
        )
        if (MORSE_DEVICE_LIST) Text(
            if (devices.isEmpty()) tr("Внешних клавиатур не видно. Подключите переходник — он появится здесь.")
            else tr("Подключено: %s", devices.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium, color = if (devices.isEmpty()) x.muted else x.ok,
        )

        Text(tr("2. Проверка: нажмите ключ"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to tr("Точка"), 2 to tr("Тире"), 0 to tr("Прямой ключ")).forEach { (r, label) ->
                val on = held.any { map.role(it) == r }
                Text(
                    label, fontWeight = FontWeight.Bold, color = if (on) c.onPrimary else c.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) c.primary else c.surface)
                        .border(1.dp, x.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            last?.let { id ->
                val r = map.role(id)
                tr("Пришло: %s — %s", morseKeyLabel(id), roleName(r)) + if (r < 0) tr(". Назначьте её ниже.") else ""
            } ?: tr("Пока ничего не пришло. Если нажатия не видны, проверьте переходник."),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(tr("3. Назначение клавиш"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text(tr("Переходник может передавать любые клавиши: нажмите «Назначить» и затем ключ."), style = MaterialTheme.typography.bodyMedium, color = x.muted)
        listOf(1 to (tr("Точка") to map.dot), 2 to (tr("Тире") to map.dah), 0 to (tr("Прямой ключ") to map.straight)).forEach { (r, p) ->
            val (label, keys) = p
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text(keys.joinToString(", ") { morseKeyLabel(it) }.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, color = x.muted)
                }
                if (learning == r) Button(onClick = { learning = null }, shape = RoundedCornerShape(12.dp)) { Text(tr("Нажмите ключ…")) }
                else OutlinedButton(onClick = { learning = r }, shape = RoundedCornerShape(12.dp)) { Text(tr("Назначить")) }
            }
        }
        TextButton(onClick = { learning = null; onMap(defaultMorseKeyMap()) }) { Text(tr("Сбросить по умолчанию")) }
    }
}

/** One way to connect a key: a title and how, opened with a tap. */
@Composable
private fun ConnectWay(title: String, how: String) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable { open = !open }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (open) Text(how, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A pad that is a key: down while pressed. Touch events are taken before the scrolling column sees them. */
@Composable
private fun KeyPad(label: String, modifier: Modifier, onKey: (Boolean) -> Unit) {
    var down by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Box(
        modifier.height(150.dp).clip(RoundedCornerShape(18.dp))
            .background(if (down) c.primary else c.primaryContainer)
            .border(2.dp, c.primary, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    first.consume()
                    down = true
                    onKey(true)
                    do {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.id == first.id && it.pressed })
                    down = false
                    onKey(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (label.length == 1) 56.sp else 22.sp, fontWeight = FontWeight.Bold, color = if (down) c.onPrimary else c.onPrimaryContainer)
    }
}

/**
 * Receiving practice by the Koch method: a group of five is played, you type what you heard. With 90% right over
 * the last five groups one more character is added. A mistake shows the character's signal and chant.
 */
@Composable
fun MorseReceiveTrainer(store: MorseStore) {
    val x = LocalExtra.current
    var s by remember { mutableStateOf(store.load()) }
    val change = { n: MorseSettings -> s = n; store.save(n) }
    var group by remember(s.cyrillic, s.level) { mutableStateOf(MorseLesson.group(s.cyrillic, s.level)) }
    var answer by remember(group) { mutableStateOf("") }
    var checked by remember(group) { mutableStateOf(false) }
    var recent by remember(s.cyrillic, s.level) { mutableStateOf(listOf<Pair<Int, Int>>()) }
    var levelUp by remember { mutableStateOf(false) }
    val play = { MorseSound.playPcm(MorseLesson.pcm(group.toList().joinToString(""), s.wpm, s.gapWpm)) }
    val check = {
        if (!checked) {
            checked = true
            val (right, _) = MorseLesson.check(group, answer, s.cyrillic)
            val r = (recent + (right to group.length)).takeLast(5)
            recent = r
            val koch = MorseCode.koch(s.cyrillic)
            // 90% of the last five groups right: one more character.
            if (r.size == 5 && r.sumOf { it.first } * 10 >= r.sumOf { it.second } * 9 && s.level < koch.length) {
                levelUp = true
                change(s.copy(level = s.level + 1))
            }
        }
    }

    MorseSettingsBlock(s, change, sending = false)
    if (levelUp) Text(tr("Отлично! Добавлен новый знак: %s", MorseCode.koch(s.cyrillic)[s.level - 1]), color = x.ok, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { levelUp = false; play() }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(tr("Слушать"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        FilledTonalButton(onClick = { group = MorseLesson.group(s.cyrillic, s.level) }, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text(tr("Новая группа"), fontSize = 16.sp)
        }
    }
    OutlinedTextField(
        value = answer, onValueChange = { if (!checked) answer = MorseLesson.normalize(it.filter { c -> !c.isWhitespace() }, s.cyrillic).take(group.length) },
        modifier = Modifier.fillMaxWidth(), label = { Text(tr("Что услышали")) }, singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono, letterSpacing = 4.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { check() }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = check, enabled = !checked && answer.isNotEmpty(), shape = RoundedCornerShape(14.dp)) { Text(tr("Проверить")) }
        if (checked) FilledTonalButton(onClick = { group = MorseLesson.group(s.cyrillic, s.level); levelUp = false }, shape = RoundedCornerShape(14.dp)) { Text(tr("Дальше")) }
    }
    if (checked) {
        val (right, wrong) = MorseLesson.check(group, answer, s.cyrillic)
        Text(
            buildAnnotatedString {
                group.forEachIndexed { i, c ->
                    withStyle(SpanStyle(color = if (answer.getOrNull(i) == c) x.ok else MaterialTheme.colorScheme.error)) { append("$c ") }
                }
            },
            fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 2.sp,
        )
        Text(tr("верно %s из %s", right, group.length), style = MaterialTheme.typography.bodyMedium)
        // The mistaken characters with their signal and chant: what to listen for next time.
        wrong.distinct().forEach { c ->
            val code = MorseCode.code(c).orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$c", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.width(36.dp))
                Text(code.replace('.', '·').replace('-', '−'), fontFamily = Mono, fontSize = 18.sp, modifier = Modifier.width(90.dp))
                Text(Reference.MORSE_CHANTS[c.toString()].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = x.muted)
            }
        }
    }
    val total = recent.sumOf { it.second }
    if (total > 0) Text(tr("Последние группы: %s%% верно", recent.sumOf { it.first } * 100 / total), style = MaterialTheme.typography.bodyMedium, color = x.muted)
    TrainerNote(tr("Метод Коха: знаки звучат сразу на рабочей скорости, сначала два, и новый добавляется, когда последние пять групп приняты на 90%. Паузы между знаками можно удлинить — так легче расслышать каждый знак."))
}

@Composable
private fun TrainerNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 4.dp))
}
