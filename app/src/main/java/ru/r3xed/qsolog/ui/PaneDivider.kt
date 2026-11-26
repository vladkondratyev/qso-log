package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** The line between the log and the card on a wide screen: a finger drags it sideways ([onDrag] gets dp). */
@Composable
fun PaneDivider(onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier.width(20.dp).fillMaxHeight().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true },
                onDragEnd = { dragging = false; onDragEnd() },
                onDragCancel = { dragging = false; onDragEnd() },
            ) { change, amount ->
                change.consume()
                onDrag(amount / density)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(LocalExtra.current.line))
        // A handle in the middle shows the line can be moved.
        Box(Modifier.width(if (dragging) 8.dp else 6.dp).height(48.dp).background(if (dragging) MaterialTheme.colorScheme.primary else LocalExtra.current.muted, RoundedCornerShape(3.dp)))
    }
}
