package ru.r3xed.qsolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/**
 * A vertical line between two panes that can be dragged sideways ([onDrag] gets the move in dp); it shows the resize
 * pointer and thickens under the mouse. A double click calls [onReset].
 */
@Composable
fun PaneDivider(onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onReset: () -> Unit) {
    val x = LocalExtra.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    // Moves come in fractions of a dp: they add up here and go out in whole dp.
    var acc by remember { mutableStateOf(0f) }
    Box(
        Modifier.width(9.dp).fillMaxHeight()
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; onDragEnd() },
                    onDragCancel = { dragging = false; onDragEnd() },
                ) { change, amount ->
                    change.consume()
                    acc += amount.x / density
                    val whole = acc.toInt()
                    if (whole != 0) { acc -= whole; onDrag(whole.toFloat()) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val active = hovered || dragging
        Box(Modifier.width(if (active) 3.dp else 1.dp).fillMaxHeight().background(if (active) androidx.compose.material3.MaterialTheme.colorScheme.primary else x.line))
    }
}
