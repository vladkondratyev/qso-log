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
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.KeyHints
import ru.r3xed.qsolog.tr

/**
 * A key from a physical keyboard (Bluetooth, USB OTG), not from the on-screen one: some on-screen keyboards send
 * Enter and Space as key events too, and those must keep doing what the field's own keyboard action says.
 */
internal fun KeyEvent.fromHardware(): Boolean = (nativeKeyEvent.flags and android.view.KeyEvent.FLAG_SOFT_KEYBOARD) == 0

/**
 * In a confirmation dialog: Enter on a physical keyboard presses the main button (Esc already dismisses the dialog).
 * Put on the dialog's text, which takes the focus so the keys reach it.
 */
@Composable
internal fun Modifier.enterKey(onEnter: () -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onEnter(); true } else false
    }.focusRequester(focus).focusable()
}

/** ⌫ at the right end of a callsign or search field: wipes the whole field. */
@Composable
internal fun ClearButton(onClear: () -> Unit) {
    IconButton(onClick = onClear) { Icon(Icons.AutoMirrored.Filled.Backspace, tr("Стереть всё"), tint = LocalExtra.current.muted) }
}

internal val DIGIT_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

/** F1 or Ctrl+/: every key of an external keyboard, by screen (the same list as on the computer). */
@Composable
fun HardwareKeysDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Внешняя клавиатура")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { KeysList() }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Закрыть")) } },
    )
}

/** The keys by screen, then the callsign commands: in the F1 dialog and on the first tab of the reference. */
@Composable
fun KeysList() {
    val x = LocalExtra.current
    KeyHints.sections(mac = false, phone = true).forEach { sec ->
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
    Text(KeyHints.keypadGestures, style = MaterialTheme.typography.bodyMedium)
    Text(
        tr("Когда подключена клавиатура, своя экранная клавиатура программы прячется, а курсор сразу стоит в позывном."),
        style = MaterialTheme.typography.bodyMedium, color = x.muted,
    )
}
