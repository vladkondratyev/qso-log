package ru.r3xed.qsolog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
 * Next to it a "⋮" menu: send the note ([onShare]) or delete it ([onDelete]).
 * The caller sizes it (the card stretches it to the height of the callsign field).
 */
@Composable
fun AudioPlayButton(file: File, onDelete: () -> Unit, onShare: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember(file) { mutableIntStateOf(0) }
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

    // Sending and deleting live in a visible "⋮" menu next to the button (no hidden long presses).
    var menu by remember(file) { mutableStateOf(false) }
    Row(modifier) {
        Column(
            Modifier
                .width(76.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (player != null) "Остановить" else "Проиграть запись",
                ) { if (player != null) stopPlayback() else startPlayback() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val fg = MaterialTheme.colorScheme.onPrimary
            if (player != null) Icon(Icons.Filled.Stop, "Остановить", Modifier.size(34.dp), tint = fg)
            else Icon(Icons.Filled.PlayArrow, "Проиграть запись", Modifier.size(38.dp), tint = fg)
            val shown = if (player != null) positionMs else totalMs
            Text("%d:%02d".format(shown / 60000, shown / 1000 % 60), fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg)
        }
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            IconButton(onClick = { menu = true }, modifier = Modifier.width(36.dp)) {
                Icon(Icons.Filled.MoreVert, "Действия с голосовой заметкой")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onShare != null) DropdownMenuItem(
                    text = { Text("Отправить заметку", fontSize = 17.sp) },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = { menu = false; stopPlayback(); onShare() },
                )
                DropdownMenuItem(
                    text = { Text("Удалить заметку", fontSize = 17.sp, color = DeleteRed) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = DeleteRed) },
                    onClick = { menu = false; stopPlayback(); onDelete() },
                )
            }
        }
    }
}
