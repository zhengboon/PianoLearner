package com.synthesia.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun FallingNotesView(
    upcomingSlots: List<List<Int>>,
    heardCurrent: Set<Int>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (upcomingSlots.isEmpty()) return@Canvas
        val rows = upcomingSlots.size
        val rowH = size.height / rows
        val noteH = rowH * 0.8f

        for (row in upcomingSlots.indices) {
            val pitches = upcomingSlots[row]
            val y = size.height - (row + 1) * rowH + (rowH - noteH) / 2f
            for (midi in pitches) {
                val cx = KeyLayout.centerX(midi, size.width)
                val w = KeyLayout.keyWidth(midi, size.width) * 0.9f
                val color = when {
                    row == 0 && midi in heardCurrent -> Color(0xFF66BB6A)
                    row == 0 -> Color(0xFFFFEB3B)
                    else -> Color(0xCC42A5F5)
                }
                drawRect(color, topLeft = Offset(cx - w / 2f, y), size = Size(w, noteH))
            }
        }
    }
}
