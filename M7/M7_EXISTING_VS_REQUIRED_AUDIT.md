# M7 — Existing vs Required Audit

**Baseline:** AVSP_M6_M6.4_UI_OVERLAY_TIMER_FIX (frozen M6 Camera)
**Module:** M7 — Personal Dataset & AI Vision
**Date:** 2026-08-24

## EXISTING (preserved)

| Area | Status | Notes |
|------|--------|-------|
| M6 Camera / Guided Capture | PRESERVED | No internal rewrite of camera engine, planner, overlays, timer |
| M6 MediaEntity + MediaDao | EXTENDED | New columns with defaults; M6 saveCapturedMedia still works |
| MediaRepository (M6 path) | PRESERVED | Defaults for new fields; delete path still optional file delete |
| MediaLibraryScreen | ENHANCED | Shows category, recommendation, best-shot, duplicate badges |
| Room DB | VERSION 2 → 3 | fallbackToDestructiveMigration already present |
| ML Kit vision (live camera) | UNTOUCHED | Used by M6 frame analysis only |
| ClipMetadata (M6.1 sidecar) | UNTOUCHED | Already designed with M7-oriented keys |
| Project / Nav / Theme | UNTOUCHED | |

## REQUIRED (implemented in M7)

| Responsibility | Implementation |
|----------------|----------------|
| Local dataset management | DatasetRepository |
| Personal media library | Extended entity + MediaLibrary badges + filter/search DAO |
| Categories | DatasetCategory + MediaEntity.category (expandable string) |
| Tags | tagsCsv / tagsList() |
| Metadata | width/height/fps/orientation/date/time/location/thumbnail |
| Thumbnail generation | ThumbnailGenerator (app-private JPEG) |
| Search | MediaDao.search + DatasetRepository.search |
| Filtering / sorting | MediaDao.queryFiltered + selection ranking |
| Quality scoring | LocalQualityAnalyzer → QualityResult |
| Blur detection | Laplacian-variance on downscaled bitmap |
| Exposure assessment | Histogram / mean luminance |
| Composition assessment | Rule-of-thirds edge energy heuristic |
| Face quality / closed-eye | Contract present; returns null when not reliable |
| Duplicate detection | DuplicateDetector average-hash (aHash) |
| Best-shot selection | BestShotSelector + missionShot grouping |
| KEEP / RETAKE / REVIEW | Recommendation enum + stored on entity |
| Automatic best-photo selection | recomputeBestShot after ingest |
| Original media rule | removeFromDataset(deleteOriginalFile=false) default |
| M4 contract | MediaSelectionApi.findBestMedia(...) |
| M8 contract | DatasetAutomationContract snapshot / findKeepable / reanalyzeAll |

## NOT implemented (per freeze)

- M8 Automation engine
- M9 Publishing
- Cloud AI requirement for basic quality / metadata
- Rewrite of M4 Video Engine or M6 Camera internals

## Local-first technologies used

- Room (SQLite)
- Android Bitmap / MediaMetadataRetriever
- ThumbnailUtils
- M7 analyzers are pure local heuristics (offline-capable)
