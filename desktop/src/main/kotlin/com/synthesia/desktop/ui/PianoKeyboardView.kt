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
import androidx.compose.ui.unit.sp

@Composable
fun PianoKeyboardView(
    expected: Set<Int>,
    heard: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val whiteW = size.width / KeyLayout.WHITE_KEY_COUNT
        val whiteH = size.height
        val blackW = whiteW * 0.55f
        val blackH = whiteH * 0.62f
        val whiteRadius = CornerRadius(whiteW * 0.1f)
        val blackRadius = CornerRadius(blackW * 0.18f)
        val whiteGap = whiteW * 0.03f

        drawRect(Color(0xFF1B1D25), size = size)

        var whiteIdx = 0
        for (midi in KeyLayout.FIRST_MIDI..KeyLayout.LAST_MIDI) {
            if (!KeyLayout.isWhite(midi)) continue
            val x = whiteIdx * whiteW
            val pressed = midi in expected || midi in heard
            val pressOffset = if (pressed) 2f else 0f
            val color = when {
                midi in heard -> Color(0xFF22C55E)
                midi in expected -> Color(0xFFFBBF24)
                else -> Color(0xFFFFFEF0)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x + 0.5f, 0f + pressOffset),
                size = Size(whiteW - whiteGap, whiteH - pressOffset),
                cornerRadius = whiteRadius,
            )
            if (midi % 12 == 0) {
                val octave = midi / 12 - 1
                val label = "C$octave"
                val style = TextStyle(
                    color = Color(0xFFB0B0B0),
                    fontSize = (whiteW * 0.30f).coerceIn(8f, 18f).sp,
                )
                val layout = textMeasurer.measure(label, style)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = x + (whiteW - layout.size.width) / 2f,
                        y = whiteH - layout.size.height - 4f,
                    ),
                )
            }
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
            val pressed = midi in expected || midi in heard
            val pressOffset = if (pressed) 2f else 0f
            val color = when {
                midi in heard -> Color(0xFF22C55E)
                midi in expected -> Color(0xFFF59E0B)
                else -> Color(0xFF0A0A0C)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, pressOffset),
                size = Size(blackW, blackH - pressOffset),
                cornerRadius = blackRadius,
            )
        }
    }
}
