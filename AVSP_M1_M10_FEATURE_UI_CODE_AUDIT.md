# AVSP M1–M10 Feature / UI / Code Audit

**Status:** READ-ONLY AUDIT — no implementation  
**Date:** 2026-03-26  
**Repository:** `knowportofficial-arch/avsp-integration`  
**Roadmap context:**

```text
M1–M10 Feature/UI/Code Audit          ← THIS DOCUMENT
        ↓
  Identify real gaps
        ↓
  Decide what to develop
        ↓
  Android Creator completion
        ↓
  Android → Windows
        ↓
  Full AVSP production
```

**Already complete (do not redo):** Phase 1 integration audit; Phase 2A M8→M9 bridge; Phase 2B/2C M3→M8; Phase 2D Android→Windows package **design** (not implemented).

**Constraint:** Inspect only. No source/test/bridge/media changes. This document is the sole deliverable.

---

## 1. Executive summary

| Layer | Reality |
|-------|---------|
| Android Pro (`com.avsp.pro`) | M1 shell + M2 mock script + M3 TTS/audio **usable**; no camera, no export |
| Android Creator (`com.avsp.creator`) | M6 capture + M7 dataset **usable on device**; 8/10 workspace tiles still placeholders; **same applicationId** on M6 and M7 trees |
| Windows | M8 render + QC strong; M4 CLI present (dual tree); M5 library weak productization; M9 **mock** publish verified; M10 **absent** |
| Bridges | M3→M8 and M8→M9 **work on Windows** when artifacts are local |
| Critical product gaps | (1) Unified Creator completion, (2) Pro↔Creator merge or dual-export, (3) Android→Windows package **implementation** (Phase 2D design only), (4) Real publish hardening, (5) M10 decision |

**Operator cannot run full AVSP phone→publish today.** Closest working spine is Windows-local: M3-shaped package → Phase 2C → M8 → Phase 2A mock M9.

---

## 2. Per-module feature / UI / code scorecard

Legend: **E2E** = can an operator use this module’s intended job end-to-end today?

| Module | Platform | App / entry | Feature core | UI | Tests | E2E | Matrix class |
|--------|----------|-------------|--------------|----|-------|-----|--------------|
| **M1** | Android Pro | Compose shell | Projects, settings, logs, modules | Strong | Strong | **YES** (shell) | PASS |
| **M2** | Android Pro | Script AI screen | Script gen/edit/save | Strong | Strong | **PARTIAL** (Mock only) | PASS |
| **M3** | Android Pro | Audio TTS screen | WAV segments + voice.json | Strong | Strong | **PARTIAL** (audio only) | PASS |
| **M4** | Desktop Python | CLI `video_engine` | FFmpeg assemble | None | Present | **PARTIAL** (CLI) | PASS (dual-tree) |
| **M5** | Desktop Python | Library + tests | yt-dlp / OCR ingest | None | Partial | **NO** (product path) | NEEDS FIX |
| **M6** | Android Creator | Camera / Guided | Capture + MediaStore/guided | Strong (capture) | Present | **PARTIAL** | PASS |
| **M7** | Android Creator | Dataset + Library | Quality KEEP/best | Strong (dataset) | Present | **PARTIAL** | NEEDS FIX |
| **M8** | Desktop Python | CLI controller | EDL→EffectsComposer→QC | None | Strong | **PARTIAL**→stronger w/ bridges | PASS |
| **M9** | Desktop Python | CLI publish | Queue + publishers | None | Mock suite | **PARTIAL** (mock YES) | PASS (mock) |
| **M10** | — | — | — | — | — | **NO** | MISSING |

---

## 3. What exists (evidence-backed)

### M1 — Core UI (`CURRENT_M1_M3/`, `com.avsp.pro`)
- Home, Projects, Project Detail, Media (empty until modules fill), Modules, Settings, Logs
- Room, `FileAvspStorage`, encrypted settings, module registry
- **FROZEN** baseline in module status; WorkManager ACK stub

### M2 — Script AI
- Mock generator, EN/BN/HI, validator, `script.json`, Script AI UI, handoff to M3
- Real LLM adapter **not registered** even if credentials configured

### M3 — Audio / TTS
- Mock + Android TTS → pcm_s16le WAVs + `voice.json` / `AudioPackage`
- Preview UI; READY status; no automatic video export from the app

### M4 — Video engine
- Full CLI render path; templates; tests; **frozen** after acceptance
- Byte-identical copy under `M8/m8/vendor/m4/`; live M8 uses **EffectsComposer**, not M4Adapter

### M5 — YouTube / screen input
- Ingestor library + tests; sample JSON schema drift vs runtime
- No operator CLI/`main.py`; ML Kit Android stub; **no bridge into M2**

### M6 — Camera (Creator)
- CameraX, guided capture, missions, Media Library, projects
- Workspace: only Field Capture + Media Library live; other tiles placeholders

### M7 — Dataset (Creator, supersets M6)
- Quality analysis, recommendations, best-shot, extended Room fields, Media Library badges
- In-memory `DatasetAutomationContract.snapshot()` — **no** `m7_snapshot.json` writer

### M8 — Autonomous production
- Topic → EDL → timeline → EffectsComposer → QC → `final.mp4`
- `M7Adapter` reads Windows-local snapshot/media; narration via Phase 2C bridge

### M9 — Publishing
- Multi-platform publishers + queue + analytics; Phase 2A bridge
- Default **`force_mock=True`**; real YouTube code exists but not verified default path

### Bridges
- `bridges/m3_m8` — voice package → real VO render
- `bridges/m8_m9` — final.mp4 → mock publish
- Phase 2D contract — **design only**

### M10
- **No directory / no code** (manifest placeholder only). M8 FinalQC is not branded M10.

---

## 4. Real gaps (prioritized)

### P0 — Blocks full AVSP production

| Gap | Why it blocks |
|-----|----------------|
| **No Android→Windows project package implementation** | Phase 2D designed; not built. Phone media/voice never reach M8 automatically |
| **Dual Android apps + dual Creator trees** | Pro has script/voice; Creator has camera/vision; no shared project/export; M6/M7 same `applicationId` |
| **Creator workspace incomplete** | Research/Script/Voice/Timeline/Captions/Thumbnails/Publishing tiles are placeholders |
| **M7 export to M8 missing** | Vision/selection never becomes Windows `m7_snapshot.json` |

### P1 — Limits production quality / speed

| Gap | Impact |
|-----|--------|
| M2 Mock-only scripts | Not production AI narration planning |
| M3 no in-app export UX | Operator cannot ship voice package from UI |
| M5 not productized + no M5→M2 | Research-from-YouTube path dead |
| M8 placeholder media without local/Pexels | Weak visuals when Creator media absent |
| M9 mock-default | Cannot claim real publish without hardening |
| M4 vs EffectsComposer dual story | Confusion; keep both per Phase 1, but product path is EffectsComposer |

### P2 — Polish / future

| Gap | Impact |
|-----|--------|
| M10 absent | Optional QC product module; M8 QC may suffice |
| Contract drift (`voice.mp3` name vs WAV) | Docs/constants vs runtime |
| Punch-library / env deps historically flaky | CI/fixture discipline |
| No unified operator console (Android + Windows) | Workflow friction |

---

## 5. Decide what to develop (decision matrix)

**Rule:** Prefer adapters/exporters over rewriting frozen engines. Do not delete dual M4. Do not force Windows work onto Android.

| Candidate | Develop now? | Rationale | Depends on |
|-----------|--------------|-----------|------------|
| **A. Android Creator completion (UI wiring)** | **YES — next product focus** | Unblocks single-app creator story; still needs Pro capabilities or deep links | Product decision: merge vs embed |
| **B. Unify Pro + Creator (one APK)** | **YES — strategic prerequisite** | Without this, “Creator completion” cannot include Script/Voice honestly | App architecture |
| **C. Android→Windows package (implement Phase 2D)** | **YES — after or with B** | Required for full production; design already exists | Exporter + importer |
| **D. M7 snapshot writer** | **YES — part of C** | Small additive writer; M8 consumer exists | C |
| **E. Harden M9 real YouTube** | **LATER** | Mock spine already proves pipeline; credentials/ops heavy | Stable MP4 inflow |
| **F. Real M2 LLM adapter** | **LATER / parallel** | Improves quality; Mock unblocks structure testing | API keys |
| **G. Productize M5 + M5→M2** | **LATER** | Nice research input; not on critical phone→publish path | M2 ingest contract |
| **H. Build M10 as new module** | **NO unless required** | M8 FinalQC already covers desktop QC; avoid duplicate | Product naming only |
| **I. Rewrite M4/M8/M3 engines** | **NO** | Bridges already work; Phase 1/2C stance | — |
| **J. Change `bridges/m3_m8` / `m8_m9` cores** | **Avoid** | Prefer feed existing contracts from importer | C |

### Recommended development order (aligned to user roadmap)

```text
1) Decide APK strategy (merge Creator+Pro vs dual-export)
2) Android Creator completion
      - Live Script/Voice tiles (embed Pro modules OR export handoff UX)
      - Keep Capture + Dataset as primary
      - Remove false placeholders where capability exists elsewhere
3) Android → Windows
      - Implement Phase 2D package exporter/importer
      - M7 snapshot + selected media + M3 audio
4) Full AVSP production
      - E2E: phone project → package → M8 QC → M9 (mock first, then real)
5) Then: M2 real AI, M5 bridge, M9 real publish hardening
6) M10 only if product requires a named QC gate beyond M8
```

---

## 6. Android Creator completion — scope sketch (not implementation)

**Already live in Creator:** Field Capture, Media Library, (M7) quality badges/dataset.

**Must become real for “completion” (choose one strategy):**

| Workspace tile | Option 1 — Merge Pro into Creator | Option 2 — Deep link / export only |
|----------------|-----------------------------------|-------------------------------------|
| Research | Port or stub→M5 later | Link out / defer |
| Script AI | Port M2 | Open Pro / import script.json |
| Voice | Port M3 | Open Pro / import voice package |
| Timeline / Captions / Thumbnails | Mostly Windows-derived — show status after export | Status-only UI |
| Publishing Package | Status from M9 job / package ready | Status-only |

**Non-negotiable for completion:** one installable Creator APK identity; guided media project-linked; export entry point.

---

## 7. Android → Windows — status

| Item | Status |
|------|--------|
| Contract | Phase 2D document **done** |
| Exporter | **MISSING** |
| Importer | **MISSING** |
| Voice path once files local | Phase 2C **DONE** |
| Publish once MP4 local | Phase 2A mock **DONE** |

---

## 8. Full AVSP production — definition of done

```text
Operator on Android Creator (unified):
  create project → script → voice → capture → vision → EXPORT
        ↓
Windows:
  import package → validate → M3→M8 (real VO) → QC PASS → M9 publish
```

| Checkpoint | Today |
|------------|-------|
| Script+voice on phone | Pro only |
| Capture+vision on phone | Creator only |
| Export package | No |
| Windows import | No |
| Real VO render | Yes (bridge + local M3 tree) |
| QC | Yes (M8) |
| Mock publish | Yes |
| Real publish | Code exists; not default-verified |

---

## 9. Protected assets (when development starts)

Do **not** casually modify:

- `M4/`, `M8/m8/vendor/m4/`
- Frozen M1/M2 baselines unless unlocking merge
- Working `bridges/m3_m8`, `bridges/m8_m9` contracts (extend via new importer/exporter)

Prefer **new** modules: `export/` / `import/` / Creator merge branch.

---

## 10. Open product decisions (required before coding)

1. **One APK or two?** Merge `com.avsp.pro` into `com.avsp.creator` vs keep dual with package merge tool.
2. **Creator “Script/Voice” tiles:** embed engines vs launch/import from Pro.
3. **M10:** rename M8 QC as M10 vs new module vs never.
4. **M5 priority** relative to phone capture path.
5. **When to enable real YouTube** (after mock E2E from phone?).

---

## 11. Acceptance criteria for *this audit*

- [x] All M1–M10 assessed from repository evidence  
- [x] Real gaps identified and prioritized  
- [x] Development decisions recommended (not implemented)  
- [x] Roadmap mapped: Creator completion → Android→Windows → Full production  
- [x] No source/bridge/test modifications  

---

## 12. Stop line

**STOP after this document.**

Do **not** begin Creator completion coding, Phase 2D implementation, M10, or real YouTube enablement until product decisions in §10 are made.

---

**END OF M1–M10 FEATURE/UI/CODE AUDIT**
