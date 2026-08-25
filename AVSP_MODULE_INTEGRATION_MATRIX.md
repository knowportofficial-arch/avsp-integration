# AVSP Module Integration Matrix

**Phase:** 1 — Audit Only (read-only)  
**Date:** 2026-08-25  
**Authority:** Actual repository files under `/workspace`  
**Allowed edits this phase:** this document set only  

Classification: **PASS** | **NEEDS FIX** | **MISSING** | **FUTURE** | **ENVIRONMENT LIMITATION**

---

## Environment limitation (applies to all runtime rows)

```
ENVIRONMENT LIMITATION
- .cursor/install.sh unavailable in the audited environment
- .cursor/start.sh unavailable in the audited environment
- therefore affected runtime checks cannot be treated as module failures
- environment was NOT modified during Phase 1
```

Evidence: workspace `.cursor/` absent; `start-user.status=127`; log `bash: .cursor/start.sh: No such file or directory`; dashboard `setup_failed`.

---

## 1. Master matrix M1–M10

| ID | Source location | Platform | Lang | Build | Classification | Integration readiness | Evidence |
|----|-----------------|----------|------|-------|----------------|----------------------|----------|
| M1 | `CURRENT_M1_M3/` (`com.avsp.pro`) | Android | Kotlin | Gradle `:app` | **PASS** | Ready in-app | Room, nav, storage, contracts |
| M2 | `CURRENT_M1_M3/.../script/` | Android | Kotlin | same app | **PASS** | Ready → M3 | `ScriptPackage`, handoff |
| M3 | `CURRENT_M1_M3/.../audio/` | Android | Kotlin | same app | **PASS** | Bridge to M4/M8 **MISSING** | WAV segments + `voice.json` |
| M4 | `M4/AVSP_M4_Video_Engine/` | Desktop Python | Python | none (script) | **PASS** | Dual-tree with vendor; **no canonical pick** | 10 tracked files |
| M4v | `M8/m8/vendor/m4/` | Desktop Python | Python | vendored | **PASS** (identity) | M8 `M4Adapter` load path | byte-identical to M4 tracked set |
| M5 | `M5/m5_youtube_screen_input/` | Desktop Python | Python | pip reqs | **NEEDS FIX** | Bridge to M2 **MISSING** | samples ≠ runtime |
| M6 | `M6/android/` (`com.avsp.creator`) | Android | Kotlin | Gradle | **PASS** | Superseded-in-tree by M7 for dataset | capture |
| M7 | `M7/android/` (`com.avsp.creator`) | Android | Kotlin | Gradle | **NEEDS FIX** | Export to M8 **MISSING** | APIs exist; no JSON writer |
| M8 | `M8/m8/` | Desktop Python | Python | none | **NEEDS FIX** | Core Windows pipeline; bridges incomplete | EffectsComposer live path |
| M9 | `M9/m9/` | Desktop Python | Python | none | **PASS** | Bridge from M8 **MISSING** | mock publish |
| M10 | *(absent)* | — | — | — | **MISSING** | — | `M10/` not in repo |

---

## 2. Per-module structural facts

### M1–M3 — `CURRENT_M1_M3/`
| Item | Evidence |
|------|----------|
| Entry | `AvspApplication`, `MainActivity` |
| Build | `settings.gradle.kts` → `:app`; AGP/Kotlin Gradle |
| Deps | Room, Compose, WorkManager, security-crypto |
| Tests | `app/src/test/java/com/avsp/pro/**` |
| Inputs | Project create; script request; TTS request |
| Outputs | `generated/script/script.json`; `generated/audio/**/*.wav` + `voice.json` |
| Paths | `filesDir/projects/<id>/…` |
| Standalone? | Single Android app embedding M1+M2+M3 |

### M4 — `M4/AVSP_M4_Video_Engine/`
| Item | Evidence |
|------|----------|
| Entry | `VideoEngine.render`, CLI `main` in `video_engine.py` |
| Build | none; `requirements.txt` = `Pillow>=10.0.0` |
| Runtime | `ffmpeg`, `ffprobe` |
| Tests | `tests/test_video_engine.py` |
| Referenced by | Standalone tests/CLI under this tree |
| Duplicate | See vendor comparison — tracked sources identical |

### M5 — `M5/m5_youtube_screen_input/`
| Item | Evidence |
|------|----------|
| Entry | `YouTubeIngestor`, `ScreenIngestor` (no `main.py`) |
| Deps | yt-dlp, opencv, pytesseract, … + system Tesseract |
| Tests | `tests/`, `test_runner.py` |
| Outputs | `YouTubeOutput` / `ScreenOutput` (`M2_HANDOFF.md`) |
| Orphaned? | Not imported by M8/M9; not wired to Android M2 |

### M6 — `M6/android/`
| Item | Evidence |
|------|----------|
| Entry | `MainActivity`, `GuidedCaptureActivity` |
| Outputs | `AVSP/Guided/.../{clip.mp4,metadata.json,thumbnail.jpg}` |
| Relation | Capture sources identical inside M7 tree |

### M7 — `M7/android/`
| Item | Evidence |
|------|----------|
| Entry | same UI + `DatasetAutomationContract`, `MediaSelectionApi` |
| Outputs | In-memory snapshot; Room enrichment; **no** `m7_snapshot.json` writer found |
| Consumer | Logical M8 `M7Adapter` |

### M8 — `M8/m8/`
| Item | Evidence |
|------|----------|
| Entry | `main.py`, `AutonomousProductionController` |
| Live render | `EffectsComposer.compose` (stage name `call_m4_renderer`) |
| M4 load | `M4Adapter(.../vendor/m4)` constructed; not used on happy path |
| Layout | `projects/<id>/{input,research,media,edl,timeline,render,qc,logs}/` |
| Output | `render/final.mp4` |

### M9 — `M9/m9/`
| Item | Evidence |
|------|----------|
| Entry | `main.py` CLI; `PublishingController` |
| Input | `PublishingJobCreate` (`video_path`, metadata, platforms) |
| Orphaned? | No code import of M8; expects external `video_path` |

### M10
**M10 = MISSING** — no `/workspace/M10` directory.

---

## 3. Test matrix (system Python / existing Gradle cache — no installs)

| Module | Result category | Detail | Failure class if any |
|--------|-----------------|--------|----------------------|
| M1–M3 `testDebugUnitTest` | **PASS** | BUILD SUCCESSFUL (gradle UP-TO-DATE cache present) | — |
| M4 A–H + landscape | **PASS** ×7; **FAIL** ×2 | B,F need Pillow | **B** missing dependency (Pillow); attributable to env not provisioning pip deps → also **ENVIRONMENT LIMITATION** context |
| M5 YouTube subset | **PASS** (partial suite ran) | 7 YouTube-related tests executed | — |
| M5 Screen suite | **NOT RUN — ENVIRONMENT LIMITATION** | `ImportError: cv2` at collection | **B**/D — opencv not installed; env install failed |
| M8 unittest | **PASS** ×43; **FAIL** ×2 | punch clip paths missing | **C** missing fixture/media (`assets/punch_library/clips/*.mp4`) |
| M9 pytest | **NOT RUN — ENVIRONMENT LIMITATION** | `No module named pytest` | **B**/D — pytest not installed; no pip install performed |
| Full certified CI on healthy env | **NOT RUN — ENVIRONMENT LIMITATION** | `.cursor/install.sh` / `start.sh` missing | **D** |

**Rule applied:** missing pip packages after failed install are **not** reported as module logic defects. M8 punch failures are **C** (repo fixtures), not environment.

---

## 4. Dependencies between modules (structural)

```
M1 → M2 → M3          (in-process Android)
M5 ╌?→ M2             (MISSING bridge)
M6 → M7               (same Creator lineage; M7 supersets)
M7 ╌?→ M8             (MISSING export)
M3 ╌?→ M4/M8          (MISSING audio bridge)
M4 ≡ vendor/m4        (identical tracked sources; dual location)
M8 → EffectsComposer  (live)
M8 ╌?→ M9             (MISSING bridge)
*  ╌?→ M10            (MISSING module)
```

---

## 5. Integration readiness scorecard

| Item | Status |
|------|--------|
| M1–M3 in-app | PASS |
| M4 engine sources | PASS |
| M4 dual-tree identity | PASS (identical); canonical **not established** |
| M5 code | PASS / samples NEEDS FIX |
| M5→M2 | MISSING |
| M6 | PASS |
| M7 APIs | PASS |
| M7→M8 export | MISSING / NEEDS FIX |
| M8 pipeline | NEEDS FIX (fixtures + stage naming) |
| M8→M9 | MISSING |
| M9 | PASS |
| M10 | MISSING |
| Android↔Windows bus | MISSING |
| Environment install/start | **ENVIRONMENT LIMITATION** |
