# AVSP Duplicate Components

**Phase:** 1 — Audit Only  
**Date:** 2026-08-25  
**Authority:** Repository file comparison + adapter/controller inspection  

**Rule:** Do **not** choose winners for rewrite in this phase unless evidence is conclusive. Prefer adapters.

---

## 1. Executive Findings

| Pair | Relationship | Evidence | Audit stance (**no premature winner**) | Status |
|------|--------------|----------|----------------------------------------|--------|
| `M4/AVSP_M4_Video_Engine` vs `M8/m8/vendor/m4` | **Byte-identical trees**; roles differ (standalone package vs M8 vendored copy) | `diff -rq` exit 0; 10/10 SHA-256 match | **Do not choose one as canonical.** Keep both until an explicit packaging decision. | **PASS** (identity) |
| M4 `VideoEngine` vs M8 `EffectsComposer` | **Complementary / partial supersession** | Stage `call_m4_renderer` calls EffectsComposer; `M4Adapter` idle on happy path | Keep both engines; clarify docs/wiring — do not delete either M4 tree | **NEEDS FIX** (docs/wiring clarity) |
| M4 `AutonomousVideoEngine` vs M8 `AutonomousProductionController` | **Overlapping names; complementary scope** | M4 planner stub (plan JSON only); M8 full pipeline | M8 owns E2E autonomy today; M4 stub remains in both trees | **PASS** (if roles clear) |
| M6 `android/` vs M7 `android/` capture tree | **M7 supersets M6** (identical capture sources) | Capture sources MD5-identical; M7 adds `dataset/**` | Prefer M7 for media+dataset integration work; keep M6 as capture-only snapshot | **PASS** |
| `com.avsp.pro` vs `com.avsp.creator` | **Parallel Android products** | Different applicationId, Room DBs, Project models | Do not merge blindly | **NEEDS FIX** (strategy) |
| CURRENT_M1_M3 stub folders vs real M4–M9 | **Placeholders vs implementations** | Stub READMEs only | Stubs must not be filled with rewrites | **PASS** (if left alone) |
| M5 vs reserved `youtube/`/`screen/`/`ocr/` | Domain overlap | Stubs empty | M5 is the implemented desktop package | **PASS** |
| M9 vs reserved `publishing/` | Domain overlap | Stub empty | M9 is the implemented desktop package | **PASS** |
| M8 FinalQC vs missing M10 | Partial functional overlap | M8 `qc/` exists; M10 folder absent | M8 QC interim; M10 still MISSING as module | **MISSING** / **FUTURE** |
| M8 research/script vs M2 Script AI | Logical overlap | Both produce script-like JSON | Different platforms; bridge later | **FUTURE** consolidation |
| M5 sample_outputs vs M5 runtime schema | **Divergent duplicates** | Field names differ | Prefer runtime dataclasses over samples | **NEEDS FIX** |

---

## 2. Special Investigation: `M4/` vs `M8/m8/vendor/m4`

### 2.1 Question

Are they identical, forked, complementary, or internal dependency vs standalone implementation?

**Constraint:** Do **not** assume either path is canonical. Do **not** choose one for deletion in this phase.

### 2.2 Evidence-based answer

| Hypothesis | Verdict |
|------------|---------|
| Identical (content) | **Yes** — every tracked source file matches by SHA-256 |
| Forked (diverged edits) | **No** — zero content diffs |
| Complementary (different features) | **No** at file level — same engines/templates/tests |
| Standalone vs internal dependency (roles) | **Yes, role difference only** — `M4/AVSP_M4_Video_Engine` is the module package under `M4/`; `M8/m8/vendor/m4` is the copy M8 loads via `M4Adapter` |

**Content relationship:** identical trees.  
**Integration relationship:** standalone package **and** vendored internal dependency (same bytes, two locations).  
**Canonical choice:** **NOT MADE.** Evidence does not require selecting a single survivor; it requires preserving both until a later packaging policy.

### 2.3 Comparison evidence (re-verified)

| Check | `M4/AVSP_M4_Video_Engine` | `M8/m8/vendor/m4` |
|-------|---------------------------|-------------------|
| Source files | 10 | 10 (same relative paths) |
| `diff -rq` (excl. temp/output/__pycache__) | — | exit **0** (no differences) |
| Per-file SHA-256 | — | **IDENTICAL** for all 10 |
| `manifest.json` | `1.0-qc-work` | `1.0-qc-work` |
| `VideoEngine.render` API | present | identical |
| Templates / tests | present | identical |
| Only-in-A / only-in-B source files | none | none |

M8 documentation *claims* `vendor/m4/** = copy of frozen M4 (unmodified source)` (`M8/m8/docs/archive/M8_CHANGED_FILES.md`). That claim matches the byte comparison. It is **descriptive of origin**, not an audit authorization to delete `M4/` or to treat only one tree as the integration source of truth.

### 2.4 How M8 uses the vendored tree

- Loader: `app/adapters/m4_adapter.py` → importlib package `m4_frozen` from `vendor/m4`
- Controller constructs `self.m4 = M4Adapter(m4_root=.../vendor/m4)`
- Live stage named `call_m4_renderer` invokes **`EffectsComposer.compose()`**, not `self.m4.render()`
- Unit tests exercise `M4Adapter` against `vendor/m4`

Therefore: **byte-identical M4 content exists in two places; M8’s production render path currently bypasses both for creative effects.**

### 2.5 Classification (no winner)

| Component | Status | Note |
|-----------|--------|------|
| `M4/AVSP_M4_Video_Engine` | **PASS** | Standalone module package; README marks module frozen |
| `M8/m8/vendor/m4` | **PASS** | Identical content; used as M8 load path |
| “Declare canonical M4 and delete the other” | **FUTURE** | **Not justified** by drift (none exists); out of Phase 1 |
| Live M8 renderer vs M4Adapter | **NEEDS FIX** (clarity) | Naming/docs vs EffectsComposer — separate from M4 dual-tree identity |

### 2.6 Must not change (this area)

- Do **not** assume `M4/` or `vendor/m4` is the sole canonical tree  
- Do **not** delete either tree in Phase 1  
- Do **not** rewrite M4 to match EffectsComposer (or the reverse) for preference  
- Do **not** replace EffectsComposer with M4 solely for architecture purity  

---

## 3. Renderer Overlap: M4 `VideoEngine` vs M8 `EffectsComposer`

| Capability | M4 VideoEngine (both trees) | M8 EffectsComposer |
|------------|-----------------------------|--------------------|
| Still/video/color assembly | Yes | Yes |
| Narration mux | Yes | Yes |
| Template shorts/landscape | Yes | Via timeline WxH |
| Basic subtitles | Yes | Yes (Windows font paths) |
| Punch / xfade / SFX / emoji | No | Yes |
| Creative EDL timeline | Via thin media list | Native Timeline schema |
| Used in M8 `run()` | No (`M4Adapter` idle) | **Yes** |

```
ISSUE: Duplicate FFmpeg render stacks with confusing stage name.
ROOT CAUSE: Creative effects exceeded M4 VideoEngine scope; EffectsComposer added; M4 trees retained frozen.
IMPACT: Ambiguity over which renderer produces the integration final.mp4 (answer today: EffectsComposer → projects/<id>/render/final.mp4).
SEVERITY: MEDIUM
RECOMMENDED FIX: Document dual-renderer policy without deleting either M4 tree. Optional later stage rename. Do not pick a “canonical M4 folder” yet.
TEST REQUIRED: Pipeline asserts render/final.mp4; M4Adapter tests against vendor/m4 remain green independently.
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
| D1 | M4 ↔ vendor/m4 | Identical content; dual locations | Keep both; **no canonical pick** | Optional single-source packaging later |
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

1. Do not delete `M4/AVSP_M4_Video_Engine` or `M8/m8/vendor/m4` based on preference — and do **not** declare either the sole canonical tree in Phase 1.  
2. Do not replace EffectsComposer with M4 (or vice versa) without a defect report.  
3. Do not paste M6/M7 sources into `CURRENT_M1_M3/camera` stubs.  
4. Do not merge `com.avsp.pro` and `com.avsp.creator` without an explicit merge design.  
5. Do not “fix” M2 by rewriting it into M8’s CreativeDirector.  
6. Do not remove M8 FinalQC because M10 is missing.
7. Do not modify module source or the Cloud environment to “fix” the failed install script during this audit.
