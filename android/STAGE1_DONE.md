# Stage 1 — Done

A debug APK has been produced from the containerized Gradle build. The goal's stop condition is met.

## Build artifact

```
d:\synthesia-android\app\build\outputs\apk\debug\app-debug.apk
```

Size: ~8.7 MB. Built inside the `synthesia-clone-build:latest` Docker image (Android cmdline-tools 11076708, platform-34, build-tools 34.0.0, Gradle 8.7, JDK 17 / Eclipse Temurin Jammy). First build pulled ~1 GB of SDK / Gradle / AndroidX artifacts; subsequent rebuilds reuse the image layer and the `gradle-cache` named volume — incremental rebuild in this session took 1 m 9 s.

## How to rebuild

```
cd d:\synthesia-android
docker compose run --rm build
```

## How to install on a phone

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If `adb` isn't on your host PATH but Docker is, you can run it from inside the build container against a remote ADB server, but for Stage 1 the simplest path is host-side adb. Android Studio's platform-tools, scrcpy, or any standalone `platform-tools.zip` from `dl.google.com/android/repository` ships an `adb.exe` that works.

## What to test manually on-device

The build verifies compile correctness only — no runtime behavior has been checked yet. When you launch the app:

1. **Permission gate**: on first launch the app should ask for microphone permission. Granting it reveals the file-picker screen.
2. **MIDI picker**: tap "Pick MIDI file" and choose a `.mid` from device storage. The picker filters on common MIDI MIME types but falls back to `*/*` because Android's MIME mapping for `.mid` is inconsistent across OEMs.
3. **Loading**: if the parser rejects the file (e.g. SMPTE division, missing MThd) the picker shows the error message in red. Try another file.
4. **Game UI**:
   - The bottom strip is an 88-key piano (A0..C8). The current slot's expected pitches are highlighted **yellow**; pitches you've successfully played in the current chord turn **green**.
   - Above the keyboard is a stack of the next ~6 slots (current at the bottom, future slots higher up). Future slots are blue.
   - Above that, a text strip shows `Slot N / Total expected=[…] heard=[…]`.
5. **Step-by-step practice**: play the expected pitch on your piano. With +/- 50 cents tolerance the matcher accepts mistuned pianos. For chord slots, every expected pitch must be heard (in any order) before the playhead advances. A 150 ms debounce prevents a sustained note from double-advancing.
6. **End**: once all slots are done, the header reads "Song complete." Hit "Pick another" or "Stop" to return to the picker.

## Known limitations (carried forward to Stage 2+)

- **Pitch detection is monophonic-leaning.** The in-tree McLeod Pitch Method is robust for single piano notes but only picks one fundamental per analysis window. Big chords work because the matcher accepts whichever expected pitch most strongly dominates each frame and accumulates partial credit — but very close-voiced chords (minor seconds) may be flaky.
- **Tempo changes mid-song are ignored.** Only the first SMF Set Tempo meta-event is captured.
- **No timing / scoring.** Wrong notes are silently ignored; the playhead just waits.
- **No falling animation.** Slots stack discretely; we don't scroll continuously like Synthesia's normal mode. This matches Synthesia's "Wait for input" mode and keeps Stage 1 self-contained.
- **Channel 9 (GM drums) is dropped** during MIDI parsing. Drum tracks won't appear in the UI.
- **Landscape orientation only** (manifest config). Portrait would squeeze 52 white keys into too little space.
- **No persistent state.** Closing the app loses the picked file and progress.

## File map (Stage 1, 31 files)

```
goal.md                                  Spec — read this for the canonical requirements.
README.md                                Quick start.
settings.gradle.kts                      Module list + repositories.
build.gradle.kts                         Plugin versions (AGP 8.5, Kotlin 1.9.24).
gradle.properties                        AndroidX + JVM args.
.gitignore                               Standard Android/Gradle ignores.
Dockerfile                               JDK 17 + Android SDK + Gradle 8.7.
docker-compose.yml                       `build` service + `shell` service.
.dockerignore                            Excludes build outputs from the image context.
docker-build.log                         Tail of most recent docker compose build.
app/build.gradle.kts                     App module config (minSdk 26, target 34, Compose on).
app/proguard-rules.pro                   Empty; Stage 1 ships unobfuscated.
app/src/main/AndroidManifest.xml         RECORD_AUDIO permission + main activity.
app/src/main/res/values/strings.xml      Strings.
app/src/main/res/values/themes.xml       Black material theme.
app/src/main/res/xml/backup_rules.xml    Exclude everything from auto-backup.
app/src/main/java/com/synthesia/stage1/
  MainActivity.kt                        Permission gate + SAF picker + game host.
  midi/NoteEvent.kt                      Pitch + tick + duration.
  midi/MidiFile.kt                       PPQN + tempo + notes container.
  midi/MidiParser.kt                     SMF Format 0/1 reader (~190 lines).
  audio/MicCapture.kt                    AudioRecord wrapper, background thread.
  audio/PitchDetector.kt                 McLeod Pitch Method on 16-bit PCM frames.
  game/PlayheadController.kt             Walks chord-aware NoteSlots.
  game/PitchMatcher.kt                   +/-50 cents tolerance, slot-aware match.
  game/GameState.kt                      UI snapshot.
  game/GameSession.kt                    Wires mic+detector+playhead, emits StateFlow.
  ui/KeyLayout.kt                        Shared key→x geometry.
  ui/PianoKeyboardView.kt                88-key piano with expected/heard highlight.
  ui/FallingNotesView.kt                 Slot rows stacked above the keyboard.
  ui/GameScreen.kt                       Composes the UI from GameSession.state.
  ui/theme/Theme.kt                      Compose Material3 theme.
```

## Next stages (sketch — not committed)

- **Stage 2**: timing — show notes scrolling continuously against song tempo, with a "hit window" the player must land in. Mid-song tempo changes.
- **Stage 3**: scoring + lesson modes (right-hand only, left-hand only, slow practice loop).
- **Stage 4**: polyphonic pitch detection (HPS or a tiny CNN) so dense chords reliably register.
- **Stage 5**: in-app MIDI library (download from /assets) and song selector UI.
