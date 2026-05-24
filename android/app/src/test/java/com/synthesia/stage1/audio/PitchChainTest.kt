package com.synthesia.stage1.audio

import com.synthesia.stage1.game.PitchMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// End-to-end synth → PitchDetector → PitchMatcher exercises. Mirrors the desktop
// TestPlayer's per-pitch logic so the Android-side audio chain has equal proof.
class PitchChainTest {

    private val sampleRate = 44100
    private val frameSize = 4096
    private val detector = PitchDetector(sampleRate = sampleRate)
    private val matcher = PitchMatcher()

    private fun synthNote(midi: Int, seconds: Float = 0.25f, withHarmonic: Boolean = true): ShortArray {
        val f = PitchDetector.midiToHz(midi).toDouble()
        val w = 2.0 * PI * f / sampleRate
        val w2 = w * 2.0
        val n = (sampleRate * seconds).toInt()
        val attack = (0.010f * sampleRate).toInt()
        val release = (0.030f * sampleRate).toInt()
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            var v = if (withHarmonic) (sin(w * i) + 0.3 * sin(w2 * i)).toFloat() / 1.3f
            else sin(w * i).toFloat()
            if (i < attack) v *= i.toFloat() / attack
            val tail = n - i
            if (tail < release) v *= tail.toFloat() / release
            pcm[i] = (v * 28000f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return pcm
    }

    private fun firstFrame(pcm: ShortArray): ShortArray = ShortArray(frameSize) { i -> pcm[i] }

    @Test fun detectsMiddleC() {
        val hz = detector.detect(firstFrame(synthNote(60)))!!  // C4
        assertTrue("$hz close to 261.63", abs(hz - 261.63f) < 5f)
    }

    @Test fun detectsLowA0() {
        val hz = detector.detect(firstFrame(synthNote(21, seconds = 0.5f)))!!  // A0 = 27.5 Hz
        assertTrue("$hz close to 27.5", abs(hz - 27.5f) < 2f)
    }

    @Test fun detectsHighC8() {
        val hz = detector.detect(firstFrame(synthNote(108)))!!  // C8 = 4186 Hz
        assertTrue("$hz close to 4186", abs(hz - 4186f) < 30f)
    }

    @Test fun matchesAllNotesInArpeggioChord() {
        val chord = listOf(60, 64, 67)  // C major
        val perNote = 0.25f
        val full = ShortArray(chord.size * (sampleRate * perNote).toInt())
        var off = 0
        for (pitch in chord) {
            val note = synthNote(pitch, perNote)
            System.arraycopy(note, 0, full, off, note.size)
            off += note.size
        }
        val pitchesArr = chord.toIntArray()
        val heard = mutableSetOf<Int>()
        var frameIdx = 0
        while (frameIdx * frameSize + frameSize <= full.size) {
            val frame = ShortArray(frameSize) { i -> full[frameIdx * frameSize + i] }
            frameIdx++
            val hz = detector.detect(frame) ?: continue
            matcher.matchInSlot(hz, pitchesArr)?.let { heard.add(it) }
        }
        assertEquals("expected to hear all 3 chord pitches, heard $heard", 3, heard.size)
    }

    @Test fun matchesWideIntervalChord() {
        // The previously-failing case from canon-3.mid: D3 (146.83) + F#5 (739.99)
        val pitches = listOf(50, 78)
        val perNote = 0.25f
        val full = ShortArray(pitches.size * (sampleRate * perNote).toInt())
        var off = 0
        for (p in pitches) {
            val note = synthNote(p, perNote)
            System.arraycopy(note, 0, full, off, note.size)
            off += note.size
        }
        val heard = mutableSetOf<Int>()
        var frameIdx = 0
        while (frameIdx * frameSize + frameSize <= full.size) {
            val frame = ShortArray(frameSize) { i -> full[frameIdx * frameSize + i] }
            frameIdx++
            val hz = detector.detect(frame) ?: continue
            matcher.matchInSlot(hz, pitches.toIntArray())?.let { heard.add(it) }
        }
        assertEquals("expected D3 (50) and F#5 (78), heard $heard", setOf(50, 78), heard)
    }

    @Test fun rejectsSilence() {
        val silent = ShortArray(frameSize)
        assertEquals(null, detector.detect(silent))
    }

    @Test fun matcherWithinFiftyCentsTolerance() {
        // Synthesize a slightly detuned A4 (+30 cents) — should still match MIDI 69.
        val midi = 69
        val baseHz = PitchDetector.midiToHz(midi)
        val detunedHz = baseHz * Math.pow(2.0, 30.0 / 1200.0).toFloat()
        val frame = ShortArray(frameSize)
        val w = 2.0 * PI * detunedHz / sampleRate
        for (i in 0 until frameSize) {
            frame[i] = (sin(w * i) * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        val hz = detector.detect(frame)
        assertNotNull(hz)
        val match = matcher.matchInSlot(hz, intArrayOf(midi))
        assertEquals(midi, match)
    }
}
