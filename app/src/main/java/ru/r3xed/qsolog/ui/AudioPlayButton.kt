package ru.r3xed.qsolog.ui

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

private val DeleteRed = Color(0xFFC62828)

/**
 * Play / stop for a contact's voice note, with the elapsed or total time under the icon.
 * A long press turns it into a red bin for a few seconds; tapping the bin calls [onDelete].
 * The caller sizes it (the card stretches it to the height of the callsign field).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioPlayButton(file: File, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember(file) { mutableIntStateOf(0) }
    var armed by remember(file) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val totalMs = remember(file) {
        try {
            MediaPlayer().run { setDataSource(file.absolutePath); prepare(); duration.also { release() } }
        } catch (e: Exception) {
            0
        }
    }

    // Named apart from MediaPlayer.stop(): a local fun called stop() would shadow it and recurse.
    fun stopPlayback() {
        val p = player ?: return
        player = null
        positionMs = 0
        try { p.stop() } catch (_: Exception) {}
        p.release()
    }

    fun startPlayback() {
        val p = MediaPlayer()
        try {
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { stopPlayback() }
            p.prepare()
            p.start()
            player = p
        } catch (_: Exception) {
            p.release()
        }
    }

    DisposableEffect(file) { onDispose { stopPlayback() } }

    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            positionMs = try { p.currentPosition } catch (_: Exception) { 0 }
            delay(200)
        }
    }

    // The bin disarms itself if not tapped.
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }

    val bg by animateColorAsState(if (armed) DeleteRed else MaterialTheme.colorScheme.primary, label = "bg")
    val fg = if (armed) Color.White else MaterialTheme.colorScheme.onPrimary
    Column(
        modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (armed) "Удалить аудиозапись" else if (player != null) "Остановить" else "Проиграть запись",
                onLongClickLabel = "Удалить аудиозапись",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    stopPlayback()
                    armed = true
                },
                onClick = {
                    when {
                        armed -> { armed = false; stopPlayback(); onDelete() }
                        player != null -> stopPlayback()
                        else -> startPlayback()
                    }
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            armed -> Icon(Icons.Filled.Delete, "Удалить аудиозапись", Modifier.size(34.dp), tint = fg)
            player != null -> Icon(Icons.Filled.Stop, "Остановить", Modifier.size(34.dp), tint = fg)
            else -> Icon(Icons.Filled.PlayArrow, "Проиграть запись", Modifier.size(38.dp), tint = fg)
        }
        if (armed) {
            Text("Удалить", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
        } else {
            val shown = if (player != null) positionMs else totalMs
            Text("%d:%02d".format(shown / 60000, shown / 1000 % 60), fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}
