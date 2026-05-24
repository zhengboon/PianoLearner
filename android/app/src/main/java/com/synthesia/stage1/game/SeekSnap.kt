package com.synthesia.stage1.game

// Atomic snapshot bumped on every explicit seek (or reset). Bundling the version
// with the target time prevents the UI from racing on stale state.slotIndex when
// the playhead is at end-of-song and the user scrubs backwards.
data class SeekSnap(
    val version: Int,
    val targetTimeSec: Float,
)
