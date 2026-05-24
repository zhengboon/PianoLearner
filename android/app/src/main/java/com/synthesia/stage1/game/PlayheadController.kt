package com.synthesia.stage1.game

import com.synthesia.stage1.midi.MidiFile
import com.synthesia.stage1.midi.NoteEvent

// A NoteSlot bundles notes that share a startTick — i.e. one chord or one single note.
// Notes that drift by a few ticks after quantization are merged into the same slot;
// the grouping uses two thresholds (PianoBooster pattern):
//   - noteGap  = max ticks between CONSECUTIVE notes in the same chord
//   - maxSpan  = max ticks from the chord's FIRST note to its LATEST note
// noteGap catches typical quantization drift; maxSpan stops a long arpeggio from
// collapsing into a single chord.
data class NoteSlot(val notes: List<NoteEvent>, val startTick: Long) {
    // Distinct pitches only — a chord can't physically have two voices on the same key,
    // so duplicates (common in multi-track MIDIs that double a melody an octave apart but
    // happen to collide on a unison) collapse to one slot pitch.
    val pitches: IntArray = notes.map { it.pitch }.distinct().toIntArray()
}

class PlayheadController(midi: MidiFile) {

    private val noteGap: Long = (midi.ticksPerQuarter / 16L).coerceAtLeast(2L)
    private val maxSpan: Long = (midi.ticksPerQuarter / 4L).coerceAtLeast(noteGap + 2L)

    val slots: List<NoteSlot> = buildSlots(midi.notes, noteGap, maxSpan)

    var index: Int = 0
        private set

    val current: NoteSlot? get() = slots.getOrNull(index)
    val isDone: Boolean get() = index >= slots.size

    fun advance() {
        if (!isDone) index++
    }

    fun reset() {
        index = 0
    }
}

private fun buildSlots(notes: List<NoteEvent>, noteGap: Long, maxSpan: Long): List<NoteSlot> {
    if (notes.isEmpty()) return emptyList()
    val sorted = notes.sortedBy { it.startTick }
    val out = ArrayList<NoteSlot>()
    var current = mutableListOf(sorted[0])
    var groupStart = sorted[0].startTick
    var lastInGroup = sorted[0].startTick
    for (i in 1 until sorted.size) {
        val n = sorted[i]
        val gap = n.startTick - lastInGroup
        val span = n.startTick - groupStart
        if (gap <= noteGap && span <= maxSpan) {
            current.add(n)
            lastInGroup = n.startTick
        } else {
            out.add(NoteSlot(current.toList(), groupStart))
            current = mutableListOf(n)
            groupStart = n.startTick
            lastInGroup = n.startTick
        }
    }
    out.add(NoteSlot(current.toList(), groupStart))
    return out
}
