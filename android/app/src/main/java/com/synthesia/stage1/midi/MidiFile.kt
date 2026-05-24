package com.synthesia.stage1.midi

data class MidiFile(
    val ticksPerQuarter: Int,
    val microsPerQuarter: Int,
    val notes: List<NoteEvent>,
)
