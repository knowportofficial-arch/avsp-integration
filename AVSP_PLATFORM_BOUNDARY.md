# AVSP Platform Boundary

**Phase:** 1 — Audit Only (read-only)  
**Date:** 2026-08-25  

```
ENVIRONMENT LIMITATION
- .cursor/install.sh unavailable in the audited environment
- .cursor/start.sh unavailable in the audited environment
- therefore affected runtime checks cannot be treated as module failures
- environment was NOT modified during Phase 1
```

**Product rule (from `MASTER_PROJECT_PROMPT.txt`):**  
Android = lightweight UI/control/capture. Windows = heavy AI/video/FFmpeg/automation/publishing.

---

## 1. Assignment from actual code locations

### ANDROID (evidence)

| Module | Location | Evidence of Android |
|--------|----------|---------------------|
| M1 | `CURRENT_M1_M3/` | `AndroidManifest.xml`, Gradle, Kotlin Compose |
| M2 | same app `com.avsp.pro.script` | Kotlin UI + Gson scripts |
| M3 | same app `com.avsp.pro.audio` | Android TTS / mock engines |
| M6 | `M6/android/` `com.avsp.creator` | CameraX, GuidedCapture |
| M7 | `M7/android/` `com.avsp.creator` | Dataset APIs on device |

### WINDOWS / DESKTOP PYTHON (evidence)

| Module | Location | Evidence |
|--------|----------|----------|
| M4 | `M4/AVSP_M4_Video_Engine/` (+ identical `M8/m8/vendor/m4/`) | Python + FFmpeg CLI |
| M5 | `M5/m5_youtube_screen_input/` | yt-dlp, Tesseract, OpenCV |
| M8 | `M8/m8/` | Python pipeline, EffectsComposer FFmpeg |
| M9 | `M9/m9/` | Python publishers / queue |

M10: **MISSING** (would be desktop QC if added).

---

## 2. Responsibilities

### Android should own (and does, in-repo)
- Project UX / settings / encrypted slots (M1)
- Script generation UI (M2)
- TTS preview/approval (M3)
- Camera / guided capture (M6)
- Local quality KEEP/REVIEW/RETAKE (M7)

### Windows/Python should own (and does, in-repo)
- FFmpeg assembly / effects (M4, M8 EffectsComposer)
- YouTube + OCR ingest (M5)
- Autonomous EDL/timeline/AI director (M8)
- Publishing APIs (M9)

### Must not move to Android
- Full FFmpeg EffectsComposer / M4 engine  
- yt-dlp desktop OCR stack  
- M9 OAuth upload engines  

---

## 3. Cross-platform transport

| Question | Finding | Status |
|----------|---------|--------|
| Shared project directory protocol? | Not defined in code | **MISSING** |
| Export/import tool Android→Windows? | Not present | **MISSING** |
| Path remapper `content://` → Windows files? | Not present | **MISSING** |
| Job queue Android→M8/M9? | Only M1 stubs / status rows | **MISSING** |
| Dual Android apps share storage? | `com.avsp.pro` ≠ `com.avsp.creator` | **MISSING** / split |

**Conclusion:** An actual Android ↔ Windows artifact/project transport mechanism **does not exist**. Documented as **missing integration boundary**. Not created in this phase.

---

## 4. Boundary scorecard

| Item | Status |
|------|--------|
| Android light / Windows heavy (code placement) | **PASS** |
| Heavy engines kept off Android | **PASS** |
| Cross-platform bus | **MISSING** |
| Unified single Android APK | **FUTURE** |
| Environment install/start | **ENVIRONMENT LIMITATION** |

---

## 5. Issues (boundary)

```
ISSUE: No Android↔Windows project/artifact bus.
ROOT CAUSE: Modules delivered as isolated packages.
IMPACT: End-to-end multi-device production cannot run without manual copy.
SEVERITY: CRITICAL for full product; HIGH for integration program
RECOMMENDED FIX: Later — define bundle + export (Phase 2+). Do not implement in Phase 1.
TEST REQUIRED: After bridge exists — export from M7 (+ optional M3) imports into M8 project_id.
```
