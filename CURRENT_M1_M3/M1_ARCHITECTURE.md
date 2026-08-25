# M1 Architecture

## Stack

- Kotlin 2.0.21
- Android Gradle Plugin 8.7.2 / Gradle 8.9
- Jetpack Compose + Material 3
- Room 2.6.1 (SQLite) with non-destructive migrations
- WorkManager (stub bridge only)
- Coroutines + ViewModel
- Robolectric unit tests

## Layers

```
Compose UI
    ↓
ViewModels (UiState: Idle/Loading/Success/Error)
    ↓
Repositories (interfaces)
    ↓
Room DAOs / FileAvspStorage / SecureConfigStore
```

Compose screens never access Room DAOs directly.

## Navigation

Stable bottom destinations:

`home` · `projects` · `media` · `modules` · `settings` · `logs`

Plus `project/{projectId}` detail.

## Database

`AvspDatabase` v2 tables:

- `projects`
- `module_status`
- `settings`
- `logs`
- `media_assets` (added via `MIGRATION_1_2`)

Normal strategy: explicit migrations. Destructive migration is **not** the default.

## Module registry

On first open, M1 seeds M1–M9. Defaults:

- M1 → READY
- M2/M3 → NOT_STARTED
- M4–M9 → FROZEN

Future packages register via `ModuleStatusRepository.registerOrUpdate`.

## Secure credentials

`EncryptedSecureConfigStore` implements `SecureConfigStore` using AndroidX `EncryptedSharedPreferences` + `MasterKey` when Android Keystore is available. Secrets are never hard-coded, never shown in UI, and never logged.

## Storage areas

| Area | Purpose |
|------|---------|
| APP_DATA | App-level data |
| PROJECT_DATA | Per-project originals/working/generated/published/logs |
| GENERATED_MEDIA | Generated outputs |
| TEMP | Cache temp files |
| LOGS | File logs root |

Paths resolve under `Context.filesDir` / `cacheDir` — no hard-coded `/storage/emulated` or desktop drive letters.

## Background work

`ModuleStatusWorker` acknowledges module IDs for future enqueueing. M8 automation is **not** implemented in M1.
