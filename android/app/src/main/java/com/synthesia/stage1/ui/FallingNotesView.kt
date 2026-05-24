package com.synthesia.stage1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.synthesia.stage1.game.NoteSlot

// Sightread-style continuous-scroll falling notes. Each note's vertical position is
// computed from its slot's startTimeSec vs the playhead's currentTimeSec:
//
//     y = hitLineY - (slot.startTimeSec - currentTimeSec) * pxPerSec - noteHeight
//
// So as currentTimeSec advances, notes scroll DOWN toward the hit line at the bottom.
// In our practice mode the playhead PAUSES the moment an un-played slot reaches the hit
// line; the heardCurrent set turns the bar green per pitch as you play them.
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
        // Background
        drawRect(Color(0xFF0F1014), size = size)

        val hitLineY = size.height
        // Note bar height in pixels — scale gently with canvas height for tablet/phone parity.
        val noteHeight = (size.height * 0.06f).coerceIn(18f, 40f)
        val corner = CornerRadius(noteHeight * 0.25f)

        // Faint vertical octave markers (sightread's renderOctaveRuler).
        val markerColor = Color(0x0FFFFFFF)
        for (m in KeyLayout.FIRST_MIDI..KeyLayout.LAST_MIDI) {
            if (m % 12 != 0) continue   // C only
            val cx = KeyLayout.centerX(m, size.width)
            drawLine(
                color = markerColor,
                start = Offset(cx, 0f),
                end = Offset(cx, hitLineY),
                strokeWidth = 1f,
            )
        }

        // Find the visible slot range — bin search would be O(log N), but linear is fine
        // for stage 1 (<10k slots typical) and we early-out as soon as slots go off-bottom.
        for (i in slots.indices) {
            val slot = slots[i]
            val dy = (slot.startTimeSec - currentTimeSec) * pxPerSec
            val barTop = hitLineY - dy - noteHeight
            // Past the bottom of the canvas → skip
            if (barTop > hitLineY + noteHeight) continue
            // Above the top → since slots are sorted by time, all later ones are also above
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

        // Hit-line strip (sightread's "red felt").
        drawRect(
            color = Color(0xFF7F1D2F),
            topLeft = Offset(0f, hitLineY - 4f),
            size = Size(size.width, 4f),
        )
        drawRect(
            color = Color(0xFF1B1D25),
            topLeft = Offset(0f, hitLineY - 9f),
            size = Size(size.width, 5f),
        )
    }
}
