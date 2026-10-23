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
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Slider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs
import kotlin.math.roundToInt
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
 * thick border and a blinking cursor at [caret] (null — at the end); a [selected] value (the next key replaces it) is
 * shaded. [onClear]: a ⌫ at the right end that wipes the whole value.
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
    caret: Int? = null,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val x = LocalExtra.current
    val c = MaterialTheme.colorScheme
    val border = when {
        isError -> c.error
        active -> c.primary
        else -> x.line
    }
    Row(
        modifier.clip(RoundedCornerShape(if (big) 16.dp else 12.dp))
            .border(if (active || isError) 2.dp else 1.dp, border, RoundedCornerShape(if (big) 16.dp else 12.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 14.dp, end = if (onClear != null) 0.dp else 14.dp, top = if (big) 6.dp else 4.dp, bottom = if (big) 6.dp else 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = if (isError) c.error else if (active) c.primary else x.muted, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(if (big) 50.dp else 38.dp)) {
                val shaded = active && selected && value.isNotEmpty()
                val at = caretIn(value, caret)
                @Composable
                fun part(text: String) = Text(
                    text,
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (big) 36.sp else 26.sp, letterSpacing = if (big) 1.sp else 0.sp,
                    maxLines = 1, softWrap = false,
                    modifier = if (shaded) Modifier.clip(RoundedCornerShape(4.dp)).background(c.primaryContainer) else Modifier,
                )
                if (active && !shaded) {
                    if (at > 0) part(value.substring(0, at))
                    // On for half a second, off for half a second.
                    val phase by rememberInfiniteTransition(label = "cursor").animateFloat(
                        0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "cursor",
                    )
                    Box(Modifier.padding(horizontal = 1.dp).width(3.dp).height(if (big) 38.dp else 28.dp).graphicsLayer { alpha = if (phase < 0.5f) 1f else 0f }.background(c.primary))
                    if (at < value.length) part(value.substring(at))
                } else part(value)
            }
        }
        if (onClear != null && value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.padding(end = 4.dp).size(if (big) 52.dp else 44.dp)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, tr("Стереть всё"), Modifier.size(if (big) 28.dp else 24.dp), tint = x.muted)
            }
        }
    }
}

/** Where the keypad's cursor stands in [value]: [caret], or the end when it is null or beyond it. */
internal fun caretIn(value: String, caret: Int?): Int = caret?.coerceIn(0, value.length) ?: value.length

/** [value] with [key] typed at the cursor, if it stays within [max]; the cursor moves past it (null stays at the end). */
internal fun typeAt(value: String, caret: Int?, key: String, max: Int): Pair<String, Int?> {
    val at = caretIn(value, caret)
    val v = value.substring(0, at) + key + value.substring(at)
    if (v.length > max) return value to caret
    return v to caret?.let { at + key.length }?.takeIf { it < v.length }
}

/** [value] with the character before the cursor deleted. */
internal fun backspaceAt(value: String, caret: Int?): Pair<String, Int?> {
    val at = caretIn(value, caret)
    if (at == 0) return value to caret
    return value.removeRange(at - 1, at) to caret?.let { at - 1 }
}

/** The cursor moved [steps] characters; at the end it becomes null again (typing appends). */
internal fun moveCaret(value: String, caret: Int?, steps: Int): Int? =
    (caretIn(value, caret) + steps).coerceIn(0, value.length).takeIf { it < value.length }

/** Height of the keypad's keys and of the buttons right under it: the compact one is 30% lower. */
internal fun keyHeight(compact: Boolean) = if (compact) 35.dp else 50.dp
internal fun keypadButtonHeight(compact: Boolean) = if (compact) 40.dp else 54.dp

/**
 * The telegraph rows of the keypad (CW on, the card in CW): the messages F1–F8 ([labels], an empty one is off) and Stop
 * while a message goes out ([sending]); with the on-screen key ([screenKey]) a row of paddles, or one straight key,
 * under them — [onKey] gets the role (0 straight, 1 dot, 2 dash) pressed and released.
 */
internal class CwKeys(
    val labels: List<String>,
    val sending: Boolean,
    val onSend: (Int) -> Unit,
    val onStop: () -> Unit,
    val screenKey: ru.r3xed.qsolog.cw.CwKeyMode = ru.r3xed.qsolog.cw.CwKeyMode.OFF,
    val onKey: (Int, Boolean) -> Unit = { _, _ -> },
)

/**
 * The app's keyboard, in the spirit of contest loggers: digits on top, Latin letters, "/" for portable calls, "-" for
 * dB reports, "." for the frequency. Holding ⌫ keeps deleting, as on a phone keyboard. [compact] makes it 30% lower.
 * A swipe to the left and, without lifting the finger, back to the right wipes the field ([onClear]); a finger held still for [holdMs] turns it into a
 * trackpad: sliding sideways moves the cursor ([onCaret], characters), as holding the space bar does on an iPhone.
 */
@Composable
internal fun Keypad(
    compact: Boolean,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit = {},
    onCaret: (Int) -> Unit = {},
    holdMs: Int = 400,
    cw: CwKeys? = null,
) {
    val x = LocalExtra.current
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val tap = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    val back by rememberUpdatedState(onBackspace)
    val clear by rememberUpdatedState(onClear)
    val caret by rememberUpdatedState(onCaret)
    var backDown by remember { mutableStateOf(false) }
    // The finger rests on the keypad and drives the cursor: the keys fade, as on an iPhone.
    var trackpad by remember { mutableStateOf(false) }
    // Where ⌫ is: holding it repeats deleting, so it never turns into the trackpad.
    var backBounds by remember { mutableStateOf(Rect.Zero) }
    // Where the on-screen key is: a finger resting on a paddle keys, it never turns into the trackpad or a swipe.
    var paddleBounds by remember { mutableStateOf(Rect.Zero) }
    val rows = listOf("1234567890", "QWERTYUIOP", "ASDFGHJKL/", "ZXCVBNM-.")
    val h = keyHeight(compact)
    val gestures = Modifier.pointerInput(holdMs) {
        val swipe = 70.dp.toPx()
        val step = 16.dp.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            // Watched before the keys see it: a swipe or the trackpad takes the touch away from them (no key is typed).
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (paddleBounds.contains(down.position)) {
                do { val e = awaitPointerEvent(PointerEventPass.Initial) } while (e.changes.any { it.pressed })
                return@awaitEachGesture
            }
            var mode = 0 // 0 — not decided, 1 — swiped, 2 — trackpad
            var still = !backBounds.contains(down.position)
            var anchor = down.position.x
            // The wipe: first far enough to the left, then from the leftmost point far enough back to the right.
            var leftmost = down.position.x
            var wentLeft = false
            try {
                while (true) {
                    val event = if (mode == 0 && still) {
                        val left = holdMs - (android.os.SystemClock.uptimeMillis() - down.uptimeMillis)
                        withTimeoutOrNull(left.coerceAtLeast(1)) { awaitPointerEvent(PointerEventPass.Initial) }
                    } else awaitPointerEvent(PointerEventPass.Initial)
                    if (event == null) {
                        mode = 2
                        trackpad = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    when (mode) {
                        0 -> {
                            if (abs(dx) > slop || abs(dy) > slop) still = false
                            val x = change.position.x
                            if (x < leftmost) leftmost = x
                            if (!wentLeft && -dx > swipe && -dx > 2 * abs(dy)) {
                                wentLeft = true
                                tap()
                            }
                            // Once it went left, the keys must not type: the touch belongs to the gesture.
                            if (wentLeft) change.consume()
                            if (wentLeft && x - leftmost > swipe) {
                                mode = 1
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                clear()
                            }
                        }
                        2 -> {
                            val steps = ((change.position.x - anchor) / step).toInt()
                            if (steps != 0) {
                                caret(steps)
                                anchor += steps * step
                                tap()
                            }
                        }
                    }
                    if (mode != 0) change.consume()
                    if (!change.pressed) break
                }
            } finally {
                trackpad = false
            }
        }
    }
    Column(gestures.graphicsLayer { alpha = if (trackpad) 0.45f else 1f }, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
        if (cw != null) Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
            @Composable
            fun key(label: String?, description: String, enabled: Boolean = true, accent: Boolean = false, width: Float = 1f, onClick: () -> Unit, icon: @Composable () -> Unit = {}) = Box(
                Modifier.weight(width).height(h).clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
                    .background(if (accent) c.errorContainer else c.secondaryContainer)
                    .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
                    .semantics { contentDescription = description; role = Role.Button }
                    .clickable(enabled = enabled) { tap(); onClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (label != null) Text(label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (compact) 13.sp else 15.sp, color = c.onSecondaryContainer, maxLines = 1)
                else icon()
            }
            cw.labels.take(8).forEachIndexed { i, m ->
                key("F${i + 1}", tr("Передать сообщение %s: %s", i + 1, m), enabled = m.isNotBlank(), onClick = { cw.onSend(i) })
            }
            // Stop takes two places: the row has ten keys of one size, as the digits under it.
            key(null, tr("Остановить передачу"), accent = cw.sending, width = 2f, onClick = cw.onStop) {
                Icon(Icons.Filled.Stop, null, Modifier.size(if (compact) 20.dp else 24.dp), tint = if (cw.sending) c.onErrorContainer else c.onSecondaryContainer)
            }
        }
        if (cw != null && cw.screenKey != ru.r3xed.qsolog.cw.CwKeyMode.OFF) Row(
            Modifier.onGloballyPositioned { r -> paddleBounds = r.parentLayoutCoordinates?.localBoundingBoxOf(r, clipBounds = false) ?: Rect.Zero },
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        ) {
            val roles = if (cw.screenKey == ru.r3xed.qsolog.cw.CwKeyMode.STRAIGHT) listOf(0 to tr("КЛЮЧ")) else listOf(1 to "·", 2 to "—")
            roles.forEach { (r, label) -> Paddle(label, r, compact, h, cw.onKey) { tap() } }
        }
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
                        Modifier.weight(1f).height(h).onGloballyPositioned { backBounds = it.boundsInParentOf() }
                            .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
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

/** A key of the on-screen telegraph key: closed while the finger is on it; the paddles take one finger each. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Paddle(
    label: String, role: Int, compact: Boolean, h: androidx.compose.ui.unit.Dp, onKey: (Int, Boolean) -> Unit, tap: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val key by rememberUpdatedState(onKey)
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier.weight(1f).height(h).clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
            .background(if (down) c.primary else c.tertiaryContainer)
            .semantics { contentDescription = if (role == 0) tr("Ключ") else if (role == 1) tr("Точка") else tr("Тире"); this.role = Role.Button }
            .pointerInput(role) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    down = true
                    tap()
                    key(role, true)
                    try {
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == first.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                        }
                    } finally {
                        down = false
                        key(role, false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = when (role) { 0 -> if (compact) 14.sp else 16.sp; 1 -> if (compact) 34.sp else 42.sp; else -> if (compact) 22.sp else 26.sp },
            color = if (down) c.onPrimary else c.onTertiaryContainer, maxLines = 1,
        )
    }
}

/** Bounds of ⌫ in the keypad's coordinates: its row's position plus its own. */
private fun LayoutCoordinates.boundsInParentOf(): Rect {
    val keypad = parentLayoutCoordinates?.parentLayoutCoordinates ?: return Rect.Zero
    return keypad.localBoundingBoxOf(this, clipBounds = false)
}

/** The three keyboard choices in the settings, as one row of buttons like the theme. */
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

/** Settings: how long a finger rests on the keypad before it moves the cursor, 0.2–1.5 s. */
@Composable
internal fun KeypadHoldRow(ms: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Удержание для курсора"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(tr("%s с", "%.1f".format(ms / 1000.0)), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Slider(
            value = ms.toFloat(), onValueChange = { onChange((it / 100).roundToInt() * 100) },
            valueRange = 200f..1500f, steps = 12,
        )
    }
}
