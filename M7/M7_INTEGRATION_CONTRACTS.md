# M7 Integration Contracts

## M4 — Media Selection

```kotlin
// Obtained from AvspApplication.mediaSelectionApi

suspend fun findBestMedia(
    projectId: String,
    category: String? = null,
    tags: List<String> = emptyList(),
    orientation: String? = null,      // "PORTRAIT" | "LANDSCAPE"
    minimumQuality: Double = 0.0,     // 0.0 .. 1.0
    mediaType: String? = null,        // "PHOTO" | "VIDEO"
    preferBestShot: Boolean = true,
    limit: Int = 20
): MediaSelectionApi.SelectionResult
```

`SelectionResult.items` is ordered: best-shot first, then quality, then recency.

```kotlin
suspend fun getBestShots(projectId: String): List<DatasetMedia>
```

## M8 — Automation surface

```kotlin
// AvspApplication.datasetAutomationContract

suspend fun snapshot(projectId: String): DatasetSnapshot
suspend fun listCategories(): List<String>
suspend fun findKeepable(projectId: String, category: String? = null, minQuality: Double = 0.7): List<DatasetMedia>
suspend fun reanalyzeAll(projectId: String): Int
```

`DatasetSnapshot` includes totals, per-category counts, KEEP/RETAKE/REVIEW counts, and a compact `MediaSummary` list.

## QualityResult JSON shape (spec)

```json
{
  "score": 0.86,
  "blur": false,
  "exposure": true,
  "composition": true,
  "face_quality": 0.91,
  "closed_eye": false,
  "duplicate": false,
  "recommendation": "KEEP"
}
```

`face_quality` / `closed_eye` may be `null` when on-device face analysis is not reliable.

## Original media rule

- M7 **never** automatically deletes original files.
- `DatasetRepository.removeFromDataset(id, deleteOriginalFile = false)` is the default.
- Thumbnails under `filesDir/thumbnails/` may be removed with the DB row.
