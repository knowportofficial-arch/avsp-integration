# M4 Android Video Engine — Hybrid Architecture

## Decision
M4 is a hybrid module:

- **Android runtime:** Jetpack Media3 Transformer (`MediaCodec`/OpenGL backed) for local rendering.
- **Desktop/Windows parity backend:** the existing M4 Python/FFmpeg engine remains unchanged and is the heavy-render/reference backend.
- **Shared contract:** `VideoRenderPlan` is the canonical timeline contract.

The Android module does not embed Python or FFmpeg. It consumes the existing M3 `AudioToVideoHandoff`, maps visuals by scene order, renders H.264/AAC MP4, and validates the output before reporting success.

## First Android acceptance scope
1. M3 audio package is required.
2. Scene order/timing must be contiguous.
3. Images are converted to timed video clips.
4. Existing video assets can be used as visual sources in the next renderer increment; the current baseline uses images/placeholder visuals to guarantee a renderable first path.
5. Output defaults to 1080x1920@30fps for Shorts and 1920x1080@30fps for landscape.
6. Output must contain video + audio and match the planned duration within 80ms.
7. Failed validation never reports M4 success.

## Hybrid rule
Do not modify the old Python/FFmpeg engine to make Android work. Android and Windows share the render-plan contract and validation rules, but use platform-native backends.
