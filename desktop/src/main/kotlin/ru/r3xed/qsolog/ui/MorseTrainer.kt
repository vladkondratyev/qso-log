// The same file as app/…/ui/MorseTrainer.kt (copied): edit both.
package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
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
    DisposableEffect(session) {
        val tone = MorseSidetone(session).also { it.start() }
        MorseKeyBus.session = session
        onDispose {
            tone.stop()
            if (MorseKeyBus.session === session) MorseKeyBus.session = null
        }
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
    // The key on the screen: one pad, or a dot pad and a dash pad side by side.
    if (s.type == KeyType.STRAIGHT) KeyPad(tr("Ключ"), Modifier.fillMaxWidth()) { session.press(0, it) }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KeyPad("·", Modifier.weight(1f)) { session.press(1, it) }
        KeyPad("−", Modifier.weight(1f)) { session.press(2, it) }
    }
    TrainerNote(tr("Внешний ключ подключается через USB- или Bluetooth-переходник, который передаёт нажатия как клавиатура: точка — левый Ctrl или «[», тире — правый Ctrl или «]», прямой ключ — любая из них или Пробел. На телефоне ключ можно подключить и как кнопку гарнитуры: прямой ключ — кнопка, манипулятор — громкость + и −."))
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
