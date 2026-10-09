package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.KeyHints
import ru.r3xed.qsolog.tr

/**
 * A key from a physical keyboard (Bluetooth, USB OTG), not from the on-screen one: some on-screen keyboards send
 * Enter and Space as key events too, and those must keep doing what the field's own keyboard action says.
 */
internal fun KeyEvent.fromHardware(): Boolean = (nativeKeyEvent.flags and android.view.KeyEvent.FLAG_SOFT_KEYBOARD) == 0

internal val DIGIT_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

/** F1 or Ctrl+/: every key of an external keyboard, by screen (the same list as on the computer). */
@Composable
fun HardwareKeysDialog(onClose: () -> Unit) {
    val x = LocalExtra.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Внешняя клавиатура")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyHints.sections(mac = false).forEach { sec ->
                    Text(sec.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    // The key on its own line, what it does under it: long combinations fit on a phone.
                    sec.keys.forEach { (k, what) ->
                        Column {
                            Text(k, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(what, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(KeyHints.commands, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    tr("Когда подключена клавиатура, своя экранная клавиатура программы прячется, а курсор сразу стоит в позывном."),
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Закрыть")) } },
    )
}
