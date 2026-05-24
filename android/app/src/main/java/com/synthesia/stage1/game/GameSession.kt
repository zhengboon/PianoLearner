package com.synthesia.stage1.game

import android.Manifest
import androidx.annotation.RequiresPermission
import com.synthesia.stage1.audio.MicCapture
import com.synthesia.stage1.audio.PitchDetector
import com.synthesia.stage1.midi.MidiFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Owns the playhead, mic, and pitch detector. Drives the step-by-step game loop and
// publishes GameState updates via StateFlow for the Compose UI.
class GameSession(
    midi: MidiFile,
    private val matcher: PitchMatcher = PitchMatcher(),
    private val mic: MicCapture = MicCapture(),
    private val detector: PitchDetector = PitchDetector(sampleRate = mic.sampleRate),
    private val advanceDebounceNanos: Long = 150_000_000L,
) {
    private val playhead = PlayheadController(midi)
    val slots: List<NoteSlot> = playhead.slots

    // All mutations are guarded by synchronized(this) so reset() (UI thread) can't race
    // the mic capture thread's onFrame().
    private val heardInCurrentSlot = HashSet<Int>()
    @Volatile private var lastAdvanceAtNanos = 0L

    private val _state = MutableStateFlow(GameState.fromPlayhead(playhead))
    val state: StateFlow<GameState> = _state.asStateFlow()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        mic.start { frame -> onFrame(frame) }
    }

    fun stop() {
        mic.stop()
    }

    fun reset() = synchronized(this) {
        playhead.reset()
        heardInCurrentSlot.clear()
        lastAdvanceAtNanos = 0L
        _state.value = GameState.fromPlayhead(playhead)
    }

    private fun onFrame(frame: ShortArray) {
        val slot = playhead.current ?: return
        val now = System.nanoTime()
        // lastAdvanceAtNanos is @Volatile — Long reads/writes are atomic via the
        // volatile contract on JVM, so this check is race-free against reset() and the
        // sync'd block below.
        if (now - lastAdvanceAtNanos < advanceDebounceNanos) return

        val hz = detector.detect(frame) ?: return
        val matchedPitch = matcher.matchInSlot(hz, slot.pitches) ?: return

        synchronized(this) {
            if (heardInCurrentSlot.add(matchedPitch)) {
                if (heardInCurrentSlot.size >= slot.pitches.size) {
                    heardInCurrentSlot.clear()
                    playhead.advance()
                    lastAdvanceAtNanos = now
                    _state.value = GameState.fromPlayhead(playhead)
                    if (playhead.isDone) {
                        // mic.stop() joins the capture thread, so we can't call it from
                        // within the capture thread itself — spawn a tiny stopper.
                        Thread({
                            try { mic.stop() } catch (_: Throwable) {}
                        }, "GameSession-Stopper").apply { isDaemon = true }.start()
                    }
                } else {
                    _state.value = _state.value.copy(heardPitches = heardInCurrentSlot.toSet())
                }
            }
        }
    }
}
