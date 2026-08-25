# AVSP Duplicate Components

**Phase:** 1 — Audit Only  
**Date:** 2026-08-25  
**Authority:** Repository file comparison + adapter/controller inspection  

**Rule:** Do **not** choose winners for rewrite in this phase unless evidence is conclusive. Prefer adapters.

---

## 1. Executive Findings

| Pair | Relationship | Evidence | Canonical stance (audit) | Status |
|------|--------------|----------|--------------------------|--------|
| `M4/AVSP_M4_Video_Engine` vs `M8/m8/vendor/m4` | **Identical frozen copy** (internal dependency vs standalone) | `diff` empty; 10/10 file MD5 match | Keep **both**: standalone = reference package; vendor = M8 frozen dependency | **PASS** |
| M4 `VideoEngine` vs M8 `EffectsComposer` | **Complementary / partial supersession** | M8 stage `call_m4_renderer` calls EffectsComposer; `M4Adapter` unused on happy path | Keep both; document dual renderer; do not delete M4 | **NEEDS FIX** (docs/wiring clarity) |
| M4 `AutonomousVideoEngine` vs M8 `AutonomousProductionController` | **Overlapping names; complementary scope** | M4 planner stub only (plan JSON); M8 full pipeline | M8 is production autonomy; M4 stub is non-canonical for E2E | **PASS** (no conflict if roles clear) |
| M6 `android/` vs M7 `android/` capture tree | **M7 supersets M6** (identical capture sources) | 74 capture files MD5-identical; M7 adds `dataset/**` | Treat **M7 tree as integrated capture+dataset app**; M6 = capture-only snapshot | **PASS** |
| `com.avsp.pro` vs `com.avsp.creator` | **Parallel Android products** | Different applicationId, Room DBs, Project models | Do not merge blindly | **NEEDS FIX** (strategy) |
| CURRENT_M1_M3 stub folders vs real M4–M9 | **Placeholders vs implementations** | Stub READMEs only | Stubs must not be filled with rewrites | **PASS** (if left alone) |
| M5 vs reserved `youtube/`/`screen/`/`ocr/` | Domain overlap | Stubs empty | M5 is real implementation | **PASS** |
| M9 vs reserved `publishing/` | Domain overlap | Stub empty | M9 is real implementation | **PASS** |
| M8 FinalQC vs missing M10 | Partial functional overlap | M8 `qc/` exists; M10 folder absent | M8 QC interim; M10 still MISSING as module | **MISSING** / **FUTURE** |
| M8 research/script vs M2 Script AI | Logical overlap | Both produce script-like JSON | Different platforms; bridge later | **FUTURE** consolidation |
| M5 sample_outputs vs M5 runtime schema | **Divergent duplicates** | Field names differ | Runtime dataclasses authoritative | **NEEDS FIX** |

---

## 2. Special Investigation: M4 Standalone vs `m8/vendor/m4`

### 2.1 Question

Are they identical, forked, complementary, or internal dependency vs standalone?

### 2.2 Answer

**Identical frozen vendored dependency of the standalone implementation.**

Not a fork. Not complementary variants. Same bytes.

### 2.3 Evidence

| Check | Result |
|-------|--------|
| Source file count | 10 files each |
| `diff` (excluding temp/output/__pycache__) | No differences |
| Per-file MD5 | All pairs identical |
| `manifest.json` version | both `1.0-qc-work` |
| `VideoEngine.render` signature | identical |
| `AutonomousVideoEngine` API | identical |
| Templates / tests | identical |
| M8 docs | `vendor/m4/** = copy of frozen M4 (unmodified source)` |

### 2.4 How M8 uses it

- Loader: `app/adapters/m4_adapter.py` → importlib package `m4_frozen`
- Controller constructs `self.m4 = M4Adapter(...)`
- Live stage `call_m4_renderer` → **`EffectsComposer.compose()`**, not `self.m4.render()`
- M4 remains available for simpler assemblies and unit tests

### 2.5 Classification

| Component | Status |
|-----------|--------|
| Standalone M4 package | **PASS** |
| Vendored copy integrity | **PASS** |
| Decision “pick one engine and delete the other” | **FUTURE** / **not justified** by drift (there is none) |
| Align live render with M4Adapter | Optional later; EffectsComposer is the creative path — **NEEDS FIX** only for naming/docs |

### 2.6 Must not change (this area)

- Do not “dedupe” by deleting `vendor/m4` without a packaging strategy  
- Do not rewrite M4 to match EffectsComposer features in-place  
- Do not replace EffectsComposer with M4 solely for architecture purity  

---

## 3. Renderer Overlap: M4 vs EffectsComposer

| Capability | M4 VideoEngine | M8 EffectsComposer |
|------------|----------------|--------------------|
| Still/video/color assembly | Yes | Yes |
| Narration mux | Yes | Yes |
| Template shorts/landscape | Yes | Via timeline WxH |
| Basic subtitles | Yes | Yes (Windows font paths) |
| Punch / xfade / SFX / emoji | No | Yes |
| Creative EDL timeline | Via thin media list | Native Timeline schema |
| Used in M8 `run()` | No (adapter idle) | **Yes** |

```
ISSUE: Duplicate FFmpeg render stacks with confusing stage name.
ROOT CAUSE: Creative effects exceeded M4 scope; new composer added; M4 frozen & vendored.
IMPACT: Ambiguity for integrators; fear of “which MP4 is canonical.”
SEVERITY: MEDIUM
RECOMMENDED FIX: Document: EffectsComposer = M8 production renderer; M4 = frozen assembler dependency + adapter for simple jobs. Rename stage in a later docs/code clarity PR if needed.
TEST REQUIRED: One pipeline run asserts final path is projects/<id>/render/final.mp4; M4 adapter tests still pass independently.
```

---

## 4. Android Capture / Dataset Triplication

### 4.1 Three slots

| Location | Reality |
|----------|---------|
| `CURRENT_M1_M3/camera`, `dataset` | README stubs (“Reserved…”) |
| `M6/android` | Full capture app |
| `M7/android` | Capture **identical to M6** + dataset intelligence |

### 4.2 M6 vs M7

- **Relationship:** complementary versions on one lineage — M7 is the integrated Creator app.  
- **Not** competing forks of capture code.  
- Shipping **only M6** loses dataset APIs required for M8 export.  

**Canonical for capture+dataset integration:** `M7/android`  
**Canonical for capture-only historical reference:** `M6/android`

Classification: **PASS** (if M7 is the Android media producer going forward)

```
ISSUE: GuidedCapture writes clip bundles but does not always ingest into M7 Room/dataset APIs.
ROOT CAUSE: Guided path was M6 filesystem-first.
IMPACT: Some captures invisible to snapshot/export.
SEVERITY: MEDIUM
RECOMMENDED FIX: Later: call ingestMedia after guided save; do not rewrite camera stack.
TEST REQUIRED: Guided clip appears in DatasetAutomationContract.snapshot.
```

### 4.3 `com.avsp.pro` vs `com.avsp.creator`

| Aspect | Pro (M1–M3) | Creator (M6–M7) |
|--------|-------------|-----------------|
| applicationId | `com.avsp.pro` | `com.avsp.creator` |
| Project model | `projectId`, name, aspect, language… | `id`, title, topic, category… |
| DB | `AvspDatabase` | `avsp_creator_db` |
| Script/TTS | Yes | No |
| Camera/dataset | Stubs | Yes |

```
ISSUE: Duplicate product shells block single-APK UX.
ROOT CAUSE: Parallel module delivery.
IMPACT: Integration cannot assume one Android process holds all state.
SEVERITY: HIGH (product); LOW if Windows-centric E2E first
RECOMMENDED FIX: Defer APK merge. Use export bridges. Any future merge is multi-module Gradle work — not a blind folder copy.
TEST REQUIRED: N/A until merge plan approved.
```

---

## 5. Script / Research Overlaps

| Producer | Output | Consumer today |
|----------|--------|----------------|
| M2 Android | `ScriptPackage` / `script.json` | M3 only |
| M5 Python | YouTube/Screen handoff JSON | Nobody wired |
| M8 CreativeDirector | `research/script.json`, scene_plan | M8 EDL builder |

These are **complementary at platform level**, overlapping at **product workflow** level.

```
ISSUE: Three script-like generators; no single authority for “approved narration script.”
ROOT CAUSE: Each module solved local needs.
IMPACT: Risk of divergent scripts between Android TTS and M8 burn-in captions.
SEVERITY: HIGH for quality E2E; MEDIUM for M8-only demos
RECOMMENDED FIX: Define authority per mode: (A) Android-approved M2/M3 drives Windows render; (B) M8 autonomous mode owns script when Android not in loop. Adapters enforce one mode per run.
TEST REQUIRED: Mode A and Mode B fixtures; assert single narration source in timeline.
```

Classification: **FUTURE** policy / **NEEDS FIX** before dual-path E2E

---

## 6. Publishing / QC Overlaps

| Component | Role | Duplicate? |
|-----------|------|------------|
| `CURRENT_M1_M3/publishing/` stub | Placeholder | Ignore |
| M9 publishers | Real | Canonical publish |
| M1 `PublishingInput` interface | Stub | Future Android trigger |
| M8 FinalQC | Render QC | Interim QC |
| M10 | Manifest only | Missing module |
| `CURRENT_M1_M3/quality/` stub | Placeholder | Ignore |

---

## 7. Subtitle / Caption Paths

| Path | Location | Notes |
|------|----------|-------|
| M4 subtitle burn | `video_engine.py` | libass/SRT; Linux fonts |
| M8 caption burn | `EffectsComposer` | Hard-fail tests exist; Windows fonts |
| `CURRENT_M1_M3/subtitles/` | Stub | Reserved |

**Relationship:** Parallel implementations; M8 path is production for autonomous pipeline.

Classification: **PASS** coexistence if M4 not used for caption-heavy Windows jobs

---

## 8. Duplicate Inventory Table (actionable)

| ID | Components | Type | Action now | Action later |
|----|------------|------|------------|--------------|
| D1 | M4 ↔ vendor/m4 | Identical copy | Keep both | Optional single-source packaging |
| D2 | VideoEngine ↔ EffectsComposer | Feature overlap | Document dual use | Clarify stage name |
| D3 | AutonomousVideoEngine ↔ M8 controller | Name overlap | Ignore stub for E2E | Deprecate stub docs |
| D4 | M6 ↔ M7 capture | Superset | Prefer M7 for media | Archive M6 as tag/reference |
| D5 | Pro ↔ Creator apps | Product split | Bridge files | Planned merge |
| D6 | M1 stubs ↔ real modules | Placeholders | Leave empty | Never reimplement |
| D7 | M5 samples ↔ runtime | Schema fork | Prefer runtime | Regenerate samples |
| D8 | M2 / M5 / M8 scripts | Workflow overlap | Mode policy | Adapters |
| D9 | M8 QC ↔ M10 | Module gap | Use M8 QC interim | Add M10 |

---

## 9. What MUST NOT Be Changed (Duplicates)

1. Do not delete `M4/AVSP_M4_Video_Engine` or `M8/m8/vendor/m4` based on preference.  
2. Do not replace EffectsComposer with M4 (or vice versa) without a defect report.  
3. Do not paste M6/M7 sources into `CURRENT_M1_M3/camera` stubs.  
4. Do not merge `com.avsp.pro` and `com.avsp.creator` without an explicit merge design.  
5. Do not “fix” M2 by rewriting it into M8’s CreativeDirector.  
6. Do not remove M8 FinalQC because M10 is missing.
