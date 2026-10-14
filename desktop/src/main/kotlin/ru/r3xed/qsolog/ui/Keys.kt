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

/** The keys by screen, then the callsign commands: on the first tab of the reference (F1 shows them in a dialog). */
@Composable
fun KeysList() {
    KeyHints.sections(IS_MAC).forEach { sec ->
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
