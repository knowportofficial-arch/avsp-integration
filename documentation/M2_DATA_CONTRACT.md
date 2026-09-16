# M2 Data Contract

## ScriptPackage

| Field | Description |
|-------|-------------|
| version | Contract version (`1.0`) |
| projectId / scriptId | Identifiers |
| topic / language / title | Content identity |
| hook / introduction / cta / ending | Script sections |
| scenes[] | Ordered production beats |
| estimatedDurationMs | Sum of scene durations |
| targetDurationMs | Requested target |
| estimatedNarrationDurationMs | Narration estimate |
| validation | `VALID` / `WARNING` / `INVALID` + messages |
| metadata | content type, audience, platform, generator id/mode, timestamps |

## ScriptScene

`sceneId`, `order`, `durationMs`, `narration`, `onScreenText`, `visualDescription`, `shotType`, `cameraDirection`, `bRollSuggestion`, `transition`, `notes`

## DurationRequest

- `ShortForm` → 30s
- `MediumForm` → 60s
- `LongForm` → 120s
- `Explicit(durationMs)`

Default tolerance: ±15%. Outside tolerance → validation **error** (not silent accept).

## Languages

`en`, `bn`, `hi` via `ScriptLanguageRegistry` (extensible).
