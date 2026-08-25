# AVSP Module Integration Matrix

**Phase:** 1 — Audit Only  
**Date:** 2026-08-25  
**Authority:** Actual repository files under `/workspace`  
**Rule:** No source modifications in this phase. Environment install failure is **not** repaired here (see `AVSP_INTEGRATION_AUDIT.md` §0).

Classification legend:

| Status | Meaning |
|--------|---------|
| **PASS** | Complete, tested, usable as reference; integrate via adapters only |
| **NEEDS FIX** | Exists but has contract drift, incomplete wiring, or test gaps before integration |
| **MISSING** | Required for end-to-end flow but absent from the workspace |
| **FUTURE** | Documented placeholder / reserved; not required for first integration slice |

---

## 1. Master Module Matrix

| ID | Location | Purpose | Platform | Language | Status | Tests (this host) | Depends on | Consumed by |
|----|----------|---------|----------|----------|--------|-------------------|------------|-------------|
| **M1** | `CURRENT_M1_M3/` (`com.avsp.pro`) | Core shell: projects, Room DB, storage, settings, nav, module registry | Android | Kotlin + Compose | **PASS** | 69/69 unit (shared app) | — | M2, M3 (in-app) |
| **M2** | `CURRENT_M1_M3/` (`com.avsp.pro.script`) | Script AI: topic → `ScriptPackage` JSON | Android | Kotlin | **PASS** | included in 69 | M1 | M3; logical M5 consumer (unwired) |
| **M3** | `CURRENT_M1_M3/` (`com.avsp.pro.audio`) | TTS/audio: script → WAV segments + `voice.json` | Android | Kotlin | **PASS** (artifact name drift → see contracts) | included in 69 | M1, M2 | Logical M4/M8 (unwired) |
| **M4** | `M4/AVSP_M4_Video_Engine/` | FFmpeg video engine/assembler | Desktop/Windows Python | Python 3 | **PASS** | 9/9 opportunistic | FFmpeg, Pillow | Also present as identical tree under M8 vendor (no canonical pick) |
| **M4-vendored** | `M8/m8/vendor/m4/` | Byte-identical tree to `M4/…` (M8 load path) | Desktop Python | Python 3 | **PASS** (identity) | covered via M8/M4 tests | same as M4 | `M4Adapter` loads this path; live M8 render uses EffectsComposer |
| **M5** | `M5/m5_youtube_screen_input/` | YouTube + screen OCR → M2 handoff JSON | Desktop Python | Python 3 | **NEEDS FIX** (sample JSON drift) | 18 run / 0 fail / 1 skip | yt-dlp, Tesseract, OpenCV | Logical M2 (unwired) |
| **M6** | `M6/android/` (`com.avsp.creator`) | Camera / guided capture | Android | Kotlin + Compose | **PASS** | 39/39 unit | CameraX, ML Kit | M7 (superset) |
| **M7** | `M7/android/` (`com.avsp.creator`) | Personal dataset + quality (KEEP/REVIEW/RETAKE) on top of M6 | Android | Kotlin | **NEEDS FIX** (no file export for M8) | 63/63 unit | M6 capture tree | Logical M8 (manual only) |
| **M8** | `M8/m8/` | Autonomous production pipeline → `final.mp4` + QC | Desktop/Windows Python | Python 3.10+ | **NEEDS FIX** (2 tests fail; M4 stage unused) | 45 / **2 fail** | FFmpeg; vendored M4; M7 snapshot JSON | Logical M9 |
| **M9** | `M9/m9/` | Multi-platform publishing queue | Desktop Python | Python 3 | **PASS** | 32/32 | optional platform SDKs | End of publish path |
| **M10** | *(absent)* | QC / final acceptance (manifest + instructions) | TBD | TBD | **MISSING** | N/A | M8 final + reports | Operator / CI |

---

## 2. Per-Module Detail

### M1 — Core & UI

| Field | Finding |
|-------|---------|
| **Purpose** | Application shell: project CRUD, Room, storage layout, encrypted settings, logs, module status |
| **Platform** | Android minSdk 26 / target 35 |
| **Language** | Kotlin 2.0.21, Jetpack Compose |
| **Entry points** | `AvspApplication`, `MainActivity`, nav destinations under `com.avsp.pro.ui` |
| **Public APIs** | `ProjectRepository`, `AvspStorage`, `SecureConfigStore`, `IntegrationContracts` stubs |
| **Inputs** | Project name/description/aspect/language; secure credential slots |
| **Outputs** | Room DB + `filesDir/projects/<id>/` layout; artifact name constants |
| **Schemas** | `app/schemas/com.avsp.pro.database*.json` (Room v1/v2) |
| **Filesystem** | `originals/`, `working/`, `generated/{script,audio,video}`, `published/`, `logs/` |
| **Dependencies** | Single Gradle `:app`; Room, Compose, WorkManager, security-crypto |
| **Tests** | `M1CoreSuiteTest` + contracts/settings/secrets scans — PASS |
| **Build** | JDK 17+, AGP 8.7.2, Gradle 8.9 |
| **Runtime** | Android API ≥ 26 |
| **Other modules** | Seeds M4–M9 status as display-only FROZEN |
| **Duplicates** | Parallel app vs `com.avsp.creator` (M6/M7) |
| **Bridges** | No Android↔Windows job/export bridge |
| **Classification** | **PASS** |

### M2 — Script AI

| Field | Finding |
|-------|---------|
| **Purpose** | Topic → structured editable `ScriptPackage` |
| **Platform / Lang** | Android / Kotlin (`com.avsp.pro.script`) |
| **Entry points** | `ScriptAiScreen`, `ScriptRepository`, `MockScriptGenerator` |
| **Public APIs** | `ScriptGenerationRequest` → `ScriptPackage`; `ScriptToTtsContract` |
| **Inputs** | topic, language (`en`/`bn`/`hi`), duration, aspect, content type |
| **Outputs** | `generated/script/{id}.json` + canonical `script.json` |
| **Handoff out** | `ScriptNarrationHandoff` → M3 |
| **Tests** | `M2ScriptAiTest` — PASS |
| **Depends on** | M1 storage/secure config |
| **Bridges** | Does **not** consume M5 `youtube_input.json` / `screen_input.json` |
| **Classification** | **PASS** (bridge to M5 = MISSING) |

### M3 — Audio / TTS

| Field | Finding |
|-------|---------|
| **Purpose** | Approved script → scene-aligned WAV package |
| **Platform / Lang** | Android / Kotlin (`com.avsp.pro.audio`) |
| **Entry points** | `AudioTtsScreen`, `AudioRepository`, Mock/Android TTS engines |
| **Public APIs** | `AudioPackage`, `AudioToVideoContract` → `AudioToVideoHandoff` |
| **Inputs** | M2 handoff / saved `script.json` |
| **Outputs** | `generated/audio/{packageId}/*.wav` + `package.json` + `voice.json` |
| **Contract note** | M1 promises `voice.mp3`; M3 does **not** write it |
| **Format** | WAV pcm_s16le 16 kHz mono segments |
| **Tests** | `M3AudioTtsTest` — PASS |
| **Bridges** | No export to M4 `audio_path` / M8 narration |
| **Classification** | **PASS** module; artifact-name drift = **NEEDS FIX** at integration layer |

### M4 — Video Engine (standalone)

| Field | Finding |
|-------|---------|
| **Purpose** | Local FFmpeg assembly: media + audio + optional subs → MP4 + render JSON |
| **Platform / Lang** | Desktop Python (Windows-target for production) |
| **Entry points** | `VideoEngine.render()`, CLI `python -m app.engines.video_engine`, planner stub `AutonomousVideoEngine` |
| **Public APIs** | `render(script, audio_path, media, template, subtitles, music_path, intro, outro, …) → RenderResult` |
| **Inputs** | media list, narration path, template name/path, optional script dict |
| **Outputs** | `{project_id}_final.mp4`, `{project_id}_render.json` under `output/` |
| **Schemas** | `templates/default_shorts.json`, `default_landscape.json` |
| **Dependencies** | system `ffmpeg`/`ffprobe`; pip `Pillow>=10` |
| **Tests** | A–H + landscape — **9/9 PASS** this host |
| **vs vendor** | **Byte-identical** to `M8/m8/vendor/m4` (10 files, matching SHA-256). **Neither declared canonical.** |
| **Classification** | **PASS** |

### M5 — YouTube / Screen Input

| Field | Finding |
|-------|---------|
| **Purpose** | Ingest YouTube URL / screen media → research JSON for M2 |
| **Platform / Lang** | Desktop Python |
| **Entry points** | `YouTubeIngestor`, `ScreenIngestor` (library; no `main.py`) |
| **Public APIs** | exports in `__init__.py`; OCR adapters Tesseract / MLKit stub |
| **Inputs** | YouTube URL; video/image paths |
| **Outputs** | `YouTubeOutput` / `ScreenOutput` dicts (see `M2_HANDOFF.md`) |
| **Schema drift** | `sample_outputs/*.json` **≠** runtime dataclasses |
| **Dependencies** | yt-dlp, OpenCV, pytesseract, system Tesseract (+ ben/hin) |
| **Tests** | 18 / 0 fail / 1 skip |
| **Bridges** | Not wired into M2 or M8 |
| **Classification** | **NEEDS FIX** (samples + bridge) |

### M6 — Camera Capture

| Field | Finding |
|-------|---------|
| **Purpose** | Guided + mission camera capture → `clip.mp4` / `metadata.json` / `thumbnail.jpg` |
| **Platform / Lang** | Android `com.avsp.creator` |
| **Entry points** | `MainActivity`, `GuidedCaptureActivity`, `CameraViewModel` |
| **Outputs** | External files `AVSP/Guided/...`; Room media rows (basic quality %) |
| **Tests** | 39/39 PASS |
| **Relation to M7** | Capture sources are identical inside M7 (M7 = M6 + dataset) |
| **Classification** | **PASS** |

### M7 — Personal Dataset

| Field | Finding |
|-------|---------|
| **Purpose** | Quality scoring, categories, KEEP/REVIEW/RETAKE, selection APIs for M4/M8 |
| **Platform / Lang** | Android `com.avsp.creator` (superset of M6) |
| **Entry points** | Same UI as M6 + `DatasetRepository`, `MediaSelectionApi`, `DatasetAutomationContract` |
| **Public APIs** | `findBestMedia`, `snapshot`, `findKeepable`, `reanalyzeAll` |
| **Outputs** | In-memory `DatasetSnapshot` / Room enrichment; thumbnails under `filesDir/thumbnails/` |
| **Gap** | **No writer** for `m7_snapshot.json`; field names ≠ M8 adapter (`fileUri` vs `path`, etc.) |
| **Tests** | 63/63 PASS |
| **Classification** | **NEEDS FIX** (export bridge) |

### M8 — Autonomous Production

| Field | Finding |
|-------|---------|
| **Purpose** | Topic → research/script/EDL/timeline → effects render → QC → `final.mp4` |
| **Platform / Lang** | Windows-first desktop Python |
| **Entry points** | `main.py` CLI; `AutonomousProductionController.run()` |
| **Public APIs** | Controller + schemas `CreativeEDL`, `Timeline`; adapters M4/M7/Pexels |
| **Project layout** | `projects/<id>/{input,research,media,edl,timeline,render,qc,logs}/` |
| **Render path** | Stage `call_m4_renderer` actually calls **`EffectsComposer`**, not `M4Adapter.render()` |
| **M4 role** | Vendored identical engine kept; adapter tested; not used on happy path |
| **M7 role** | File/JSON only via `M7Adapter` |
| **Tests** | 45 total, **2 FAILED** (missing `assets/punch_library/clips/*.mp4`) |
| **Classification** | **NEEDS FIX** |

### M9 — Publishing

| Field | Finding |
|-------|---------|
| **Purpose** | Queue + publish final MP4 to YouTube/FB/IG/Telegram/Web (mock-default) |
| **Platform / Lang** | Desktop Python |
| **Entry points** | `main.py` (`publish`, `process-queue`, `status`, …); `PublishingController` |
| **Inputs** | `PublishingJobCreate` (project_id, video_path, metadata, platforms) |
| **Outputs** | SQLite queue + analytics JSON; platform status payloads |
| **Tests** | 32/32 PASS (mock) |
| **Bridges** | No auto-ingest from M8 `pipeline_report.json` / `final.mp4` |
| **Classification** | **PASS** module; M8→M9 bridge = **MISSING** |

### M10 — QC / Acceptance

| Field | Finding |
|-------|---------|
| **Manifest** | Listed in `MODULE_MANIFEST.txt` and `INTEGRATION_INSTRUCTIONS.md` |
| **Workspace** | `/workspace/M10` **does not exist** |
| **Partial substitute** | M8 `FinalQC` + `qc/` reports; `CURRENT_M1_M3/quality/` stub README only |
| **Classification** | **MISSING** |

---

## 3. Logical vs Physical Flow

**Documented logical flow:**

```
M1 → M2 → M3 → M5? → M6/M7 → M4 → M8 → M9 → M10/QC
```

**Physical reality today:**

```
Android app A (com.avsp.pro):     M1 ──► M2 ──► M3          [isolated]
Android app B (com.avsp.creator): M6 ──► M7                  [isolated]
Desktop Python:                   M5 (orphan) | M4 ←vendor─ M8 ─?→ M9
                                  M10 absent
```

No automated cross-process bridges exist. Integration must be adapters + shared artifact directories, not merges.

---

## 4. Build / Runtime Snapshot (opportunistic host)

**Caveat:** Cloud environment install/start **failed** (`.cursor/start.sh` missing; `setup_failed`). Results below are opportunistic runs where tools happened to be present — **not** a certified green environment. Failures caused only by missing env tooling would be recorded as **ENVIRONMENT LIMITATION** (none of the rows below were blocked solely by that hook; M8’s 2 failures are missing punch media assets in-repo).

| Module | Build/Run command used | Result | Limitation note |
|--------|------------------------|--------|-----------------|
| CURRENT_M1_M3 | `bash ./gradlew testDebugUnitTest` | 69 PASS | Opportunistic; env start failed |
| M4 | `python3 tests/test_video_engine.py` (venv+Pillow) | 9 PASS | Opportunistic |
| M5 | `python test_runner.py` | 18 PASS / 1 skip | Opportunistic |
| M6 | `./gradlew testDebugUnitTest` | 39 PASS | Opportunistic |
| M7 | `./gradlew testDebugUnitTest` | 63 PASS | Opportunistic |
| M8 | `python -m unittest discover -s tests` | 43 PASS / **2 FAIL** | Failures = missing `punch_library/clips/*.mp4` (module packaging), not install hook |
| M9 | `pytest tests/test_m9_all.py` | 32 PASS | Opportunistic |
| M10 | — | **MISSING** | Folder absent |
| Env start | `bash .cursor/start.sh` | **FAIL** exit 127 | **ENVIRONMENT LIMITATION** — not repaired |

---

## 5. Integration Readiness Scorecard

| Component | Classification |
|-----------|----------------|
| M1 core/UI | PASS |
| M2 script | PASS |
| M3 audio | PASS |
| M3→M4 audio bridge | MISSING |
| M4 engine | PASS |
| M4 vs vendor/m4 identity | PASS (byte-identical; **no canonical pick**) |
| Cloud install/start script | ENVIRONMENT LIMITATION (failed; not repaired) |
| M5 ingest | PASS (code) / NEEDS FIX (samples) |
| M5→M2 bridge | MISSING |
| M6 capture | PASS |
| M7 dataset APIs | PASS (in-process) |
| M7→M8 JSON export | MISSING / NEEDS FIX |
| M8 pipeline | NEEDS FIX |
| M8 EffectsComposer as live renderer | PASS (works) / NEEDS FIX (naming/docs vs M4) |
| M8→M9 bridge | MISSING |
| M9 publishing | PASS |
| M10 QC module | MISSING |
| Unified Android app (`pro` ↔ `creator`) | FUTURE |
| End-to-end topic→publish run | MISSING |
