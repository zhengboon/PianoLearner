# Synthesia-Desktop (Windows) — Stage 1

A learn-to-play piano trainer for Windows, inspired by [Synthesia](https://synthesiagame.com/) and ported from the Android Stage 1 in [d:\synthesia-android\](../synthesia-android/).

**Stage 1 scope** (in development): load a `.mid` file, render falling notes, wait for the user to play each note on a real acoustic/electric piano (detected via the system default microphone via `javax.sound.sampled`), and step through the song.

See [goal.md](goal.md) for the canonical spec.

## Build (compile + tests in Docker)

The canonical compile/test path is Docker — no need for a host JDK.

```
cd d:\synthesia-windows
docker compose run --rm build
```

Runs `gradle build` inside `eclipse-temurin:17-jdk-jammy` with Gradle 8.7. Caches `~/.gradle` in a named volume.

### Ad-hoc Gradle commands inside the container

```
docker compose run --rm shell
# inside the container:
gradle tasks
gradle test
gradle run                 # launches the app — works only if you have an X server forwarded; usually
                           # you'll run the app on the Windows host instead, not inside Docker.
```

## Run on Windows (outside Docker)

To get a launchable Windows app, run on the Windows host (Docker can't help here — `jpackage` only emits installers for the OS it runs on):

```
gradlew createDistributable
```

This produces `build\compose\binaries\main\app\SynthesiaDesktop\` — a self-contained folder with a bundled JRE. Launch:

```
build\compose\binaries\main\app\SynthesiaDesktop\bin\SynthesiaDesktop.bat
```

(Optional, slow) for a real installer: `gradlew packageMsi` → `build\compose\binaries\main\msi\SynthesiaDesktop-0.1.0.msi`. Requires WiX Toolset installed on the host.

## Use

1. Launch `SynthesiaDesktop.bat`.
2. Click "Pick MIDI file" and choose a `.mid` from disk.
3. Make sure your system default mic is your piano-facing input (Windows Sound settings → Recording).
4. The first MIDI slot is highlighted on the keyboard. Play it. The app advances when it hears you.
5. Continue until the song ends.

## Known limitations (Stage 1)

- **Monophonic-leaning detection** — the in-tree MPM picks one fundamental per analysis window. Dense chords with close voicings may be flaky.
- **No timing / scoring** — wrong notes are silently ignored.
- **No falling animation** — slots step forward when you play correctly, matching Synthesia's "Wait for input" mode.
- **System default mic only** — Stage 1 doesn't expose a mic-picker UI.
- **Tempo changes mid-song ignored** — only the first Set Tempo meta-event is captured.
