# Guided Capture — Functional Audit + Video Fixes (1.0.2-m7)

Based on real-device testing of `com.avsp.pro` `1.0.0-m7` and code trace of
`CURRENT_M1_M3` Guided Capture.

**Guided Capture is NOT marked ACCEPTED.** Real-device retest of this APK is required.

## Task 1 — What Guided Capture actually does

| ID | Topic | Actual executable behavior |
|----|--------|----------------------------|
| A | Session creation | `ProjectDetailScreen` → `GuidedCaptureActivity.launchIntent(projectId)` → hardcodes `GuidedCaptureTemplate.sample()` |
| B | Shot list | **Only** `GuidedCaptureTemplate.sample()` (Intro/Wide/Medium/Close). Not from MasterShotPlan / ShotMission / script |
| C | WIDE/MEDIUM/CLOSE | String `GuidedClipSpec.category`. Now mapped to `CameraShotType` zoom on bind |
| D | Duration | `targetDurationSeconds` on clip; UI progress; auto-stop ticker; early stop allowed |
| E | Zoom | Previously always `CameraShotType.WIDE` and never `applyShotTypeZoom`. **Fixed:** category → shot type → `applyShotTypeZoom` |
| F | Subject validation | **NOT IMPLEMENTED** on Guided path (`noOpVisualAnalyzer`) |
| G | Framing validation | Live: **NOT IMPLEMENTED**. Post-hoc `AspectRatioValidator` only |
| H | Stability validation | **NOT IMPLEMENTED** on Guided path |
| I | Duration validation | Soft enforce (auto-stop). Early stop OK. No reject for short clips |
| J | ML Kit | **NOT used** on Guided path |
| K | Quality scoring | After session finish → `MediaRepository` / `LocalQualityAnalyzer` (not live in guided UI) |
| L | KEEP/REVIEW/RETAKE | Guided UI: RETAKE / NEXT / FINISH. M7 KEEP/REVIEW/RETAKE only after media registration |
| M | project→scene→shot→take | **projectId only**. No scene/shot/take. `missionId`/`missionShotId` null |
| N | Media pipeline | File under app external files → (now) FileProvider content URI → `saveCapturedMedia` → `MediaEntity` + Pro `MediaAsset` |

## Task 2 — Feature gap table

| Feature | Code exists | Actually connected | Expected AVSP behavior | Gap |
|---------|-------------|--------------------|------------------------|-----|
| Shot instruction | Yes (template + UI) | Yes | Clear per-shot instruction | Improved instruction string; still sample-template only |
| WIDE | Category + zoom contract | Yes (zoom now) | Framing + zoom | Not from MasterShotPlan |
| MEDIUM | Category + zoom contract | Yes (zoom now) | Framing + zoom | Same |
| CLOSE | Category + zoom contract | Yes (zoom now) | Framing + zoom | Same |
| Duration | Template + ticker | Yes | Planned duration | Soft only; no hard gate |
| Zoom | `CameraShotType` | **Connected now** | Per-shot zoom | Was always WIDE |
| Subject detection | ML Kit in AI camera | **No** | Detect intended subject | NOT IMPLEMENTED in Guided |
| Framing guidance | AI analyzer | **No** (live) | Live framing cues | NOT IMPLEMENTED in Guided |
| Stability guidance | AI analyzer | **No** | Live stability | NOT IMPLEMENTED in Guided |
| Quality score | `LocalQualityAnalyzer` | After registration | Score each take | Not in guided REVIEW UI |
| KEEP | RecommendationPolicy | After registration | Label KEEP | Not guided-UI KEEP |
| REVIEW | RecommendationPolicy | After registration | Label REVIEW | Same |
| RETAKE | Guided UI retake() | Yes (session) | Retake clip | Different from M7 RETAKE enum |
| Video capture | CameraX FileOutput | Yes | Reliable record | Was broken by rebind |
| Video finalization | Finalize callback | Yes + playable gate | Clean finalize | Error 4 fixed; unplayable rejected |
| Playable video validation | **New** validator | Yes | Playable MP4 | Was missing (size-only) |
| Project identity | EXTRA_PROJECT_ID | Yes | Bind to Pro project | OK |
| Media Library registration | MediaRepository | Yes + FileProvider | Openable asset | file:// was unplayable externally |

## Task 3 — Video root causes

### A. Error code 4 = `VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE`

Compose `LaunchedEffect` keyed on `phase == READY` Boolean caused rebind/`unbindAll`
while recording. Fixed earlier (bind only in READY; refuse rebind while active; defer unbind).

### B. Saved but unplayable

Two compounding causes:

1. **Weak recovery** after error 4: file size ≥ 8KB was treated as success → truncated/corrupt MP4 promoted to REVIEW/DB.
2. **`file://` URI registration**: Media Library `ACTION_VIEW` with raw `file://` fails on modern Android for external players; probes via `contentResolver` often return 0 for `file://`.

Fixes:

- `GuidedCaptureVideoValidator` requires ftyp + readable video track + ≥2 samples + duration.
- Finalize / error-4 recovery refuse non-playable files and delete partials.
- Register via `FileProvider` content URI; Media Library opens file:// via FileProvider fallback.
- `MediaRepository` probes support `file://` path fallback.

## Files changed (this pass)

- `GuidedCaptureVideoValidator.kt` (new)
- `GuidedCaptureVideoPolicy.kt`
- `GuidedCaptureViewModel.kt`
- `GuidedCaptureTemplate.kt` (shot mapping / instruction)
- `GuidedCaptureScreen.kt`
- `GuidedCaptureActivity.kt`
- `GuidedCaptureUris.kt` + `res/xml/guided_capture_file_paths.xml` + Manifest provider
- `ClipInspector.kt` (`probeDurationMs`)
- `MediaRepository.kt`
- `MediaLibraryScreen.kt`
- `app/build.gradle.kts` → `1.0.2-m7` / versionCode 3
- Tests + fixtures under `app/src/test/...`

## Acceptance

Unit tests / APK build do **not** prove Guided Capture video on hardware.
**REAL DEVICE verification required** before M7 Guided Capture can be ACCEPTED.
