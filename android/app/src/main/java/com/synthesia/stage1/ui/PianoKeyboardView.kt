package com.synthesia.stage1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

// 88-key piano (A0..C8). Keys in `expected` are highlighted (yellow shades). Keys in `heard`
// override expected highlight (green shades) to show progress on a chord slot.
@Composable
fun PianoKeyboardView(
    expected: Set<Int>,
    heard: Set<Int>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val whiteW = size.width / KeyLayout.WHITE_KEY_COUNT
        val whiteH = size.height
        val blackW = whiteW * KeyLayout.BLACK_KEY_WIDTH_RATIO
        val blackH = whiteH * KeyLayout.BLACK_KEY_HEIGHT_RATIO

        // White keys
        var whiteIdx = 0
        for (midi in KeyLayout.FIRST_MIDI..KeyLayout.LAST_MIDI) {
            if (!KeyLayout.isWhite(midi)) continue
            val x = whiteIdx * whiteW
            val color = when {
                midi in heard -> Color(0xFF66BB6A)
                midi in expected -> Color(0xFFFFEB3B)
                else -> Color.White
            }
            drawRect(color, topLeft = Offset(x, 0f), size = Size(whiteW, whiteH))
            // Inset by half stroke-width so the 1px border draws fully inside the white key
            // and doesn't bleed onto the adjacent key.
            drawRect(
                color = Color.Black,
                topLeft = Offset(x + 0.5f, 0.5f),
                size = Size(whiteW - 1f, whiteH - 1f),
                style = Stroke(width = 1f),
            )
            whiteIdx++
        }

        // Black keys (drawn on top of white-key strokes)
        whiteIdx = 0
        for (midi in KeyLayout.FIRST_MIDI..KeyLayout.LAST_MIDI) {
            if (KeyLayout.isWhite(midi)) {
                whiteIdx++
                continue
            }
            val cx = whiteIdx * whiteW
            val x = cx - blackW / 2f
            val color = when {
                midi in heard -> Color(0xFF2E7D32)
                midi in expected -> Color(0xFFF9A825)
                else -> Color.Black
            }
            drawRect(color, topLeft = Offset(x, 0f), size = Size(blackW, blackH))
        }
    }
}
