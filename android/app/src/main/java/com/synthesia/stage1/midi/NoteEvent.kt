package com.synthesia.stage1.midi

data class NoteEvent(
    val pitch: Int,
    val startTick: Long,
    val durationTicks: Long,
    val velocity: Int,
    val channel: Int,
)
