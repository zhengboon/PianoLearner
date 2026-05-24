package com.synthesia.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun SongScrubBar(
    totalSlots: Int,
    currentSlot: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(totalSlots) {
            if (totalSlots == 0) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                fun seekAt(x: Float) {
                    val idx = ((x / size.width) * totalSlots).toInt().coerceIn(0, totalSlots - 1)
                    onSeek(idx)
                }
                seekAt(down.position.x)
                drag(down.id) { change ->
                    seekAt(change.position.x)
                    change.consume()
                }
            }
        },
    ) {
        drawRect(Color(0xFF1B1D25), size = size)
        if (totalSlots == 0) return@Canvas

        val fraction = currentSlot.toFloat() / totalSlots
        val fillX = (size.width * fraction).coerceIn(0f, size.width)

        drawRect(
            color = Color(0xFFFB923C),
            topLeft = Offset.Zero,
            size = Size(fillX, size.height),
        )
        val thumbR = size.height * 0.45f
        drawCircle(
            color = Color(0xFFFBBF24),
            radius = thumbR,
            center = Offset(fillX, size.height / 2f),
        )
    }
}
