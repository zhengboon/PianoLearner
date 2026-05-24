package com.synthesia.stage1.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiParserTest {

    private fun smf(trackBody: ByteArray): ByteArray {
        val header = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(),
            0, 0, 0, 6,
            0, 0,                       // format 0
            0, 1,                       // 1 track
            0x01, 0xE0.toByte(),        // PPQN 480
        )
        val len = trackBody.size
        val mtrk = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(),
            (len ushr 24).toByte(),
            (len ushr 16).toByte(),
            (len ushr 8).toByte(),
            len.toByte(),
        )
        return header + mtrk + trackBody
    }

    @Test fun parsesHeader() {
        val midi = MidiParser.parseBytes(smf(byteArrayOf(0, 0xFF.toByte(), 0x2F, 0)))
        assertEquals(480, midi.ticksPerQuarter)
    }

    @Test fun capturesFirstSetTempo() {
        val track = byteArrayOf(
            0, 0xFF.toByte(), 0x51, 3, 0x07, 0xA1.toByte(), 0x20, // 500000us = 120 BPM
            0, 0xFF.toByte(), 0x2F, 0,
        )
        val midi = MidiParser.parseBytes(smf(track))
        assertEquals(500_000, midi.microsPerQuarter)
    }

    @Test fun extractsNoteOnNoteOffPair() {
        val track = byteArrayOf(
            0, 0x90.toByte(), 60, 80,                  // NoteOn C4 vel 80 at tick 0
            0x83.toByte(), 0x60, 0x80.toByte(), 60, 0, // NoteOff C4 at tick 480
            0, 0xFF.toByte(), 0x2F, 0,
        )
        val midi = MidiParser.parseBytes(smf(track))
        assertEquals(1, midi.notes.size)
        val n = midi.notes[0]
        assertEquals(60, n.pitch)
        assertEquals(0L, n.startTick)
        assertEquals(480L, n.durationTicks)
        assertEquals(80, n.velocity)
        assertEquals(0, n.channel)
    }

    @Test fun treatsNoteOnVelocityZeroAsNoteOff() {
        val track = byteArrayOf(
            0, 0x90.toByte(), 60, 80,
            0x83.toByte(), 0x60, 0x90.toByte(), 60, 0,
            0, 0xFF.toByte(), 0x2F, 0,
        )
        val midi = MidiParser.parseBytes(smf(track))
        assertEquals(1, midi.notes.size)
        assertEquals(480L, midi.notes[0].durationTicks)
    }

    @Test fun dropsChannel9DrumNotes() {
        val track = byteArrayOf(
            0, 0x99.toByte(), 60, 80,
            0x83.toByte(), 0x60, 0x89.toByte(), 60, 0,
            0, 0xFF.toByte(), 0x2F, 0,
        )
        val midi = MidiParser.parseBytes(smf(track))
        assertEquals(0, midi.notes.size)
    }

    @Test fun flushesDanglingNoteOnAtEndOfTrack() {
        val track = byteArrayOf(
            0, 0x90.toByte(), 60, 80,
            0x83.toByte(), 0x60, 0xFF.toByte(), 0x2F, 0,
        )
        val midi = MidiParser.parseBytes(smf(track))
        assertEquals(1, midi.notes.size)
        assertEquals(60, midi.notes[0].pitch)
        assertEquals(480L, midi.notes[0].durationTicks)
    }

    @Test fun emitsBothEventsWhenNoteOnReTriggersWithoutNoteOff() {
        // NoteOn C4 → NoteOn C4 → NoteOff: should yield TWO events, not one.
        val track = byteArrayOf(
            0, 0x90.toByte(), 60, 80,                              // NoteOn 1, vel 80
            0x83.toByte(), 0x60, 0x90.toByte(), 60, 90,            // NoteOn 2 at tick 480, vel 90
            0x83.toByte(), 0x60, 0x80.toByte(), 60, 0,             // NoteOff at tick 960
            0, 0xFF.toByte(), 0x2F, 0,
        )
        val midi = MidiParser.parseBytes(smf(track))
        assertEquals(2, midi.notes.size)
        val first = midi.notes[0]
        assertEquals(0L, first.startTick)
        assertEquals(480L, first.durationTicks)
        assertEquals(80, first.velocity)
        val second = midi.notes[1]
        assertEquals(480L, second.startTick)
        assertEquals(480L, second.durationTicks)
        assertEquals(90, second.velocity)
    }

    @Test fun discardsZeroDurationPriorWhenNoteOnReTriggersAtSameTick() {
        // Two NoteOn 60 at the same tick (delta=0 between them) → expect ONE event.
        val track = byteArrayOf(
            0, 0x90.toByte(), 60, 80,                  // NoteOn 1 at tick 0
            0, 0x90.toByte(), 60, 90,                  // NoteOn 2 at tick 0 (re-trigger)
            0x83.toByte(), 0x60, 0x80.toByte(), 60, 0, // NoteOff at tick 480
            0, 0xFF.toByte(), 0x2F, 0,
        )
        val midi = MidiParser.parseBytes(smf(track))
        assertEquals(1, midi.notes.size)
        assertEquals(480L, midi.notes[0].durationTicks)
        assertEquals(90, midi.notes[0].velocity)       // second NoteOn's velocity wins
    }

    @Test fun rejectsZeroSetTempo() {
        val track = byteArrayOf(
            0, 0xFF.toByte(), 0x51, 3, 0, 0, 0,            // tempo 0us/qn
            0, 0xFF.toByte(), 0x2F, 0,
        )
        var threw = false
        try { MidiParser.parseBytes(smf(track)) } catch (_: Throwable) { threw = true }
        assertEquals(true, threw)
    }

    @Test fun rejectsSmpteDivision() {
        val header = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(),
            0, 0, 0, 6,
            0, 0,
            0, 1,
            0xE2.toByte(), 0x78,
        )
        val mtrk = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(),
            0, 0, 0, 4,
            0, 0xFF.toByte(), 0x2F, 0,
        )
        var threw = false
        try { MidiParser.parseBytes(header + mtrk) } catch (_: Throwable) { threw = true }
        assertEquals(true, threw)
    }
}
