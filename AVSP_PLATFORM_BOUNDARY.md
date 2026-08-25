# AVSP Platform Boundary

**Phase:** 1 — Audit Only  
**Date:** 2026-08-25  
**Authority:** `MASTER_PROJECT_PROMPT.txt`, `INTEGRATION_INSTRUCTIONS.md`, actual module locations

**Established decision (do not reverse):**

> **ANDROID** = lightweight client / control / capture  
> **WINDOWS (desktop Python)** = heavy AI / video / FFmpeg / automation / publishing  

Do **not** force heavy Windows processing into Android.

**Environment note:** The Cloud Agent install/update script **failed** (missing `.cursor/start.sh`; `setup_failed`). That is an **ENVIRONMENT LIMITATION** recorded in `AVSP_INTEGRATION_AUDIT.md` §0 — it does **not** change the Android/Windows product boundary, and it was **not** repaired in this phase.

---

## 1. Platform Assignment Matrix

| Module | Runs on | Workload class | Allowed on Android? | Classification |
|--------|---------|----------------|---------------------|----------------|
| M1 Core/UI | Android | Light | Yes (canonical) | **PASS** |
| M2 Script AI | Android (mock/local) | Light–medium | Yes today; cloud AI optional | **PASS** |
| M3 TTS | Android (mock / Android TTS) | Light–medium | Yes; remote TTS optional | **PASS** |
| M4 Video Engine | Desktop Python + FFmpeg | **Heavy** | **No** | **PASS** placement |
| M5 YouTube/OCR | Desktop Python + yt-dlp + Tesseract | **Heavy** | ML Kit stub only; not full OCR pipeline | **PASS** placement |
| M6 Camera | Android | Light (device capture) | Yes (canonical) | **PASS** |
| M7 Dataset/Quality | Android local heuristics | Light–medium | Yes (canonical); no FFmpeg server | **PASS** placement |
| M8 Autonomous pipeline | Desktop Python + FFmpeg (+ optional Gemini/Pexels) | **Heavy** | **No** | **PASS** placement |
| M9 Publishing | Desktop Python + platform APIs | Medium–heavy (network/credentials) | Trigger-only from Android later | **PASS** placement |
| M10 QC | Missing; M8 QC is desktop | Heavy / analytical | **No** (when added) | **MISSING** |

---

## 2. What Belongs Where

### 2.1 Android responsibilities (keep)

- Project UX, navigation, settings, encrypted credential *slots*
- Script editing / approval UI (M2)
- TTS preview / approval UI (M3)
- Camera, guided capture, shot missions (M6)
- On-device media library, quality scores, KEEP/REVIEW/RETAKE (M7)
- Status/progress display for remote Windows jobs (**FUTURE** bridge)
- Export of artifacts to shared/transfer location (**MISSING**)

### 2.2 Windows / desktop Python responsibilities (keep)

- FFmpeg render & effects (M4, M8 EffectsComposer)
- YouTube download + Tesseract OCR (M5)
- Autonomous EDL/timeline/AI director (M8)
- Optional Gemini / Pexels network AI (M8)
- Publishing OAuth/API uploads (M9)
- Final QC probing / reports (M8 today; M10 later)
- Performance benchmark (~10 min for 5-min video reference)

### 2.3 Explicitly forbidden moves

| Anti-pattern | Why |
|--------------|-----|
| Port M4/M8 FFmpeg pipeline into Android | Violates master prompt; battery/thermal; not the reference path |
| Embed yt-dlp / desktop Tesseract stack in Android M1 | M5 already defines desktop OCR; Android has ML Kit stub only |
| Merge M9 upload credentials into Android Keystore as sole store | Publishing engines live on Windows; sync policy needed later |
| Rewrite M7 quality to call Windows synchronously from UI thread | Use export snapshot; async job later |
| Delete `vendor/m4` to “simplify” onto Android | Breaks M8 packaging and frozen reference |

---

## 3. Android ↔ Windows Boundary Requirements

### 3.1 Process model

```
┌─────────────────────────────┐         transfer          ┌──────────────────────────────┐
│  Android                    │  JSON + media files +     │  Windows / Desktop Python    │
│  com.avsp.pro  (M1–M3)      │  optional job request ──► │  M5 / M4 / M8 / M9 (/M10)    │
│  com.avsp.creator (M6–M7)   │ ◄── status + final.mp4    │                              │
└─────────────────────────────┘                           └──────────────────────────────┘
```

**Today:** no transfer bus exists. Both sides are standalone.

**Required boundary properties:**

1. **File/JSON first** — no shared memory, no embedding Python on device  
2. **Path remapping** — Android sandbox/`content://` → Windows absolute or project-relative paths  
3. **Idempotent project IDs** — same `project_id` string across export and M8/M9  
4. **Credential separation** — Android SecureConfigStore ≠ M9 `.env`; define sync later, do not merge stores blindly  
5. **Heavy work acknowledgment** — Android shows status; Windows executes  

### 3.2 Artifact exchange contract (proposed; not implemented)

| Direction | Payload | Minimum contents |
|-----------|---------|------------------|
| Android → Windows | Export bundle | `project_id`, `script.json` and/or topic, narration audio (concatenated), `m7_snapshot.json` + media files, optional M5 JSON |
| Windows → Android | Result bundle | `final.mp4`, `render.json`, `final_qc.json`, optional publish status |

Classification of current exchange: **MISSING**

### 3.3 Two Android applications

| App | Package | Modules | Role |
|-----|---------|---------|------|
| AVSP Pro | `com.avsp.pro` | M1–M3 | Control / script / TTS |
| AVSP Creator | `com.avsp.creator` | M6–M7 | Capture / dataset |

```
ISSUE: Two applicationIds and Room databases; stub folders in CURRENT_M1_M3 reserve camera/dataset but implementations live in Creator.
ROOT CAUSE: Parallel delivery tracks.
IMPACT: No single Android client for full capture→script→TTS flow.
SEVERITY: HIGH for product unification; MEDIUM for desktop-first E2E using M8 alone
RECOMMENDED FIX: Do NOT blindly merge APKs in phase 1+. Prefer: (a) keep Creator as capture exporter + Pro as control, or (b) planned multi-module Gradle merge later. First integration should use file bridges, not app merge.
TEST REQUIRED: Export from Creator + script/audio from Pro both land in same Windows project_id directory.
```

---

## 4. Per-Module Boundary Notes

### M1–M3 (Android)
- Correctly lightweight.
- Integration stubs (`VideoAssemblerInput`, `PublishingInput`) anticipate Windows engines — **PASS** intent, **MISSING** IPC.
- Reserved folders (`video/`, `youtube/`, `camera/`, …) must stay stubs — do not reimplement M4–M9 inside them.

### M4 (Desktop)
- Placement correct.
- Font discovery in engine is Linux-oriented; M8 EffectsComposer adds Windows fonts — reinforces Windows production path for captions.
- Consume via CLI/library on desktop only.

### M5 (Desktop)
- `MLKitAdapter` is Android-shaped stub inside Python package — documents future mobile OCR parity, **not** a mandate to run M5 on phone.
- Real path: Tesseract on Windows/Linux.

### M6–M7 (Android)
- Capture + local quality on device is correct.
- `MediaSelectionApi` is in-process Kotlin for on-device consumers — **not** a substitute for M8’s Python `M7Adapter`.
- Cross-boundary export is the missing piece (see Contract Map).

### M8 (Desktop)
- Entire pipeline is Windows-first (`PYTHONPATH`, bat-oriented docs).
- Vendored M4 stays on desktop.
- Must continue to ingest M7 via files, never by calling Android APIs directly.

### M9 (Desktop)
- Platform API credentials and uploads stay on Windows.
- Android may later enqueue “publish requests” as JSON only (**FUTURE**).

### M10 (Missing)
- When added, keep on Windows beside M8 QC.

---

## 5. Boundary Status Scorecard

| Boundary item | Status |
|---------------|--------|
| Android light / Windows heavy split (design) | **PASS** |
| M4/M5/M8/M9 not on Android | **PASS** |
| M6/M7 capture/dataset on Android | **PASS** |
| Shared project export/import bus | **MISSING** |
| Path remapper content:// → Windows | **MISSING** |
| Unified single Android APK | **FUTURE** |
| Android job status UI for M8/M9 | **FUTURE** |
| Credential sync policy | **FUTURE** |
| Dual-app coexistence strategy | **NEEDS FIX** (documented decision required before merge) |

---

## 6. Issues (Boundary-Specific)

```
ISSUE: No defined transfer mechanism (adb, shared folder, sync agent, API).
ROOT CAUSE: Modules completed in isolation.
IMPACT: End-to-end production cannot leave a single machine role.
SEVERITY: CRITICAL for full Android+Windows product; HIGH for integration program
RECOMMENDED FIX: Introduce a minimal “project bundle” directory convention + copy script/tool; delay network sync.
TEST REQUIRED: Manual export → M8 run → final.mp4 appears; optional import back listed in Android media screen.
```

```
ISSUE: M7 content:// URIs unusable on Windows host.
ROOT CAUSE: Android MediaStore URIs are device-local.
IMPACT: Even a correct JSON snapshot fails M8 if paths are not rewritten to copied files.
SEVERITY: HIGH
RECOMMENDED FIX: Export copies binaries into bundle `media/` with relative paths in snapshot JSON.
TEST REQUIRED: M8 M7Adapter loads exported snapshot; all KEEP paths open as files.
```

---

## 7. Guidance for Later Implementation Phases

1. Respect platform split in every adapter PR.  
2. Prefer **adapters/bridges** over moving engines across OS.  
3. Do not recreate M4–M9 inside `CURRENT_M1_M3` stub folders.  
4. Do not replace EffectsComposer with Android MediaCodec “for convenience.”  
5. First safe slice can be **Windows-only E2E** (M8→M9) while Android export matures in parallel.
