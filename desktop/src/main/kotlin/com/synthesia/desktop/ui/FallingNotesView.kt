package com.synthesia.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.synthesia.desktop.game.NoteSlot
import com.synthesia.desktop.midi.midiToName

@Composable
fun FallingNotesView(
    slots: List<NoteSlot>,
    currentSlotIndex: Int,
    currentTimeSec: Float,
    heardCurrent: Set<Int>,
    modifier: Modifier = Modifier,
    pxPerSec: Float = 220f,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)

    Canvas(modifier = modifier) {
        drawRect(Color(0xFF0F1014), size = size)

        val hitLineY = size.height
        val noteHeight = (size.height * 0.06f).coerceIn(18f, 40f)
        val corner = CornerRadius(noteHeight * 0.25f)

        val labelStyle = TextStyle(
            color = Color(0xFF111111),
            fontSize = (noteHeight * 0.42f).coerceIn(9f, 14f).toSp(),
        )

        val cLineColor = Color(0x12FFFFFF)
        val fLineColor = Color(0x08FFFFFF)
        for (m in KeyLayout.FIRST_MIDI..KeyLayout.LAST_MIDI) {
            val pc = m % 12
            if (pc != 0 && pc != 5) continue
            val cx = KeyLayout.centerX(m, size.width)
            drawLine(
                color = if (pc == 0) cLineColor else fLineColor,
                start = Offset(cx, 0f),
                end = Offset(cx, hitLineY),
                strokeWidth = 1f,
            )
        }

        // Binary-search the first slot still on-canvas (sorted by startTimeSec).
        // Avoids walking past hundreds of off-bottom slots each frame.
        val belowCanvasCutoff = currentTimeSec - noteHeight / pxPerSec
        val startIdx = run {
            var lo = 0
            var hi = slots.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (slots[mid].startTimeSec < belowCanvasCutoff) lo = mid + 1 else hi = mid
            }
            lo
        }
        for (i in startIdx until slots.size) {
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
                    pitch < 60 -> Color(0xFFFB923C)
                    else -> Color(0xFF60A5FA)
                }
                val barLeft = cx - w / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(w, noteHeight),
                    cornerRadius = corner,
                )

                if (!isPast && noteHeight >= 20f && w >= 26f) {
                    val name = midiToName(pitch)
                    val layout = textMeasurer.measure(name, labelStyle)
                    val tw = layout.size.width.toFloat()
                    val th = layout.size.height.toFloat()
                    if (tw <= w - 4f && th <= noteHeight - 2f) {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = barLeft + (w - tw) / 2f,
                                y = barTop + (noteHeight - th) / 2f,
                            ),
                        )
                    }
                }
            }
        }

        drawRect(Color(0xFF7F1D2F), Offset(0f, hitLineY - 4f), Size(size.width, 4f))
        drawRect(Color(0xFF1B1D25), Offset(0f, hitLineY - 9f), Size(size.width, 5f))
    }
}
