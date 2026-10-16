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
import ru.r3xed.qsolog.keys.Chord
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

/** The name of an Android key code in a [Chord] (see [Chord.key]); null for modifiers and keys a chord cannot have. */
fun androidKeyName(code: Int): String? = when (code) {
    in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z -> ('A' + (code - android.view.KeyEvent.KEYCODE_A)).toString()
    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> ('0' + (code - android.view.KeyEvent.KEYCODE_0)).toString()
    in android.view.KeyEvent.KEYCODE_F1..android.view.KeyEvent.KEYCODE_F12 -> "F" + (code - android.view.KeyEvent.KEYCODE_F1 + 1)
    else -> ANDROID_NAMES[code]
}

private val ANDROID_NAMES = mapOf(
    android.view.KeyEvent.KEYCODE_ESCAPE to "Esc", android.view.KeyEvent.KEYCODE_ENTER to "Enter",
    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER to "Enter", android.view.KeyEvent.KEYCODE_SPACE to "Space",
    android.view.KeyEvent.KEYCODE_TAB to "Tab", android.view.KeyEvent.KEYCODE_DPAD_UP to "Up",
    android.view.KeyEvent.KEYCODE_DPAD_DOWN to "Down", android.view.KeyEvent.KEYCODE_DPAD_LEFT to "Left",
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT to "Right", android.view.KeyEvent.KEYCODE_PAGE_UP to "PgUp",
    android.view.KeyEvent.KEYCODE_PAGE_DOWN to "PgDn", android.view.KeyEvent.KEYCODE_MOVE_HOME to "Home",
    android.view.KeyEvent.KEYCODE_MOVE_END to "End", android.view.KeyEvent.KEYCODE_FORWARD_DEL to "Delete",
    android.view.KeyEvent.KEYCODE_DEL to "Backspace", android.view.KeyEvent.KEYCODE_SLASH to "/",
    android.view.KeyEvent.KEYCODE_COMMA to ",", android.view.KeyEvent.KEYCODE_PERIOD to ".",
    android.view.KeyEvent.KEYCODE_MINUS to "-", android.view.KeyEvent.KEYCODE_EQUALS to "=",
    android.view.KeyEvent.KEYCODE_SEMICOLON to ";", android.view.KeyEvent.KEYCODE_APOSTROPHE to "'",
    android.view.KeyEvent.KEYCODE_LEFT_BRACKET to "[", android.view.KeyEvent.KEYCODE_RIGHT_BRACKET to "]",
    android.view.KeyEvent.KEYCODE_BACKSLASH to "\\", android.view.KeyEvent.KEYCODE_GRAVE to "`",
    android.view.KeyEvent.KEYCODE_PLUS to "+",
)

/** The key pressed as a [Chord] (Ctrl and ⌘ count the same), or null for a modifier alone. */
fun android.view.KeyEvent.chord(): Chord? =
    androidKeyName(keyCode)?.let { Chord(it, ctrl = isCtrlPressed || isMetaPressed, alt = isAltPressed, shift = isShiftPressed) }

fun KeyEvent.chord(): Chord? = nativeKeyEvent.chord()

internal val DIGIT_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

/** F1 or Ctrl+/: every key of an external keyboard, by screen (the same list as on the computer). */
@Composable
fun HardwareKeysDialog(cwMessages: List<String>? = null, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Внешняя клавиатура")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { KeysList(cwMessages) }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Закрыть")) } },
    )
}

/** The keys by screen, then the callsign commands: in the F1 dialog and on the first tab of the reference. */
@Composable
fun KeysList(cwMessages: List<String>? = null) {
    val x = LocalExtra.current
    KeyHints.sections(mac = false, cwMessages).forEach { sec ->
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
