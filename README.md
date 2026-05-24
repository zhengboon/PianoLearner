# PianoLearner

A Synthesia-style step-by-step piano practice tool. Loads a MIDI file, listens through your device's microphone, and only advances to the next note once you've actually played it on a real piano.

Two builds in this repository:

- [**android/**](android/) — Jetpack Compose app, min SDK 26, target SDK 34
- [**desktop/**](desktop/) — Compose for Desktop (Kotlin/JVM), JDK 17, runs on Windows/macOS/Linux

Both share the same Kotlin algorithm code (MIDI parser, McLeod Pitch Method detector, chord-slot playhead, ±50 cent pitch matcher). Only the audio capture (`AudioRecord` vs `javax.sound.sampled.TargetDataLine`), file picker, and entry point differ.

## Status

Stage 1: **complete**. Verified end-to-end against:

- 613 / 613 chord slots in Pachelbel's Canon (`canon-3.mid`)
- 500 / 500 randomly-sampled MIDIs from a 13 536-file generated chord corpus
- 27 JVM unit tests passing across both projects (`MidiParserTest`, `PitchChainTest`, `PlayheadControllerTest`, `MicCaptureTest`)
- Manual smoke test on Android emulator (API 35, x86_64)

See [`docs/`](docs/) for a website, screenshots, and an interactive demo.

## Build

### Android

```
cd android
docker compose run --rm build
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Docker build needs no host JDK / Android SDK — it pulls cmdline-tools, platform-34, build-tools 34.0.0, and Gradle 8.7 once and caches them.

### Desktop

Compile + unit tests in Docker (no host JDK required):

```
cd desktop
docker compose run --rm build
```

Native Windows distributable (needs JDK 17 on the host — `jpackage` is OS-bound and can't cross-compile a `.exe` from Linux):

```
cd desktop
gradlew.bat --no-daemon createDistributable
```

Output lands at `desktop/build/compose/binaries/main/app/SynthesiaDesktop/SynthesiaDesktop.exe` with a bundled `jlink` JRE.

## Test the algorithm against your own MIDIs

The desktop project includes an offline `TestPlayer` that synthesizes audio for every slot of a MIDI file and runs it through the exact `PitchDetector` + `PitchMatcher` chain the live app uses. Use it for fast regression checks without needing a piano.

```
cd desktop
gradlew.bat --no-daemon -PmidiPath="C:\path\to\file.mid" testPlayer

# or recurse a directory + sample N random files
gradlew.bat --no-daemon -PmidiPath="C:\path\to\midi-corpus" -PsampleSize=500 testPlayer
```

## How it works

1. **MIDI loaded** → in-tree SMF Format 0/1 parser extracts NoteOn/NoteOff events and the first Set Tempo. Channel 9 (GM drums) is dropped.
2. **Notes grouped into chord slots** using PianoBooster's two-threshold model: `noteGap = PPQN/16` (max gap between consecutive notes in the same chord) and `maxSpan = PPQN/4` (max from first to last). Duplicate pitches inside a slot collapse — same key can't be physically pressed twice simultaneously.
3. **Microphone opens** (`AudioRecord.UNPROCESSED` → falls back to `VOICE_RECOGNITION` on Android; `javax.sound.sampled.TargetDataLine` on desktop). 16-bit PCM mono @ 44.1 kHz, 4 096-sample frames.
4. **Each frame → McLeod Pitch Method**. In-tree port of TarsosDSP's `McLeodPitchMethod.java` with the Tartini zero-crossing peak picker, `SMALL_CUTOFF = 0.5`, and `cutoff = 0.97`. Returns the dominant pitch in Hz, or `null` if below the RMS gate.
5. **Hz → MIDI match** within ±50 cents. When every unique pitch in the current slot has been heard, the playhead advances.

## Project layout

```
android/                         Android app
  app/src/main/java/com/synthesia/stage1/
    MainActivity.kt              Compose entry, permission gate, file picker, sample button
    midi/                        SMF parser, NoteEvent, name helper
    audio/                       MicCapture (AudioRecord), PitchDetector (MPM)
    game/                        PlayheadController, PitchMatcher, GameSession, GameState
    ui/                          KeyLayout, PianoKeyboardView, FallingNotesView, GameScreen, theme
  app/src/test/java/             JUnit unit tests (parser, pitch chain, playhead)
  Dockerfile, docker-compose.yml AGP build env

desktop/                         Compose Desktop app
  src/main/kotlin/com/synthesia/desktop/
    Main.kt                      Compose application, mic picker, window state persistence
    midi/                        (same as android, package renamed)
    audio/                       MicCapture (TargetDataLine), PitchDetector
    game/                        (same as android)
    tools/TestPlayer.kt          Offline batch verification tool
    ui/                          (same as android)
  src/test/kotlin/               JUnit tests + TargetDataLine smoke test
  Dockerfile, docker-compose.yml

docs/                            Static website (GitHub Pages compatible)
  index.html, style.css, demo/, screenshots/
```

## Known limitations (deferred to Stage 2+)

- **No timing or scoring** — wrong notes are silently ignored; the playhead simply waits.
- **No falling-notes scroll animation** — slots step forward when you play correctly. This matches Synthesia's "Wait for input" mode.
- **No hand-split UI** — both hands' notes are treated equally.
- **Pitch detection is monophonic-leaning** — McLeod Pitch Method finds the dominant fundamental per frame. Wide-interval chords pass because the mic captures each note as it's arpeggiated; very tight close-voiced chords may need a deliberate arpeggio from the player.
- **System default microphone only** on Android. Desktop has a basic mic-picker UI.
- **Tempo changes mid-song ignored** — only the first Set Tempo meta-event is captured.

## License

MIT — see [LICENSE](LICENSE).
