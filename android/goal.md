# Synthesia-Clone — Stage 1 Goal Spec

## Outcome (Stage 1)

A working Android app, hand-installable as a debug APK on a phone, that:

1. Loads a MIDI file (`.mid`) from device storage (Storage Access Framework picker).
2. Renders a Synthesia-style falling-notes UI above a piano keyboard at the bottom.
3. Continuously listens through the phone's microphone for the user playing a real piano.
4. Holds at the current note position until the **expected** note (or chord) is detected from the microphone.
5. Once the expected note is heard, advances the playhead to the next note event in the MIDI sequence.
6. Repeats until the MIDI sequence ends.

## Build & deploy — Docker

The user wants the project deployable via Docker. Android applications do NOT run inside containers on the phone (Docker doesn't execute Android binaries), so "Docker" here means **the build pipeline is fully containerized**. Anyone with Docker + the source can produce a working APK without installing Android Studio, JDK, or the Android SDK locally.

Two files at the project root provide this:

- `Dockerfile` — builds an image with JDK 17, Gradle 8.7, Android cmdline-tools, platform-34, build-tools 34.0.0. The image's default CMD assembles a debug APK.
- `docker-compose.yml` — bind-mounts the project at `/workspace`, caches `~/.gradle` in a named volume, and runs the same build with `docker compose run --rm build`.

Operator workflow:

```
cd d:\synthesia-android
docker compose run --rm build
# APK lands at app\build\outputs\apk\debug\app-debug.apk on the host
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The APK is the deployment artifact. Docker is the reproducible build environment, nothing more — there is no server-side component in Stage 1.

## Non-goals (deferred to later stages)

- No multi-instrument support — assume the user plays an acoustic/electric piano in front of the phone.
- No timing/scoring/rhythm grading — Stage 1 is "step-by-step" practice mode only, like Synthesia's "Wait for input" mode.
- No background playback of expected audio. Just visual.
- No left-hand / right-hand split — play whatever the MIDI says.
- No accidentals UI polish, animations, particle effects, etc.

## Tech stack (locked in for Stage 1)

- **Language**: Kotlin
- **Build**: Gradle (Kotlin DSL), Android Gradle Plugin (AGP) 8.5+
- **Min SDK**: 26 (Android 8.0) — gives us android.media.midi APIs and modern audio routing
- **Target SDK**: 34
- **UI**: Jetpack Compose
- **Pitch detection**: in-tree McLeod Pitch Method (MPM) — about 60 lines of Kotlin in `PitchDetector.kt`. Decision (iter-3): we deliberately dropped the TarsosDSP dependency to keep the Docker build hermetic (TarsosDSP is hosted on a non-Maven-Central repo, and its `:jvm` module pulls in `javax.sound` which isn't on Android). Robust on monophonic piano. For chords, run the detector across short windows; accept the chord when its N expected pitches all appear within a window (deferred to iter-4 logic if needed).
- **MIDI parsing**: in-tree minimal parser (Format 0 + Format 1, NoteOn / NoteOff events, tempo + ticks resolution). External MIDI libs for Android are sparse and we only need the read path.
- **Storage**: SAF (`OpenDocument`) — no MANAGE_EXTERNAL_STORAGE.

## Repository layout

```
d:\synthesia-android\
├── goal.md                      <- this file (canonical spec)
├── README.md                    <- build / install / use instructions
├── settings.gradle.kts
├── build.gradle.kts             <- project-level
├── gradle.properties
├── .gitignore
├── app\
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src\main\
│       ├── AndroidManifest.xml
│       ├── java\com\synthesia\stage1\
│       │   ├── MainActivity.kt
│       │   ├── midi\
│       │   │   ├── MidiFile.kt
│       │   │   ├── MidiParser.kt
│       │   │   └── NoteEvent.kt
│       │   ├── audio\
│       │   │   ├── MicCapture.kt
│       │   │   └── PitchDetector.kt
│       │   ├── game\
│       │   │   ├── PlayheadController.kt
│       │   │   └── PitchMatcher.kt
│       │   └── ui\
│       │       ├── FallingNotesView.kt
│       │       ├── PianoKeyboardView.kt
│       │       └── theme\Theme.kt
│       └── res\
│           ├── values\strings.xml
│           ├── values\themes.xml
│           └── xml\backup_rules.xml
```

## Verification (per-iteration acceptance is below; this is the overall stop condition)

The goal is **complete** when ALL of:

1. `d:\synthesia-android\` contains a fully-formed Android Gradle project (every file above exists, non-empty, syntactically valid).
2. `docker compose run --rm build` (run from the project root) produces an APK at `d:\synthesia-android\app\build\outputs\apk\debug\app-debug.apk`. The Docker build is the canonical build path; running `gradlew :app:assembleDebug` directly on the host is supported but not required.
3. A `STAGE1_DONE.md` file is written at project root summarizing what was built, what the user should test manually on a real Android device, and known limitations.

The operator (user) is responsible for installing and side-loading the APK to the phone. We will NOT attempt to install on a device from this loop.

## Iteration plan (rough — adjust as we learn)

- **Iter 1**: scaffold project (Gradle files, manifest, empty MainActivity, theme, .gitignore, README, Dockerfile, docker-compose.yml). Acceptance: every file in repo layout exists with at minimum a valid stub.
- **Iter 2**: implement minimal MIDI parser (Format 0/1, NoteOn/NoteOff, tempo). Unit-test on a known fixture mentally or via reading hex.
- **Iter 3**: pitch detection — verify TarsosDSP coordinates resolve, implement `MicCapture` (AudioRecord) and `PitchDetector` wrapping MPM. Add RECORD_AUDIO permission flow.
- **Iter 4**: game loop — `PlayheadController` advances through `NoteEvent` list; `PitchMatcher` consumes detected pitches and signals "expected note heard". Hook the two together.
- **Iter 5**: Compose UI — falling notes lane + piano keyboard. Highlight the next-expected note. Wire to `PlayheadController` state.
- **Iter 6**: file pick → load → play flow in MainActivity.
- **Iter 7**: Docker build verification — actually run `docker compose run --rm build`, confirm APK lands on the host. Write `STAGE1_DONE.md`.

## Scope / write constraints

- **Allowed write scope**: anything under `d:\synthesia-android\`.
- **Forbidden**: do not touch anything outside the project directory. Do not modify global Gradle config, do not install Android SDK components, do not run `gradlew` if it would download large dependencies without warning the operator first.

## Pause condition

If the operator types `/goal pause`, halt at the next iteration boundary.

If a required external dependency cannot be resolved (e.g., TarsosDSP Maven coordinates fail), write a `BLOCKED_<reason>.md` and pause.

## Stop condition (the literal check)

`Test-Path d:\synthesia-android\app\build\outputs\apk\debug\app-debug.apk` returns `True` **AND** `Test-Path d:\synthesia-android\STAGE1_DONE.md` returns `True` **AND** `Test-Path d:\synthesia-android\Dockerfile` returns `True`.

Until all three are true, the loop continues.

If the Docker build proves intractable in this environment (Docker daemon not installed, network restrictions blocking SDK download, etc.), write `BLOCKED_docker.md` documenting the blocker, set goal status to `paused`, and exit. The operator can resume after resolving the blocker.
