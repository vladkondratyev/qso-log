package ru.r3xed.qsolog.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.r3xed.qsolog.IS_MAC
import ru.r3xed.qsolog.KeyHints
import ru.r3xed.qsolog.keys.Chord
import ru.r3xed.qsolog.keys.KeyAction
import ru.r3xed.qsolog.keys.KeyMap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.KeyShortcut
import java.awt.event.KeyEvent as AwtKey
import ru.r3xed.qsolog.tr

/**
 * In a confirmation dialog: Enter presses the main button (Esc already dismisses the dialog).
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

/** The name of an AWT key code in a [Chord] (see [Chord.key]); null for modifiers and keys a chord cannot have. */
fun awtKeyName(code: Int): String? = when (code) {
    in AwtKey.VK_A..AwtKey.VK_Z -> ('A' + (code - AwtKey.VK_A)).toString()
    in AwtKey.VK_0..AwtKey.VK_9 -> ('0' + (code - AwtKey.VK_0)).toString()
    in AwtKey.VK_NUMPAD0..AwtKey.VK_NUMPAD9 -> ('0' + (code - AwtKey.VK_NUMPAD0)).toString()
    in AwtKey.VK_F1..AwtKey.VK_F12 -> "F" + (code - AwtKey.VK_F1 + 1)
    else -> AWT_NAMES[code]
}

private val AWT_NAMES = mapOf(
    AwtKey.VK_ESCAPE to "Esc", AwtKey.VK_ENTER to "Enter", AwtKey.VK_SPACE to "Space", AwtKey.VK_TAB to "Tab",
    AwtKey.VK_UP to "Up", AwtKey.VK_DOWN to "Down", AwtKey.VK_LEFT to "Left", AwtKey.VK_RIGHT to "Right",
    AwtKey.VK_PAGE_UP to "PgUp", AwtKey.VK_PAGE_DOWN to "PgDn", AwtKey.VK_HOME to "Home", AwtKey.VK_END to "End",
    AwtKey.VK_DELETE to "Delete", AwtKey.VK_BACK_SPACE to "Backspace", AwtKey.VK_SLASH to "/", AwtKey.VK_COMMA to ",",
    AwtKey.VK_PERIOD to ".", AwtKey.VK_MINUS to "-", AwtKey.VK_EQUALS to "=", AwtKey.VK_SEMICOLON to ";",
    AwtKey.VK_QUOTE to "'", AwtKey.VK_OPEN_BRACKET to "[", AwtKey.VK_CLOSE_BRACKET to "]", AwtKey.VK_BACK_SLASH to "\\",
    AwtKey.VK_BACK_QUOTE to "`", AwtKey.VK_PLUS to "+",
)

/** The key pressed as a [Chord]: Ctrl, or ⌘ on the Mac, is the chord's Ctrl. Null for a modifier alone. */
fun KeyEvent.chord(): Chord? = awtKeyName(key.nativeKeyCode)?.let {
    Chord(it, ctrl = if (IS_MAC) isMetaPressed else isCtrlPressed, alt = isAltPressed, shift = isShiftPressed)
}

/** [a]'s first key as a menu shortcut, when the menu can show it. */
fun menuShortcut(a: KeyAction): KeyShortcut? {
    val c = KeyMap.chords(a).firstOrNull() ?: return null
    val code = (AWT_NAMES.entries.firstOrNull { it.value == c.key }?.key) ?: when {
        c.key.length == 1 && c.key[0] in 'A'..'Z' -> AwtKey.VK_A + (c.key[0] - 'A')
        c.key.length == 1 && c.key[0] in '0'..'9' -> AwtKey.VK_0 + (c.key[0] - '0')
        c.key.startsWith("F") && c.key.drop(1).toIntOrNull() in 1..12 -> AwtKey.VK_F1 + c.key.drop(1).toInt() - 1
        else -> return null
    }
    return KeyShortcut(Key(code), ctrl = c.ctrl && !IS_MAC, meta = c.ctrl && IS_MAC, alt = c.alt, shift = c.shift)
}

/** The keys by screen, then the callsign commands: on the first tab of the reference (F1 shows them in a dialog). */
@Composable
fun KeysList(cwMessages: List<String>? = null) {
    KeyHints.sections(IS_MAC, cwMessages).forEach { sec ->
        Text(sec.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        sec.keys.forEach { (k, what) ->
            Row {
                Text(k, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(220.dp))
                Text(what, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
    Text(KeyHints.commands, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
}
