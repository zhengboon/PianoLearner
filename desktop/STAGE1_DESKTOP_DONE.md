# Stage 1 (Desktop) — Done

A runnable Windows desktop build of Synthesia-Clone is in place. The goal's stop condition is met:

- `build\compose\binaries\main\app\SynthesiaDesktop\SynthesiaDesktop.exe` exists (549 KB launcher).
- A bundled `runtime\` JRE (jlink-trimmed) is alongside it; the whole `SynthesiaDesktop\` folder is 119 MB.
- `MicCaptureTest` passed in the most recent `docker compose run --rm build` (skipped via `assumeTrue` since the container has no mic, which is the documented behavior).

## Launch

Double-click `D:\synthesia-windows\build\compose\binaries\main\app\SynthesiaDesktop\SynthesiaDesktop.exe`, or run it from PowerShell:

```
& 'D:\synthesia-windows\build\compose\binaries\main\app\SynthesiaDesktop\SynthesiaDesktop.exe'
```

No JDK install required for the user — the bundled `runtime\` folder ships its own.

## How to rebuild

Compile + tests in Docker (no host JDK needed):

```
cd d:\synthesia-windows
docker compose run --rm build
```

Windows-native distributable (requires JDK 17 on the host):

```
cd d:\synthesia-windows
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
.\gradlew.bat --no-daemon createDistributable
```

(`jpackage` is OS-bound, which is why this step can't run inside the Linux container.)

## Manual test plan (on-device)

The build verifies compile correctness only. Runtime behavior to check by hand:

1. **Launch**: double-click `SynthesiaDesktop.exe`. A 1280x720 dark window titled "Synthesia Desktop" appears.
2. **File picker**: click "Pick MIDI file". A Swing `JFileChooser` opens, filtered to `*.mid; *.midi` (use "All files" if your `.mid` isn't shown). Pick a file. The Picker screen reads "Loading..." briefly, then transitions to the game screen.
3. **Game UI**:
   - Bottom 120 dp is the 88-key piano (A0..C8). The current slot's expected pitches are yellow; pitches you've successfully heard within a chord are green.
   - Above the keyboard, the next ~6 slots are stacked. Current slot (bottom) yellow; future slots blue.
   - Top of screen shows `Slot N / Total expected=[...] heard=[...]`.
4. **Mic + step-by-step**: speak into the system default mic, or — better — play a single piano note matching the highlighted pitch. The playhead should advance on a successful match. +/- 50 cents tolerance, 150 ms debounce so a single sustained note doesn't double-advance.
5. **End of song**: header reads "Song complete." Click "Stop" or "Pick another" to return to the picker.
6. **Mic device selection**: Stage 1 uses the system default recording device. To switch mics, change it in Windows Sound Settings → Input → "Choose a device for speaking or recording" BEFORE launching.

## Known limitations (Stage 1)

- **No mic-picker UI** — system default only. Add an `AudioSystem.getMixerInfo()` dropdown in Stage 2.
- **Monophonic-leaning detection** — the in-tree MPM picks one fundamental per analysis window. Dense voicings may be flaky.
- **No timing / scoring** — wrong notes are silently ignored.
- **No falling animation** — slots step forward when you play correctly, matching Synthesia's "Wait for input" mode.
- **Tempo changes ignored** — only the first SMF Set Tempo meta-event is captured.
- **Channel 9 (GM drums) dropped** during MIDI parsing.
- **Landscape-only logical layout** — the window is wide; resizing to portrait squashes 52 white keys.
- **No persistent state** — closing the app loses the picked file and progress.

## File map (Stage 1, 24 source/config files + Gradle wrapper)

```
goal.md                                              Canonical spec.
README.md                                            Quick start.
settings.gradle.kts                                  Module + repo config.
build.gradle.kts                                     Kotlin 1.9.24 + Compose 1.6.10 + compose.material3.
gradle.properties                                    JVM args.
.gitignore                                           Standard Gradle ignores.
Dockerfile                                           JDK 17 + Gradle 8.7 (compile/test container).
docker-compose.yml                                   `build` + `shell` services.
.dockerignore                                        Excludes build outputs from image context.
docker-build.log                                     Tail of last container build.
host-build.log                                       Tail of last `gradlew createDistributable`.
gradlew, gradlew.bat                                 Gradle wrapper (generated inside container).
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties

src/main/kotlin/com/synthesia/desktop/
  Main.kt                                            Compose application + JFileChooser.
  midi/NoteEvent.kt                                  Pitch + tick + duration.
  midi/MidiFile.kt                                   PPQN + tempo + notes container.
  midi/MidiParser.kt                                 SMF Format 0/1 reader (ported from Android).
  audio/MicCapture.kt                                javax.sound.sampled.TargetDataLine wrapper.
  audio/PitchDetector.kt                             McLeod Pitch Method (ported from Android).
  game/PlayheadController.kt                         Walks chord-aware NoteSlots.
  game/PitchMatcher.kt                               +/-50 cents tolerance, slot-aware.
  game/GameState.kt                                  UI snapshot.
  game/GameSession.kt                                Wires mic+detector+playhead, emits StateFlow.
  ui/KeyLayout.kt                                    Shared key→x geometry.
  ui/PianoKeyboardView.kt                            88-key piano with expected/heard highlight.
  ui/FallingNotesView.kt                             Slot rows stacked above the keyboard.
  ui/GameScreen.kt                                   Composes the UI from GameSession.state.

src/test/kotlin/com/synthesia/desktop/
  audio/MicCaptureTest.kt                            JUnit, assumeTrue-skips in headless environments.
```

## Relationship to the Android Stage 1

11 of the source files (~90% of the line count) are bit-for-bit ports from `d:\synthesia-android\` with only the package declaration changed (`com.synthesia.stage1.*` → `com.synthesia.desktop.*`). Stage 1 is intentionally a parallel codebase — refactoring into a shared `:common` Gradle module / Kotlin Multiplatform structure is a Stage 2+ task.

## Next stages (sketch)

- **Stage 2**: extract shared code into a `:common` KMP module; both Android and Desktop pull from it.
- **Stage 3**: timing — scrolling falling notes, hit window, tempo-aware playback.
- **Stage 4**: scoring + lesson modes (hand split, slow practice loop).
- **Stage 5**: polyphonic pitch detection so dense chords register reliably.
- **Stage 6**: in-app MIDI library, song selector UI, mic device picker.
