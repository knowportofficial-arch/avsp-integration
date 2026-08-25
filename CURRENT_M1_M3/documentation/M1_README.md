# M1 README — AVSP Core & UI

## Purpose

M1 establishes the AVSP application foundation:

- Application shell & Jetpack Compose navigation
- Project management (CRUD) with Room/SQLite
- Core data contracts for future modules
- Module status registry
- Storage abstraction (platform-safe paths)
- Settings + secure config abstraction
- Structured errors + centralized logging
- Repository / ViewModel layer
- Unit test suite (A–X)
- Integration contracts for M2–M9 handoff

## Not in M1

AI script generation, TTS, camera, OCR, YouTube, video rendering, publishing, automation.

## Build

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Windows: `gradlew.bat` equivalents.

## Package layout

```
app/src/main/java/com/avsp/pro/
  core/          contracts, models, errors, UiState, integration, module
  database/      Room DB, entities, DAOs, migrations, mappers
  storage/       AvspStorage + FileAvspStorage
  settings/      AppSettings + SecureConfigStore
  logs/          AvspLogger + SecretRedactor
  repository/    Project / Settings / ModuleStatus / Log repositories
  ui/            theme, navigation, screens, viewmodels, components
  workers/       ModuleStatusWorker (future bridge only)
  di/            AppContainer
```

Reserved top-level folders (`script/`, `tts/`, `video/`, …) are placeholders for future modules — not implemented in M1.

## Acceptance demo checklist

1. Launch app → Home shows AVSP brand
2. Create project → persisted in Room
3. Reopen project → dashboard with module statuses
4. Modules screen → M1 READY, M2/M3 NOT_STARTED, M4–M9 FROZEN
5. Settings → save preferences; secrets show CONFIGURED/NOT CONFIGURED only
6. Logs → events appear; errors surface in UI states

## Known limitations

- No instrumented UI/emulator tests in this environment (unit tests with Robolectric cover A–X + V2 corrections)
- SecureConfigStore uses EncryptedSharedPreferences + MasterKey on device; AES-GCM encrypted fallback on JVM unit tests (AndroidKeyStore unavailable)
- Media screen is inventory-only until M3/M4/M6 produce assets
- WorkManager worker is a status ACK stub, not M8 automation
