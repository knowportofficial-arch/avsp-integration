# Guided Capture Completion — 1.0.4-m7

## Scope

Complete actual AVSP Guided Capture intelligence in Pro (`com.avsp.pro`) without starting M4-A and without redesigning shot naming.

## What changed

1. **Shot-plan → Guided Capture path**
   - `GuidedCapturePlanAdapter` maps `MasterShotPlan` / `ShotMission` → full `GuidedCaptureTemplate` clip list.
   - `GuidedCaptureActivity` loads project name/description, runs `LocalShotPlanner`, and drives Guided Capture from that plan.
   - Sample fallback (`Intro / Wide / Medium / Close`) only when no project id is present.

2. **Naming preserved (not redesigned)**
   - Format remains: `{semantic} · {CODE} · {Ns} · {Framing} ({existing CameraShotType description})`
   - Example: `Intro · INTRO · 5s · Wide (Establishing, environment & landscape shot)`
   - Planner titles (Cooking setup, Entrance, …) stay primary; WIDE/MEDIUM/CLOSE stay technical framing.

3. **Live capture intelligence**
   - Guided Capture binds `DefaultVisualAnalyzer(MlKitVisionModel())` (object detection + image labels).
   - Scene / subject / cinematographer decision engines produce READY / NOT READY + guidance text that updates the UI.
   - READY is not asserted merely because the camera preview is open.

4. **Full shot sequence**
   - Clip count and identity come from the plan (e.g. temple plan = 5 shots), not a hard-coded WIDE→MEDIUM→CLOSE only flow.

5. **Capture → quality → KEEP/REVIEW/RETAKE → Media Library**
   - Photo and video finalize run `LocalQualityAnalyzer`.
   - REVIEW UI shows KEEP / REVIEW / RETAKE with quality %.
   - Registration passes `missionId` + `missionShotId` (and semantic `displayName` / `category`).
   - Video playability gate retained; photos use existence/size gate (video validator is not applied to JPEG).

6. **Zoom**
   - Requested zoom = `CameraShotType.defaultZoomRatio` from the shot’s framing (1.0 / 1.8 / 3.0). Applied via existing CameraX shot-type zoom.

## Identity mapping (honest)

| Concept | Status |
|---|---|
| `projectId` | Pro project id passed into Guided Capture / Media Library |
| `missionId` | From adapted `ShotMission.id` |
| `missionShotId` / `shotId` | From `PlannedShot.id` / clip `missionShotId` |
| `takeIndex` | Incremented on RETAKE; written into clip metadata JSON |
| `sceneId` | **NOT IMPLEMENTED** — `MasterShotPlan` has no scene contract; field kept null (not invented) |

## ML Kit honesty — what is / is not supported

**Supported (used):**
- Object detection (STREAM mode) + image labels
- Subject-hint matching against detected labels/objects
- Basic framing acceptability from decision engine
- Stability / focus / exposure signals from visual analysis pipeline
- READY / NOT READY + actionable guidance messages

**Not claimed / not reliable with current stack:**
- Arbitrary open-world “intended target” recognition beyond ML Kit labels/objects
- True semantic scene understanding (e.g. “this is a temple interior”) beyond label heuristics
- Guaranteeing subject presence for abstract subjects with no matching labels
- Treating zoom alone as capture intelligence

If a shot’s subject cannot be matched by available detections, guidance stays **NOT READY** with “Target not detected…” rather than fake READY.

## Build

- `applicationId`: `com.avsp.pro`
- `versionName`: `1.0.4-m7`
- `versionCode`: `5`
- Unit tests: **155 / 155** passed
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Acceptance note

Unit tests prove naming, plan→sequence adaptation, zoom mapping, and registration contracts. **Real-device camera verification is still required** before Guided Capture can be marked ACCEPTED on device.
