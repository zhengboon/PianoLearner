# Synthesia-Clone Desktop — Code Review (Stage 1)

Reviewed: 2026-05-23, after Stage 1 completion.

## Fixes applied in this review

Both fixes mirror the Android-side fixes (this is a near-copy codebase).

### 1. MidiParser: dangling notes at end-of-track were silently dropped

**Where:** [MidiParser.kt](src/main/kotlin/com/synthesia/desktop/midi/MidiParser.kt)

**Issue:** `NoteOn` without matching `NoteOff` before end-of-track was lost — the `pending[channel][pitch]` slot never got flushed.

**Fix:** Sweep `pending[]` at end-of-track and emit each leftover note with `durationTicks = absTick - p.startTick`.

### 2. GameSession: detector sampleRate was decoupled from mic sampleRate

**Where:** [GameSession.kt](src/main/kotlin/com/synthesia/desktop/game/GameSession.kt)

**Issue:** Default `detector = PitchDetector()` was 44100 Hz hardcoded. Defaults matched MicCapture's 44100 so it never bit in practice, but a caller passing `MicCapture(sampleRate = 48000)` would get wrong Hz readings out of the detector.

**Fix:** `detector: PitchDetector = PitchDetector(sampleRate = mic.sampleRate)`.

## Recommended upgrades (prioritized)

### High value, low effort

1. **JFileChooser parent window.** [Main.kt](src/main/kotlin/com/synthesia/desktop/Main.kt) calls `chooser.showOpenDialog(null)`. On Windows this means the dialog can appear behind the Compose window or not properly grab focus. Pass the actual `ComposeWindow` — get it from inside the `Window { ... }` scope via `LocalWindowInfo` + a `SwingPanel` workaround, or simpler: use `androidx.compose.ui.awt.ComposeWindow` accessor (the receiver of the `Window` content lambda) and store the JFrame in a state holder accessible to `pickMidiFile()`.

2. **Mic device picker.** Stage 1 silently uses the system default recording device. Add a dropdown using `AudioSystem.getMixerInfo()` filtered for mixers that support `TargetDataLine` with PCM_SIGNED 44100/16/mono. Surface device names from `Mixer.Info.getName()`.

3. **Show note names instead of MIDI numbers.** Same as Android. Add a `MidiNote.kt` helper exposing `fun midiToName(midi: Int): String` returning e.g. `"A4"`.

4. **Bundle sample MIDIs in `src/main/resources/`.** Compose Desktop ships resources in the runtime fat-jar. Drop a `samples/` directory and add a "Load sample" button. Hook this up with the cloned chord-generator at `D:\midi-files\` (Python scripts producing MIDI files — run `make` or invoke `gen.py` to populate `samples/`).

5. **Unit tests for MidiParser.** Zero parser tests today. The JUnit harness is already configured (see [MicCaptureTest.kt](src/test/kotlin/com/synthesia/desktop/audio/MicCaptureTest.kt)). Add a `MidiParserTest.kt` with a synthetic SMF byte array fixture.

6. **TargetDataLine error diagnostics.** When `AudioSystem.isLineSupported(info)` returns `false` (e.g. exotic sample rate, no mic plugged in), the user sees a bare `IllegalStateException`. List the supported formats by calling `AudioSystem.getMixer(null).sourceLineInfo` and include them in the error message.

### Medium value, medium effort

7. **Auto-disable mic processing when song completes.** Same recommendation as Android — `onFrame` already short-circuits when `playhead.current == null` but the AudioRecord read loop keeps running. Call `mic.stop()` from outside the callback thread when `isDone` flips.

8. **Fuzzy chord-slot grouping.** Same as Android. Group `NoteEvent`s within a small startTick tolerance (e.g. PPQN/64).

9. **Compose Desktop window state persistence.** The window starts at 1280×720 every launch. Use `rememberWindowState` + persist its `WindowPlacement` and `WindowPosition` via java.util.prefs.Preferences.

10. **Make the `.exe` produce a `.msi` too.** [build.gradle.kts](build.gradle.kts) already lists `TargetFormat.Msi` in `targetFormats`. Running `gradlew packageMsi` on a Windows host with WiX Toolset 3.x installed will produce a proper `SynthesiaDesktop-0.1.0.msi` at `build/compose/binaries/main/msi/`. Install WiX from <https://wixtoolset.org/releases/> if you want this.

### Lower priority

11. **Shared module with the Android project.** 11 of the source files are bit-for-bit identical to `d:\synthesia-android\app\src\main\java\com\synthesia\stage1\` with only the package name changed. Extract them into a `:common` Kotlin Multiplatform module that both projects depend on. Refactor target: convert this repo from `kotlin("jvm")` to `kotlin("multiplatform")` with jvm + android targets.

12. **App icon.** The `.exe` ships with the default jpackage icon. Add `compose.desktop.application.nativeDistributions.windows.iconFile.set(file("icon.ico"))` and supply a 256×256 icon.

13. **Drop the explicit `compose.material3` dep guesswork in favor of a BOM.** Compose Multiplatform 1.6.x doesn't ship a BOM yet, but tracking aggregate version numbers manually is fiddly. Once Compose Multiplatform 1.7 / 1.8 lands, switch.

14. **Gradle deprecation warning.** The last `createDistributable` run logged `Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0`. Run `gradle build --warning-mode all` to see what; almost certainly Compose Desktop's own task wiring. Will be a no-op when Compose Desktop publishes a Gradle-9-compatible version.

## Non-issues I checked but found fine

- **MicCapture's `line?.run { stop(); close() }` scoping.** Inside the `run` block, `this` is the `TargetDataLine`, so `stop()` resolves to `TargetDataLine.stop()`, not `MicCapture.stop()`. Safe.
- **Little-endian sample decode in MicCapture.** `(hi shl 8) or lo` with `lo = bytes[i*2].toInt() and 0xFF` correctly handles the sign bits.
- **JUnit assumeTrue on the mic test.** Skips cleanly inside Docker (no mic), passes on the host when run with a mic. Correct behavior.
- **`compose.material3` dep.** `compose.desktop.currentOs` brings core Compose but NOT Material 3 — explicit `compose.material3` is required and present.
- **Gradle wrapper.** Generated inside the container (no host JDK needed at generation time). `gradlew.bat` works on Windows once JDK 17 is on PATH or `JAVA_HOME` is set.

## File map (with fix annotations)

```
src/main/kotlin/com/synthesia/desktop/
  Main.kt                          See recommendation #1 (file chooser parent), #9 (window state)
  midi/NoteEvent.kt
  midi/MidiFile.kt
  midi/MidiParser.kt               FIXED: dangling-notes flush
  audio/MicCapture.kt              See recommendation #2 (device picker), #6 (diagnostics)
  audio/PitchDetector.kt
  game/PlayheadController.kt       See recommendation #8 (fuzzy slot grouping)
  game/PitchMatcher.kt
  game/GameState.kt
  game/GameSession.kt              FIXED: detector sampleRate sync; see #7 (auto-stop on done)
  ui/KeyLayout.kt
  ui/PianoKeyboardView.kt
  ui/FallingNotesView.kt
  ui/GameScreen.kt                 See recommendation #3 (note names)
src/test/kotlin/com/synthesia/desktop/audio/MicCaptureTest.kt
build.gradle.kts                   See recommendation #10 (packageMsi), #12 (icon)
```

## To apply the fixes to your built `.exe`

The fixes touched source files, not the already-built distributable. Rebuild on the Windows host:

```
cd d:\synthesia-windows
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
.\gradlew.bat --no-daemon createDistributable
```

That'll replace `build\compose\binaries\main\app\SynthesiaDesktop\SynthesiaDesktop.exe` with the fixed build. Container compile (`docker compose run --rm build`) is also still green.
