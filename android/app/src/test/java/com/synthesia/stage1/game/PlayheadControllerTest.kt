package com.synthesia.stage1.game

import com.synthesia.stage1.midi.MidiFile
import com.synthesia.stage1.midi.NoteEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayheadControllerTest {

    private fun note(pitch: Int, tick: Long, vel: Int = 80, ch: Int = 0): NoteEvent =
        NoteEvent(pitch = pitch, startTick = tick, durationTicks = 480L, velocity = vel, channel = ch)

    private fun midi(ppqn: Int, notes: List<NoteEvent>): MidiFile =
        MidiFile(ticksPerQuarter = ppqn, microsPerQuarter = 500_000, notes = notes)

    @Test fun emptyMidiYieldsNoSlots() {
        val p = PlayheadController(midi(480, emptyList()))
        assertEquals(0, p.slots.size)
        assertTrue(p.isDone)
    }

    @Test fun singleNoteYieldsOneSlot() {
        val p = PlayheadController(midi(480, listOf(note(60, 0))))
        assertEquals(1, p.slots.size)
        assertArrayEquals(intArrayOf(60), p.slots[0].pitches)
    }

    @Test fun threeNotesAtSameTickFormOneChord() {
        val p = PlayheadController(midi(480, listOf(note(60, 0), note(64, 0), note(67, 0))))
        assertEquals(1, p.slots.size)
        assertArrayEquals(intArrayOf(60, 64, 67), p.slots[0].pitches)
    }

    @Test fun notesFarApartFormSeparateSlots() {
        val p = PlayheadController(midi(480, listOf(note(60, 0), note(62, 480))))
        assertEquals(2, p.slots.size)
        assertArrayEquals(intArrayOf(60), p.slots[0].pitches)
        assertArrayEquals(intArrayOf(62), p.slots[1].pitches)
    }

    @Test fun staircaseOnsetsWithinNoteGapGroup() {
        val p = PlayheadController(midi(480, listOf(note(60, 0), note(64, 20), note(67, 40))))
        assertEquals(1, p.slots.size)
        assertArrayEquals(intArrayOf(60, 64, 67), p.slots[0].pitches)
    }

    @Test fun chordSpanCappedByMaxSpan() {
        val onTime = listOf(note(60, 0), note(62, 30), note(64, 60), note(65, 90), note(67, 120))
        assertEquals(1, PlayheadController(midi(480, onTime)).slots.size)
        val split = PlayheadController(midi(480, onTime + note(69, 150))).slots
        assertEquals(2, split.size)
        assertArrayEquals(intArrayOf(60, 62, 64, 65, 67), split[0].pitches)
        assertArrayEquals(intArrayOf(69), split[1].pitches)
    }

    @Test fun gapExceedingNoteGapSplitsEvenWithinMaxSpan() {
        val p = PlayheadController(midi(480, listOf(note(60, 0), note(62, 100))))
        assertEquals(2, p.slots.size)
    }

    @Test fun duplicatePitchesInChordCollapseInPitchesButRetainInNotes() {
        val p = PlayheadController(midi(480, listOf(note(60, 0), note(60, 0), note(64, 0))))
        val slot = p.slots[0]
        assertArrayEquals(intArrayOf(60, 64), slot.pitches)
        assertEquals(3, slot.notes.size)
    }

    @Test fun verySmallPpqnUsesFlooredThresholds() {
        val p = PlayheadController(midi(8, listOf(note(60, 0), note(62, 2), note(64, 5))))
        assertEquals(2, p.slots.size)
        assertArrayEquals(intArrayOf(60, 62), p.slots[0].pitches)
        assertArrayEquals(intArrayOf(64), p.slots[1].pitches)
    }

    @Test fun advanceAndResetWalkLinearly() {
        val p = PlayheadController(midi(480, listOf(note(60, 0), note(62, 480), note(64, 960))))
        assertEquals(0, p.index)
        assertArrayEquals(intArrayOf(60), p.current!!.pitches)
        p.advance()
        assertEquals(1, p.index)
        p.advance(); p.advance()
        assertTrue(p.isDone)
        p.reset()
        assertEquals(0, p.index)
        assertTrue(!p.isDone)
    }

    @Test fun unsortedInputIsSortedBeforeGrouping() {
        val p = PlayheadController(midi(480, listOf(note(62, 480), note(60, 0), note(64, 960))))
        assertEquals(3, p.slots.size)
        assertArrayEquals(intArrayOf(60), p.slots[0].pitches)
        assertArrayEquals(intArrayOf(62), p.slots[1].pitches)
        assertArrayEquals(intArrayOf(64), p.slots[2].pitches)
    }
}
