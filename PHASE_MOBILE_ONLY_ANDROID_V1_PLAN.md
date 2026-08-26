# PHASE — Mobile-Only Android AVSP V1 Plan

**Status:** DESIGN ONLY — no implementation  
**Date:** 2026-03-26  
**Repository:** `knowportofficial-arch/avsp-integration`  
**Product decision (authoritative for this phase):**  

> **PRIMARY V1 TARGET = a genuinely usable ANDROID-ONLY AVSP.**  
> Windows is **not** required for V1. M8 is **not** required for Android V1.  
> Windows M4/M5/M8/M9 remain protected reference implementations — **not** V1 dependencies.

**Override note:** `MASTER_PROJECT_PROMPT.txt` historically assigns heavy render/publish to Windows. This document records an explicit **product-first override for V1** without deleting or rewriting Windows modules. Future hybrid (Phase 2D package) remains possible but **out of scope to implement now**.

**Inputs read:** Master prompt, module manifest, integration instructions, Phase 1 audit set, `AVSP_M1_M10_FEATURE_UI_CODE_AUDIT.md` (filename in-repo; no `AVSP_MASTER_FEATURE_UI_CODE_AUDIT.md`), Phase 2B/2D plans, Android trees `CURRENT_M1_M3/`, `M6/`, `M7/`, Windows `M4/`,`M5/`,`M8/`,`M9/` for reference only.

---

## 1. Product Objective

**What Android V1 must do completely by itself** (derived from master flow + audits + existing code — not invented features):

```text
Create project → enter topic → generate/edit script → generate/review narration
→ capture/import media → (optional) OCR/reference intelligence → AI vision / KEEP/best
→ build simple timeline → attach narration (+ optional BGM/captions)
→ preview → render final MP4 on device → QC gate → SEO fields → publish (mock→real)
→ persist project state / result
```

**Target mobile pipeline:**

```text
M1 → M2 → M3 → M4-A → M5-A → M6 → M7 → M9-A → ANDROID OUTPUT
```

*(Logical order; UI may interleave M5-A with import and M6/M7 before M4-A render.)*

**Success criterion:**  
**Android alone, without Windows, produces a real final video** (and can attempt publish).

**Non-goals for this design phase:** coding M4-A/M5-A/M9-A; Android→Windows export; modifying protected modules; enabling real OAuth in production.

---

## 2. Mobile-Only Architecture

```text
┌─────────────────────────────────────────────────────────┐
│           ONE Android Creator Application (V1)          │
│  Shell: unify Pro (M1–M3) + Creator (M6–M7) capabilities│
├──────────┬──────────┬──────────┬──────────┬─────────────┤
│ M1 Shell │ M2 Script│ M3 Voice │ M6 Capture│ M7 Vision  │
│ projects │ package  │ AudioPkg │ CameraX  │ quality/KEEP│
├──────────┴──────────┴──────────┴──────────┴─────────────┤
│ M5-A (NEW): on-device OCR / reference text intelligence │
│ M4-A (NEW): Android video compose + render → final.mp4  │
│ M9-A (NEW): SEO + mock/real publish from device         │
└─────────────────────────────────────────────────────────┘
         │
         ▼  (optional later — NOT V1)
   Phase 2D project package → Windows M8/M9 hybrid
```

| Layer | V1 role |
|-------|---------|
| Existing M1–M3, M6–M7 | Reuse; extend only as needed for unification |
| M4-A / M5-A / M9-A | **New Android modules** — do not replace Windows trees |
| Windows M4/M5/M8/M9 | Protected; reference only for capability taxonomy |
| `bridges/m3_m8`, `bridges/m8_m9` | Protected; Windows spine; unused by Android V1 runtime |

---

## 3. Current Android State

| App | ID | Modules | Usable today |
|-----|-----|---------|--------------|
| AVSP Pro | `com.avsp.pro` | M1–M3 | Project shell, Mock script, TTS→WAV |
| AVSP Creator | `com.avsp.creator` | M6 + M7 (M7 supersets M6) | Capture, library, quality/KEEP |

| Gap | Evidence |
|-----|----------|
| Two apps, no shared project bus | Different `applicationId`; Phase 2D / feature audit |
| Creator workspace 8/10 placeholders | `ProjectWorkspaceScreen` — only Field Capture + Media Library live |
| No Android video render | No Media3/Transformer/FFmpegKit/MediaMuxer compose code in Kotlin |
| No Android OCR text recognition dep | ML Kit object-detection/labeling only; no text-recognition; no Tesseract |
| No Android publish UI | Publishing tile placeholder; M9 is Windows Python |
| M2 Mock-only | `DefaultScriptGeneratorRegistry` still Mock |
| M3 no number→words preprocessor | Raw text to Android TTS |
| Dual M6/M7 same app ID | Install collision if both APKs built |

---

## 4. M1 Role

| # | Item | Definition for mobile V1 |
|---|------|--------------------------|
| 1 | Purpose | Canonical project shell, navigation, persistence, module gateway |
| 2 | Required features | Create/open/rename projects; dashboard; nav to script/voice/media/vision/edit/publish; settings; logs |
| 3 | UI | Reuse Pro: Home, Projects, ProjectDetail, Media, Modules, Settings, Logs — **absorb Creator workspace flow** |
| 4 | Inputs | Operator actions; module outputs |
| 5 | Processing | Project lifecycle; state routing (no heavy media) |
| 6 | Outputs | `projectId`, project records, navigation context |
| 7 | Storage | Extend `FileAvspStorage` + Room as unified store |
| 8 | Project identity | Single `projectId` across all modules |
| 9 | Downstream | All modules |
| 10 | Reusable | `CURRENT_M1_M3` shell, Room, storage, Compose nav |
| 11 | New code | App merge / nav to M6–M7 and future M4-A/M5-A/M9-A screens |
| 12 | Windows-only | N/A |

**Do not rewrite M1 core** unless merge forces package/namespace consolidation.

---

## 5. M2 Role

| # | Item | Definition |
|---|------|------------|
| 1 | Purpose | Topic → production script with scenes and narration |
| 2 | Required | Topic input; generate/edit/regenerate; EN/BN/HI; duration targets; scene structure; persist `ScriptPackage` |
| 3 | UI | Existing `ScriptAiScreen` (bring into unified app) |
| 4 | Inputs | Topic, language, duration, optional M5-A research JSON later |
| 5 | Processing | Generator registry (Mock now; real AI adapter later) |
| 6 | Outputs | `generated/script/script.json`, `{scriptId}.json` |
| 7 | Storage | Pro project-relative script paths |
| 8 | Identity | `projectId` + `scriptId` |
| 9 | Downstream | M3 (`ScriptToTtsContract`) |
| 10 | Reusable | Full `ScriptPackage` / validator / UI — **structurally enough for mobile workflow** |
| 11 | New | Wire into Creator shell; optional M5-A ingest; real generator when ready |
| 12 | Windows-only | Heavy desktop research pipelines (full yt-dlp M5) |

**Readiness:** **PARTIAL** — contracts + UI production-shaped; content quality Mock-limited. Usable for V1 structure demos; real AI is quality upgrade, not architecture blocker.

---

## 6. M3 Role

| # | Item | Definition |
|---|------|------------|
| 1 | Purpose | TTS → timed AudioPackage for M4-A |
| 2 | Required | Android TTS + Mock; locales EN/BN/HI; rate/pitch; segment WAVs; `voice.json`; preview |
| 3 | UI | Existing `AudioTtsScreen` |
| 4 | Inputs | Script handoff |
| 5 | Processing | Per-scene synthesize; contiguous timeline |
| 6 | Outputs | `aseg_*.wav`, `package.json`, `voice.json`, `totalDurationMs` |
| 7 | Storage | `generated/audio/…` under project |
| 8 | Identity | `audioPackageId` |
| 9 | Downstream | **M4-A** (not Windows M8 for V1) |
| 10 | Reusable | Entire M3 stack — **PROTECTED** |
| 11 | New | Optional pronunciation preprocessor; silence for `pauseAfter`; handoff API to M4-A |
| 12 | Windows-only | Phase 2C concat-for-M8 (Windows path) |

**Pronunciation evidence:** No number normalizer — `"105"` is passed raw to `TextToSpeech`; engine-dependent. Physical listening tests required later (BN/HI/en-IN). SSML: **absent**. `pauseAfter`: metadata only, **not** timeline gaps. Speech rate: **implemented** (`0.5–2.0`).

**Do not rewrite M3.** Prefer adapter: M3 AudioPackage → M4-A narration track (concat on Android inside M4-A or thin helper — **not** via Windows bridge).

---

## 7. M4-A Role

**NEW Android module. Do not modify `M4/` or `M8/m8/vendor/m4/`.**

| # | Item | Definition |
|---|------|------------|
| 1 | Purpose | Minimum on-device compose + render → `final.mp4` |
| 2 | Required features | See §16 classification |
| 3 | UI | Edit / Timeline / Preview / Render progress (Creator placeholders today) |
| 4 | Inputs | M3 audio; M7-selected media; optional captions/BGM |
| 5 | Processing | Trim/order/scale/crop; mux narration; one encode pass preferred |
| 6 | Outputs | `renders/{renderId}/final.mp4` + render metadata |
| 7 | Storage | Project-relative renders |
| 8 | Identity | `renderId` |
| 9 | Downstream | M9-A |
| 10 | Reusable | Windows M4/EffectsComposer = **reference taxonomy only**; CameraX capture already exists |
| 11 | New | Full Android media engine (Media3 Transformer preferred first) |
| 12 | Windows-only | Full EffectsComposer punch/emoji/AI director stack (M8) |

---

## 8. M5-A Role

**NEW Android module. Do not modify Windows `M5/`.**

| # | Item | Definition |
|---|------|------------|
| 1 | Purpose | Lightweight media/OCR intelligence for script/research assist |
| 2 | Required / optional | See §17 |
| 3 | UI | Import image/screen still; show extracted text; attach to project |
| 4 | Inputs | Images / stills / optional shared video frames |
| 5 | Processing | On-device OCR (ML Kit Text Recognition) |
| 6 | Outputs | Extracted text JSON consumable by M2 (optional) |
| 7 | Storage | `research/` or `m5a/` under project |
| 8 | Identity | research artifact id |
| 9 | Downstream | M2 (optional enrichment) |
| 10 | Reusable | Intent from Windows M5 handoff shapes; ML Kit already partially in Creator |
| 11 | New | Text recognition dep + UI + JSON writer |
| 12 | Windows-only | yt-dlp YouTube ingest, desktop Tesseract batch, OpenCV screen pipeline |

---

## 9. M6 Role

| # | Item | Definition |
|---|------|------------|
| 1 | Purpose | Capture/import media into project |
| 2 | Required | Preview, photo, video, resolution/FPS/orientation, mic, guided capture, library, metadata, project link |
| 3 | UI | Existing camera + guided + Media Library |
| 4–6 | I/O | MediaStore / guided files → Room media rows |
| 7 | Storage | Creator paths today → unify under Pro-style project root |
| 8 | Identity | media UUID / clipId |
| 9 | Downstream | M7 → M4-A |
| 10 | Reusable | CameraX stack — **PROTECTED** |
| 11 | New | Guided→Room auto-ingest; storage unification; wire tiles |
| 12 | Windows-only | N/A |

**Gaps:** guided auto-ingest MISSING; workspace placeholders; not in Pro app.

---

## 10. M7 Role

| # | Item | Definition |
|---|------|------------|
| 1 | Purpose | Quality / KEEP / best-shot selection for M4-A |
| 2 | Required | Score, blur/exposure/composition, recommendation, ranking, override, persistence, UI badges |
| 3 | UI | Media Library enrichment (exists) |
| 4 | Inputs | M6 media |
| 5 | Processing | Local analyzers (exist) |
| 6 | Outputs | Selected media set + scores + decisions |
| 7–8 | Storage / IDs | Room + future `vision/selection.json` for M4-A |
| 9 | Downstream | **M4-A** (contract below) |
| 10 | Reusable | Dataset package — **PROTECTED** where working |
| 11 | New | On-device selection snapshot for M4-A (not Windows `m7_snapshot` unless dual-write) |
| 12 | Windows-only | Feeding `M8/m8` `M7Adapter` |

### Future contract (design only — do not implement)

```text
M7
 → selected media IDs + file refs + qualityScore + recommendation + isBestShot + order hint
 → M4-A timeline builder
```

---

## 11. M9-A Role

**NEW Android module. Do not modify Windows `M9/`.**

| # | Item | Definition |
|---|------|------------|
| 1 | Purpose | SEO package + publish from device |
| 2 | Required | Title/description/tags/hashtags; thumbnail; platform pick; progress; status; mock publish first |
| 3 | UI | Publishing Package screen (placeholder today) |
| 4 | Inputs | `final.mp4`, project topic/script |
| 5 | Processing | Rule-based SEO (mirror Phase 2A `seo.py` approach); upload adapters |
| 6 | Outputs | Job status, platform ids, analytics stub |
| 7 | Storage | `publishing/` under project |
| 8 | Identity | `publishingJobId` |
| 9 | Downstream | Operator / analytics UI |
| 10 | Reusable | SEO mapping ideas from `bridges/m8_m9/seo.py` (token/rule-based, **not AI**) |
| 11 | New | Android publishers + credential UX |
| 12 | Windows-only | Desktop queue/OAuth engines in `M9/m9` |

**MOCK vs REAL:** V1 acceptance can ship with **MOCK** publish proof; REAL YouTube/Telegram gated behind credentials (design later — not this phase).

---

## 12. Unified Project Identity

One `projectId` owns:

| Artifact | ID field | Source |
|----------|----------|--------|
| Project | `projectId` | M1 |
| Script | `scriptId` | M2 |
| Voice | `audioPackageId` | M3 |
| Media | media / clip UUID | M6 |
| Vision selection | selection / snapshot id | M7 |
| Render | `renderId` | M4-A |
| Publish job | `publishingJobId` | M9-A |

Reuse existing Kotlin contracts (`ScriptPackage`, `AudioPackage`, Creator `MediaEntity`) — **do not invent parallel schemas**.

---

## 13. Unified Storage

Derived from Pro `ProjectPaths` + Creator needs (not the blind example tree):

```text
{filesDir}/projects/{projectId}/
  project.json                 # identity + status (new thin manifest)
  originals/camera|media/      # M6 imports (ProjectPaths already define)
  generated/script/            # M2 (existing)
  generated/audio/             # M3 (existing)
  generated/video/             # reserved → M4-A working
  vision/                      # M7 selection JSON (new additive)
  research/                    # M5-A OCR outputs (new additive)
  renders/{renderId}/final.mp4 # M4-A output
  publishing/                  # M9-A jobs
  logs/
```

**Rules:** project-relative paths in JSON; minimize copies (reference MediaStore URI until render needs bytes); never store API secrets in project tree (use encrypted settings like M1).

---

## 14. Unified Data Flow

```text
M1 projectId
  → M2 ScriptPackage
    → M3 AudioPackage (WAVs + voice.json + totalDurationMs)
  → M6 media files + Room
    → M7 scores / KEEP / best
  → [optional] M5-A text → M2 regenerate
  → M4-A: selected clips + narration (+ captions/BGM)
    → final.mp4
  → M9-A: SEO + publish
```

Voice-first timing for V1: **`totalDurationMs` drives timeline length** (same principle as Phase 2C, implemented on-device).

---

## 15. Unified UI Workflow

**Target flow:**

HOME → PROJECT → SCRIPT → VOICE → CAPTURE/IMPORT → MEDIA LIBRARY → AI VISION → SHOT SELECTION → EDIT → PREVIEW → RENDER → SEO → PUBLISH → RESULT

| Step | Status | Source screen |
|------|--------|---------------|
| Home / Projects | **Exists** | M1 |
| Script | **Exists** | M1 `ScriptAi` |
| Voice | **Exists** | M1 `AudioTts` |
| Capture / Import | **Exists** | M6/M7 |
| Media Library | **Exists** | M6/M7 |
| AI Vision / selection | **Partial** | M7 analysis; weak dedicated “selection” UX |
| Edit / Timeline | **Missing** (placeholder tile) | — |
| Preview / Render | **Missing** | — |
| SEO / Publish | **Missing** (placeholder) | — |
| Result / Analytics | **Missing** | — |

**Conflicts:** Pro vs Creator duplicate project models; Creator placeholders vs Pro live Script/Voice; same Creator `applicationId` on M6/M7 trees.

**Principle:** One APK; reuse existing screens; fill placeholders rather than redesign.

---

## 16. M4-A Design

### Capability classification (from Windows M4/M8 taxonomy — not a commit to port all)

| Capability | Mobile V1 |
|------------|-----------|
| Clip order / basic trim | **REQUIRED** |
| Scale / crop / 9:16 (or script aspect) | **REQUIRED** |
| Attach M3 narration (concat segments → one A/V mux) | **REQUIRED** |
| Image-to-video stills | **REQUIRED** (common field capture) |
| Final MP4 render | **REQUIRED** |
| Preview before render | **REQUIRED** |
| Simple fade / cut | **OPTIONAL** |
| Captions burn-in | **OPTIONAL** (high value; phase V1.1 if heavy) |
| BGM + ducking | **OPTIONAL** |
| SFX / punch / emoji overlays | **WINDOWS-ONLY** (M8 EffectsComposer) |
| AI Creative Director / Pexels | **WINDOWS-ONLY** (M8) |
| Full ASS subtitle styling | **WINDOWS-ONLY** initially |

### Technology preference (repo-evidence)

| Option | In Android deps today? | Guidance |
|--------|------------------------|----------|
| Media3 Transformer | **No** | **Preferred first evaluation** — Google-supported, HW encode, fits Compose apps |
| CameraX | **Yes** (M6/M7) | Capture only — not a compositor |
| FFmpegKit | **No** | Only if Media3 cannot meet REQUIRED set; APK size/thermal cost |
| Copy Windows Python | N/A | **Forbidden** on device |

### Performance rules

- Prefer **one** video encode pass  
- Concat M3 WAVs once (PCM) — **no** WAV→MP3→WAV  
- Render from selected clips only  
- Avoid dual full-resolution intermediates  

---

## 17. M5-A Design

| Capability | Class |
|------------|-------|
| On-device OCR (EN; BN/HI as ML Kit allows) | **REQUIRED** for M5-A MVP |
| Number / heading extraction from OCR text | **OPTIONAL** |
| Attach OCR result to project / feed M2 | **REQUIRED** (contract) |
| YouTube URL ingest / yt-dlp | **WINDOWS-ONLY** (desktop M5) |
| Full screen-recording OCR pipeline | **OPTIONAL** / mostly Windows |
| Cloud OCR APIs | Avoid for V1 |

Add ML Kit **Text Recognition** (not present today). Keep object-detection for M6/M7 as-is.

---

## 18. M9-A Design

| Capability | Class |
|------------|-------|
| Title/description/tags/hashtags from topic+script (rule/token) | **REQUIRED** |
| Thumbnail pick (frame or M7 thumb) | **REQUIRED** |
| Mock publish + status UI | **REQUIRED** for V1 acceptance |
| Real YouTube / Telegram | **OPTIONAL** after mock E2E (credentials) |
| Full Windows queue/analytics parity | **WINDOWS-ONLY** initially |

SEO audit: Phase 2A bridge SEO is **rule/token-based**, not AI — reuse that minimal approach on Android.

---

## 19. Performance Strategy

| Goal | Tactic |
|------|--------|
| Render time | Media3 HW encode; short V1 targets (≤60s) first |
| Battery / thermal | Single encode; background WorkManager with constraints |
| RAM | Stream transforms; avoid full decoded timelines in memory |
| Storage | Selected media only; project-relative; no secret bloat |
| Audio path | M3 WAV → mux; forbid unnecessary re-encode chains |

Reference: Windows ~10 min for 5 min video is **not** the Android V1 bar — ship short-form first.

---

## 20. Windows Independence

Android V1 **must not** require:

- Windows M4 / M5 / M8 / M9  
- Windows filesystem / FFmpeg / PC online  
- `bridges/m3_m8` or `bridges/m8_m9` at runtime  

Final MP4 + publish attempt must complete on-device.

---

## 21. Future Windows Compatibility

- Keep artifact shapes compatible with Phase 2D package (`script.json`, M3 audio layout, media bytes, vision JSON).  
- **Do not implement** Android→Windows export in V1 build phases until mobile E2E works.  
- Do not break Windows bridges while adding Android modules (separate packages).

---

## 22. Protected Existing Code

| Tree | Rule |
|------|------|
| `CURRENT_M1_M3/` M2/M3 engines | Prefer untouched; merge/nav only as needed |
| `M4/` | **Never** replace with M4-A; no edits for Android V1 |
| `M8/m8/vendor/m4/` | **Untouched** |
| `M5/` Windows | **Untouched** |
| `M8/`, `M9/` | **Untouched** for Android V1 |
| `bridges/m3_m8/`, `bridges/m8_m9/` | **Untouched** |
| `M6/` / `M7/` working camera/dataset | Prefer additive fixes only |

M4-A / M5-A / M9-A live in **new Android source packages**, not inside Windows folders.

---

## 23. Feature Gaps (acceptance flow mapping)

| Step | Status |
|------|--------|
| 1 Create project | **Implemented** (both apps) |
| 2 Enter topic | **Implemented** |
| 3–4 Script generate/edit | **Partial** (Mock) |
| 5–6 Narration generate/review | **Partial** (TTS quality / no normalizer) |
| 7–8 Capture + library | **Implemented** (Creator) |
| 9 M5-A OCR | **Missing** |
| 10–11 M7 analysis / KEEP | **Partial** (exists; weak M4-A handoff) |
| 12–17 Timeline / BGM / SFX / captions / preview | **Missing** (M4-A) |
| 18 Render final MP4 | **Missing** |
| 19 QC | **Missing** on Android (M8 QC is Windows) |
| 20–22 SEO / publish / result | **Missing** (M9-A) |
| 23 Persist state | **Partial** |

---

## 24. Implementation Sequence (do not implement now)

Safest order answering the required questions:

1. **Complete first:** **App unification decision + M1 shell merge** (bring M2/M3 + M6/M7 into one APK). Without this, mobile V1 cannot be “one Creator.”  
2. **Canonical shell:** **Yes — M1-derived shell** should be canonical navigation host (or Creator shell that embeds M1–M3 modules — pick one; recommend **single `applicationId`, M1-style project storage**).  
3. **Bring M1–M3 with M6/M7:** Merge code modules into one Gradle app; unify `projectId` + storage; wire Creator tiles Script/Voice to existing screens; fix guided ingest.  
4. **M4-A:** After unified media+voice available in one project — **largest new build**.  
5. **M5-A:** After or parallel to unification; **before** claiming research completeness; can lag first render MVP.  
6. **M9-A:** After `final.mp4` exists; start **mock**; real later.  
7. **Full E2E Android testing:** Begin when M4-A produces MP4; expand when M9-A mock works.  
8. **Remain untouched:** Windows M4/M5/M8/M9, vendor M4, bridges; avoid M3 rewrite.

**Suggested milestones:**

```text
P0  One APK + unified project identity/storage
P1  Wire Script/Voice tiles; M7→selection export for editor
P2  M4-A MVP: selected clips + narration → final.mp4 + preview
P3  Lightweight Android QC (duration/audio/video streams)
P4  M9-A mock SEO/publish
P5  M5-A OCR assist
P6  Optional captions/BGM; real publish; Phase 2D export later
```

---

## 25. Test Strategy (specify only — do not write yet)

| Module | Future tests |
|--------|----------------|
| M1 | Project CRUD; nav to all studios; persistence |
| M2 | ScriptPackage validity; EN/BN/HI; duration bounds |
| M3 | WAV format; timeline contiguous; rate applied; locale availability |
| M4-A | Concat audio; mux; resolution; duration≈narration; file playable |
| M5-A | OCR extracts text from fixture image; JSON persisted |
| M6 | Capture/import associates `projectId` |
| M7 | KEEP/best selection feeds M4-A input list |
| M9-A | SEO fields non-empty; mock publish status |

**E2E:** Topic → script → voice → media → vision → timeline → render → QC → SEO → publish — **all on Android emulator/device without Windows**.

**Physical audio tests (later):** Listen EN/BN/HI samples including numbers (`105`), dates, abbreviations — code cannot prove TTS quality.

---

## 26. Android V1 Acceptance Criteria

Must prove:

1. Single installed Android app  
2. One project contains script + voice + media + vision decisions  
3. On-device render yields **real `final.mp4`** with audible narration and picture  
4. No Windows process involved in that run  
5. Mock publish completes with title/tags  
6. Existing Windows modules/bridges remain unmodified and still buildable  
7. Protected trees unchanged  

---

## 27. Risks

| Risk | Mitigation |
|------|------------|
| App merge complexity (Pro vs Creator) | Incremental module ports; freeze Windows workstreams |
| Media3 cannot meet needs | Spike before full M4-A; FFmpegKit only with size budget |
| Device thermal on long renders | Cap V1 duration (≤60s); HW encode |
| TTS locale gaps on devices | Fallback Mock + operator warning |
| Dual M6/M7 trees drift | Treat M7 as canonical Creator base |
| Scope creep (full M8 effects on phone) | Enforce §16 WINDOWS-ONLY list |
| Master prompt vs product override confusion | This document is V1 product authority |

---

## 28. Open Questions

1. Final `applicationId` for unified app (`com.avsp.creator` vs `com.avsp.pro` vs new)?  
2. Is Mock script acceptable for public V1 or must real LLM ship in same milestone as M4-A?  
3. Are burn-in captions REQUIRED for first store build or V1.1?  
4. Minimum supported Android API for Media3 Transformer spike?  
5. Real YouTube in V1 launch criteria or post-MVP?  
6. Retain separate Windows product track in parallel marketing/docs?

---

## Appendix — Module responsibility cheat sheet

| Module | V1 job | Status |
|--------|--------|--------|
| M1 | Shell + identity | Exists — unify |
| M2 | Script | Exists — Mock |
| M3 | Voice | Exists — protect |
| M4-A | Render MP4 | **New** |
| M5-A | OCR/intel | **New** |
| M6 | Capture | Exists — protect |
| M7 | Vision/KEEP | Exists — handoff to M4-A |
| M9-A | SEO/publish | **New** |
| M8 / Win M4/M5/M9 | Not in Android V1 path | Protected |

---

**END OF DESIGN — NO IMPLEMENTATION**  
**STOP. Do not code M4-A / M5-A / M9-A. Do not start Android→Windows. Do not modify existing modules.**
