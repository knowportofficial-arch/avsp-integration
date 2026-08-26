# Guided Capture Video — ERROR_SOURCE_INACTIVE (4) Fix

## Root cause

CameraX `VideoRecordEvent.Finalize` **error code 4** = `ERROR_SOURCE_INACTIVE`.

In `GuidedCaptureScreen`, camera bind was keyed on `state.phase == GuidedCapturePhase.READY` (a Boolean). Leaving `READY` for `RECORDING` flipped that key, restarted `LaunchedEffect`, and called `bindCamera()` → `ProcessCameraProvider.unbindAll()` while `VideoCapture` was recording. Detaching the video source finalizes with error 4.

Photo capture was unaffected because it is a single-shot `ImageCapture`, not a continuous recording surface.

## Fix

1. Bind camera only when phase is `READY` (`GuidedCaptureVideoPolicy.shouldBindCamera`).
2. Refuse rebind while `CameraController.isRecordingActive()`.
3. Do not clear `activeRecording` on `stop()`; clear on `Finalize`.
4. Defer `unbindAll` until after Finalize when unbind is requested mid-recording.
5. Recover `ERROR_SOURCE_INACTIVE` only if the output file is non-trivial; otherwise delete partial file.

## Files changed

- `GuidedCaptureScreen.kt`
- `GuidedCaptureViewModel.kt`
- `GuidedCaptureVideoPolicy.kt` (new)
- `CameraController.kt`
- `app/build.gradle.kts` (versionName `1.0.1-m7`)
- tests under `capture/camera/guided/`

## Device verification

Unit tests cannot prove CameraX recording on hardware. **Real-device retest of Guided Capture video is still required.**
