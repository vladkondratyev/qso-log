package ru.r3xed.qsolog.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.r3xed.qsolog.AppViewModel

private val RecordRed = Color(0xFFC62828)

/**
 * "Добавить QSO". A tap opens an empty card.
 * Press and hold: the button turns red with a microphone and records a voice note while held;
 * on release the card opens with the recording attached.
 */
@Composable
fun AddQsoButton(
    vm: AppViewModel,
    hasMicPermission: () -> Boolean,
    requestMicPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recording = vm.recordingSince != null
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (recording) RecordRed else MaterialTheme.colorScheme.primary, label = "bg")
    val onBg = if (recording) Color.White else MaterialTheme.colorScheme.onPrimary
    val permissionCheck by rememberUpdatedState(hasMicPermission)
    val permissionRequest by rememberUpdatedState(requestMicPermission)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The button sits at the bottom, so the hint goes above it: while recording, and on the first few starts.
        if (recording || vm.showRecordHint) {
            Text(
                if (recording) "Отпустите кнопку, чтобы открыть карточку" else "Удерживайте кнопку, чтобы записать голос",
                style = MaterialTheme.typography.bodyMedium,
                color = if (recording) RecordRed else LocalExtra.current.muted,
                fontWeight = if (recording) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (recording) 84.dp else 68.dp)
                .shadow(if (recording) 10.dp else 4.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(bg)
                .semantics {
                    role = Role.Button
                    contentDescription = "Добавить QSO. Удерживайте, чтобы записать голос"
                    onClick("Добавить QSO") { vm.newQso(); true }
                    onLongClick("Записать голос") { false }
                }
                .pointerInput(Unit) {
                    val longPress = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        awaitFirstDown()
                        var released = false
                        var cancelled = false
                        withTimeoutOrNull(longPress) {
                            if (waitForUpOrCancellation() != null) released = true else cancelled = true
                        }
                        when {
                            cancelled -> return@awaitEachGesture
                            released -> { vm.newQso(); return@awaitEachGesture }
                        }
                        // Held long enough: record while the finger stays down.
                        if (!permissionCheck()) {
                            permissionRequest()
                            waitForAllUp()
                            return@awaitEachGesture
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!vm.startRecording()) {
                            waitForAllUp()
                            return@awaitEachGesture
                        }
                        try {
                            waitForAllUp()
                        } finally {
                            // Also runs if the gesture is interrupted, so a recording is never left running.
                            vm.finishRecording()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (recording) RecordingContent(vm.recordingSince ?: 0L, onBg)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, Modifier.size(32.dp), tint = onBg)
                Spacer(Modifier.width(10.dp))
                Text("Добавить QSO", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = onBg)
                // Reminder that holding records a voice note, once the text hint is gone.
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Filled.Mic, null, Modifier.size(22.dp), tint = onBg.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun RecordingContent(since: Long, color: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    val secs = ((now - since) / 1000).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Mic, "Идёт запись", Modifier.size(40.dp).alpha(a), tint = color)
        Spacer(Modifier.width(12.dp))
        Text("Запись", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(14.dp))
        Text("%d:%02d".format(secs / 60, secs % 60), fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private suspend fun AwaitPointerEventScope.waitForAllUp() {
    while (true) {
        val e = awaitPointerEvent()
        e.changes.forEach { it.consume() }
        if (e.changes.none { it.pressed }) return
    }
}
