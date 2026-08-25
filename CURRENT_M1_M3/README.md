# AVSP — AI Video Studio Pro

**M1 Core & UI** foundation for the modular AVSP platform.

## What this is

AVSP is an Android-first, modular AI video-production platform. **M1** delivers the application shell, navigation, project management, Room persistence, storage abstraction, settings, logging, error model, and stable contracts for future modules (M2–M9).

M1 Core/UI and M2 Script AI are **FROZEN**. M3 Audio/TTS is **READY**.
M4–M9 are represented as **FROZEN** (accepted external modules; sources not modified).

## Requirements

- JDK 17+
- Android SDK (compileSdk/targetSdk 35, minSdk 26)
- Android Studio Ladybug+ or command-line Gradle

## Run locally

```bash
# Linux/macOS
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew installDebug   # with device/emulator

# Windows
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`

## App areas

| Screen | Purpose |
|--------|---------|
| Home | Studio overview, recent projects, module snapshot |
| Projects | Create / open / rename / delete projects |
| Media | Asset inventory (empty until later modules produce files) |
| Modules | M1–M9 status registry |
| Settings | Language, aspect ratio, theme, log level, config state |
| Logs | Centralized application logs |

## Security

No API keys, tokens, or passwords are hard-coded. Settings UI shows only `CONFIGURED` / `NOT CONFIGURED` for future credentials.

## Documentation

See `documentation/` for M1 architecture, contracts, tests, and acceptance reports.

## Module status

| Module | Status |
|--------|--------|
| M1 Core/UI | FROZEN |
| M2 Script AI | FROZEN |
| M3 Audio/TTS | READY |
| M4–M9 | FROZEN (display only; sources not modified) |

## Documentation

See `documentation/` and root `M3_*.md` for M3 architecture, contracts, tests, and acceptance.
