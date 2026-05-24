package com.synthesia.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

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
