package com.synthesia.desktop.game

// Per-frame diagnostic snapshot the UI shows as a tiny mic-meter strip.
//
// Interpretation:
//   rms = 0.000, frames not incrementing  → mic thread isn't running (bug in MicCapture)
//   rms = 0.000, frames incrementing      → mic delivers silence (HAL/permission issue, source unsupported)
//   rms < 0.005, frames incrementing      → mic too quiet (move closer, lower rmsGate)
//   rms ≥ 0.005, hz = null                 → mic loud enough but detector finds no clear pitch
//   rms ≥ 0.005, hz ≈ <some Hz>            → detector working; if playhead still stuck, matcher tolerance / wrong note
data class MicDebug(
    val rms: Float = 0f,
    val hz: Float? = null,
    val frameCount: Long = 0L,
)
