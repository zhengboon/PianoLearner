package com.synthesia.desktop.game

import com.synthesia.desktop.audio.PitchDetector
import kotlin.math.abs
import kotlin.math.ln

class PitchMatcher(private val toleranceCents: Float = 50f) {

    fun matchInSlot(detectedHz: Float?, slotPitches: IntArray): Int? {
        if (detectedHz == null || detectedHz <= 0f) return null
        for (p in slotPitches) {
            val expectedHz = PitchDetector.midiToHz(p)
            val cents = abs(1200.0 * ln(detectedHz / expectedHz.toDouble()) / ln(2.0)).toFloat()
            if (cents <= toleranceCents) return p
        }
        return null
    }
}
