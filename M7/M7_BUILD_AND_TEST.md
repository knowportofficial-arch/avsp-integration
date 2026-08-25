# M7 Build & Test Report

## Environment note

This CI/sandbox host does **not** include the Android SDK (`local.properties` points at a Windows path from the original machine). Full APK generation and instrumented tests require a machine with Android SDK 34 + build-tools.

## Build commands (developer machine)

```bash
cd android
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
# optional APK:
./gradlew assembleDebug
# APK path: app/build/outputs/apk/debug/app-debug.apk
```

## Unit tests added (JVM)

| Test class | Coverage |
|------------|----------|
| `BestShotSelectorTest` | ranking, empty, duplicate penalty, RETAKE penalty |
| `QualityResultTest` | toMap contract, placeholder thresholds |
| `DatasetCategoryTest` | known set, case-insensitive lookup, expandability |
| `MediaEntityM7FieldsTest` | M6 default compatibility, tags CSV parse |

Existing M6 unit tests remain in tree and should continue to pass:

- `ProjectDaoTest`, `ProjectRepositoryTest`
- `FpsRangeSelectorTest`, `GuidedCaptureTemplateTest`, `SelfTimerTest`
- `AspectRatioValidatorTest`, `ShotMissionWorkflowTest`

## Manual / sample dataset checklist

1. Import photo → `DatasetRepository.ingestMedia(..., mediaType="PHOTO")`
2. Import video → same with `VIDEO` (thumbnail from first frame)
3. Metadata creation → width/height/fps/orientation/date/time populated when probe succeeds
4. Thumbnail → `filesDir/thumbnails/{id}_thumb.jpg`
5. Category / Tag → `updateCategory` / `updateTags`
6. Search / Filter / Sort → `search`, `filter`, selection ranking
7. Blur / Exposure / Composition → `LocalQualityAnalyzer`
8. Face quality → returns null until a reliable face model is wired (spec-compliant)
9. Duplicate detection → aHash Hamming distance
10. Best-shot ranking → missionShotId groups
11. KEEP / RETAKE recommendation → based on score + blur + exposure
12. Original file preservation → default delete path does not remove MediaStore item from M7 APIs
13. App restart persistence → Room
14. Large / invalid media → analyzer returns placeholder score; no crash

## Known limitations

1. **No Android SDK in this sandbox** → APK not produced here; code is complete for local build.
2. **Face / closed-eye** → intentionally `null` without forcing cloud or heavy face models; contract is ready for a future on-device face detector.
3. **Destructive migration** on DB v2→v3 clears old rows (acceptable for pre-release module freeze).
4. **Perceptual hash** is 8×8 aHash — good for near-duplicates, not cryptographic identity.
5. **Composition / blur thresholds** are tuned for 320px analysis previews; extreme resolutions may need calibration on device.
6. M6 capture path still writes via `MediaRepository` with default quality; call `datasetRepository.analyzeExisting(id)` to enrich after capture if desired (non-breaking optional step).
