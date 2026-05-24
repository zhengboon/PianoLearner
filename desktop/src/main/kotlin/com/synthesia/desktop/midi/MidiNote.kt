package com.synthesia.desktop.midi

private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

// MIDI note number → name like "A4" (A0 = 21, middle C = 60).
fun midiToName(midi: Int): String {
    val pc = ((midi % 12) + 12) % 12
    val octave = midi / 12 - 1
    return NAMES[pc] + octave
}

fun midiListToNames(midis: Collection<Int>): String =
    midis.joinToString(", ") { midiToName(it) }
