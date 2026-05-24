# Synthesia-Clone Android — Code Review (Stage 1)

Reviewed: 2026-05-23, after Stage 1 completion.

## Fixes applied in this review

### 1. MidiParser: dangling notes at end-of-track were silently dropped

**Where:** [MidiParser.kt](app/src/main/java/com/synthesia/stage1/midi/MidiParser.kt)

**Issue:** When a track contained a `NoteOn` without a matching `NoteOff` (sometimes happens in MIDI exports from older notation software, or when a song ends on a held chord), the `pending[channel][pitch]` slot was never flushed. The note was silently lost from `MidiFile.notes`.

**Fix:** After the per-track event loop, sweep `pending[]` and emit any leftover notes with `durationTicks = absTick - p.startTick`. Channel 9 (drums) still skipped.

### 2. GameSession: detector's sampleRate was hardcoded, not synced with mic

**Where:** [GameSession.kt](app/src/main/java/com/synthesia/stage1/game/GameSession.kt)

**Issue:** Default `detector: PitchDetector = PitchDetector()` always used 44100 Hz. If a caller passed a `MicCapture(sampleRate = 48000)`, the detector still computed in the 44100 frame and produced wrong Hz readings. Defaults happened to match (both 44100), so the bug never fired in current use — but the invariant was fragile.

**Fix:** Change default to `PitchDetector(sampleRate = mic.sampleRate)`. Kotlin's primary-constructor default-arg scoping allows it.

## Recommended upgrades (prioritized)

### High value, low effort

1. **Auto-disable mic processing when song completes.** Right now the mic keeps recording after `playhead.isDone`; `onFrame` short-circuits on `playhead.current == null` so the cost is just the AudioRecord read loop. Not free, but cheap. Calling `mic.stop()` from within the capture thread itself would self-join — do it from a small launched coroutine in `GameSession`, or expose a "done" signal the UI watches and stops via `DisposableEffect`.

2. **Show note names instead of MIDI numbers.** [GameScreen.kt](app/src/main/java/com/synthesia/stage1/ui/GameScreen.kt) currently renders `expected=[60, 64, 67]`. Replace with `expected=[C4, E4, G4]` — a 6-line helper and a much better UX.

3. **Bundle a sample MIDI in `assets/`.** New users have no `.mid` to test with. Bundle a copyright-clear scale or simple piece and add a "Try sample" button next to "Pick MIDI file". The cloned `d:\midi-files\` generator (see `D:\midi-files`) produces chord MIDIs — make a target `make tracks` and copy outputs into `assets/`.

4. **Unit tests for MidiParser.** The parser has zero tests today. Build a tiny SMF byte array fixture (header + one track + one NoteOn + NoteOff) and assert the resulting `NoteEvent`s. AGP supports `androidTestImplementation("junit:junit:4.13.2")`. Cheap insurance.

5. **Permission rationale.** [MainActivity.kt](app/src/main/java/com/synthesia/stage1/MainActivity.kt) `PermissionGate` shows the same screen whether the user has just been shown the prompt or has denied it. Use `shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)` to give a clearer message after denial.

### Medium value, medium effort

6. **Fuzzy chord-slot grouping.** [PlayheadController.kt](app/src/main/java/com/synthesia/stage1/game/PlayheadController.kt) groups by exact `startTick`. After MIDI quantization round-trips, "simultaneous" chord notes can drift by 1–5 ticks and end up in adjacent slots. Group within a small tolerance window (e.g. PPQN/64).

7. **Background processing inside the mic callback is unsynchronized.** `heardInCurrentSlot` and `lastAdvanceAtNanos` are read/written only from the MicCapture background thread, so there's no actual race today. But the contract is implicit. Add a comment or move to a single-thread executor explicitly.

8. **Compose lifecycle-awareness.** Use `collectAsStateWithLifecycle()` (needs `androidx.lifecycle:lifecycle-runtime-compose:2.8.0`) so the UI stops collecting `GameSession.state` when the activity is paused/stopped. Saves a tiny bit of work; aligns with Compose best practice.

### Lower priority

9. **Material 3 theme parent.** [themes.xml](app/src/main/res/values/themes.xml) inherits from `android:Theme.Material.NoActionBar` — the deprecated framework Material. For a pure-Compose app it doesn't visually matter, but for consistency switch to `Theme.AppCompat.DayNight.NoActionBar` (with `appcompat` dep) or just `android:style/Theme.Translucent.NoTitleBar`.

10. **Mic source selection.** Stage 1 hard-codes `MediaRecorder.AudioSource.VOICE_RECOGNITION`. On devices that support `MediaRecorder.AudioSource.UNPROCESSED`, that gives noticeably better pitch accuracy (no AGC, no echo cancellation). Probe via `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)`.

11. **Shared module with the desktop project.** 11 of the source files are bit-for-bit identical to `d:\synthesia-windows\src\main\kotlin\com\synthesia\desktop\` with only the package name changed. Extract them into a `:common` Kotlin Multiplatform module that both `app` (Android) and `desktop` (JVM) depend on.

## Non-issues I checked but found fine

- **`readInt32` and the "MThd"/"MTrk" tag constants.** The high byte 'M' = 0x4D is positive in a signed Int, so no sign-extension surprises.
- **Variable-length quantity decode.** Caps at 4 bytes per the SMF spec; throws cleanly on a 5th continuation.
- **Running status discipline.** Only updated on channel-voice status bytes (0x80–0xEF), correctly skipping system messages (0xF0–0xFF). Spec-correct.
- **`Modifier.weight(1f)` in GameScreen.** Resolves through `ColumnScope` receiver; no import needed (we already removed the bogus `androidx.compose.foundation.layout.weight` import in iter-7).
- **Channel-9 drum drop.** Handled in both `emitNoteOff` and the `0x90` NoteOn case.
- **PitchDetector MPM peak threshold.** 0.93 × maxNSDF is the value recommended in McLeod's original paper for piano.
- **`@Volatile` on `lastAdvanceAtNanos`.** Only ever touched on the mic thread, so volatile is overkill but harmless.

## File map (with fix annotations)

```
app/src/main/java/com/synthesia/stage1/
  MainActivity.kt                  See recommendation #5 (permission rationale)
  midi/NoteEvent.kt
  midi/MidiFile.kt
  midi/MidiParser.kt               FIXED: dangling-notes flush
  audio/MicCapture.kt              See recommendation #10 (UNPROCESSED audio source)
  audio/PitchDetector.kt
  game/PlayheadController.kt       See recommendation #6 (fuzzy slot grouping)
  game/PitchMatcher.kt
  game/GameState.kt
  game/GameSession.kt              FIXED: detector sampleRate sync; see #1 (auto-stop on done)
  ui/KeyLayout.kt
  ui/PianoKeyboardView.kt
  ui/FallingNotesView.kt
  ui/GameScreen.kt                 See recommendation #2 (note names) and #8 (lifecycle)
  ui/theme/Theme.kt
res/values/themes.xml              See recommendation #9
```
