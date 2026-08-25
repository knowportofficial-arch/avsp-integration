# AVSP Contract Map

**Phase:** 1 — Audit Only (read-only)  
**Date:** 2026-08-25  
**Authority:** Repository source + module docs  
**No bridges implemented.**

```
ENVIRONMENT LIMITATION
- .cursor/install.sh unavailable in the audited environment
- .cursor/start.sh unavailable in the audited environment
- therefore affected runtime checks cannot be treated as module failures
- environment was NOT modified during Phase 1
```

Compatibility: **compatible** | **incompatible** | **unknown**

---

## Contract traces

### 1. M1 → M2 → M3

| | |
|--|--|
| **SOURCE** | M1 project + storage; M2 `ScriptGenerationRequest` |
| **Produced** | `ScriptPackage` → `generated/script/script.json`; `ScriptNarrationHandoff` |
| **Consumer** | M3 `AudioRepository` / `ScriptToTtsContract` |
| **Compatibility** | **compatible** (same process) |
| **Evidence** | `ScriptToTtsContract.kt`, `AudioRepository`, M3 tests in-tree |
| **Future bridge** | none for in-app path |

### 2. M3 → M4

| | |
|--|--|
| **SOURCE** | M3 `AudioToVideoHandoff` + per-segment WAVs + `voice.json` |
| **Produced** | segment `relativeAudioPath` list; **no** `voice.mp3` written |
| **Expected by M4** | single `audio_path` file; optional `script` dict; `media[]` |
| **Compatibility** | **incompatible** (shape) |
| **Evidence** | `AudioToVideoContract.kt`; M1 `ArtifactNames.VOICE_MP3`; `VideoEngine.render(audio_path=...)` |
| **Future bridge** | concat/export WAVs → one narration file; map handoff → `render()` (**do not implement now**) |

Special notes:
- **audio_path vs segment WAVs:** incompatible without adapter  
- **voice.mp3:** declared by M1, not produced by M3 → **incompatible** name contract  

### 3. M4 → M8

| | |
|--|--|
| **SOURCE** | `VideoEngine` in `M4/` **or** `M8/m8/vendor/m4/` (byte-identical tracked sources) |
| **M8 expected** | `M4Adapter` loads **`vendor/m4`**; docs also describe `VideoEngine.render` |
| **Live M8 path** | `EffectsComposer.compose` → `projects/<id>/render/final.mp4` |
| **Compatibility** | Adapter API **compatible** with vendored M4; **live path bypasses** M4 → **incompatible with stage name / architecture docs** |
| **Evidence** | `m4_adapter.py`; `controller.py` `_render` uses `EffectsComposer` |
| **Future bridge** | clarify dual-renderer policy; optional use of `M4Adapter` for simple jobs (**docs/adapter only later**) |

**final MP4 paths:**
- M4 default: `output/{project_id}_final.mp4`  
- M8 live: `projects/<id>/render/final.mp4`  
- M1 constant: `generated/video/final.mp4`  
→ **incompatible** conventions without a path remapper  

### 4. M5 → M2

| | |
|--|--|
| **SOURCE** | `YouTubeOutput` / `ScreenOutput` (`M2_HANDOFF.md`) |
| **Produced** | topics, hooks, transcript/text, confidence, … |
| **Expected by M2** | `ScriptGenerationRequest` (topic, instructions, …) — **no M5 fields** |
| **Compatibility** | **incompatible** / unwired |
| **Evidence** | M5 dataclasses; M2 `ScriptContracts.kt`; no importer |
| **Future bridge** | map M5 JSON → request fields (**not now**) |

**M5 sample vs runtime:**
- `sample_outputs/*.json` use alternate wrapper (`input_type`, `data`, …)  
- Runtime `to_dict()` matches `M2_HANDOFF.md`  
→ samples **incompatible** with runtime; prefer runtime (**NEEDS FIX** docs/samples later)  

### 5. M6 → M7

| | |
|--|--|
| **SOURCE** | M6 capture URIs / guided sidecars |
| **Consumer** | M7 `MediaRepository` / dataset ingest (in M7 app tree) |
| **Compatibility** | **compatible** for mission/save path in M7; guided→dataset auto-ingest **unknown**/partial |
| **Evidence** | M7 supersets M6 capture sources; guided storage may not call `ingestMedia` |
| **Future bridge** | ensure guided clips enter dataset before export |

### 6. M7 → M8

| | |
|--|--|
| **SOURCE** | `DatasetAutomationContract.snapshot` → `MediaSummary(clipId, fileUri, qualityScore, …)` |
| **Expected by M8** | JSON with `path`/`filePath`/`uri`, `quality_score`/`score`, `id`/`asset_id` (`m7_adapter.py`) |
| **Compatibility** | **incompatible** field names; **missing** file export; `content://` **incompatible** with Windows paths |
| **Evidence** | `DatasetAutomationContract.kt`; `M7Adapter._load`; no Android writer for `m7_snapshot.json` |
| **Future bridge** | serialize + copy media + remap paths (**not now**) |

Attention items:
| Android | M8 | Status |
|---------|-----|--------|
| `fileUri` | `path` / `filePath` / `uri` | incompatible |
| `qualityScore` | `quality_score` / `score` | incompatible |
| `clipId` | `id` / `asset_id` | incompatible |
| on-disk snapshot | required by M8 offline | **MISSING** |

### 7. M8 → M9

| | |
|--|--|
| **SOURCE** | `projects/<id>/render/final.mp4` + research/script metadata |
| **Expected by M9** | `PublishingJobCreate(project_id, video_path, title, …)` |
| **Compatibility** | artifacts **compatible in principle**; wiring **MISSING** |
| **Evidence** | M8 layout; `M9_API_CONTRACTS.md`; no importer |
| **Future bridge** | thin job creator from M8 report (**not now**) |

---

## Cross-cutting filesystem / bundle conventions

| Concern | Status | Evidence |
|---------|--------|----------|
| Shared Android↔Windows project bus | **MISSING** | no transfer tool/protocol in repo |
| media/project bundle convention | **MISSING** / undefined | dual apps + dual path layouts |
| M8 input `request.json` | defined | `projects/<id>/input/request.json` |
| M7 export capability | **MISSING** file writer | Kotlin API only |
| M9 publishing input | defined | `PublishingJobCreate` |

---

## Compatibility summary

| Link | Status |
|------|--------|
| M1→M2→M3 | compatible |
| M3→M4 | incompatible |
| M4↔vendor/m4 sources | equivalent (identical) |
| M4Adapter↔live M8 render | docs/stage incompatible; live uses EffectsComposer |
| M5→M2 | incompatible / MISSING |
| M5 samples→runtime | incompatible |
| M6→M7 | mostly compatible (same app lineage) |
| M7→M8 | incompatible / MISSING export |
| M8→M9 | unwired (MISSING bridge) |
| *→M10 | MISSING module |
