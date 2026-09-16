# M3 Changed Files

## Minimal M1/M2 compatibility (status + navigation wiring only)

Behavioral M1/M2 features were not redesigned. Touches are status seed, navigation, DI wiring, and CTAs.

| File | Change |
|------|--------|
| `app/src/main/java/com/avsp/pro/core/module/ModuleStatus.kt` | M1/M2 FROZEN; M3 READY (was NOT_STARTED); `FROZEN_MODULE_IDS` excludes M3 |
| `app/src/main/java/com/avsp/pro/database/DatabaseProvider.kt` | Seed/update module status rows for M3 READY |
| `app/src/main/java/com/avsp/pro/repository/ModuleStatusRepositoryImpl.kt` | Align repository seeding with M3 READY |
| `app/src/main/java/com/avsp/pro/di/AppContainer.kt` | Wire `AudioRepository` / TTS registry |
| `app/src/main/java/com/avsp/pro/ui/navigation/AvspDestination.kt` | Audio/TTS destination |
| `app/src/main/java/com/avsp/pro/ui/navigation/AvspNavHost.kt` | Route to Audio/TTS screen |
| `app/src/main/java/com/avsp/pro/ui/viewmodel/ViewModelFactory.kt` | Audio ViewModel factory |
| `app/src/main/java/com/avsp/pro/ui/screens/projects/ProjectDetailScreen.kt` | Open Audio/TTS CTA |
| `app/src/main/java/com/avsp/pro/ui/screens/modules/ModulesScreen.kt` | Display M3 READY |
| `app/src/main/java/com/avsp/pro/script/ui/ScriptAiScreen.kt` | Continue to Audio CTA only (no M2 logic change) |
| `app/src/test/java/com/avsp/pro/M1CoreSuiteTest.kt` | Status expectations for FROZEN M1/M2, READY M3 |
| `app/src/test/java/com/avsp/pro/core/ContractsTest.kt` | Module contract expectations |
| `app/src/test/java/com/avsp/pro/core/FrozenModuleStatusTest.kt` | Frozen-set excludes M3 |
| `app/src/test/java/com/avsp/pro/script/M2ScriptAiTest.kt` | M2 status FROZEN expectations |
| `audio/README.md` | Points to `com.avsp.pro.audio` |
| `tts/README.md` | Points to TTS engines under audio package |
| `README.md` | Module status table |

## M3 new sources

All under:

- `app/src/main/java/com/avsp/pro/audio/`
- `app/src/test/java/com/avsp/pro/audio/`

## Docs

- `M3_ARCHITECTURE.md`
- `M3_DATA_CONTRACT.md`
- `M3_INTEGRATION_CONTRACT.md`
- `M3_TEST_REPORT.md`
- `M3_BUILD_REPORT.md`
- `M3_ACCEPTANCE_REPORT.md`
- `M3_CHANGED_FILES.md`
- copies under `documentation/`

## Delivery artifact

- `AVSP_M3_AUDIO_TTS_FINAL.zip` (complete project; created at delivery time)
