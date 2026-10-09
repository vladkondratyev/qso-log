package ru.r3xed.qsolog.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.r3xed.qsolog.data.KeypadMode
import ru.r3xed.qsolog.tr

/** Names of the three keyboard choices, the same in the settings and in the cards. */
internal fun KeypadMode.label() = when (this) {
    KeypadMode.OFF -> tr("Системная")
    KeypadMode.NORMAL -> tr("Своя")
    KeypadMode.COMPACT -> tr("Своя компактная")
}

/**
 * A field typed on the app's keypad: looks like a text field but never opens the system keyboard. The active one has a
 * thick border and a blinking cursor; a [selected] value (the next key replaces it) is shaded.
 */
@Composable
internal fun KeyField(
    label: String,
    value: String,
    active: Boolean,
    selected: Boolean,
    isError: Boolean,
    modifier: Modifier,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    val x = LocalExtra.current
    val c = MaterialTheme.colorScheme
    val border = when {
        isError -> c.error
        active -> c.primary
        else -> x.line
    }
    Column(
        modifier.clip(RoundedCornerShape(if (big) 16.dp else 12.dp))
            .border(if (active || isError) 2.dp else 1.dp, border, RoundedCornerShape(if (big) 16.dp else 12.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (big) 6.dp else 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (isError) c.error else if (active) c.primary else x.muted, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(if (big) 50.dp else 38.dp)) {
            Text(
                value,
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (big) 36.sp else 26.sp, letterSpacing = if (big) 1.sp else 0.sp,
                maxLines = 1, softWrap = false,
                modifier = if (active && selected && value.isNotEmpty()) Modifier.clip(RoundedCornerShape(4.dp)).background(c.primaryContainer) else Modifier,
            )
            if (active && !(selected && value.isNotEmpty())) {
                // On for half a second, off for half a second.
                val phase by rememberInfiniteTransition(label = "cursor").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "cursor",
                )
                Box(Modifier.padding(start = 2.dp).width(3.dp).height(if (big) 38.dp else 28.dp).graphicsLayer { alpha = if (phase < 0.5f) 1f else 0f }.background(c.primary))
            }
        }
    }
}

/** Height of the keypad's keys and of the buttons right under it: the compact one is 30% lower. */
internal fun keyHeight(compact: Boolean) = if (compact) 35.dp else 50.dp
internal fun keypadButtonHeight(compact: Boolean) = if (compact) 40.dp else 54.dp

/**
 * The app's keyboard, in the spirit of contest loggers: digits on top, Latin letters, "/" for portable calls, "-" for
 * dB reports, "." for the frequency. Holding ⌫ keeps deleting, as on a phone keyboard. [compact] makes it 30% lower.
 */
@Composable
internal fun Keypad(compact: Boolean, onKey: (String) -> Unit, onBackspace: () -> Unit) {
    val x = LocalExtra.current
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val tap = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    val back by rememberUpdatedState(onBackspace)
    var backDown by remember { mutableStateOf(false) }
    val rows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL/", "ZXCVBNM-.")
    val h = keyHeight(compact)
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
        rows.forEachIndexed { i, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
                keys.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(h).clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
                            // Digits stand out: serials, reports and frequencies are typed there.
                            .background(if (i == 0) c.primaryContainer else x.field)
                            .clickable { tap(); onKey(k.toString()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            k.toString(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (compact) 18.sp else 22.sp,
                            color = if (i == 0) c.onPrimaryContainer else c.onSurface,
                        )
                    }
                }
                if (i == rows.lastIndex) {
                    Box(
                        Modifier.weight(1f).height(h).clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
                            .background(if (backDown) c.primary.copy(alpha = 0.35f) else c.primaryContainer)
                            .semantics { contentDescription = tr("Стереть"); role = Role.Button }
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    backDown = true
                                    tap()
                                    back()
                                    coroutineScope {
                                        val repeat = launch {
                                            delay(450)
                                            while (true) {
                                                back()
                                                delay(70)
                                            }
                                        }
                                        tryAwaitRelease()
                                        repeat.cancel()
                                    }
                                    backDown = false
                                })
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.Backspace, null, Modifier.size(if (compact) 22.dp else 26.dp), tint = c.onPrimaryContainer) }
                }
            }
        }
    }
}

/** The keyboard button in a card's header: one tap shows the three choices, the chosen one is ticked and remembered. */
@Composable
internal fun KeypadMenuButton(mode: KeypadMode, size: Int = 48, onChange: (KeypadMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(size.dp)) {
            Icon(
                Icons.Filled.Keyboard, tr("Клавиатура"), Modifier.size(26.dp),
                tint = if (mode == KeypadMode.OFF) LocalExtra.current.muted else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(tr("Клавиатура"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            KeypadMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label(), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { if (m == mode) Icon(Icons.Filled.Check, null) else Box(Modifier.size(24.dp)) },
                    onClick = { open = false; onChange(m) },
                )
            }
        }
    }
}

/** The same three choices in the settings, as one row of buttons like the theme. */
@Composable
internal fun KeypadModeRow(mode: KeypadMode, onChange: (KeypadMode) -> Unit) {
    val x = LocalExtra.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KeypadMode.entries.forEach { m ->
            val on = mode == m
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else x.field)
                    .selectable(selected = on, role = Role.RadioButton) { onChange(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label(), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 17.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
