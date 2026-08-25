# AVSP Integration Audit

**Phase:** 1 — Audit Only (STOP — no implementation)  
**Date:** 2026-08-25  
**Repository:** avsp-integration (`/workspace`)  
**Branch intent:** documentation-only audit deliverables  

**Inputs read first:**  
`MASTER_PROJECT_PROMPT.txt`, `INTEGRATION_INSTRUCTIONS.md`, `MODULE_MANIFEST.txt`  

**Then inspected:** `CURRENT_M1_M3/`, `M4/`, `M5/`, `M6/`, `M7/`, `M8/`, `M9/` (and confirmed **M10 absent**)

Companion documents:

- `AVSP_MODULE_INTEGRATION_MATRIX.md`
- `AVSP_CONTRACT_MAP.md`
- `AVSP_PLATFORM_BOUNDARY.md`
- `AVSP_DUPLICATE_COMPONENTS.md`

**Source code in this phase:** not modified. Environment not debugged or repaired.

---

## 0. Environment / Install-Script Failure (separate from module audit)

The Cloud Agent environment panel reports install/setup failure. **This is recorded here only.** No environment repair was performed in Phase 1.

| Item | Observation |
|------|-------------|
| Dashboard event | `setup_failed` — “Failed to start the development environment. The update script in your configuration failed during VM startup.” |
| `start-user.status` | `127` (command not found) |
| `start-user.log` | `bash: .cursor/start.sh: No such file or directory` |
| Workspace `.cursor/` | **Absent** — no `start.sh` / `environment.json` in repo checkout |
| `/tmp/cursor/async-install/` | No durable `install-user.status` present at inspection time |
| Build resolution | `no_finished_builds` (environment-info) |
| Audit action taken | **None** — do not debug/modify environment in this phase |

```
ISSUE: Cloud environment install/update script failed at VM startup.
ROOT CAUSE (observed only): start hook invokes `.cursor/start.sh`, which is missing from the workspace; setup event kind=setup_failed.
IMPACT: Environment “may not work as expected.” Some services may be unavailable; runtime tests are opportunistic, not a certified CI baseline.
SEVERITY: HIGH for agent DX / reproducibility; does **not** by itself invalidate structural file audit
RECOMMENDED FIX: Out of scope for Phase 1. Later: restore or define `.cursor/start.sh` / environment config — do not change M1–M9 modules to work around this.
TEST REQUIRED: After env repair (future phase), re-run module test matrix on a healthy install.
```

**Test-result interpretation rule for this audit:**

- Structural/contract conclusions are based on **repository files** (authoritative).
- Where unit tests were executed successfully despite the failed install hook, results are reported as **opportunistic host evidence**.
- Where a test cannot run or is incomplete because of the environment failure, classify as **ENVIRONMENT LIMITATION**, not a module defect.
- Do **not** change module code to compensate for the install failure.

---

## 1. Complete Module Status

| Module | Path | Platform | Lang | Classification | Test status (opportunistic host) | Notes |
|--------|------|----------|------|----------------|----------------------------------|-------|
| M1 | `CURRENT_M1_M3` | Android | Kotlin | **PASS** | 69/69 with M2/M3 | Shell, Room, storage |
| M2 | in M1–M3 app | Android | Kotlin | **PASS** | included | Script AI frozen |
| M3 | in M1–M3 app | Android | Kotlin | **PASS** | included | Audio; `voice.mp3` drift |
| M4 | `M4/AVSP_M4_Video_Engine` | Desktop Py | Python | **PASS** | 9/9 opportunistic | FFmpeg assembler; see §M4 dual-tree |
| M4 vendor | `M8/m8/vendor/m4` | Desktop Py | Python | **PASS** | byte-identical tree | Role = M8 vendored copy; **not declared canonical over** `M4/` |
| M5 | `M5/m5_youtube_screen_input` | Desktop Py | Python | **NEEDS FIX** | 18/0 fail/1 skip | Sample schema drift |
| M6 | `M6/android` | Android | Kotlin | **PASS** | 39/39 | Capture |
| M7 | `M7/android` | Android | Kotlin | **NEEDS FIX** | 63/63 | APIs OK; export missing |
| M8 | `M8/m8` | Desktop Py | Python | **NEEDS FIX** | 43 pass / **2 fail** | Punch assets missing (module packaging); live render ≠ M4Adapter |
| M9 | `M9/m9` | Desktop Py | Python | **PASS** | 32/32 | Mock publish |
| M10 | *(none)* | — | — | **MISSING** | — | Listed in manifest only |
| Env install/start | Cloud VM | — | — | **ENVIRONMENT LIMITATION** | start exit 127 | Separate from module PASS/FAIL |

**Reserved stubs in CURRENT_M1_M3** (`video`, `youtube`, `screen`, `ocr`, `camera`, `dataset`, `automation`, `publishing`, `quality`, …): **FUTURE** placeholders — **PASS** only if left unimplemented.

---

## 2. Integration Dependency Graph

### 2.1 Intended logical graph

```
M1 (projects/UI)
 └─► M2 (script)
      └─► M3 (audio)
M5 (research ingest) ─?► M2
M6 (capture) ─► M7 (dataset)
M7 ─?► M8 (media selection)
M3 ─?► M4/M8 (narration)
M4 ◄── vendor ── M8 (optional simple render)
M8 (EffectsComposer) ─► final.mp4 + qc
M8 ─?► M9 (publish)
M8/M9 ─?► M10 (acceptance QC)
```

`?►` = **missing bridge**

### 2.2 Implemented edges today

```
M1 ──in-process──► M2 ──in-process──► M3

M6 ──same APK (M7 tree)──► M7 APIs

M4/AVSP_M4_Video_Engine ══byte-identical══ M8/m8/vendor/m4
     (neither declared canonical; both retained)
M8 ──constructs──► M4Adapter(vendor/m4) [tests; idle on happy path]
M8 ──live render──► EffectsComposer ──► render/final.mp4
M8 ──file──► M7Adapter (expects snapshot JSON; not fed by Android)

M5  (orphan library)
M9  (orphan CLI; awaits video_path)
M10 (absent)
```

### 2.3 Cross-app / cross-OS edges

| Edge | Status |
|------|--------|
| `com.avsp.pro` ↔ `com.avsp.creator` | **MISSING** |
| Android ↔ Windows project bus | **MISSING** |
| M5 → M2 | **MISSING** |
| M3 → M4/M8 | **MISSING** |
| M7 → M8 | **NEEDS FIX** / **MISSING** export |
| M8 → M9 | **MISSING** |
| Any → M10 | **MISSING** |

---

## 3. Contract Conflicts

| Conflict | Detail | Severity | Class |
|----------|--------|----------|-------|
| C1 Audio shape | M3 segment WAVs + `AudioToVideoHandoff` vs M4 single `audio_path` | HIGH | **NEEDS FIX** |
| C2 voice.mp3 | M1 `ArtifactNames.VOICE_MP3` vs M3 reality | MEDIUM | **NEEDS FIX** |
| C3 M7 field names | `fileUri`/`qualityScore`/`clipId` vs M8 `path`/`quality_score`/`id` | HIGH | **NEEDS FIX** |
| C4 M7 paths | Android `content://` vs Windows filesystem | HIGH | **NEEDS FIX** |
| C5 Final MP4 path | M1 `generated/video/final.mp4` vs M8 `render/final.mp4` vs M4 `output/*_final.mp4` | MEDIUM | **NEEDS FIX** |
| C6 M5 samples | `sample_outputs` ≠ `M2_HANDOFF` / dataclasses | MEDIUM | **NEEDS FIX** |
| C7 M8 stage name | `call_m4_renderer` ≠ M4 call | MEDIUM | **NEEDS FIX** |
| C8 Project models | Pro `Project` vs Creator `Project` fields | HIGH (if merging apps) | **FUTURE**/strategy |
| C9 Script authority | M2 vs M8 CreativeDirector vs M5 | HIGH for dual-path | **FUTURE**/policy |

Full field maps: `AVSP_CONTRACT_MAP.md`.

---

## 4. Duplicate Implementations

See `AVSP_DUPLICATE_COMPONENTS.md`. Headline:

1. **`M4/` ↔ `M8/m8/vendor/m4`:** byte-identical content; **roles** differ (standalone package vs vendored load path). **No canonical winner chosen.** Keep both (**PASS** identity / **FUTURE** packaging).  
2. **M4 VideoEngine ↔ EffectsComposer:** complementary; M8 production uses EffectsComposer (**NEEDS FIX** docs/stage name).  
3. **M6 ↔ M7:** M7 supersets M6 (**PASS** prefer M7 for dataset+capture).  
4. **Pro ↔ Creator apps:** parallel (**NEEDS FIX** strategy; no blind merge).  
5. **Stub folders ↔ real modules:** do not reimplement (**PASS** if untouched).

---

## 5. Required Adapters / Bridges

| Priority | Bridge | From → To | Purpose | Class |
|----------|--------|-----------|---------|-------|
| P0 | M7 snapshot exporter + path rewrite | Android M7 → M8 `m7_snapshot.json` + media files | Feed KEEP media | **MISSING** |
| P0 | M8 → M9 job adapter | `final.mp4` + metadata → `PublishingJobCreate` | Close publish loop | **MISSING** |
| P1 | M3 audio concat/export | `AudioToVideoHandoff` → `audio_path` | Narration for render | **MISSING** |
| P1 | Project bundle / path remapper | Android trees ↔ M8 `projects/<id>` | Shared artifacts | **MISSING** |
| P2 | M5 → M2 request mapper | YouTube/Screen JSON → `ScriptGenerationRequest` | Research-assisted scripts | **MISSING** |
| P2 | Dual-renderer documentation or stage rename | M8 controller clarity | Reduce confusion | **NEEDS FIX** |
| P3 | Punch media pack or soft-skip | M8 assets | Green tests | **NEEDS FIX** |
| P3 | M5 sample regeneration | samples → runtime schema | Doc honesty | **NEEDS FIX** |
| Later | Android job trigger/status | Pro UI → M8/M9 | Control plane | **FUTURE** |
| Later | M10 acceptance module | M8 QC → gate | Formal QC | **MISSING** |

**Design constraint:** adapters only — do not rewrite M4–M9 engines.

---

## 6. Android / Windows Boundary

| Side | Owns |
|------|------|
| **Android** | M1–M3 UI/script/TTS; M6–M7 capture/dataset; future status UI |
| **Windows** | M4/M5/M8/M9 (+ M10); FFmpeg; yt-dlp; Tesseract; publishing APIs |

Do not move heavy AI/video/FFmpeg/automation/publishing onto Android.

Detail: `AVSP_PLATFORM_BOUNDARY.md`.

---

## 7. Highest-Risk Integration Issues

### RISK-1 — No cross-platform artifact bus (CRITICAL)

```
ISSUE: Android and Windows modules cannot exchange project artifacts automatically.
ROOT CAUSE: Delivered as isolated packages; no export/import tool.
IMPACT: Impossible to run true multi-device E2E; forces manual copy or Windows-only demos.
SEVERITY: CRITICAL
RECOMMENDED FIX: Define project bundle directory + export/import scripts; path remapping for media.
TEST REQUIRED: Bundle from M7 (+ optional M3) imported into M8 project_id; pipeline finds media; produces final.mp4.
```

### RISK-2 — M7→M8 schema/path mismatch (HIGH)

```
ISSUE: DatasetAutomationContract.snapshot field names and URIs do not match M7Adapter; no JSON file writer.
ROOT CAUSE: Kotlin in-process API vs Python file contract evolved separately.
IMPACT: Live dataset never selects correctly in M8 (empty paths / wrong scores).
SEVERITY: HIGH
RECOMMENDED FIX: Serialization adapter mapping clipId→id, fileUri→path (after copy), qualityScore→quality_score; write m7_snapshot.json.
TEST REQUIRED: Exported snapshot loads in M7Adapter; find_keepable returns KEEP items with openable paths.
```

### RISK-3 — Script/narration dual authority (HIGH)

```
ISSUE: M2/M3 and M8 CreativeDirector can produce conflicting scripts/captions.
ROOT CAUSE: Autonomous M8 embeds research/script; Android M2/M3 are separate.
IMPACT: Wrong voiceover vs on-screen text; wasted renders.
SEVERITY: HIGH (when both paths used)
RECOMMENDED FIX: Explicit run modes (Android-approved vs M8-autonomous); one narration source per run.
TEST REQUIRED: Mode fixtures assert timeline narration provenance.
```

### RISK-4 — M3→M4/M8 audio gap (HIGH)

```
ISSUE: No adapter from multi-WAV AudioPackage to single audio_path.
ROOT CAUSE: Contract designed on Android; engine API on Python.
IMPACT: Approved TTS not used in final MP4.
SEVERITY: HIGH for creator-approved audio path
RECOMMENDED FIX: Concat/export adapter only.
TEST REQUIRED: Duration and stream presence after render.
```

### RISK-5 — M10 missing + M8 test red (MEDIUM–HIGH)

```
ISSUE: M10 absent; M8 has 2 failing tests (missing punch clips).
ROOT CAUSE: Incomplete packaging / undelivered module.
IMPACT: Acceptance story unclear; CI signal noisy before integration.
SEVERITY: MEDIUM (tests) / HIGH (M10 for formal gate)
RECOMMENDED FIX: Restore punch assets or skip; treat M8 QC as interim M10.
TEST REQUIRED: M8 suite green; document interim QC gate using final_qc.json.
```

### RISK-6 — Dual Android apps (HIGH product / MEDIUM near-term)

```
ISSUE: com.avsp.pro and com.avsp.creator split state.
ROOT CAUSE: Parallel tracks.
IMPACT: No single client for full mobile workflow.
SEVERITY: HIGH product; MEDIUM if Windows-first integration
RECOMMENDED FIX: File bridges first; APK merge only with explicit design.
TEST REQUIRED: Cross-export identity on project_id.
```

---

## 8. Recommended Integration Order

Respect: audit → minimum adapters → test before widen. **Do not merge module folders.**

| Step | Work | Rationale |
|------|------|-----------|
| **0** | This audit (done) | Required stop point |
| **1** | Stabilize M8 package (punch assets / skips) so desktop baseline is green | Reliable Windows core |
| **2** | M8 → M9 mock publish bridge | Shortest path to “final MP4 → published (mock)” |
| **3** | Document/canonicalize renderer policy (EffectsComposer primary; M4 frozen) | Remove ambiguity |
| **4** | M7 → M8 snapshot export + media copy | Connect Android media to Windows pipeline |
| **5** | M3 → narration export → M8/M4 audio_path | Approved voice path |
| **6** | M5 → M2 (or → M8 research) adapter | Optional research assist |
| **7** | Android control/status thin client for Windows jobs | UX glue |
| **8** | M10 module or promote M8 QC to formal gate | Acceptance |
| **9** | Performance benchmark (5-minute video ~10 min reference) | Master prompt metric |
| **10** | Consider Pro↔Creator merge **only** with design | Last, high risk |

**First Windows-only E2E (safe):** M8 autonomous topic run → `final.mp4` → M9 mock publish.  
**First cross-platform E2E:** M7 export → M8 → M9.

---

## 9. What MUST NOT Be Changed

1. **Do not rewrite** completed M1–M9 engine internals without a reproducible defect.  
2. **Do not recreate** M4–M9 inside `CURRENT_M1_M3` stub folders.  
3. **Do not blindly merge** Android Pro + Creator apps or Android + Python trees.  
4. **Do not delete** `M4/` or `M8/m8/vendor/m4` — they are byte-identical but **neither is declared canonical** in this audit; packaging policy is **FUTURE**.  
5. **Do not replace** EffectsComposer with M4 (or vice versa) for preference.  
6. **Do not force** FFmpeg / yt-dlp / desktop OCR / publishing stacks onto Android.  
7. **Do not implement** new product features in Phase 1 (this audit).  
8. **Do not choose** a “single script engine” by deleting M2 or M8 CreativeDirector without a mode policy.  
9. **Do not remove** M8 FinalQC because M10 is missing.  
10. **Do not debug/modify the failed Cloud install/start environment** as part of this audit phase.  
11. **Repair before rewrite; verify before modifying** (`MASTER_PROJECT_PROMPT.txt`).

---

## 10. First Safe Implementation Task

**Recommended first implementation task (Phase 2 start — not done now):**

> **Create a minimal M8→M9 publish bridge (mock-first) plus green M8 baseline.**

### Why this is first

- Stays entirely on **Windows/desktop** (respects platform boundary).  
- Touches **no Android merges**.  
- Does **not** rewrite M4/M8/M9 engines — only a thin adapter/CLI glue.  
- Produces a demonstrable slice: existing `projects/*/render/final.mp4` → M9 `PublishingJobCreate` → mock `PUBLISHED`.  
- Parallel prep: fix/skip missing punch clips so M8 tests are trustworthy before broader integration.

### Explicit non-goals for that first task

- No Pro↔Creator merge  
- No M7 export yet  
- No M5→M2 yet  
- No M4 deletion / EffectsComposer replacement  
- No M10 implementation unless package arrives  

### Acceptance tests for that first task

1. M8 unit suite green (or documented skips only for absent optional binaries).  
2. Bridge reads an existing M8 project `render/final.mp4` + title/tags from research/script.  
3. `PublishingController(force_mock=True)` reaches terminal success status.  
4. No modifications to publisher internals beyond what’s required for the adapter entrypoint.

---

## 11. Issue Register (consolidated)

| ID | Issue | Severity | Class |
|----|-------|----------|-------|
| I01 | No Android↔Windows project bus | CRITICAL | MISSING |
| I02 | M7 snapshot export + field/path mismatch | HIGH | MISSING / NEEDS FIX |
| I03 | M3→M4/M8 audio adapter absent | HIGH | MISSING |
| I04 | Script authority (M2 vs M8) undefined | HIGH | FUTURE / NEEDS FIX |
| I05 | M8→M9 bridge absent | MEDIUM–HIGH | MISSING |
| I06 | M10 module absent | HIGH (formal) | MISSING |
| I07 | M8 punch clips missing (2 tests fail) | MEDIUM | NEEDS FIX |
| I08 | M8 stage name vs EffectsComposer | MEDIUM | NEEDS FIX |
| I09 | voice.mp3 promised not produced | MEDIUM | NEEDS FIX |
| I10 | M5 sample_outputs schema drift | MEDIUM | NEEDS FIX |
| I11 | M5→M2 unwired | HIGH (research path) | MISSING |
| I12 | Dual Android applicationIds | HIGH product | NEEDS FIX / FUTURE |
| I13 | GuidedCapture not always ingested to dataset | MEDIUM | NEEDS FIX |
| I14 | Final MP4 path conventions differ | MEDIUM | NEEDS FIX |
| I15 | Template logo/text_overlay unread in M4 | LOW | FUTURE |
| I16 | Cloud install/start script failed (`.cursor/start.sh` missing) | HIGH (DX) | **ENVIRONMENT LIMITATION** (not a module defect; not repaired) |

Each major issue’s ROOT CAUSE / IMPACT / RECOMMENDED FIX / TEST REQUIRED appears in companion docs and §7 above.

---

## 12. Audit Verdict

The repository contains **solid reference modules** for M1–M9, with **M10 missing**. The blocking problem for full AVSP integration is **not** a need to rewrite engines — it is the **absence of thin adapters and a file-based Android↔Windows boundary**, plus **contract drifts** (audio shape, M7 snapshot vocabulary, M5 samples) and **M8 packaging gaps** (punch media).

**M4 comparison:** `M4/AVSP_M4_Video_Engine` and `M8/m8/vendor/m4` are **byte-identical**. Roles differ (standalone package vs M8 vendored load path). **Neither path is declared the sole canonical implementation** in this audit. Live M8 rendering uses **EffectsComposer**; `M4Adapter` is constructed but not used on the happy path.

**Environment (separate):** Cloud install/update script **failed** (`setup_failed`; `.cursor/start.sh` missing → start exit 127). Recorded only; **not repaired**. Runtime tests above are opportunistic host evidence, not a certified environment baseline.

**Phase 1 complete. STOP. Do not implement adapters until a follow-up implementation phase is explicitly started.**
