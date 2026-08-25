# M7 Changed / New Files

## Recommendation + M6→M7 pipeline fix (this revision)

| File | Change |
|------|--------|
| `data/repository/MediaRepository.kt` | **Critical:** every `saveCapturedMedia` now runs `LocalQualityAnalyzer`, `DuplicateDetector`, `ThumbnailGenerator`; persists full QualityResult onto MediaEntity including recommendation |
| `dataset/analyzer/RecommendationPolicy.kt` | Deterministic KEEP≥0.70 / REVIEW≥0.40 / RETAKE&lt;0.40; analysisFailed→REVIEW |
| `dataset/analyzer/LocalQualityAnalyzer.kt` | Uses `QualityResult.fromMetrics` / `analysisFailed()` |
| `dataset/model/QualityResult.kt` | `analysisFailed()`, `fromMetrics()` via policy |
| `database/entity/MediaEntity.kt` | Defaults: qualityScore=0, recommendation=REVIEW (not KEEP) |
| `test/.../RecommendationPolicyTest.kt` | high/medium/low/failure/device 0% & 26% cases |
| `test/.../MediaEntityM7FieldsTest.kt` | Unanalyzed default is REVIEW |
| `test/.../QualityResultTest.kt` | analysisFailed + fromMetrics bands |

## Original M7 package (still present)

```
dataset/model/{DatasetCategory,DatasetMedia,QualityResult}
dataset/analyzer/{LocalQualityAnalyzer,DuplicateDetector,ThumbnailGenerator,BestShotSelector,RecommendationPolicy}
dataset/repository/DatasetRepository
dataset/api/{MediaSelectionApi,DatasetAutomationContract}
```

## Unchanged (intentionally)

All M6 camera, planner, guided capture, composition overlay, live vision analyzer, mission packages.
