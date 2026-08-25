# M7 Recommendation + M6→M7 Analysis Pipeline Fix

## Bug (device)

Media Library showed:
- 90% → KEEP  (OK)
- 70% → KEEP  (OK)
- 26% → KEEP  (BUG)
- 0%  → KEEP  (BUG)

## Root cause

1. `MediaEntity.recommendation` defaulted to KEEP (fixed earlier → REVIEW).
2. M6 `CameraViewModel` → `MediaRepository.saveCapturedMedia` did **not** run
   `LocalQualityAnalyzer`. It only stored the M6 readiness integer as
   `qualityScore` and either left recommendation at default KEEP or derived
   it from the readiness percent without image analysis.
3. When readiness was 0/26, the library still showed KEEP.

## Fix

### Thresholds (`RecommendationPolicy`)

| Band | Normalized score | Recommendation |
|------|------------------|----------------|
| High | >= 0.70 | KEEP |
| Medium / uncertain | 0.40 .. 0.69 | REVIEW |
| Low | < 0.40 | RETAKE |
| Analysis failure | — | REVIEW (never KEEP) |
| Duplicate | — | REVIEW |
| Blur or bad exposure and score < 0.70 | — | RETAKE |

### M6 capture path

`MediaRepository.saveCapturedMedia` now:

1. Runs `DuplicateDetector` against existing project media
2. Runs `LocalQualityAnalyzer.analyze(uri, mediaType, isDuplicate)`
3. Generates thumbnail via `ThumbnailGenerator`
4. Probes width/height/fps/orientation
5. Persists full QualityResult fields on MediaEntity:
   - qualityScore / qualityScoreNormalized
   - blurDetected, exposureOk, compositionOk
   - faceQuality, closedEye, isDuplicate
   - **recommendation** (from analyzer / policy)
   - thumbnailPath, perceptualHash, date/time
6. Recomputes best-shot within missionShotId group when applicable

If the file cannot be decoded, recommendation is forced to **REVIEW**
(analysisFailed=true), even if M6 supplied a high readiness percent.

### Unchanged

- M6 camera capture, overlays, timer, planner internals
- M4 video engine
- M8/M9 not implemented

## Unit tests

`RecommendationPolicyTest` covers:
- high → KEEP
- medium → REVIEW
- low (0%, 26%) → RETAKE
- analysis failure → REVIEW
- fromMetrics high/low
- blur / duplicate edge cases

`MediaEntityM7FieldsTest` asserts unanalyzed default is REVIEW not KEEP.

## Build

```bat
cd android
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
```

APK requires Android SDK 34 on the build machine (not present in this sandbox).
