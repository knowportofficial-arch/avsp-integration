# PHASE 2B — M3 → M8 Contract Plan

**Status:** DESIGN ONLY — no implementation  
**Date:** 2026-03-26  
**Repository:** `knowportofficial-arch/avsp-integration`  
**Prerequisite:** Phase 1 audit accepted; Phase 2A (M8 → M9 bridge) complete  
**Constraint:** Read-only inspection. No M1–M9 source changes. No adapters built yet.

**Authoritative inputs read before this plan:**

| Document | Role |
|----------|------|
| `AVSP_MODULE_INTEGRATION_MATRIX.md` | Module inventory + bridge gaps |
| `AVSP_CONTRACT_MAP.md` | Declared contracts + handoff shapes |
| `AVSP_PLATFORM_BOUNDARY.md` | Android vs Windows boundary |
| `AVSP_DUPLICATE_COMPONENTS.md` | Dual M4 / do-not-delete stance |
| `AVSP_INTEGRATION_AUDIT.md` | Phase 1 verdicts |

**Source inspected (evidence-based, not inferred):**

| Area | Paths |
|------|-------|
| M3 | `CURRENT_M1_M3/app/src/main/java/com/avsp/pro/audio/**`, `.../core/integration/IntegrationContracts.kt`, `.../audio/integration/AudioToVideoContract.kt`, `CURRENT_M1_M3/app/src/test/java/com/avsp/pro/audio/M3AudioTtsTest.kt` |
| M8 | `M8/m8/app/m8/controller.py`, `M8/m8/app/engines/{timeline_composer,effects_composer,creative_director}.py`, `M8/m8/app/schemas/{timeline,edl}.py`, `M8/m8/app/adapters/m4_adapter.py` |
| Phase 2A | `bridges/m8_m9/**` (consumes final MP4 only; does not inject VO) |

---

## 1. Scope

### In scope (this document)

- Exact M3 audio **output** contract from code + tests
- Exact M8 audio **input** contract from code + tests
- Explicit M3 → M8 comparison with COMPATIBLE / INCOMPATIBLE / MISSING / UNKNOWN
- Voice-first timing analysis (narration duration → scene timing → M8 duration)
- Minimum future bridge design (no implementation)
- Protected files / future touch list
- Isolated test design for a future Phase 2C
- Production-time impact of the proposed bridge
- Conceptual production walkthrough for topic *"Airplane Mode — Myth or Fact?"*

### Out of scope (explicit stop)

- Implementing any adapter, bridge, concat, convert, or copy
- Modifying M3, M8, M4, `M8/m8/vendor/m4`, M9, or tests
- Changing schemas, paths, or configuration
- Phase 2C implementation
- Real YouTube publishing
- Generating media artifacts

---

## 2. M3 current audio architecture

### 2.1 Entry points

| Layer | Symbol / path | Role |
|-------|---------------|------|
| UI | `com.avsp.pro.audio.ui.AudioTtsScreen` / `AudioTtsViewModel` | Operator UI for TTS run |
| Repository | `com.avsp.pro.audio.repository.AudioRepository` / `AudioRepositoryImpl` | Orchestrates script handoff → TTS → WAV → package + `voice.json` |
| Engines | `MockTtsEngine`, `AndroidTtsEngine` via `TtsEngineRegistry` | TTS providers |
| Domain contracts | `com.avsp.pro.audio.contract.AudioContracts` | `AudioPackage`, `AudioSegment`, `AudioFormatInfo` |
| Handoff (in-process) | `AudioToVideoContract` / `AudioToVideoHandoff` | Intended **M3 → M4** handoff (not Windows M8) |
| Artifact names | `ArtifactNames`, `ProjectPaths` in `IntegrationContracts.kt` | Path constants |

There is **no** Windows export path and **no** bridge under `bridges/` for M3→M8 today.

### 2.2 Voice generation engine / TTS provider

- Interface: `TtsEngine.synthesize(TtsSynthesisRequest) → TtsSynthesisResult` with `audioBytes`, `durationMs`, `mimeType`.
- Implementations:
  - **`MockTtsEngine`**: deterministic tone WAV via `WavEncoder`; used in unit tests (`M3AudioTtsTest`).
  - **`AndroidTtsEngine`**: Android platform TTS; writes temp `.wav` then returns bytes (same WAV consumer path in repository).
- Provider selection is Android-side; Windows/M8 never invokes these engines.

### 2.3 Generated formats and files

From `AudioRepositoryImpl.save` / `generate` + `WavEncoder` + `M3AudioTtsTest`:

| Artifact | Relative path (project root) | Format / content |
|----------|------------------------------|------------------|
| Segment WAV | `generated/audio/{audioPackageId}/aseg_NN.wav` | RIFF WAV, **pcm_s16le**, **16000 Hz**, **1 channel**, 16-bit |
| Package JSON | `generated/audio/{audioPackageId}/package.json` | Full `AudioPackage` JSON |
| Canonical voice JSON | `generated/audio/voice.json` | Same `AudioPackage` JSON (latest package pointer at audio root) |

**Encoding constants** (`WavEncoder` / `AudioFormatInfo` defaults / test `wavFormatConstants`):

| Property | Value |
|----------|-------|
| Container | `wav` |
| Codec / encoding | `pcm_s16le` |
| Sample rate | `16000` Hz |
| Channels | `1` (mono) |
| Bits | `16` |
| Segment naming | `aseg_{NN}` → file `aseg_NN.wav` (zero-padded index) |
| Package id | `aud_` + 16 hex chars |

**Not produced by M3 (code evidence):**

- `voice.mp3` — `ArtifactNames.VOICE_MP3 = "voice.mp3"` exists in M1 integration constants; `AudioRepositoryImpl.save` comment states it is only a compatibility **pointer note** and does **not** write an MP3 file.
- A single concatenated final voice file — M3 writes **per-segment WAVs only**, plus JSON metadata.

Integration docs (`DataContracts.kt` / `IntegrationContracts.kt`) still *declare* “TTS → voice.mp3, voice.json”; **runtime M3 output does not match the mp3 half of that declaration.**

### 2.4 Segment / timeline structure

`AudioSegment` fields: `segmentId`, `sceneId`, `order`, `sourceText`, `relativeAudioPath`, `durationMs`, `startMs`, `endMs`, `provider`, `language`, `status`, `plannedDurationMs`, `durationDeltaMs`, errors.

Timeline construction in `AudioRepositoryImpl.generate`:

- Segments ordered by M2 narration `order`.
- `startMs` / `endMs` are **contiguous** (`cursor` advances by each `durationMs`).
- Explicit code comment: *“Contiguous timeline for M4; pauseAfter is M2 intent metadata, not a gap.”*
- `totalDurationMs` = `segments.sumOf { it.durationMs }` (= last `endMs` when contiguous).
- Drift warnings when actual vs M2 `plannedDurationMs` exceed validator ratio.

`voice.json` / `package.json` serialize the full `AudioPackage` (not a separate slim VoiceTimeline type).

### 2.5 One file vs many

| Question | Answer (from code) |
|----------|--------------------|
| One final voice file? | **No** |
| Multiple segment files? | **Yes** (`aseg_00.wav`, `aseg_01.wav`, …) |
| Both? | **No** — segments + JSON only; no final mixdown |
| MP3? | **No** (name constant only) |
| WAV? | **Yes** |

### 2.6 Downstream consumers expected by M3

1. **In-app Android → M4 handoff:** `AudioToVideoHandoff` with ordered `VideoAudioSegmentRef` (relative paths + start/end ms) — still inside Android process; documented as M3→M4, not M8.
2. **Canonical artifact:** `generated/audio/voice.json` (+ per-package `package.json` and WAVs).
3. **No code** in M3 references M8 schemas, EDL, `narration_audio`, or Windows paths.

---

## 3. M8 current audio architecture

### 3.1 Entry points

| Layer | Symbol / path | Role |
|-------|---------------|------|
| Controller | `AutonomousProductionController.run` in `M8/m8/app/m8/controller.py` | Full Topic → MP4 pipeline |
| Director | `CreativeDirector` | Builds script + scene plan + `CreativeEDL` from topic + **duration string** |
| Composer | `TimelineComposer.compose(edl, audio_path=None)` | Builds `Timeline`; optional single `audio_path` stamped onto narration events |
| Render | `EffectsComposer.compose(timeline, output, narration_audio=...)` | FFmpeg assembly + optional real VO |
| Schemas | `app/schemas/timeline.py`, `app/schemas/edl.py` | `NarrationEvent`, `Timeline`, `CreativeEDL` |
| Unused live path | `M4Adapter` | Present; live render uses **EffectsComposer**, not vendor M4 |

Live autonomous path (evidence in `controller.py`):

```text
topic + duration → CreativeDirector → CreativeEDL
  → TimelineComposer.compose(edl)          # audio_path default None
  → EffectsComposer.compose(..., narration_audio=None)
  → simulated tone VO muxed into projects/{id}/render/final.mp4
```

Hard-coded wire: `fx.compose(timeline, out_mp4, narration_audio=None)` at controller render stage.

### 3.2 Project / timeline / EDL input

- Primary production input: **topic string + duration string** (e.g. `"5m"`) → `parse_duration` → `duration_sec`.
- Director emits **`CreativeEDL`**: scenes with `narration_segment` **text**, start/duration in **seconds**, media, punch/sfx/etc.
- `Timeline` holds `NarrationEvent(start, end, text, audio_path=Optional)`.
- Live path: `audio_path` remains **None** on every narration event.

### 3.3 Audio input fields

| Mechanism | Field | Used on live path? |
|-----------|-------|--------------------|
| Schema optional path | `NarrationEvent.audio_path` | **No** (unset) |
| Composer override | `TimelineComposer.compose(edl, audio_path=...)` | **API supports**; controller does **not** pass it |
| EffectsComposer | `compose(..., narration_audio: Optional[Path])` | Called with **`None`** |

When `narration_audio` is provided (EffectsComposer `_build_audio`):

- FFmpeg reads the file, **trims with `-t {timeline.duration}`**, encodes AAC base track, mixes with SFX/BGM.
- Expectation is **one complete narration file**, not a segment folder.
- If missing/nonexistent → **simulated sine VO** for pipeline audibility (not M3 speech).

### 3.4 Formats, duration, copy/load

| Topic | Evidence |
|-------|----------|
| Expected filename | **None fixed** — any path passed as `narration_audio` |
| Expected path | Local path readable on M8 host |
| Supported formats | Whatever FFmpeg accepts for `-i` (WAV acceptable); **no M3-specific reader** |
| One complete narration? | **Yes** if provided |
| Segmented audio? | **No first-class support** — concat/adapter required before compose |
| Duration source | CLI/`run` **duration** → `CreativeDirector` `duration_target` / scene plan → `Timeline.duration` |
| Does audio duration control video duration? | **No** — audio is fitted **to** `timeline.duration` via `-t total` |
| Where audio enters final render | `_build_audio` → `final_audio.m4a` → mux into `render/final.mp4` |

### 3.5 Relation to Phase 2A

`bridges/m8_m9` consumes **final MP4 + report metadata** only. It does **not** inject or validate VO. Fixing M3→M8 VO is a separate contract from M8→M9 publishing.

---

## 4. M3 output contract

**Canonical M3 voice package (relative to Android project storage root):**

```text
generated/audio/{audioPackageId}/
  aseg_00.wav
  aseg_01.wav
  ...
  package.json          # AudioPackage
generated/audio/voice.json   # latest AudioPackage (same schema)
```

**Audio encoding (locked by `WavEncoder` + package metadata + tests):**

| Property | Value |
|----------|-------|
| Container | WAV |
| Codec | pcm_s16le |
| Sample rate | 16000 Hz |
| Channels | 1 (mono) |
| Naming | `aseg_{NN}.wav` |

**Timing / identity metadata (`AudioPackage` in `voice.json` / `package.json`):**

| Field | Meaning |
|-------|---------|
| `projectId` | Android project id |
| `scriptId` / `audioPackageId` | Script + package identity |
| `language` / `provider` / `voice` | Voice settings |
| `totalDurationMs` | Sum of segment durations |
| per-segment `startMs` / `endMs` / `durationMs` | Contiguous timeline in ms |
| `sceneId` / `order` / `sourceText` | Scene alignment to M2 |
| `relativeAudioPath` | Path to each WAV |
| `metadata.format` | wav / 16000 / mono / pcm_s16le |

**Handoff object (in-process only):** `AudioToVideoHandoff` via `AudioToVideoContract.fromPackage`.

**Explicit non-outputs:** no `voice.mp3` file, no single mixdown WAV/MP3, no Windows path remapping, no M8 `CreativeEDL` / `Timeline` emission.

---

## 5. M8 input contract

**What M8 actually consumes for narration today:**

| Input | Required? | Live path |
|-------|-----------|-----------|
| Topic + duration string | **Yes** | Yes |
| EDL narration **text** events | **Yes** (director-generated) | Yes |
| External narration audio file | **Optional** | **Not supplied** (`narration_audio=None`) |
| M3 `voice.json` | No | Never read |
| M3 `package.json` | No | Never read |
| M3 `aseg_*.wav` folder | No | Never read |
| `AudioToVideoHandoff` | No | Never read |

**Optional real-audio contract (EffectsComposer when path given):**

| Property | Expectation |
|----------|-------------|
| Count | **One** file |
| Role | Full narration track for the timeline |
| Duration behavior | Trim to **`timeline.duration`** (`ffmpeg -t total`) |
| Path | Local filesystem path on M8 host |
| Format | FFmpeg-decodable (WAV OK) |

**Duration authority:** caller `duration` / director `duration_target` — **not** measured VO length.

---

## 6. Contract comparison

| Dimension | M3 PRODUCES | M8 EXPECTS | Classification |
|-----------|-------------|------------|----------------|
| Filename | `aseg_NN.wav` + `package.json` / `voice.json` | Arbitrary single `narration_audio` path; no fixed name | **INCOMPATIBLE** (shape) |
| Path | Android `generated/audio/...` under project storage | Windows/local path under M8 project tree | **INCOMPATIBLE** (platform + location) |
| Format | WAV pcm_s16le 16 kHz mono | Any FFmpeg-readable file | **COMPATIBLE** (codec/container OK if delivered) |
| Duration | `totalDurationMs` from real TTS | Timeline from topic `duration` / `duration_target`; audio trimmed to it | **INCOMPATIBLE** (authority) |
| Segmentation | Multiple WAVs + contiguous ms timeline | Single optional narration file | **INCOMPATIBLE** |
| Metadata | `AudioPackage` JSON (`voice.json`) | `CreativeEDL` / `Timeline` (Python dataclasses) | **INCOMPATIBLE** |
| Schema | Kotlin `AudioPackage` / `AudioToVideoHandoff` | Pydantic/dataclass M8 schemas | **INCOMPATIBLE** |
| Project ID | Android `projectId` + `audioPackageId` | M8 `project_id` (`proj_…`) | **MISSING** (no shared ID map) |
| Language | In `AudioPackage.language` | Topic/script language in director; not read from M3 | **MISSING** (no handoff) |
| Sample rate | 16000 Hz | Not enforced; FFmpeg resamples as needed | **COMPATIBLE** (acceptable) |
| Channels | Mono | Not enforced (sim VO uses stereo lavfi) | **COMPATIBLE** (acceptable) |
| Timing information | Segment `startMs`/`endMs` in `voice.json` | Narration event `start`/`end` **seconds** from director scene plan | **INCOMPATIBLE** (units + source) |
| Transport / bus | None to Windows | None from Android | **MISSING** |
| Final mixdown | None | Prefers one file if real VO used | **MISSING** |
| Live VO injection | N/A | `narration_audio=None` → simulated tone | **MISSING** (runtime wire) |
| Declared `voice.mp3` | Not written | Not required by EffectsComposer | **INCOMPATIBLE** vs M1 name myth; **COMPATIBLE** with WAV-in |

**Summary:** COMPATIBLE (format/rate/channels) · INCOMPATIBLE (filename shape, path, duration authority, segmentation, metadata/schema, timing source, voice.mp3 myth) · MISSING (transport, ID map, mixdown, live wire).

**Do not invent a compatibility layer in engines** — classify only; adapter belongs outside M3/M8 cores.

---

## 7. Voice-first timing analysis

AVSP target chain:

```text
Narration → exact duration → scene timing → video segment duration → M8 final duration
```

### 7.1 Where exact voice duration is calculated

| Location | Behavior |
|----------|----------|
| M3 TTS engines | Return `durationMs` per synthesis (`MockTtsEngine` from WAV PCM size; Android TTS from result) |
| M3 `AudioRepositoryImpl` | Builds contiguous ms timeline; `totalDurationMs` = sum of segments; persisted in `voice.json` |
| M8 `parse_duration` + `CreativeDirector` | Sets `duration_sec` / scene lengths from **topic duration string**, independent of any VO file |
| M8 `EffectsComposer._build_audio` | If VO provided, **trims audio to `timeline.duration`**; does not replan scenes from VO length |

### 7.2 Does M3 voice timeline contain segment start/end?

**Yes.** Contiguous ms ranges in `AudioPackage.segments` inside `voice.json` / `package.json`. Evidence: `AudioRepositoryImpl` + `M3AudioTtsTest.audioPackageValidationAndTiming` + `AudioValidator`.

### 7.3 Can M8 consume those timings?

**Not today.** M8 never reads `voice.json`. Narration event times come from the director’s scene plan in **seconds**. Optional `NarrationEvent.audio_path` / `compose(..., narration_audio=)` are unused on the live path.

### 7.4 Does M8 derive duration independently?

**Yes.** `AutonomousProductionController.run(..., duration=...)` → `duration_sec` → EDL/timeline length. Audio does not drive video length.

### 7.5 Timing mismatch risk

| Risk | Severity | Evidence |
|------|----------|----------|
| M3 VO length ≠ M8 duration target | **High** | Independent authorities; live path ignores M3 duration |
| Contiguous M3 ms timeline ≠ M8 narration event seconds | **High** | Different schemas; no mapper |
| M2 `pauseAfter` not in M3 gaps | **Medium** | Repository packs segments contiguously by design |
| Providing raw multi-WAV without concat | **High** | EffectsComposer expects one file |
| Using simulated tone while M3 VO exists | **High** | `narration_audio=None` hardcoded in controller |
| Longer VO silently trimmed | **High** | `_build_audio` uses `-t total` |

**Verdict:** Current M3 output **does** contain enough timing metadata for a **future adapter** to drive voice-first planning, but **M8 does not consume it**. Without a bridge that (a) delivers one narration file and (b) aligns timeline duration to `totalDurationMs` (or remaps scenes), voice-first AVSP timing is **not satisfied**.

---

## 8. Exact incompatibilities

1. **Artifact cardinality:** M3 multi-WAV vs M8 single `narration_audio`.
2. **Missing mixdown:** No M3 final voice file for M8 to point at.
3. **Platform path gulf:** Android sandbox paths vs Windows M8 filesystem.
4. **No transport:** No export/bus copying M3 artifacts to the Windows host.
5. **Schema gulf:** `AudioPackage` / `voice.json` ≠ `CreativeEDL` / `Timeline`.
6. **Duration authority:** M3 measured ms vs M8 planned duration string / `duration_target`.
7. **Timing units/source:** M3 ms segment timeline vs M8 director seconds.
8. **Live wire gap:** Controller hardcodes `narration_audio=None`.
9. **Shared identity:** No Android `projectId`/`audioPackageId` ↔ M8 `project_id` mapping.
10. **MP3 name myth:** `ArtifactNames.VOICE_MP3` is declared; M3 does not emit MP3 — do not plan around `voice.mp3` unless an adapter creates it.
11. **Intended consumer mismatch:** M3 handoff targets **M4** (`AudioToVideoContract`); Windows production render path is **EffectsComposer**, not live `M4Adapter` (Phase 1: keep both M4 trees; do not force rewrite).

---

## 9. Minimum bridge architecture

**Preferred shape (design only):**

```text
M3 (unchanged)
  → M3 voice artifact folder (aseg_*.wav + voice.json)
    → small adapter/normalizer (NEW, under bridges/)
      → one narration file + duration + M8 compose inputs
        → M8 render (existing EffectsComposer API)
```

### 9.1 Necessary operations (repository-justified)

| Operation | Necessary? | Why |
|-----------|------------|-----|
| Discover M3 package (`voice.json` + referenced `aseg_*.wav`) | **Yes** | Only durable M3 contract |
| Concatenate segment WAVs in timeline order | **Yes** | M8 expects one file |
| Convert WAV → MP3 | **No** (not required) | EffectsComposer/FFmpeg accepts WAV; avoid extra encode |
| Loudness normalize | **Optional / later** | Not required for acceptance; adds time |
| Copy/rename into M8 project audio/input dir | **Yes** | Platform path + stable `narration_audio` path |
| Create/adapt metadata for M8 | **Yes** | At minimum: path + duration seconds from `totalDurationMs` |
| Align Topic/EDL duration to voice | **Yes for voice-first** | Else FFmpeg will trim/pad wrongly |
| Map voice timing → scene/EDL times | **Yes for full voice-first**; **MVP may use duration-only sync first** | Full scene remap is larger than concat+wire |
| Map project / package IDs | **Yes** | Traceability Android → Windows |
| Transport Android → Windows | **Yes** (external to engines) | MISSING bus; adapter assumes artifacts already on Windows or copied |

### 9.2 Minimum viable bridge (smallest useful)

1. **Input:** directory containing valid M3 `voice.json` + referenced `aseg_*.wav` (already on Windows workspace or copied by operator/CI).
2. **Validate:** format 16 kHz mono PCM WAV; timeline contiguous; files exist; `totalDurationMs` consistent.
3. **Concat:** lossless WAV concat (or FFmpeg concat demuxer) → e.g. `narration.wav` (keep PCM; **no MP3** unless forced).
4. **Duration:** `duration_sec = totalDurationMs / 1000.0`; pass into M8 run / director as the duration authority.
5. **Wire options (prefer least invasive):**
   - **A (preferred):** Bridge pipeline (Phase 2A style under `bridges/m3_m8/`) that invokes M8 compose APIs and passes `narration_audio=<concat path>` into `EffectsComposer.compose` — **without** editing M3, vendor M4, or EffectsComposer internals.
   - **B:** If autonomous controller must stay untouched initially, wrapper duplicates the minimal stage chain and calls EffectsComposer with the path (same pattern as Phase 2A wrapping M8→M9).
   - Avoid editing controller solely to flip `None` → path **unless** that one-line wire is clearly safer than a wrapper; prefer adapter-first.
6. **Output:** M8 project with real VO in `final.mp4`; duration approximately matching narration.

### 9.3 Explicitly avoid in the bridge

- Rewriting `MockTtsEngine` / `AndroidTtsEngine`
- Rewriting EffectsComposer VO mux logic (already supports a file)
- Touching `M8/m8/vendor/m4` or root `M4/`
- Inventing `voice.mp3` as a required intermediate
- Changing Phase 2A M8→M9 contracts beyond consuming a better MP4

---

## 10. Files that would need modification

**Later implementation only — not in this phase.**

| Path | Likely change |
|------|----------------|
| `bridges/m3_m8/` (new package) | Adapter: discover, validate, concat, duration extract, path handoff |
| `bridges/m3_m8/pipeline.py` (new) | Optional end-to-end wrapper (mirror Phase 2A style) |
| `bridges/m3_m8/CONTRACTS.md` (new) | Frozen bridge contract |
| `tests/test_phase2b_m3_m8_bridge.py` or `tests/test_phase2c_…` (new) | Isolated bridge tests |
| Optionally thin wrapper only around M8 entry | Pass `narration_audio` + aligned duration **without** rewriting planner/composer cores |

**Prefer zero edits** to:

- M3 Kotlin sources under `CURRENT_M1_M3/`
- `M8/m8/app/engines/effects_composer.py` (already has `narration_audio`)
- `M8/m8/app/engines/timeline_composer.py` (already accepts optional `audio_path`)

---

## 11. Files that must remain untouched

| Path / tree | Rule |
|-------------|------|
| `CURRENT_M1_M3/**` (M3) | Prefer **unchanged** — output already sufficient for an external adapter |
| `M4/**` | **Do not modify** (Phase 1 dual-tree protection) |
| `M8/m8/vendor/m4/**` | **Do not modify** |
| `M9/**` | Out of scope for M3→M8 |
| `bridges/m8_m9/**` | Leave Phase 2A green; do not break |
| Existing M3 unit tests | Must remain green without edits for bridge work |
| Existing M8 unit tests | Must remain green |
| Schemas inside M3/M8 engines | Prefer adapter mapping over schema rewrites |

**Stance:**

- **M3 can remain unchanged** for Phase 2C if artifacts are exported/copied as-is.
- **M8 engines can remain unchanged** if the bridge supplies `narration_audio` and aligns duration via existing APIs.
- **Adapter over stable engines** is mandatory preference.

---

## 12. Test plan

**Do not write these tests yet.** Future isolated bridge tests should cover:

| # | Test | Intent |
|---|------|--------|
| 1 | M3 output discovery | Locate `voice.json` + ordered `aseg_*.wav` from a fixture package |
| 2 | Audio format validation | Assert WAV pcm_s16le / 16 kHz / mono (reject wrong formats) |
| 3 | Duration validation | `sum(segment.durationMs) == totalDurationMs`; concat duration ≈ total |
| 4 | Timing extraction | Parse `startMs`/`endMs`; verify contiguous timeline |
| 5 | Adapter conversion | Multi-WAV → single narration file; path stable for M8 |
| 6 | M8 project creation | Bridge builds/invokes project with duration aligned + audio attached |
| 7 | M8 audio acceptance | `EffectsComposer.compose(..., narration_audio=...)` succeeds (real VO, not tone-only fallback) |
| 8 | Final MP4 duration ≈ narration | Tolerance band (e.g. ±0.5s or ±5%) vs `totalDurationMs` |
| 9 | Audio present in final MP4 | Probe streams / energy / ffmpeg detect audio track |
| 10 | Existing M3 tests green | No M3 source/test edits; suite still passes |
| 11 | Existing M8 tests green | No M8 engine breakage |
| 12 | Phase 2A M8→M9 tests green | `tests/test_phase2a_bridge.py` still passes |

**Fixture strategy:** synthetic M3-like package under `bridges/m3_m8/fixtures/` (or tests fixtures) — **do not** require a live Android device for CI.

---

## 13. Production-time impact

| Factor | Impact | Guidance |
|--------|--------|----------|
| Concatenate PCM WAVs | Low | Near-lossless, fast vs re-encode |
| WAV → MP3 convert | Avoid | Unnecessary CPU; M8 accepts WAV |
| Loudness normalize | Medium | Defer unless QC requires |
| File copy Android→Windows | I/O bound | Reuse exported package; avoid re-TTS |
| Re-TTS on Windows | High | Do **not** regenerate voice in M8 if M3 package exists |
| Re-encode video only | Existing M8 cost | Bridge should not add a second video encode |
| Near-zero overhead path | Achievable | Discover → concat → copy once → pass `narration_audio`; set duration from `totalDurationMs` |

**Goal alignment:** Prefer **reuse M3 files**, **one concat**, **no MP3 hop**, **no engine rewrite** → minimal added production time vs today’s simulated-VO path.

---

## 14. Acceptance criteria

Phase 2B **design** is accepted when:

1. This document exists as the sole Phase 2B deliverable.
2. M3 output and M8 input are stated from **code/tests**, not inference.
3. Every comparison row is classified COMPATIBLE / INCOMPATIBLE / MISSING / UNKNOWN.
4. Voice-first timing gaps are explicit.
5. Minimum bridge lists only necessary operations.
6. Protected trees (`M4/`, `M8/m8/vendor/m4/`, prefer untouched M3/M8 engines) are listed.
7. Test plan covers discovery through Phase 2A regression.
8. Git shows **only** this markdown file as the intentional change (no source/test/media edits).

Phase 2C implementation (future) should be considered successful only when tests 1–12 pass and final MP4 uses real M3-derived narration with duration aligned to voice.

---

## 15. Risks

| Risk | Detail | Mitigation (future) |
|------|--------|---------------------|
| Duration authority conflict | Planner target vs real VO length | Set M8 duration from `totalDurationMs` or replan scenes from voice timeline |
| Silent VO trim | Longer narration cut by `-t total` | Align duration before compose |
| Partial export | Missing `aseg` files vs `voice.json` | Fail closed in discovery validation |
| Sample-rate assumptions | Non-mock Android TTS drift | Validate WAV header before concat |
| pauseAfter ignored | Script pauses not in M3 timeline | Document; optional silence insert in adapter later |
| Touching vendor M4 | Accidental dual-tree edits | Hard protect; adapter-only |
| Breaking Phase 2A | Publishing bridge regressions | Keep M8→M9 tests green; VO quality improves MP4 only |
| Assuming `voice.mp3` | M1 name ≠ M3 output | Never require MP3 intermediate |
| Platform transport | Artifacts never leave Android | Explicit export/copy step outside engines |
| Confusing M4 vs EffectsComposer | M3 handoff names M4; live M8 uses EffectsComposer | Bridge targets EffectsComposer `narration_audio`; leave vendor M4 untouched |

---

## 16. Recommended implementation sequence

**Stop after this document. Sequence below is for a future Phase 2C only.**

1. Freeze this contract plan as the implementation brief.
2. Add `bridges/m3_m8/` skeleton + `CONTRACTS.md` (no engine edits).
3. Build fixture M3 package (multi WAV + `voice.json` as `AudioPackage`).
4. Implement discovery + format/duration/timing validation.
5. Implement ordered WAV concat → single `narration.wav`.
6. Implement wrapper pipeline: map `totalDurationMs` → M8 duration; call EffectsComposer with `narration_audio`.
7. Assert MP4 has audio + duration ≈ narration.
8. Run M3, M8, and Phase 2A regression suites.
9. Only then consider optional timing remap of EDL/timeline events from segment `startMs`/`endMs` (full voice-first scene sync).
10. Do **not** start real YouTube publishing as part of M3→M8 work.

---

## Appendix A — Conceptual production walkthrough

**Topic:** *"Airplane Mode — Myth or Fact?"*  
**Assumption:** M3 generates approximately 30–60 seconds of narration (illustrative range only; no fabricated package IDs or durations invented as facts).

### How current M3 output would travel today

```text
[Android] Operator runs M3 TTS for the script
    → generated/audio/{aud_…}/aseg_00.wav … aseg_N.wav
    → generated/audio/{aud_…}/package.json
    → generated/audio/voice.json   (AudioPackage; totalDurationMs in ~30–60s range)
    → AudioToVideoHandoff available in-process for M4-style consumers only

[Windows M8] Autonomous run for same topic title
    → duration string drives CreativeDirector / Timeline (independent of M3)
    → TimelineComposer.compose(edl) without audio_path
    → EffectsComposer.compose(..., narration_audio=None) → simulated tone
    → final.mp4 published via Phase 2A without M3 VO
```

**Missing contract for this flow:** transport of the M3 package to Windows; concat to one file; wire as `narration_audio`; align M8 duration authority to `voice.json` `totalDurationMs` (and optionally segment timings).

### How the minimum future bridge would carry it (design)

```text
Export/copy M3 folder → bridges/m3_m8 adapter
  → validate + concat → narration.wav
  → M8 duration_sec = totalDurationMs / 1000
  → EffectsComposer.compose(..., narration_audio=narration.wav)
  → final.mp4 with real VO ≈ narration length
  → existing Phase 2A M8→M9 mock publish unchanged
```

---

## Appendix B — Evidence index (primary)

| Claim | Evidence |
|-------|----------|
| M3 writes WAV segments + `package.json` + `voice.json` | `AudioRepositoryImpl.kt` `generate` / `save` |
| 16 kHz mono pcm_s16le | `WavEncoder.kt`; `AudioFormatInfo`; `M3AudioTtsTest.wavFormatConstants` |
| No voice.mp3 file from M3 | No writer; save() comment only; `ArtifactNames.VOICE_MP3` is name constant |
| Contiguous start/end ms; pauseAfter not a gap | `AudioRepositoryImpl` comments + cursor logic; `AudioValidator`; timing test |
| Handoff is M3→M4 shaped | `AudioToVideoContract.kt` |
| M8 live path `narration_audio=None` | `controller.py` render `_render` |
| Single-file VO support exists | `EffectsComposer.compose` / `_build_audio` trim with `-t total` |
| Duration from topic duration string | `AutonomousProductionController.run` + `CreativeDirector` |
| TimelineComposer can stamp one `audio_path` | `timeline_composer.py` `compose(edl, audio_path=...)` — unused live |
| Phase 2A ignores VO injection | `bridges/m8_m9/` consumes final MP4 |

---

**END OF PHASE 2B DESIGN DOCUMENT — NO IMPLEMENTATION**
