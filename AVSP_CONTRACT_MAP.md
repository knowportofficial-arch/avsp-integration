# AVSP Contract Map

**Phase:** 1 — Audit Only  
**Date:** 2026-08-25  
**Authority:** Actual repository source + module docs  
**Scope:** Input/output/JSON/filesystem contracts across M1–M9 and gaps to M10.

Classification: **PASS** | **NEEDS FIX** | **MISSING** | **FUTURE**

---

## 1. Contract Inventory by Boundary

| From → To | Contract artifact | Owner format | Consumer format | Status |
|-----------|-------------------|--------------|-----------------|--------|
| M1 → M2/M3 | Project + storage paths | Kotlin Room + `ProjectPaths` | Same process | **PASS** |
| M2 → M3 | `ScriptNarrationHandoff` / `script.json` | Kotlin + Gson JSON | M3 `AudioRepository` | **PASS** |
| M3 → M4 | `AudioToVideoHandoff` | Kotlin segments + WAV paths | M4 `audio_path` (single file) | **NEEDS FIX** |
| M1 artifact names → M3 | `voice.mp3` + `voice.json` | `ArtifactNames` | M3 writes `voice.json` + WAVs only | **NEEDS FIX** |
| M5 → M2 | `YouTubeOutput` / `ScreenOutput` | Python dict (`M2_HANDOFF.md`) | M2 `ScriptGenerationRequest` | **MISSING** |
| M5 samples → M5 runtime | `sample_outputs/*.json` | Alternate schema | Dataclass `to_dict()` | **NEEDS FIX** |
| M6 → M7 | Room media URI + clip sidecars | Same app (M7 supersets M6) | Dataset ingest/analyze | **PASS** (mission path); guided path **NEEDS FIX** |
| M7 → M8 | `DatasetAutomationContract.snapshot` | Kotlin `MediaSummary` | `M7Adapter` JSON fields | **NEEDS FIX** / **MISSING** file export |
| M7 → M4 (Kotlin) | `MediaSelectionApi` | In-process Android | Not used by Python M4 | **FUTURE** / unused across platform |
| M8 timeline → M4 | `M4Adapter.timeline_to_m4_media` | `Timeline` schema | `VideoEngine.render` | **PASS** (adapter exists) |
| M8 live render | Stage `call_m4_renderer` | Name implies M4 | Calls `EffectsComposer` | **NEEDS FIX** (contract/docs drift) |
| M8 → M9 | `final.mp4` + metadata | `projects/.../render/final.mp4` | `PublishingJobCreate` | **MISSING** bridge |
| M8 QC → M10 | `qc/final_qc.json`, reports | M8 FinalQC | M10 module | **MISSING** (M10 absent) |
| M1 UI → M8/M9 | Job trigger / status | Module status stubs | Python CLIs | **MISSING** |

---

## 2. Detailed Contracts

### 2.1 M1 Project & Artifact Contract — **PASS**

**Code:** `CURRENT_M1_M3/app/src/main/java/com/avsp/pro/core/integration/IntegrationContracts.kt`

**ArtifactNames (declared):**

| Constant | Intended file |
|----------|---------------|
| `CLIP_MP4` | `clip.mp4` |
| `THUMBNAIL_JPG` | `thumbnail.jpg` |
| `METADATA_JSON` | `metadata.json` |
| `SCRIPT_JSON` | `script.json` |
| `VOICE_MP3` | `voice.mp3` |
| `VOICE_JSON` | `voice.json` |
| `FINAL_MP4` | `final.mp4` |
| `TEMPLATE_JSON` | `template.json` |

**ProjectPaths:** `originals`, `working`, `generated/script`, `generated/audio`, `generated/video`, `published`, `logs`, plus camera/media under originals.

**Room schemas:**  
`app/schemas/com.avsp.pro.database/1.json`,  
`app/schemas/com.avsp.pro.database.AvspDatabase/2.json`  
Tables: `projects`, `module_status`, `settings`, `logs`, `media_assets`.

---

### 2.2 M2 Script Package — **PASS**

**Code:** `com.avsp.pro.script.contract.ScriptContracts.kt`  
**Docs:** `CURRENT_M1_M3/documentation/M2_DATA_CONTRACT.md`

**Input — `ScriptGenerationRequest`:**

| Field | Notes |
|-------|-------|
| `projectId`, `topic`, `languageCode` | required |
| `duration` | Short 30s / Medium 60s / Long 120s / Explicit |
| `aspectRatioLabel` | default `9:16` |
| `contentType`, `audience`, `platform` | enums |
| `userInstructions`, `factualRequirements` | optional |
| `durationToleranceRatio` | default 0.15 |

**Output — `ScriptPackage` (version `1.0`):** title, scenes, hook/intro/cta/ending, duration fields, validation, metadata → `generated/script/script.json`.

**Handoff → M3 — `ScriptNarrationHandoff`:**

| Field | Type |
|-------|------|
| `projectId`, `scriptId`, `language`, `title` | string |
| `segments[]` | sceneId, order, narration, language, durationMs, pauseAfterMs |
| `totalNarrationDurationMs`, `scriptVersion` | |

---

### 2.3 M3 Audio Package & M3→M4 Handoff — **NEEDS FIX**

**Code:** `AudioToVideoContract.kt`, audio package contracts  
**Docs:** `documentation/M3_DATA_CONTRACT.md`, `M3_INTEGRATION_CONTRACT.md`

**Output filesystem:**

```
generated/audio/{audioPackageId}/{segmentId}.wav
generated/audio/{audioPackageId}/package.json
generated/audio/voice.json          # canonical AudioPackage pointer
# voice.mp3 — DECLARED BY M1, NOT PRODUCED
```

**Audio format:** WAV, pcm_s16le, 16 kHz, mono.

**Handoff — `AudioToVideoHandoff`:**

| Field | Notes |
|-------|-------|
| `projectId`, `scriptId`, `audioPackageId` | |
| `language`, `provider`, `voiceId` | |
| `totalDurationMs` | |
| `segments[].relativeAudioPath` | per-scene WAV |
| `segments[].startMs/endMs/durationMs` | timeline |

**M4 consumer expectation:** single `audio_path` file (wav/mp3/…) plus optional `script` dict / `media[]`.

```
ISSUE: M3 emits segment WAVs + Kotlin handoff; M4 expects one narration file path.
ROOT CAUSE: Contracts designed separately; no concat/export adapter.
IMPACT: Cannot feed Android TTS output into M4/M8 without a bridge.
SEVERITY: HIGH
RECOMMENDED FIX: Thin adapter that concatenates segment WAVs (or ffmpeg concat) into narration.wav/mp3 and maps handoff → VideoEngine.render(audio_path=...). Do not rewrite M3 or M4 engines.
TEST REQUIRED: Golden project: M3 package → adapter → M4 render duration matches totalDurationMs ± tolerance; audio stream present.
```

```
ISSUE: ArtifactNames.VOICE_MP3 promised but never written.
ROOT CAUSE: M3 chose WAV packages; M1 constant not updated.
IMPACT: Any consumer looking for voice.mp3 fails.
SEVERITY: MEDIUM
RECOMMENDED FIX: Either emit optional voice.mp3 pointer in adapter, or update integration docs/constants in a later phase (prefer adapter alias first).
TEST REQUIRED: Assert adapter output path exists and is listed in export manifest.
```

---

### 2.4 M4 Render Contract — **PASS**

**Code:** `M4/AVSP_M4_Video_Engine/app/engines/video_engine.py`  
**Identical copy:** `M8/m8/vendor/m4/...`

**`VideoEngine.render(...)` inputs:**

| Param | Shape |
|-------|-------|
| `media[]` | `{path, duration?, start?, end?}` or `{color, duration}` |
| `audio_path` | narration media file |
| `script` | optional dict (scenes/audio/subtitles/music/intro/outro) |
| `subtitles[]` | `{start, end, text}` |
| `template` | `default_shorts` \| `default_landscape` \| path \| dict |
| `music_path`, `intro`, `outro`, `force_duration` | optional |

**Outputs:**

| Artifact | Fields |
|----------|--------|
| MP4 | H.264 + AAC, template WxH, `+faststart` |
| RenderResult / `*_render.json` | project_id, file, width, height, fps, duration, codecs, status, error, progress, render_time_sec |

**Templates:** `templates/default_shorts.json` (9:16), `default_landscape.json` (16:9).  
**Note:** template keys `logo` / `text_overlay` are schema-only (unread by engine) — **FUTURE**/dead fields, not blocking.

---

### 2.5 M5 → M2 Handoff — **MISSING** (wiring) / runtime schema **PASS**

**Authoritative doc:** `M5/m5_youtube_screen_input/M2_HANDOFF.md`

**YouTubeOutput fields:**  
`source`, `url`, `metadata{title,duration,channel,views,likes,description,upload_date,tags}`, `transcript`, `segments[{text,length,sentences}]`, `topics`, `hooks`, `structured_content{...}`, `confidence`, `processed_at`

**ScreenOutput fields:**  
`source`, `text`, `numbers`, `confidence`, `frames_processed`, `headings`, `repeated_text`, `processed_at` (+ `lang`, `adapter`, `is_mock` for image path)

```
ISSUE: M2 ScriptGenerationRequest has no field/path to ingest M5 JSON.
ROOT CAUSE: M5 and M2 shipped as separate packages; no adapter.
IMPACT: YouTube/screen research cannot drive script generation automatically.
SEVERITY: HIGH (for research-assisted flow); MEDIUM for topic-only MVP
RECOMMENDED FIX: Adapter mapping M5 topics/hooks/transcript → ScriptGenerationRequest.userInstructions / factualRequirements (and optional topic). Keep MockScriptGenerator; do not rewrite M2.
TEST REQUIRED: Fixture youtube_input.json → adapter → ScriptPackage contains topic-derived content; confidence gate rejects low-confidence inputs.
```

```
ISSUE: sample_outputs/youtube_input.json and screen_input.json diverge from runtime to_dict().
ROOT CAUSE: Samples use alternate wrapper (input_type, data{}, m5_format_version).
IMPACT: Integrators copying samples will build wrong consumers.
SEVERITY: MEDIUM
RECOMMENDED FIX: Regenerate samples from YouTubeOutput/ScreenOutput.to_dict() in a later phase; mark samples non-authoritative now.
TEST REQUIRED: Golden compare sample == dataclass dump.
```

---

### 2.6 M6 Capture Sidecar — **PASS**

**Code:** `GuidedCaptureStorage` / `ClipMetadata` (M6 & M7)

**Filesystem:**

```
<externalFiles>/AVSP/Guided/<templateId>/<clip_id>/
  clip.mp4
  metadata.json
  thumbnail.jpg
```

**metadata.json fields:** clip_id, category, date/time, duration_ms, width/height/fps/orientation, requested_* vs actual_*, verification flags, geo, device, file.

Aligns with M1 artifact *names* but **not** M1 path layout (`originals/camera`).

---

### 2.7 M7 Dataset / Quality — **PASS** (in-app) / export **NEEDS FIX**

**Docs:** `M7/M7_INTEGRATION_CONTRACTS.md`

**QualityResult:**

```json
{
  "score": 0.86,
  "blur": false,
  "exposure": true,
  "composition": true,
  "face_quality": 0.91,
  "closed_eye": false,
  "duplicate": false,
  "recommendation": "KEEP"
}
```

**Bands:** KEEP ≥ 0.70; REVIEW ≥ 0.40; else RETAKE (duplicates/analysisFailed → REVIEW).

**Kotlin MediaSummary:** `clipId`, `category`, `mediaType`, `qualityScore`, `recommendation`, `isBestShot`, `tags`, `orientation`, `fileUri`.

**M8 M7Adapter accepted keys:**

| Concept | Android | M8 accepts |
|---------|---------|------------|
| id | `clipId` | `id`, `asset_id` |
| path | `fileUri` | `path`, `filePath`, `uri` (**not** `fileUri`) |
| score | `qualityScore` | `quality_score`, `score`, `quality.score` |
| type | `mediaType` | `type`, `mediaType` |
| recommendation | same | same |

```
ISSUE: No Android writer for m7_snapshot.json; field names incompatible with M8 adapter.
ROOT CAUSE: M7 exposes in-process Kotlin APIs; M8 was built against a file snapshot vocabulary.
IMPACT: Live M7 library cannot drive M8 media selection without manual JSON crafting.
SEVERITY: HIGH
RECOMMENDED FIX: Export adapter (on Android or host-side) that serializes DatasetSnapshot → M8 vocabulary and copies media to Windows-readable paths. Do not change M7 scoring engine or M8 selection policy.
TEST REQUIRED: Export fixture from MediaSummary → M7Adapter.load → find_keepable returns expected KEEP assets with non-empty paths.
```

---

### 2.8 M8 Pipeline Artifacts — **PASS** (layout) / render stage **NEEDS FIX**

**Docs:** `M8/m8/docs/M8_EDL_SCHEMA.md`, `M8_TIMELINE_SCHEMA.md`, `M8_INTEGRATION_CONTRACT.md`

**Project tree:**

```
projects/<project_id>/
  input/request.json
  research/{plan,script,scene_plan}.json
  media/m7_snapshot.json
  edl/creative_edl.json
  edl/decision_source.txt
  timeline/timeline.json
  render/final.mp4
  render/render.json
  render/fx_work/
  qc/final_qc.json
  qc/FINAL_QC_REPORT.md
  qc/visual_verification.json
  qc/frames/
  logs/pipeline_report.json
```

**request.json:** `project_id`, `topic`, `duration` (`5m`|`30s`|…), `format` (`9:16`|`16:9`|…).

**CreativeEDL / Timeline:** see schema docs; CTA duration must be 5.0 ± 0.15s.

```
ISSUE: Stage name call_m4_renderer invokes EffectsComposer, not M4Adapter.render().
ROOT CAUSE: EffectsComposer required for punch/xfade/SFX/emoji/captions beyond M4 scope; stage name retained.
IMPACT: Integrators may expect M4 output contracts (output/*_final.mp4) but receive EffectsComposer paths (render/final.mp4).
SEVERITY: MEDIUM
RECOMMENDED FIX: Document dual-renderer policy; keep both; optionally rename stage later. Do not delete vendor/m4.
TEST REQUIRED: Pipeline produces render/final.mp4 with ffprobe video+audio; adapter unit tests for M4 path remain green.
```

```
ISSUE: punch_library.json references clips/punch_01|02|03.mp4 which are absent; 2 M8 tests fail.
ROOT CAUSE: Binary assets not packaged in repo.
IMPACT: Incomplete creative effects verification; CI red.
SEVERITY: MEDIUM
RECOMMENDED FIX: Supply minimal punch clips or make tests skip when missing; do not rewrite EffectsComposer.
TEST REQUIRED: test_I_punch_actual_render and test_punch_sfx_emoji_bgm_verified PASS.
```

---

### 2.9 M9 Publishing Job — **PASS**

**Docs:** `M9/m9/M9_API_CONTRACTS.md`  
**Code:** `app/schemas/publishing.py`

**PublishingJobCreate:**

| Field | Notes |
|-------|-------|
| `project_id`, `video_path`, `title`, `description` | required |
| `tags[]`, `hashtags[]` | |
| `thumbnail_path`, `category` | optional |
| `privacy` | private \| unlisted \| public |
| `scheduled_time` | ISO-8601 or null |
| `target_platforms[]` | youtube, facebook, instagram, telegram, web |
| `platform_metadata{}`, `language` | |

**Status output:** job_id, status, attempt, platform_statuses{…}, errors, timestamps.

**Storage:** `data/m9_queue.db`, `data/m9_analytics.json` (env-overridable).

```
ISSUE: No adapter maps M8 pipeline_report + final.mp4 → PublishingJobCreate.
ROOT CAUSE: Modules delivered separately.
IMPACT: Manual CLI publish only.
SEVERITY: MEDIUM
RECOMMENDED FIX: Thin bridge reading render/final.mp4 + research/script title/description/tags → create_job. Mock-first.
TEST REQUIRED: After M8 fixture project, bridge creates job; process_job with force_mock → PUBLISHED.
```

---

### 2.10 M10 QC — **MISSING**

M8 already emits QC artifacts. Dedicated M10 package (manifest) is absent.

```
ISSUE: M10/ directory missing from workspace.
ROOT CAUSE: Not delivered into integration repo yet.
IMPACT: No standalone acceptance/QC module; rely on M8 FinalQC temporarily.
SEVERITY: HIGH for “full” acceptance flow; MEDIUM if M8 QC accepted as interim
RECOMMENDED FIX: Treat M8 qc/ as interim QC; add M10 later without rewriting M8.
TEST REQUIRED: When M10 arrives — consume M8 final_qc.json + final.mp4; gate publish.
```

---

## 3. Filesystem Contract Conflicts

| Concern | Side A | Side B | Resolution approach |
|---------|--------|--------|---------------------|
| Project root | Android `filesDir/projects/<id>` | M8 `projects/<id>` | Export/sync tree; path remap |
| Final video | M1 `generated/video/final.mp4` | M8 `render/final.mp4`; M4 `output/*_final.mp4` | Canonicalize in bridge manifest |
| Narration | M3 segment WAVs + `voice.json` | M4/M8 `audio_path` | Concat adapter |
| Media URIs | Android `content://` | Windows absolute paths | Copy-out + path rewrite |
| Capture layout | M6 `AVSP/Guided/...` | M1 `originals/camera` | Import adapter |
| App identity | `com.avsp.pro` DB | `com.avsp.creator` DB | Do not merge DBs blindly |

---

## 4. JSON Schema Authority Order

When documents conflict, prefer **runtime serializers/dataclasses** over samples/docs:

1. Kotlin data classes / Python dataclasses & schemas in `app/`  
2. Module `*_DATA_CONTRACT*.md` / `*_API_CONTRACTS.md` / `M2_HANDOFF.md`  
3. Example project JSON under `M8/m8/projects/`  
4. `sample_outputs/` (M5) — **non-authoritative** until regenerated  

---

## 5. Contract Status Summary

| Contract | Status |
|----------|--------|
| M1↔M2↔M3 in-process | PASS |
| M3→M4 audio | NEEDS FIX |
| voice.mp3 name | NEEDS FIX |
| M4 render API | PASS |
| M4 standalone ≡ vendor | PASS |
| M5 runtime handoff schema | PASS |
| M5 samples | NEEDS FIX |
| M5→M2 wiring | MISSING |
| M6 clip sidecar | PASS |
| M7 quality JSON | PASS |
| M7→M8 snapshot export | MISSING / NEEDS FIX |
| M8 EDL/Timeline/project layout | PASS |
| M8 renderer naming | NEEDS FIX |
| M8 punch assets | NEEDS FIX |
| M8→M9 | MISSING |
| M10 | MISSING |
| Unified path remapper Android↔Windows | MISSING |
