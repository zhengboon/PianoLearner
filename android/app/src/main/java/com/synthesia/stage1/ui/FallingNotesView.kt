package com.synthesia.stage1.ui

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
import com.synthesia.stage1.game.NoteSlot
import com.synthesia.stage1.midi.midiToName

// Sightread-style continuous-scroll falling notes. Each note's vertical position is
// computed from its slot's startTimeSec vs the playhead's currentTimeSec:
//
//     y = hitLineY - (slot.startTimeSec - currentTimeSec) * pxPerSec - noteHeight
//
// Future notes are color-coded by hand using a middle-C split (sightread idiom):
// pitch < 60 → left (orange), pitch ≥ 60 → right (blue). Current slot stays yellow
// (or green per-pitch as you hear each one). Past slots dim to gray.
// Note labels (C4, F#5, ...) render inside the bar when there's room.
@Composable
fun FallingNotesView(
    slots: List<NoteSlot>,
    currentSlotIndex: Int,
    currentTimeSec: Float,
    heardCurrent: Set<Int>,
    modifier: Modifier = Modifier,
    pxPerSec: Float = 220f,
) {
    // Cached text measurer — labels are limited to 88 strings ×  ~3 font sizes; well within cache.
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

        // Faint vertical octave markers — line on C, fainter line on F (sightread idiom).
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

        // Slots are sorted by startTimeSec ascending. Binary-search the first slot
        // whose bottom edge is still on-canvas — anything earlier has already scrolled
        // off the bottom and can be skipped without iteration. Was an O(N) scan per
        // frame even after the off-bottom slots; now O(log N) lookup + O(visible) draw.
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
            // Defensive: binary search may pick slightly low if pxPerSec changes; safety-net
            if (barTop > hitLineY + noteHeight) continue
            if (barTop < -noteHeight) break

            val isCurrent = i == currentSlotIndex
            val isPast = i < currentSlotIndex

            for (pitch in slot.pitches) {
                val cx = KeyLayout.centerX(pitch, size.width)
                val w = KeyLayout.keyWidth(pitch, size.width) * 0.85f
                val color = when {
                    isCurrent && pitch in heardCurrent -> Color(0xFF22C55E)         // heard — green
                    isCurrent -> Color(0xFFFBBF24)                                  // waiting — yellow
                    isPast -> Color(0xFF3F3F46)                                     // passed — dim gray
                    pitch < 60 -> Color(0xFFFB923C)                                 // left hand — orange
                    else -> Color(0xFF60A5FA)                                       // right hand — blue
                }
                val barLeft = cx - w / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(w, noteHeight),
                    cornerRadius = corner,
                )

                // Inline pitch label, if the bar is large enough to fit it and the
                // contrast is decent (skip on dim-past notes — gray on dark = unreadable).
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

        // Hit-line strip (sightread's "red felt").
        drawRect(Color(0xFF7F1D2F), Offset(0f, hitLineY - 4f), Size(size.width, 4f))
        drawRect(Color(0xFF1B1D25), Offset(0f, hitLineY - 9f), Size(size.width, 5f))
    }
}
