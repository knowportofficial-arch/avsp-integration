# AVSP Android V1 — M7 → CURRENT_M1_M3 Integration Report

## Status: COMPLETE (this phase only)

Final build root: `CURRENT_M1_M3/`  
Final applicationId: `com.avsp.pro`  
Donor: `M7/android/`  
M6 was not used as base.

---

## 1. Baseline tests (before)

| Suite | Result |
|-------|--------|
| CURRENT_M1_M3 `testDebugUnitTest` | **69 / 69** pass |
| M7/android `testDebugUnitTest` | **63 / 63** pass |

HEAD at start: `ddbfa24` (final APK architecture decision)

## 2. M7 functionality integrated

- CameraX capture (photo + video) via `CameraPreviewScreen` / `CameraController`
- Guided Capture (`GuidedCaptureActivity` + template flow), registered to Pro `projectId`
- Media Library with KEEP / REVIEW / RETAKE display
- ML Kit vision pipeline (object detection / labeling / analyzers)
- Local quality scoring (`LocalQualityAnalyzer`, `RecommendationPolicy`)
- KEEP / best-shot selection (`BestShotSelector`, MediaDao flags)
- Dataset APIs (`DatasetRepository`, `MediaSelectionApi`, `DatasetAutomationContract`)
- Future M4-A surface only: `EditorMediaSelection` / `EditorSelectionProvider` (no M4-A implementation)

## 3. Package mapping

| M7 (`com.avsp.creator`) | Pro (`com.avsp.pro`) |
|-------------------------|----------------------|
| `capture.camera.*` | `capture.camera.*` |
| `dataset.*` | `dataset.*` |
| `data.repository.MediaRepository` | `capture.data.MediaRepository` |
| `database` media only | `capture.database` (`avsp_capture_db`) |
| `ui.workspace.MediaLibraryScreen` | `media.MediaLibraryScreen` |
| `core.theme` (capture colors) | `capture.theme` |
| Creator `ProjectRepository` | `capture.data.ProjectRepository` → wraps Pro projects |

## 4. Dependencies added (Pro toolchain unchanged)

- CameraX 1.3.2 (core, camera2, lifecycle, video, view)
- ML Kit object-detection 17.0.2, image-labeling 17.0.9, vision-common 17.3.0
- Kotlin 2.0.21 / AGP 8.7.2 / compileSdk 35 / Compose BOM 2024.10.01 **not downgraded**

## 5. Navigation

Extended existing `AvspNavHost` (one shell, one launcher `MainActivity`):

- `camera/{projectId}`
- `media_library/{projectId}`
- Project detail CTAs: Camera, Guided Capture, Media Library / Quality
- `GuidedCaptureActivity` secondary Activity (`exported=false`, not LAUNCHER)

Preserved: Home, Projects, Project detail, Script, Audio, Settings, Logs, Media inventory

## 6. Storage / database

- Pro DB `avsp_m1.db` **unchanged schema** (no destructive migration)
- New capture DB `avsp_capture_db` v1 — `project_media` only, keyed by **Pro projectId**
- Capture save mirrors `MediaAsset` into Pro inventory under `ProjectPaths.CAMERA`
- Project delete cleans capture rows via `onProjectDeleted` hook
- Authoritative project root: `FileAvspStorage` + `ProjectPaths`

## 7. Project / media identity

```
Pro Project (projectId)
  → Camera / Guided Capture
  → MediaEntity (avsp_capture_db) + MediaStore/file URI
  → quality / KEEP / best metadata
  → mirrored MediaAsset (avsp_m1.db) for inventory
  → EditorMediaSelection (future M4-A)
```

## 8. Tests after

| Suite | Result |
|-------|--------|
| CURRENT_M1_M3 `testDebugUnitTest` | **131 / 131** pass |

Includes prior M1–M3 suites + ported M7 unit tests + `CaptureProjectIdentityTest` + `EditorSelectionContractTest`.  
Module catalog expectations updated: M6/M7 default **READY**; M4/M5/M8/M9 remain **FROZEN**.

## 9. APK

| Field | Value |
|-------|--------|
| Result | SUCCESS |
| Path | `CURRENT_M1_M3/app/build/outputs/apk/debug/app-debug.apk` |
| Size | ~67.7 MB (71,023,725 bytes) |
| applicationId | `com.avsp.pro` |
| versionName | `1.0.0-m7` |
| Install | **Not claimed** (no device install in this run) |

## 10. Explicitly not done

- M4-A / M5-A / M9-A
- Gemini production / TTS upgrade
- OAuth / YouTube / Facebook / Telegram publishing
- Google Drive / Sheets / Content Intelligence
- Windows modules / bridges untouched

## 11. Known limitations / remaining M7 work

- Device/camera UI not manually exercised in this cloud VM (no emulator camera proof)
- Guided clips still write under app external `AVSP/Guided/...` then register into Pro project
- Capture Room is separate from Pro Room (by design); no cross-DB transaction
- Live ML Kit quality depends on device camera frames
- ProjectDao / Creator project CRUD UI from M7 intentionally not ported (Pro projects are authority)

## 12. Exact next task

**STOP.** Do not start M4-A.  
When ready: Android V1 **M4-A** editor consuming `EditorSelectionProvider` / KEEP+best media for `com.avsp.pro`.
