package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.cw.CwState
import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap
import ru.r3xed.qsolog.tr

/** A message's button: "F1 CQ", from the first word of the message ({CALL} → CALL). */
internal fun cwButton(i: Int, message: String): String {
    val key = KeyMap.chords(KeyAction.entries.first { it.cwMessage == i }).firstOrNull()?.label(false)
    val word = message.trim().split(' ').firstOrNull().orEmpty().replace("{", "").replace("}", "").take(8)
    return listOfNotNull(key, word.ifBlank { null }).joinToString(" ")
}

/**
 * The telegraph messages of the card, as buttons (a phone without a keyboard): shown while the card is in CW and the
 * key line is on. While a message goes out, its text with the sent part marked, and Stop.
 */
@Composable
fun CwBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    if (!vm.cwActive) return
    // Keyed by hand from an external keyboard (CW settings → «Ключ на внешней клавиатуре»): the letters, newest at the end.
    var keyed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(vm.cwConfig.keyMode) {
        while (vm.cwConfig.keyMode != ru.r3xed.qsolog.cw.CwKeyMode.OFF) {
            keyed = vm.cwTapeNow()
            kotlinx.coroutines.delay(150)
        }
    }
    // The hand keyer stops, and its letters are forgotten, with the bar (the card closed or no longer in CW).
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.closeCwHand() } }
    val x = LocalExtra.current
    val st = vm.cwState
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CW", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            IconButton(onClick = { vm.changeCwSpeed(-2) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Remove, tr("Медленнее")) }
            Text(tr("%s WPM", vm.cwConfig.wpm), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            IconButton(onClick = { vm.changeCwSpeed(2) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Add, tr("Быстрее")) }
            when (st) {
                is CwState.Sending -> Text(
                    st.text.substring(0, st.done.coerceAtMost(st.text.length)).lowercase() + st.text.substring(st.done.coerceAtMost(st.text.length)),
                    fontFamily = Mono, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                is CwState.Failed -> Text(st.message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
                CwState.Idle -> Text("", Modifier.weight(1f))
            }
            if (st is CwState.Sending) IconButton(onClick = vm::stopCw) { Icon(Icons.Filled.Stop, tr("Остановить передачу"), tint = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.cwConfig.messages.forEachIndexed { i, m ->
                if (m.isBlank()) return@forEachIndexed
                Text(
                    cwButton(i, m), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                    modifier = Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(x.field)
                        .clickable(onClickLabel = vm.cwText(i)) { vm.sendCw(i) }.padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        // The tape appears once the key is worked in this card: each letter over its dots and dashes, the newest at the right.
        if (keyed.isNotBlank()) {
            val scroll = rememberScrollState()
            androidx.compose.runtime.LaunchedEffect(keyed) { scroll.animateScrollTo(scroll.maxValue) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(x.field)
                    .horizontalScroll(scroll).padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                keyed.takeLast(80).forEach { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(c.toString(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            ru.r3xed.qsolog.morse.MorseCode.code(c)?.replace('-', '–')?.replace('.', '·').orEmpty(),
                            fontFamily = Mono, fontSize = 11.sp, color = x.muted,
                        )
                    }
                }
            }
        }
    }
}
