# M1 Data Contracts

Stable contracts under `com.avsp.pro.core.contracts` / `core.module` / `core.error`.

## Project

| Field | Type |
|-------|------|
| projectId | String |
| name | String |
| description | String |
| createdAt / updatedAt | Long |
| status | DRAFT \| PROCESSING \| READY \| FAILED \| COMPLETED |
| duration | Long? |
| aspectRatio | 9:16 \| 16:9 |
| language | bn \| en \| hi |
| outputPath | String? |
| metadata | Map\<String,String\> |

## Other contracts

- `MediaAsset` — generic media reference
- `ScriptReference` — M2 will produce `script.json`
- `AudioAsset` — M3 will produce `voice.mp3` / `voice.json`
- `VideoAsset` — M4 will produce `final.mp4`
- `PublishingReference` — M9 publishing handoff
- `ModuleStatus` — moduleId, version, status, lastUpdated, error
- `ErrorInfo` — code, message, module, severity, timestamp, details, recoverable

## Artifact status

`PENDING | READY | PROCESSING | SUCCESS | FAILED`

M1 does not fabricate generated scripts/audio.
