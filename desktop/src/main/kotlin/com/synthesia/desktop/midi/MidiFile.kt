package com.synthesia.desktop.midi

data class MidiFile(
    val ticksPerQuarter: Int,
    val microsPerQuarter: Int,
    val notes: List<NoteEvent>,
)
