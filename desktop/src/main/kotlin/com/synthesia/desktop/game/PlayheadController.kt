package com.synthesia.desktop.game

import com.synthesia.desktop.midi.MidiFile
import com.synthesia.desktop.midi.NoteEvent

data class NoteSlot(
    val notes: List<NoteEvent>,
    val startTick: Long,
    // Real-time offset from song start, in seconds. Computed from MidiFile.ticksPerQuarter
    // + initial Set Tempo (microsPerQuarter). Mid-song tempo changes are ignored (Stage 1).
    val startTimeSec: Float,
) {
    val pitches: IntArray = notes.map { it.pitch }.distinct().toIntArray()
}

class PlayheadController(midi: MidiFile) {

    private val noteGap: Long = (midi.ticksPerQuarter / 16L).coerceAtLeast(2L)
    private val maxSpan: Long = (midi.ticksPerQuarter / 4L).coerceAtLeast(noteGap + 2L)
    private val secPerTick: Float = (midi.microsPerQuarter.toFloat() / 1_000_000f) / midi.ticksPerQuarter.toFloat()

    val slots: List<NoteSlot> = buildSlots(midi.notes, noteGap, maxSpan, secPerTick)
    val songLengthSec: Float = slots.lastOrNull()?.startTimeSec ?: 0f

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

private fun buildSlots(
    notes: List<NoteEvent>,
    noteGap: Long,
    maxSpan: Long,
    secPerTick: Float,
): List<NoteSlot> {
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
            out.add(NoteSlot(current.toList(), groupStart, groupStart * secPerTick))
            current = mutableListOf(n)
            groupStart = n.startTick
            lastInGroup = n.startTick
        }
    }
    out.add(NoteSlot(current.toList(), groupStart, groupStart * secPerTick))
    return out
}
