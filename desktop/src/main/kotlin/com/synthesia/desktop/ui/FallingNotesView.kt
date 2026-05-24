package com.synthesia.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.synthesia.desktop.game.NoteSlot

@Composable
fun FallingNotesView(
    slots: List<NoteSlot>,
    currentSlotIndex: Int,
    currentTimeSec: Float,
    heardCurrent: Set<Int>,
    modifier: Modifier = Modifier,
    pxPerSec: Float = 220f,
) {
    Canvas(modifier = modifier) {
        drawRect(Color(0xFF0F1014), size = size)

        val hitLineY = size.height
        val noteHeight = (size.height * 0.06f).coerceIn(18f, 40f)
        val corner = CornerRadius(noteHeight * 0.25f)

        // Octave markers on C
        val markerColor = Color(0x0FFFFFFF)
        for (m in KeyLayout.FIRST_MIDI..KeyLayout.LAST_MIDI) {
            if (m % 12 != 0) continue
            val cx = KeyLayout.centerX(m, size.width)
            drawLine(markerColor, Offset(cx, 0f), Offset(cx, hitLineY), strokeWidth = 1f)
        }

        for (i in slots.indices) {
            val slot = slots[i]
            val dy = (slot.startTimeSec - currentTimeSec) * pxPerSec
            val barTop = hitLineY - dy - noteHeight
            if (barTop > hitLineY + noteHeight) continue
            if (barTop < -noteHeight) break

            val isCurrent = i == currentSlotIndex
            val isPast = i < currentSlotIndex
            for (pitch in slot.pitches) {
                val cx = KeyLayout.centerX(pitch, size.width)
                val w = KeyLayout.keyWidth(pitch, size.width) * 0.85f
                val color = when {
                    isCurrent && pitch in heardCurrent -> Color(0xFF22C55E)
                    isCurrent -> Color(0xFFFBBF24)
                    isPast -> Color(0xFF3F3F46)
                    else -> Color(0xFFFB923C)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - w / 2f, barTop),
                    size = Size(w, noteHeight),
                    cornerRadius = corner,
                )
            }
        }

        drawRect(Color(0xFF7F1D2F), Offset(0f, hitLineY - 4f), Size(size.width, 4f))
        drawRect(Color(0xFF1B1D25), Offset(0f, hitLineY - 9f), Size(size.width, 5f))
    }
}
