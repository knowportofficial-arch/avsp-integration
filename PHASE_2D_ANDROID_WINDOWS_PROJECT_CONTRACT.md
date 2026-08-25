# PHASE 2D — Android → Windows Project Package Contract

**Status:** DESIGN ONLY — no implementation  
**Date:** 2026-03-26  
**Repository:** `knowportofficial-arch/avsp-integration`  
**Prerequisite:** Phase 1 audit; Phase 2A M8→M9; Phase 2B/2C M3→M8 complete  

**Constraint:** Read-only inspection. The only deliverable of this phase is this document.

**Authoritative inputs read:**

| Document | Role |
|----------|------|
| `AVSP_MODULE_INTEGRATION_MATRIX.md` | Module inventory / bridge gaps |
| `AVSP_CONTRACT_MAP.md` | Declared handoffs |
| `AVSP_PLATFORM_BOUNDARY.md` | Android vs Windows; missing bus |
| `AVSP_DUPLICATE_COMPONENTS.md` | Dual M4 stance |
| `AVSP_INTEGRATION_AUDIT.md` | Phase 1 verdicts |
| `PHASE_2B_M3_M8_CONTRACT_PLAN.md` | M3↔M8 contract |
| `bridges/m3_m8/CONTRACTS.md` | Frozen Phase 2C voice bridge |
| `bridges/m8_m9/CONTRACTS.md` | Frozen Phase 2A publish bridge |

**Verified production spine (already working on Windows when artifacts are local):**

```text
M3 AudioPackage → bridges/m3_m8 → M8 real narration → QC → bridges/m8_m9 → M9 mock publish
```

**Missing boundary this phase designs (does not implement):**

```text
Android Creator/Pro project state → portable AVSP PROJECT PACKAGE → Windows import
```

---

## 1. Executive summary

Android today is split across **two apps** and **no export bus**:

| App | Package | Modules | Storage |
|-----|---------|---------|---------|
| AVSP Pro | `com.avsp.pro` | M1–M3 | `FileAvspStorage` under `context.filesDir/projects/{projectId}/…` |
| AVSP Creator | `com.avsp.creator` | M6 + M7 (M7 tree supersets M6) | MediaStore URIs + Room + guided `getExternalFilesDir` + in-memory dataset APIs |

Windows can already produce and publish **if** it has a topic (or richer artifacts on disk). It does **not** read Android sandboxes, `content://` URIs, or Kotlin Room DBs.

**Minimum future contract:** a versioned, project-relative **AVSP Project Package** that physically carries:

1. **Identity + settings** (`project.json`)
2. **Script** (M2 `ScriptPackage` / `script.json`) — when Pro is source
3. **Voice** (M3 `voice.json` + `aseg_*.wav` layout already understood by `bridges/m3_m8`)
4. **Selected media files** (copied bytes; Windows-relative paths)
5. **Vision/selection snapshot** (M7-derived JSON shaped for `M8` `M7Adapter`, which does not exist as an Android writer today)

**Do not** pre-bake `narration.wav` as the only audio artifact: keep M3 segment package; reuse Phase 2C bridge on Windows.  
**Do not** put API keys/OAuth secrets in the package.  
**Do not** rewrite M3/M4/M7/M8/M9 engines for the first importer — prefer new exporter + importer modules.

---

## 2. Current Android capabilities

### 2.1 M1–M3 (`CURRENT_M1_M3/` — `com.avsp.pro`)

| Concern | Evidence | Exists? |
|---------|----------|---------|
| Project identity / `projectId` | Script/Audio packages keyed by `projectId`; Room/module status; `FileAvspStorage` project roots | **Yes** |
| Script ID | `ScriptPackage.scriptId` | **Yes** |
| Script data | `ScriptPackage`: topic, language, title, hook, intro, scenes[], cta, ending, duration fields, validation, metadata | **Yes** |
| Scene data | `ScriptScene`: sceneId, order, durationMs, narration, onScreenText, visualDescription, shotType, cameraDirection, bRollSuggestion, transition, notes | **Yes** |
| Voice / AudioPackage | `AudioPackage` + segments | **Yes** |
| `voice.json` | Written at `generated/audio/voice.json` (full `AudioPackage` JSON) | **Yes** |
| Segment WAVs | `generated/audio/{audioPackageId}/aseg_NN.wav` pcm_s16le 16 kHz mono | **Yes** |
| `totalDurationMs` | Sum of contiguous segment durations | **Yes** |
| Language | On script + audio package | **Yes** |
| Timing | Contiguous `startMs`/`endMs` per segment; pauseAfter not inserted as gaps | **Yes** |
| `voice.mp3` | Name constant only (`ArtifactNames.VOICE_MP3`); **not written** | **MISSING** (file) |
| Single mixdown WAV | Not produced by M3 | **MISSING** |
| Camera / M6 capture in Pro | Stub / FROZEN placeholders | **MISSING** (in Pro) |
| Dataset / M7 in Pro | Stub / FROZEN | **MISSING** (in Pro) |
| Export to Windows | None | **MISSING** |

Canonical relative layout (`ProjectPaths` + repositories):

```text
{filesDir}/projects/{projectId}/
  originals/…  working/…  generated/…  published/…  logs/…
  generated/script/{scriptId}.json
  generated/script/script.json          # latest pointer
  generated/audio/{aud_…}/aseg_NN.wav
  generated/audio/{aud_…}/package.json
  generated/audio/voice.json            # latest AudioPackage
```

### 2.2 M6 (`M6/android/` — `com.avsp.creator`)

| Concern | Evidence | Exists? |
|---------|----------|---------|
| Captured photos | MediaStore JPEG `AVSP_IMG_*.jpg` | **Yes** |
| Captured videos | MediaStore MP4 `AVSP_VID_*.mp4` | **Yes** |
| Guided capture files | `getExternalFilesDir/AVSP/Guided/<templateId>/<clipId>/{clip.mp4\|clip.jpg, metadata.json, thumbnail.jpg}` | **Yes** |
| Media IDs | Room `MediaEntity.id` (UUID); guided `clipId` | **Yes** |
| Project association | `MediaEntity.projectId` (AI camera KEEP path) | **Yes** (AI path) |
| Guided → Room project link | Auto-ingest | **MISSING** |
| Resolution / fps / orientation | Guided `ClipMetadata` JSON; AI Room entity lacks width/height/fps/orientation | **Partial** |
| Duration | Room `durationSeconds`; guided `durationMs` | **Yes** |
| Timestamps | `createdAt`; guided date/time strings | **Yes** |
| Geo | lat/lng (+ accuracy on CapturedMedia) | **Yes** |
| Storage | MediaStore `content://` URIs in Room; guided absolute file paths in sidecar | **Yes** |
| `FileAvspStorage` | Not used | **MISSING** |

### 2.3 M7 (`M7/android/` — `com.avsp.creator`, supersets M6)

| Concern | Evidence | Exists? |
|---------|----------|---------|
| Vision / quality | `QualityResult`: score, blur, exposure, composition, face_quality, closed_eye, duplicate, recommendation KEEP\|RETAKE\|REVIEW | **Yes** (in-process) |
| Ranking / best shots | `BestShotSelector`, `isBestShot`, selection sort | **Yes** |
| Extended media fields | width, height, fps, orientation, category, tags, thumbnailPath, quality flags, recommendation, perceptualHash | **Yes** (Room v3) |
| Dataset APIs | `MediaSelectionApi`, `DatasetAutomationContract.snapshot()` | **Yes** (in-memory) |
| On-disk `m7_snapshot.json` writer | Not in M7 sources | **MISSING** |
| Detected subjects persisted on dataset items | Live `DetectedSubject` only; not on `DatasetMedia` | **MISSING** |
| Windows/M8-readable export | None | **MISSING** |
| Same `applicationId` as M6 | Both `com.avsp.creator` (alternate trees) | **Yes** (collision if both installed) |

---

## 3. Current Windows requirements

### 3.1 Live M8 (`AutonomousProductionController.run`)

| Input | Class |
|-------|--------|
| `topic` | **REQUIRED** |
| `duration` | OPTIONAL (default `5m`) → DERIVED seconds |
| `format_name` | OPTIONAL (default `9:16`) |
| `project_id` | OPTIONAL → DERIVED |
| Narration file | **NOT REQUIRED** (hardcoded `narration_audio=None` → simulated VO) |
| Android package | **NOT REQUIRED** / unread |
| M7 Android export | **NOT REQUIRED** / unread |

### 3.2 M4 `VideoEngine.render` (standalone / vendor copy)

All of `audio_path`, `media`, `script`, `template`, `project_id`, `subtitles`, `music_path` are **OPTIONAL** at the API validator. Useful production typically supplies audio + media. **Live M8 does not call M4Adapter.render**; it uses EffectsComposer.

### 3.3 Phase 2C `bridges/m3_m8`

| Input | Class |
|-------|--------|
| M3 package dir (`voice.json`/`package.json` + segment WAVs, valid encoding/timeline) | **REQUIRED** |
| `topic` | **REQUIRED** |
| Duration | OPTIONAL → DERIVED from `totalDurationMs` |
| Format / project id | OPTIONAL |

### 3.4 Phase 2A `bridges/m8_m9`

| Input | Class |
|-------|--------|
| `final.mp4` | **REQUIRED** |
| `project_id`, title/topic | **REQUIRED** (title path) |
| Script JSON / QC frames | OPTIONAL |
| Credentials | **NOT REQUIRED** for mock (`force_mock=True`) |

### 3.5 M8 `M7Adapter` (Windows-local)

| Input | Class |
|-------|--------|
| `assets/m7_snapshot.json` or scanned `assets/local_media` | OPTIONAL |
| Per item `path`\|`filePath`\|`uri` that exists on Windows | REQUIRED to keep item |
| `quality_score` / `recommendation` | OPTIONAL (defaults) |

Does **not** consume Android Room or `content://` URIs.

---

## 4. Android storage model

| Store | Location | Type | Crosses to Windows? |
|-------|----------|------|---------------------|
| Pro project files | `context.filesDir/projects/{projectId}/…` | App-specific internal | **No** (absolute sandbox) |
| Pro relative paths | `generated/audio/…`, `generated/script/…` | Relative inside project | **Yes if packaged** |
| Creator Room | `avsp_creator_db` | SQLite app DB | **No** (must serialize) |
| Creator MediaStore | `content://media/…` | Content URIs | **No** (must copy bytes) |
| Guided files | `getExternalFilesDir/AVSP/Guided/…` | App-specific external files | Absolute Android paths **No**; files **Yes if copied** |
| M7 thumbnails | `filesDir/thumbnails/…` | App-specific | Copy if needed |
| Live vision frames | In-memory | Ephemeral | **No** unless snapshotted |

**Android-specific (must not appear as runtime paths on Windows):**  
`/data/user/0/…`, `/storage/emulated/0/…`, `content://…`, MediaStore IDs without exported files.

---

## 5. Windows input model

| Consumer | Needs to start | Preferred rich inputs |
|----------|----------------|----------------------|
| M8 live | Topic string | Duration, format, local media/snapshot |
| `bridges/m3_m8` | On-disk M3 package + topic | — |
| EffectsComposer | Timeline (+ optional `narration_audio`) | Real VO file |
| `bridges/m8_m9` | Final MP4 + metadata | SEO from script |
| M4 engine | Optional kwargs | audio_path + media list |
| M7Adapter | Optional JSON/files under M8 assets/projects | Windows paths + scores |

**No code today imports an Android project package.**

---

## 6. Contract comparison

| Dimension | ANDROID CURRENT OUTPUT | WINDOWS REQUIRED / USED INPUT | Classification |
|-----------|------------------------|-------------------------------|----------------|
| Project ID | Pro `projectId`; Creator Room `project.id` | M8 `project_id` (separate namespace) | **REQUIRES TRANSFER** (map/record both) |
| Script | M2 `ScriptPackage` JSON | M8 generates its own unless bridged; M9 SEO reads `research/script.json` optionally | **REQUIRES TRANSFER** (optional for MVP voice+media) |
| Language | Script/Audio `language` | M9 `language` optional; M8 director defaults `en` | **REQUIRES TRANSFER** |
| Narration text | Script scenes + audio `sourceText` | EDL narration text (director-generated today) | **REQUIRES TRANSFER** / else **WINDOWS-DERIVED** |
| Voice timeline | M3 `voice.json` timings | Unused by live M8; used by Phase 2C duration align | **COMPATIBLE** with `bridges/m3_m8` |
| Voice files | Multi WAV segments | One narration file after Phase 2C concat | **REQUIRES TRANSFER** (segments) + **WINDOWS-DERIVED** (concat) |
| Media files | MediaStore URI / guided files | Local filesystem paths for FFmpeg | **REQUIRES TRANSFER** (bytes) |
| Media IDs | UUID / clipId | M7Adapter `id`/`asset_id` | **REQUIRES TRANSFER** |
| Media metadata | Room / ClipMetadata / QualityResult | Optional scores/recs for M7Adapter | **REQUIRES TRANSFER** |
| Scene information | M2 scenes; M8 scenes DERIVED | M8 director plan | **REQUIRES TRANSFER** or **WINDOWS-DERIVED** |
| Shot information | ShotType / guided category | Partial via media selection goals | **PARTIAL / REQUIRES TRANSFER** |
| Vision results | M7 in-memory / Room | M7Adapter JSON | **MISSING** writer + **REQUIRES TRANSFER** |
| Timeline | M3 ms; M8 seconds DERIVED | M8 timeline | **WINDOWS-DERIVED** (Phase 2C duration sync) |
| Captions | Script onScreenText; M8 burns from narration | M8-generated captions | **WINDOWS-DERIVED** |
| Music / SFX | Not Android-owned for production | M8 assets libraries | **WINDOWS-DERIVED** / **ANDROID-ONLY** N/A |
| Thumbnail | Guided thumb; M7 thumbs; M9 optional QC frame | Optional | **REQUIRES TRANSFER** or **WINDOWS-DERIVED** |
| Creator settings | Creator project fields (audience, platform, language) | Sparse on Windows | **REQUIRES TRANSFER** |
| Output format / aspect | Script metadata aspectRatio; Creator capture orientation | M8 `format_name` | **REQUIRES TRANSFER** |
| Duration target | Script target/estimated; M3 totalDurationMs | M8 duration / Phase 2C from voice | **COMPATIBLE** (prefer voice duration when VO present) |
| Shared export bus | None | None | **MISSING** |
| Dual-app merge Pro↔Creator | Separate applicationIds | N/A | **MISSING** |

---

## 7. Proposed portable package

Derived from existing contracts (M3 layout, M8 M7Adapter, ProjectPaths), **not** invented greenfield.

### 7.1 Recommended minimum structure

```text
avsp_project/
  manifest.json                 # package version, ids, checksums of included files
  project.json                  # identity + creator/pro settings + language/format hints
  script/
    script.json                 # M2 ScriptPackage (if available)
  audio/
    voice.json                  # M3 AudioPackage (required for voice-first path)
    {audioPackageId}/
      package.json
      aseg_00.wav
      aseg_01.wav
      …
  media/
    {mediaId}/
      media.json                # portable metadata (no content://)
      original.mp4|jpg|…
      thumbnail.jpg             # optional
  vision/
    m7_snapshot.json            # Windows M7Adapter-compatible selection snapshot
  metadata/
    export.json                 # export time, source apps, warnings
```

Exact nesting may mirror Pro `generated/` for audio/script to maximize Phase 2C reuse:

```text
audio/generated/audio/voice.json
audio/generated/audio/{aud_…}/aseg_NN.wav
```

Importer may accept either flattened `audio/` or Pro-relative tree; **future schema must pick one** and document it (open question §21).

### 7.2 Field sources

| Package field | Source |
|---------------|--------|
| `manifest.package_version` | DERIVED (new) |
| `project.android_project_id` | M1 / Creator project |
| `project.topic` / title | M1 / M2 / Creator |
| `project.language` | M2 / M3 / Creator |
| `project.format_hint` | M2 metadata.aspectRatio / capture | → maps to M8 `9:16`/`16:9` |
| `script/*` | M2 |
| `audio/*` | M3 |
| `media/*` files | M6 capture bytes |
| `media/*/media.json` | M6/M7 Room + guided ClipMetadata (serialized) |
| `vision/m7_snapshot.json` | M7 DatasetAutomationContract **serialized** (new writer) |
| Windows `project_id` | DERIVED on import |
| `narration.wav` | **Not required in package** — DERIVED on Windows by `bridges/m3_m8` |
| EDL / timeline.json | **Not required** — DERIVED by M8 |
| final.mp4 / publish credentials | **Not in package** |

### 7.3 Required vs optional (MVP)

**Required for voice-first Windows production:**

- `manifest.json`, `project.json` (topic + ids)
- `audio/…` valid M3 package (as Phase 2C expects)
- At least topic string usable by M8

**Required for media-aware production (recommended):**

- ≥1 media file under `media/` with Windows-relative path listed in `vision/m7_snapshot.json` **or** a simple `media_index.json`

**Optional:**

- Full M2 script
- Thumbnails
- Incomplete vision (importer may fall back to unscored media list)
- Captions, music, SFX (Windows-derived)

---

## 8. Path / URI strategy

| Kind | Rule |
|------|------|
| Absolute Android paths | **MUST NOT** be used as Windows runtime paths |
| `content://` URIs | **MUST NOT** cross boundary; resolve → copy bytes at export |
| Package-relative paths | **MUST** be used inside JSON (POSIX-style `/` separators) |
| Example | `media/clip_ab12/original.mp4`, `audio/generated/audio/voice.json` |
| Windows absolute | Created only by importer under `M8/m8/projects/{id}/…` |
| Guided `metadata.json` `file` absolute | Strip/replace with package-relative on export |

**Physical transfer required for:** all referenced WAV/JPEG/MP4 (and optional thumbs).  
**Serializable only:** JSON metadata, scores, IDs, timings, language, topic.

---

## 9. Media transfer strategy

| Approach | Supported by current code? | Fit |
|----------|----------------------------|-----|
| A. Copy all media | Possible but costly | Slow; large videos |
| B. Copy only selected media | Aligns with M7 KEEP/best APIs (in-memory today) | **Preferred** |
| C. Reference remote media | No shared remote protocol in-repo | **Not supported** |
| D. Hybrid | Selected local copy + optional later remote | Future |

**Recommendation (evidence-based):** **B — copy only selected media** (KEEP + `isBestShot` / operator-selected), plus **always copy full M3 audio package** (small WAVs, required for Phase 2C).

Rationale:

- Production speed goal → avoid shipping RETAKE/duplicates
- Offline Windows production → bytes must be local (C unsupported)
- M7 already ranks KEEP/REVIEW/RETAKE and best shots
- Guided full-res originals only when selected

---

## 10. M3 audio integration

**Verified Phase 2C path is sufficient:**

```text
Android package
  → include M3 AudioPackage layout (voice.json + aseg_*.wav)
  → Windows importer places tree on disk
  → bridges/m3_m8 discovers/validates/concats → narration.wav
  → M8 render with real VO
```

| Question | Answer |
|----------|--------|
| Pre-concat on Android? | **Not required** — duplicates work Phase 2C already does |
| Include `narration.wav` in package? | **Optional cache only**; source of truth remains segments + `voice.json` |
| Change M3? | **No** for MVP |
| Change `bridges/m3_m8`? | Prefer **unchanged**; importer outputs a directory the bridge already accepts |

---

## 11. M7 vision integration

| Today | Gap |
|-------|-----|
| M7: in-memory `DatasetSnapshot` / Room enrichment | No on-disk export writer |
| M8: `M7Adapter` reads JSON with `path`/`quality_score`/`recommendation`/`asset_id` | Expects Windows-local files |

**Future (design only):** Android exporter serializes selection to `vision/m7_snapshot.json` using **M8-compatible keys** (or a thin Windows normalizer). Map:

| M7 Kotlin | M8 adapter expectation |
|-----------|------------------------|
| `clipId` | `id` / `asset_id` |
| `fileUri` (after copy) | `path` (package-relative → absolute on import) |
| `qualityScore` (0–1) | `quality_score` |
| `recommendation` | `recommendation` |
| `mediaType` | `type` |
| `isBestShot` | optional flag / ordering hint |

Live M8 **can** consume this **after** import writes files + snapshot under the M8 project/assets. Until exporter exists, mark **MISSING bridge**.

---

## 12. Creator workflow (future)

```text
Android:
  CREATE PROJECT
   → Enter topic
   → Script          (exists in Pro M2)
   → Voice           (exists in Pro M3)
   → Capture/import  (exists in Creator M6)
   → AI vision       (exists in Creator M7 in-app)
   → Preview         (partial UIs)
   → EXPORT PACKAGE  (MISSING)
   → hand to Windows
```

| Step | Status |
|------|--------|
| Create project / topic | Exists (both apps, separate) |
| Script | Exists (Pro) |
| Voice | Exists (Pro) |
| Capture | Exists (Creator) |
| Vision | Exists (Creator, not exported) |
| Unified Pro+Creator project | **MISSING** (dual `applicationId`) |
| Preview spanning modules | Partial |
| EXPORT PROJECT | **MISSING** |

---

## 13. Windows workflow (future)

```text
AVSP Project Package
  → Windows import + validate
  → Materialize under M8 projects/{id}/
  → M3 artifacts → bridges/m3_m8 (existing)
  → vision snapshot + media → M7Adapter / local media (existing consumer)
  → M8 render + QC (existing)
  → bridges/m8_m9 → M9 mock/real publish (existing mock)
```

| Step | Status |
|------|--------|
| Import/validate package | **MISSING** |
| Path remap to Windows | **MISSING** |
| M3→M8 voice | **Works** (Phase 2C) once files local |
| M7 snapshot consume | **Works** for Windows JSON; Android writer **MISSING** |
| M8 QC | **Works** |
| M9 mock | **Works** (Phase 2A) |
| Exact missing boundary | **Package export (Android) + import (Windows) + dual-app identity merge** |

---

## 14. Failure handling (design)

| Failure | Required behavior |
|---------|-------------------|
| Transfer interrupted | Fail closed; incomplete package not importable; resume via re-export or checksum retry |
| Media missing vs manifest | Import **REJECT** or quarantine with explicit error listing IDs |
| Corrupted WAV | Phase 2C validation fails; do not render simulated VO silently if package declared voice-required |
| Invalid `project.json` / manifest version | Reject with version error |
| Unsupported video/image | Skip asset with warning **or** reject if it was marked required/selected |
| Incomplete M7 data | Allow import with media-only index; M8 falls back to placeholders/Pexels as today |
| Windows unavailable | Package remains on Android/share medium; no credential dependency |
| Android offline after export | Package must be self-contained (selected media + audio + JSON) |

---

## 15. Security

**MUST NEVER appear in the project package:**

- API keys (`GEMINI_API_KEY`, `PEXELS_API_KEY`, etc.)
- OAuth client secrets / refresh tokens
- YouTube/Telegram credentials
- EncryptedSecureConfigStore contents from Pro
- Passwords / private keys

Package = **project media + editorial metadata only**. Publishing credentials stay on Windows M9 environment.

---

## 16. Performance

| Stage | Time character | Avoid |
|-------|----------------|-------|
| Android preparation | Analysis already done in M7 | Re-run vision on Windows for MVP |
| Package creation | Dominated by media copy | Copying RETAKE/all MediaStore |
| Transfer | I/O / USB / network | Re-encode during export |
| Windows import | Copy + validate | Transcoding WAVs |
| M3→M8 | Cheap PCM concat | WAV→MP3 hop |
| M8 render | Dominant CPU | Second full render |
| QC / M9 | Existing costs | — |

Prefer: **reuse M3 WAVs**, **selected media only**, **no premature transcode**, **Phase 2C concat once**.

---

## 17. Versioning

| Field | Purpose |
|-------|---------|
| `manifest.package_version` | e.g. `"1.0"` — importer compatibility |
| `manifest.min_windows_spine` | e.g. requires Phase 2C bridge capabilities |
| Embedded `ScriptPackage.version` / `AudioPackage.version` | Already `"1.0"` in Kotlin contracts |
| Reject unknown major package_version | Fail closed |

Optional checksums (sha256 per file in manifest): **justified** for transfer integrity of media/WAV; not required for local USB trust MVP but recommended for network transfer.

---

## 18. Protected files / future touch list

### Prefer remain untouched (evidence-based)

| Tree | Recommendation |
|------|----------------|
| `CURRENT_M1_M3/` M2/M3 engines | Untouched — export reads existing artifacts |
| `M4/` | Untouched |
| `M8/m8/vendor/m4/` | Untouched |
| `M8/m8/app/engines/*` | Untouched for MVP (reuse bridges) |
| `M9/` engines | Untouched |
| `bridges/m3_m8/` | **Prefer untouched** — importer feeds its existing input contract |
| `bridges/m8_m9/` | **Prefer untouched** |

### Likely need *new* code later (not modify engines)

| Future module | Role |
|---------------|------|
| Android exporter (Pro and/or Creator) | Build package; copy selected media; serialize vision |
| Windows importer | Validate; materialize; invoke existing bridges |
| Optional thin Creator↔Pro project link | Dual-app gap |

### M6/M7

| Tree | Note |
|------|------|
| `M6/` | Prefer not forked; M7 already supersets capture |
| `M7/` | May need **additive** export writer only (new files), not dataset engine rewrite |

---

## 19. Future implementation sequence (do not implement now)

Repository-derived smallest sequence:

1. Freeze this contract + choose exact relative audio tree shape (Pro-mirror vs flat).
2. Define `manifest.json` / `project.json` schemas (docs + fixtures only).
3. Android **Pro** exporter MVP: script + M3 audio package → zip/folder (no media).
4. Windows importer MVP: unpack → call existing `bridges/m3_m8` → M8 → `bridges/m8_m9`.
5. Android **Creator** media copy for selected KEEP/best → `media/`.
6. M7 snapshot serializer → `vision/m7_snapshot.json` (M8 adapter field names).
7. Importer wires snapshot + media into M8 project/assets paths.
8. Dual-app identity strategy (single export from one APK **or** merge tool) — blocks full creator UX.
9. Validation suite + failure cases (§14).
10. E2E acceptance (§20).  
    **Then** consider Phase 2E+ (unified APK, real publish, deeper EDL from M2 scenes).

---

## 20. Acceptance criteria (future)

Do not implement tests now. Future proof should show:

```text
Android project
  → export package (no secrets)
  → transfer to Windows
  → import validates
  → M3 voice artifacts available on disk
  → M7/media snapshot available (or explicit media-only mode)
  → bridges/m3_m8 produces real VO MP4
  → QC passes
  → bridges/m8_m9 mock publish succeeds
```

Also: existing Phase 2C and Phase 2A tests remain green; M3/M8/M4/M9 engines unmodified.

---

## 21. Open questions

1. **Single APK vs dual export:** Will Pro and Creator merge before exporter ships, or does MVP export from one app only?
2. **Audio tree shape:** Strict Pro `generated/audio/…` mirror vs flattened `audio/`?
3. **Must script be required** when voice+topic exist (M8 can regenerate script)?
4. **Checksum mandate** for v1 or defer to network-transfer profile?
5. **Guided clips without Room projectId:** include via explicit operator selection only?
6. **Should importer set M8 `assets/m7_snapshot.json` or per-project `media/m7_snapshot.json`?** (Controller defaults to assets path today.)
7. **ZIP vs folder** as transfer medium?
8. **Who owns package_version bumps** when M3 AudioPackage version changes?

---

## Appendix A — Evidence index

| Claim | Evidence |
|-------|----------|
| Dual Android apps | `AVSP_PLATFORM_BOUNDARY.md`; Gradle `applicationId`s |
| Pro storage sandbox | `FileAvspStorage.kt` |
| M3 artifacts | `AudioRepositoryImpl`; `bridges/m3_m8/CONTRACTS.md` |
| M2 ScriptPackage fields | `ScriptContracts.kt` |
| M6/M7 capture + Room | M6/M7 `MediaEntity`, GuidedCaptureStorage |
| M7 no snapshot writer | Matrix + Contract map + M7 source absence |
| M8 M7Adapter fields | `M8/m8/app/adapters/m7_adapter.py` |
| M8 live ignores Android packages | Phase 2B plan; controller `narration_audio=None` |
| Phase 2C sufficient for voice | `bridges/m3_m8` |
| Missing cross-platform bus | `AVSP_PLATFORM_BOUNDARY.md` §3 |

---

**END OF PHASE 2D DESIGN DOCUMENT — NO IMPLEMENTATION**
