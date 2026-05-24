# Synthesia-Clone (Android) — Stage 1

A learn-to-play piano trainer for Android, inspired by [Synthesia](https://synthesiagame.com/).

**Stage 1 scope** (currently in development): load a MIDI file, render falling notes, wait for the user to play each note on a real acoustic/electric piano (detected via the phone's microphone), and advance through the song step-by-step.

See [goal.md](goal.md) for the full spec.

## Project structure

```
synthesia-android/
├── goal.md                  Canonical Stage 1 spec.
├── README.md                This file.
├── settings.gradle.kts      Gradle settings (Kotlin DSL).
├── build.gradle.kts         Root build script.
├── gradle.properties        JVM args, AndroidX flags.
├── .gitignore               Standard Android/Gradle ignores.
└── app/
    ├── build.gradle.kts     App module build script.
    ├── proguard-rules.pro   Empty for now (Stage 1 ships unobfuscated).
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/synthesia/stage1/
        │   ├── MainActivity.kt           Compose entry point.
        │   ├── midi/                     MIDI file parsing.
        │   ├── audio/                    Microphone capture + pitch detection.
        │   ├── game/                     Playhead + match logic.
        │   └── ui/                       Falling-notes + piano keyboard composables.
        └── res/                          Strings, themes, backup rules.
```

## Build

The canonical build path is Docker — you don't need Android Studio or a local JDK / SDK.

```
cd d:\synthesia-android
docker compose run --rm build
```

The Docker image fetches Android cmdline-tools, platform-34, build-tools 34.0.0, and Gradle 8.7 once, caches them in a named volume, and assembles a debug APK on each run. Output lands at `app\build\outputs\apk\debug\app-debug.apk` on the host (the project is bind-mounted at `/workspace` inside the container).

### Drop into the container for ad-hoc Gradle commands

```
docker compose run --rm shell
# inside the container:
gradle :app:tasks
gradle :app:assembleDebug
```

### Host-side build (optional)

If you'd rather build on the host: Android Studio Iguana (2023.2.1) or later, Android SDK Platform 34, JDK 17, and either a Gradle wrapper (`gradle wrapper --gradle-version 8.7`) or a system Gradle. Then `gradle :app:assembleDebug`.

## Install on device

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Use

1. Launch "Synthesia Clone" on the phone.
2. Tap the file-picker button → choose a `.mid` file from device storage.
3. Grant `RECORD_AUDIO` permission when prompted.
4. Place the phone near your piano with the microphone facing the soundboard.
5. The first MIDI note is highlighted on the keyboard. Play it. The app advances to the next note when it hears you.
6. Continue until the song ends.

## Known limitations (Stage 1)

- Monophonic-first: chords are recognized when ALL expected notes are detected within a short window, but spectral leakage on cheap mics may make this flaky.
- No timing feedback. Wrong notes are simply ignored — the app waits indefinitely for the correct one.
- No persistent settings — relaunch loads no state.

## License

TBD. (Stage 1 codebase is a starting scaffold; choose a license before publishing.)
