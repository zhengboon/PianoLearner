# Synthesia-Desktop — Stage 1 Goal Spec

## Outcome

A Windows desktop app (Kotlin + Compose for Desktop) that reproduces the Android Stage 1 behavior:

1. Loads a `.mid` file picked via a Swing file chooser.
2. Renders a Synthesia-style falling-notes lane above an 88-key piano keyboard.
3. Listens through the system default microphone using `javax.sound.sampled.TargetDataLine`.
4. Holds at the current note slot until the player plays the expected pitch(es) on a real piano.
5. Advances to the next slot when the mic detects the expected pitch (with +/- 50 cents tolerance) and a 150 ms debounce.
6. Continues until the MIDI ends.

## Tech stack (locked in)

- **Language**: Kotlin 1.9.24
- **UI**: Jetbrains Compose for Desktop 1.6.10
- **Audio capture**: `javax.sound.sampled.TargetDataLine` (mono, 16-bit PCM, 44.1 kHz)
- **Build**: Gradle 8.7 (Kotlin DSL), JDK 17 (Eclipse Temurin)
- **Coroutines**: kotlinx-coroutines (pulled in transitively by compose-desktop; explicit swing dispatcher dep if needed)

## Code reuse

The following modules are pure-Kotlin and port directly from `d:\synthesia-android\`. Stage 1 copies them into the new project (package rename only: `com.synthesia.stage1.*` → `com.synthesia.desktop.*`):

- `midi/NoteEvent.kt`, `midi/MidiFile.kt`, `midi/MidiParser.kt` — SMF Format 0/1 reader
- `audio/PitchDetector.kt` — in-tree McLeod Pitch Method
- `game/PlayheadController.kt` (NoteSlot + slot-walker), `game/PitchMatcher.kt`, `game/GameState.kt`
- `ui/KeyLayout.kt`, `ui/PianoKeyboardView.kt`, `ui/FallingNotesView.kt`, `ui/GameScreen.kt` — Compose composables (the Compose API is identical on desktop)

Platform-specific replacements:

- `audio/MicCapture.kt` — wraps `TargetDataLine` instead of `AudioRecord`. Runs a background thread that reads fixed-size frames and pushes them to a callback.
- `game/GameSession.kt` — same logic as Android, minus `@RequiresPermission(RECORD_AUDIO)` and `@SuppressLint`.
- `Main.kt` — Compose `application { Window(...) { ... } }` entry, hosts a Swing `JFileChooser` for the MIDI picker. No permission flow (desktop just opens the mic when the OS allows it).

## Repository layout

```
d:\synthesia-windows\
├── goal.md                                            <- this file
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── .gitignore
├── Dockerfile                                         <- linux JDK 17 + gradle 8.7
├── docker-compose.yml                                 <- `build` and `shell` services
├── .dockerignore
└── src\
    ├── main\kotlin\com\synthesia\desktop\
    │   ├── Main.kt
    │   ├── midi\        (NoteEvent.kt, MidiFile.kt, MidiParser.kt)
    │   ├── audio\       (PitchDetector.kt, MicCapture.kt)
    │   ├── game\        (PlayheadController.kt, PitchMatcher.kt, GameState.kt, GameSession.kt)
    │   └── ui\          (KeyLayout.kt, PianoKeyboardView.kt, FallingNotesView.kt, GameScreen.kt)
    └── test\kotlin\com\synthesia\desktop\
        └── MicCaptureTest.kt                          <- opens TargetDataLine, reads 1 frame, skips if no mic
```

## Build pipeline

Containerized **compile + tests** only:

```
cd d:\synthesia-windows
docker compose run --rm build
```

This runs `gradle build` inside an `eclipse-temurin:17-jdk-jammy` container with Gradle 8.7 preinstalled. It compiles Kotlin and runs JUnit tests. Caches `~/.gradle` in a named volume so re-runs are fast.

**Windows packaging cannot happen inside a Linux container** (`jpackage` only emits installers for the OS it runs on). To produce the runnable folder, run on the Windows host outside Docker:

```
gradlew createDistributable
```

Output lands at `build\compose\binaries\main\app\SynthesiaDesktop\` containing a bundled `jlink` JRE and a launcher script. Launch by double-clicking `SynthesiaDesktop\bin\SynthesiaDesktop.bat`.

## Iteration plan (rough)

- **Iter 1**: scaffold project (gradle config, Dockerfile, docker-compose, README, goal.md).
- **Iter 2**: copy pure-Kotlin files from `d:\synthesia-android\` with package rename.
- **Iter 3**: write desktop-specific files — MicCapture (TargetDataLine), GameSession (sans Android annotations), Main.kt entry.
- **Iter 4**: JUnit test in `src\test` that opens TargetDataLine and reads one frame (skips gracefully if no mic).
- **Iter 5**: `docker compose run --rm build` — verify compile + tests pass. Iterate fixes.
- **Iter 6**: `gradlew createDistributable` on Windows host — verify the runnable folder lands. Write `STAGE1_DESKTOP_DONE.md`.

## Stop condition (literal check)

`Test-Path d:\synthesia-windows\build\compose\binaries\main\app\SynthesiaDesktop\bin\SynthesiaDesktop.bat` returns `True` **AND** `Test-Path d:\synthesia-windows\STAGE1_DESKTOP_DONE.md` returns `True` **AND** the JUnit `MicCaptureTest` passed in the most recent Docker build.

## Pause condition

- JDK 17 not findable by Gradle inside Docker → write `BLOCKED_jdk.md`, pause.
- Compose Desktop 1.6.10 artifacts fail to resolve from Maven Central + Compose dev repo → write `BLOCKED_compose_resolve.md`, pause.

## Scope / write constraints

- **Allowed write scope**: anything under `d:\synthesia-windows\`.
- **Read-only**: `d:\synthesia-android\` (we copy from it but never modify it).
- **Forbidden**: do not modify global Gradle config, do not install JDKs on the host outside Docker.
