package com.synthesia.stage1.game

import com.synthesia.stage1.audio.PitchDetector
import kotlin.math.abs
import kotlin.math.ln

// Decides whether a detected mic pitch corresponds to any pitch in the current slot,
// within +/- toleranceCents (default 50 — half a semitone, generous for out-of-tune pianos).
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
