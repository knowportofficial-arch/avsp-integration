# PHASE — Mobile-Only Final Pre-Build Audit

**Status:** FINAL PRE-BUILD GATE — DESIGN ONLY — NO IMPLEMENTATION  
**Date:** 2026-03-26  
**Repository:** `knowportofficial-arch/avsp-integration`  
**Product authority:** `PHASE_MOBILE_ONLY_ANDROID_V1_PLAN.md`  
**Primary target:** Genuinely usable **ANDROID-ONLY** AVSP (Windows not required for V1)

**Constraint:** Inspect only. Create **only** this file. Do not code M4-A/M5-A/M9-A, add APIs, modify modules, Gradle, tests, or bridges.

**Evidence basis:** Master/integration docs, Phase 1–2D plans, feature audit, mobile V1 plan, and **actual source** under `CURRENT_M1_M3/`, `M4/`, `M5/`, `M6/`, `M7/`, `M8/`, `M9/`, `bridges/`.

---

## 1. Executive Summary

| Question | Verdict |
|----------|---------|
| Is Android-only V1 architecture **build-ready**? | **Architecturally yes as a plan; not build-complete.** Gate can open for P0 (one APK) after this audit. |
| Can Android alone finish video+publish today? | **NO** — no renderer, no Android publish, split apps |
| Is M2 AI-powered today? | **NO** — `MockScriptGenerator` only; Gemini slot exists but unused |
| Content Intelligence / daily topics? | **MISSING** — not implemented |
| Google Sheets / Drive / DataLog? | **MISSING — NOT IMPLEMENTED** |
| Last 5 / Next 5 day planning? | **MISSING** |
| Pexels on Android? | **MISSING** (Windows M8 only) |
| Biggest blockers | Dual APK; no M4-A; no Content Intelligence; M2 Mock; no M9-A |

**Build posture:** Proceed with development **only after** accepting this audit’s roadmap. Do not start M4-A/M5-A/M9-A or real publishing in this phase.

---

## 2. Module Purpose Audit (Android V1)

| Module | Purpose | Status summary |
|--------|---------|----------------|
| M1 | Project shell / identity / nav | **PARTIAL** (Pro only) |
| M2 | Script from topic | **MOCK** |
| M3 | TTS → AudioPackage | **PARTIAL** (usable; quality/locale gaps) |
| M4-A | On-device edit/render → MP4 | **MISSING** (new) |
| M5-A | OCR / light research intel | **MISSING** (new; Windows M5 ≠ Android) |
| M6 | Capture / import | **PARTIAL** (Creator) |
| M7 | Vision / KEEP / best | **PARTIAL** (no editor handoff) |
| M9-A | SEO + publish | **MISSING** (new; Windows M9 reference) |

---

## 3. M1 Audit

| # | Answer |
|---|--------|
| 1 Purpose | Project foundation, navigation, settings, logs |
| 2 User action | Create/open projects; enter studios |
| 3 Input | Operator |
| 4 Processing | Persistence, routing |
| 5 Output | `projectId`, project records |
| 6 Downstream | All modules |
| 7 UI | Home, Projects, Detail, Media, Modules, Settings, Logs, Script, Audio |
| 8 Automation | Minimal (WorkManager ACK stub) |
| 9 External APIs | None required |
| 10 Implemented | Shell, Room, `FileAvspStorage`, encrypted settings slots |
| 11 Partial | Not unified with Creator |
| 12 Mock | N/A |
| 13 Missing | Single-APK host for M6–M7 + future M4-A/M5-A/M9-A |
| 14 Develop | Unify + storage unification |

**Class:** **PARTIAL**

---

## 4. M2 Audit

| # | Answer |
|---|--------|
| 1 Purpose | Topic → structured `ScriptPackage` |
| 2 User action | Generate / edit / regenerate script |
| 3 Input | Topic, language, duration; optional future research |
| 4 Processing | Generator registry |
| 5 Output | `script.json` / `{scriptId}.json` |
| 6 Downstream | M3 |
| 7 UI | `ScriptAiScreen` |
| 8 Automation | On-demand generate only |
| 9 External | Future Gemini (slot only) |

### Is M2 actually AI-powered today?

**NO.**

Evidence (`DefaultScriptGeneratorRegistry.kt`):

- `available()` returns **only** Mock  
- Even when `SecureConfigKeys.AI_API` is **CONFIGURED**, resolve() **still uses Mock** until a real provider adapter is registered  
- Interface comment lists Gemini/OpenAI as future — **no Gemini client in M2**

Gemini **does** exist in **Windows M8** `CreativeDirector` (`gemini-2.0-flash`) — not in Android M2.

| Class | Item |
|-------|------|
| **MOCK** | Script content |
| **IMPLEMENTED** | Contracts, UI, EN/BN/HI packs, duration validation, persistence |
| **MISSING** | Real `ScriptGenerator` adapter (Gemini), structured JSON prompts, retry, research ingest |
| **EXTERNAL DEPENDENCY** | Gemini API key (future) |

**For real Gemini production later:** implement `GeminiScriptGenerator : ScriptGenerator`, register in registry when `AI_API` configured, secure key via `EncryptedSecureConfigStore`, offline fallback to Mock, never invent facts without verified M5-A/source input.

---

## 5. M3 Audit

| # | Answer |
|---|--------|
| 1 Purpose | Narration audio + timing for video |
| 2 User action | Generate / preview / regenerate voice |
| 3 Input | Script handoff |
| 4 Processing | Mock tone or Android `TextToSpeech` |
| 5 Output | Segment WAVs + `voice.json` / `AudioPackage` |
| 6 Downstream | **M4-A** (V1); Windows bridges only for hybrid later |
| 7 UI | `AudioTtsScreen` |
| 8 Automation | Per-package generation |
| 9 External | Optional future cloud TTS; local Android TTS now |

### TTS quality contract (code-verifiable)

| Topic | Finding | Class |
|-------|---------|-------|
| Providers | `MockTtsEngine`, `AndroidTtsEngine` | **IMPLEMENTED** |
| Locales | `en-US`, `bn-BD`, `hi-IN` (not `en-IN` / `bn-IN`) | **PARTIAL** |
| Rate / pitch | Applied (`0.5–2.0`) | **IMPLEMENTED** |
| SSML | None | **MISSING** |
| Number/date/abbrev/unit preprocessing | None — raw text to TTS | **MISSING** |
| pauseAfter | Handoff metadata; **not** timeline silence | **PARTIAL** |
| Segment timing / totalDurationMs | Contiguous ms; sum | **IMPLEMENTED** |
| WAV | pcm_s16le, 16 kHz, mono | **IMPLEMENTED** |
| Voice selection UI | voiceId field; engine-default voices | **PARTIAL** |

### Example strings (raw vs spoken)

| Input | Code behavior | Spoken form |
|-------|---------------|-------------|
| `105` | Passed raw | **PHYSICAL-LISTENING REQUIRED** (engine-dependent) |
| `2026` | Raw | PHYSICAL |
| `₹1500` | Raw | PHYSICAL |
| `10 km` / `5 kg` / `12:30 PM` / `15%` | Raw | PHYSICAL |
| `BSNL` / `Wi-Fi` / `YouTube` | Raw | PHYSICAL |

**CODE-VERIFIABLE:** no normalizer/SSML in repo.  
**PHYSICAL-LISTENING REQUIRED:** clarity of Indian English / Bengali / Hindi; pauses; numbers.

### Future acceptance tests (do not run now)

- Device with en-IN / bn / hi voices installed  
- Script containing all example tokens  
- Human listen checklist: numbers, currency, units, abbreviations, pause at punctuation  
- Compare Mock vs Android TTS  

**Class:** **PARTIAL** (+ **EXTERNAL DEPENDENCY** on device TTS voices)

---

## 6. M4-A Requirements

**NEW Android module — do not modify Windows `M4/` or vendor M4.**

| Feature | Class |
|---------|-------|
| Clip order, trim, scale/crop, 9:16 | **REQUIRED** |
| Images + videos | **REQUIRED** |
| Attach M3 narration (duration authority = `totalDurationMs`) | **REQUIRED** |
| Preview + progress + final MP4 | **REQUIRED** |
| Basic cut/fade | **OPTIONAL** |
| Captions / BGM / ducking | **OPTIONAL** (V1.1 if heavy) |
| SFX / punch / emoji / AI director | **WINDOWS ONLY** (M8) |

**Evaluate later (do not add deps now):** Media3 Transformer first; MediaCodec/MediaMuxer; FFmpegKit only if required.

**Status today:** **MISSING**

---

## 7. M5-A Requirements

**NEW — do not modify Windows `M5/`.**

| Function | Class |
|----------|-------|
| On-device OCR (ML Kit Text Recognition) | **MOBILE REQUIRED** |
| Screenshot / image text → research JSON | **MOBILE REQUIRED** |
| Feed optional text to M2 | **MOBILE OPTIONAL** |
| Stock media discovery (Pexels) | **MOBILE OPTIONAL** (separate contract) |
| YouTube URL / yt-dlp ingest | **WINDOWS ONLY** |
| Desktop Tesseract/OpenCV screen pipeline | **WINDOWS ONLY** |

Windows M5 = library (YouTube + screen OCR); Android ML Kit text OCR **not** in M6/M7 deps today (object-detection/labeling only).

**Status today:** **MISSING**

---

## 8. M6 Audit

| Area | Status |
|------|--------|
| Camera preview / photo / video / guided | **IMPLEMENTED** (Creator) |
| Resolution / FPS / orientation | **PARTIAL**–**IMPLEMENTED** |
| Media library + project association (AI path) | **IMPLEMENTED** |
| Guided → Room auto-ingest | **MISSING** |
| Unified with Pro storage | **MISSING** |
| Workspace tiles Script/Voice/Edit/Publish | **MOCK**/placeholder UI |

**Class:** **PARTIAL** — protect CameraX stack.

---

## 9. M7 Audit

| Area | Status |
|------|--------|
| Quality score, blur, exposure, composition | **IMPLEMENTED** |
| KEEP / RETAKE / REVIEW, best shot, ranking | **IMPLEMENTED** |
| User override / persistence (Room) | **PARTIAL**–**IMPLEMENTED** |
| On-disk selection for editor / M4-A | **MISSING** |
| Windows `m7_snapshot.json` writer | **MISSING** |

**M7 → M4-A contract (design):**  
`mediaId`, file ref, `qualityScore`, `recommendation`, `isBestShot`, optional order → timeline builder.

**Class:** **PARTIAL**

---

## 10. M9-A Requirements

**NEW — do not modify Windows `M9/`.**

| Platform | Windows code | Android V1 |
|----------|--------------|------------|
| YouTube | Real + Mock publishers | Mock **REQUIRED**; Real **OPTIONAL** later (OAuth) |
| Facebook/Meta | Real + Mock | Same |
| Instagram | Real + Mock | Same |
| Telegram | Real + Mock | Same |
| Web | Real + Mock | Optional |

Windows defaults: `force_mock=True`, `.env.example` all `*_USE_MOCK=true`.

**SEO today:** `bridges/m8_m9/seo.py` — **rule/token-based** (topic word tokens → tags/hashtags; title←topic; description←script text). **Not AI.**

**M9-A V1:** port minimal SEO package + mock publish UI; real OAuth later; Gemini SEO optional enhancement only after facts exist.

**Status today:** **MISSING** on Android; Windows **PARTIAL** (mock verified)

---

## 11. Content Intelligence

| Capability | Status |
|------------|--------|
| Daily topic discovery | **MISSING** |
| Important news / trending | **MISSING** |
| Weather / gold / silver / fuel / currency / rashifal / festivals / alerts | **MISSING** |
| Topic ranking / dedupe / schedule | **MISSING** |
| Content queue | **MISSING** |
| Previous history / next 5-day plan | **MISSING** |

Only incidental **test topic strings** (“weather”, “gold price”) in M1/M2 unit tests — **not** integrations.

**Architectural home (no new module number invented):**  
**Content Intelligence / Planning Layer** owned by **M1 shell + dedicated planning package** (feeds M2). Not M8. Not Windows-only by necessity — APIs are mobile-suitable when designed.

**Class:** **MISSING** (+ future **EXTERNAL DEPENDENCY** per source)

---

## 12. News & Trending

| Concern | Status |
|---------|--------|
| News API / RSS client in repo | **MISSING EXTERNAL CONTRACT** |
| Trending API in repo | **MISSING EXTERNAL CONTRACT** |
| Freshness / geo / language filters | **MISSING** |
| Dedup / ranking / attribution | **MISSING** |

**Verification chain (design):**

```text
SOURCE (API/feed)
 → VERIFIED DATA (timestamp, URL, attribution stored)
 → AI PROCESSING (Gemini summarize/rewrite — optional)
 → SCRIPT (M2)
```

**Rule:** Gemini must **not** invent current facts. No source → no factual claim in script (or mark as evergreen/opinion).

---

## 13. Daily Data Sources

| Domain | In repository? | Status |
|--------|----------------|--------|
| Weather | No | **MISSING EXTERNAL CONTRACT** |
| Gold / Silver | No | **MISSING EXTERNAL CONTRACT** |
| Fuel | No | **MISSING EXTERNAL CONTRACT** |
| Currency | No | **MISSING EXTERNAL CONTRACT** |
| Rashifal | No | **MISSING EXTERNAL CONTRACT** |
| Calendar / festivals | No | **MISSING EXTERNAL CONTRACT** |
| Public / transport alerts | No | **MISSING EXTERNAL CONTRACT** |

Do **not** invent providers in this audit. Provider selection is a later product decision.

---

## 14. Pexels / Stock Media

| Item | Evidence | Status |
|------|----------|--------|
| Pexels client | `M8/m8/app/adapters/pexels_client.py` | **IMPLEMENTED** (Windows) |
| Auth | `PEXELS_API_KEY` / `PEXELS_KEY` | Env |
| Search videos/photos, orientation, download, cache | Yes in client | Windows |
| Android Pexels | No Kotlin client | **MISSING** |
| M7 integration of Pexels | Not on Android | **MISSING** |
| M4-A integration | N/A until M4-A exists | **MISSING** |

**Smallest Android contract (design):** optional query → download cache under project `media/stock/` → attribution JSON → selectable like M6 media. **Do not** copy M8 controller blindly.

**Class:** Windows **PARTIAL**/optional; Android **MISSING** / **EXTERNAL DEPENDENCY**

---

## 15. Google Sheets / Drive / DataLog

Repo-wide search (Sheets, Drive API, gspread, DataLog, content calendar, topic queue, last/next 5 days):

| Capability | Status |
|------------|--------|
| Google Sheets integration | **MISSING — NOT IMPLEMENTED** |
| Google Drive integration | **MISSING — NOT IMPLEMENTED** |
| Content calendar | **MISSING — NOT IMPLEMENTED** |
| Production / topic queue | **MISSING — NOT IMPLEMENTED** |
| Published history store (cross-day) | **MISSING** (M9 has per-job analytics SQLite on Windows only) |
| Next 5-day plan | **MISSING — NOT IMPLEMENTED** |
| Analytics write-back to Sheets | **MISSING — NOT IMPLEMENTED** |

M9 Windows analytics = local JSON/SQLite job counters — **not** a content intelligence datalog.

---

## 16. Last 5 Days / Next 5 Days

**Required data flow (design only — not implemented):**

```text
LAST 5 DAYS: published topics, categories, platforms, views/engagement (if available),
             success/fail → performance notes
        ↓
NEXT 5 DAYS: date, topic, category, priority, platform, status
        ↓
DAILY PRODUCTION QUEUE
        ↓
M1 → M2 → M3 → M5-A/M6/M7 → M4-A → M9-A
```

**Current status:** **MISSING** end-to-end.

---

## 17. External API Matrix

| Service | Purpose | Module | Current Code | UI | Credential | Automatic | Mock/Real | Status |
|---------|---------|--------|--------------|-----|------------|-----------|-----------|--------|
| Gemini | Script / EDL AI | M2 future; M8 CreativeDirector | M8 Python only | M8 none | `GEMINI_API_KEY` / `GOOGLE_API_KEY`; Android `AI_API` slot unused for calls | M8 optional | Real if key | Android **MISSING**; M8 **PARTIAL** |
| Pexels | Stock media | M8; future Android | `pexels_client.py` | none | `PEXELS_API_KEY` | M8 fallback | Real if key | Android **MISSING** |
| YouTube Data API | Publish | M9; future M9-A | `youtube.py` | CLI | OAuth tokens | Manual/CLI | Mock default / Real optional | Android **MISSING** |
| Facebook/Meta | Publish | M9 | `facebook.py` | CLI | Page token | Manual | Mock/Real | Android **MISSING** |
| Instagram | Publish | M9 | `instagram` publisher | CLI | IG token | Manual | Mock/Real | Android **MISSING** |
| Telegram Bot | Publish | M9 | `telegram.py` | CLI | Bot token + chat | Manual | Mock/Real | Android **MISSING** |
| Web publish API | Publish | M9 | web publisher | CLI | API key | Manual | Mock/Real | Android **MISSING** |
| Android TTS | Voice | M3 | `AndroidTtsEngine` | Yes | Device voices | On generate | Real (device) | **PARTIAL** |
| Cloud TTS | Voice | Future | Interface only | — | Future | — | — | **MISSING** |
| yt-dlp | YouTube ingest | M5 | Python | none | none | Library | Real if deps | **WINDOWS**; Android N/A |
| Tesseract | OCR | M5 | Python | none | system | Library | Real/mock fallback | **WINDOWS** |
| ML Kit OCR text | OCR | M5-A future | Not in deps | — | none | — | — | **MISSING** |
| Weather | Daily data | Content Intel | none | — | — | — | — | **MISSING EXTERNAL CONTRACT** |
| News | Topics | Content Intel | none | — | — | — | — | **MISSING EXTERNAL CONTRACT** |
| Trending | Topics | Content Intel | none | — | — | — | — | **MISSING EXTERNAL CONTRACT** |
| Gold/Silver/Fuel/Currency/Rashifal | Daily data | Content Intel | none | — | — | — | — | **MISSING EXTERNAL CONTRACT** |
| Google Sheets | Calendar/log | Planning | none | — | OAuth | — | — | **MISSING — NOT IMPLEMENTED** |
| Google Drive | Asset backup | Planning | none | — | OAuth | — | — | **MISSING — NOT IMPLEMENTED** |

No additional undocumented production SaaS clients found beyond the above (plus optional Pillow/ffmpeg system tools on desktop).

---

## 18. Credential & Security Model

| Mechanism | Status |
|-----------|--------|
| Android `EncryptedSecureConfigStore` | **IMPLEMENTED** (AI, YouTube, Meta, Telegram, Web slots) |
| Secrets in APK / git | Forbidden; `NoSecretsSourceScanTest` in Pro |
| Project package secrets | Must never include (Phase 2D) |
| Windows `.env` | Example only; mock defaults |
| Permanent API keys in APK | **MUST NOT** — use encrypted store / backend proxy for sensitive keys where needed |
| OAuth refresh | Windows M9 fields exist; Android flow **MISSING** |

---

## 19. Automation Matrix

| Transition | Status |
|------------|--------|
| Daily content source → topic discovery | **MISSING** |
| Topic verification | **MISSING** |
| Topic selection | **MANUAL** (operator types topic) |
| Script | **PARTIAL** / **MOCK** |
| Voice | **PARTIAL** (manual trigger) |
| Media | **PARTIAL** (manual capture) |
| Vision | **PARTIAL** (on save/analyze) |
| Timeline / render | **MISSING** |
| QC | **MISSING** on Android |
| SEO | **MISSING** on Android (Windows bridge rule-based) |
| Publish | **MISSING** on Android; Windows **MOCK** default |
| Analytics → history → next plan | **MISSING** |

**FULL AUTOMATION** (daily loop) = **MISSING**. V1 can start **semi-automated** (operator selects topic; rest assisted).

---

## 20. Mobile Suitability Matrix

| Capability | Class |
|------------|-------|
| News / weather / commodity APIs | **MOBILE-SUITABLE** / **CLOUD/API DEPENDENT** |
| Gemini script | **MOBILE-SUITABLE** / **CLOUD/API DEPENDENT** |
| Google Sheets/Drive | **MOBILE-SUITABLE** / **CLOUD/API DEPENDENT** |
| YouTube/Telegram upload | **MOBILE-SUITABLE** / **CLOUD/API DEPENDENT** |
| OCR (ML Kit) | **MOBILE-NATIVE** |
| TTS (Android) | **MOBILE-NATIVE** |
| M7 vision | **MOBILE-NATIVE** / **MOBILE-LIGHTWEIGHT** |
| M6 capture | **MOBILE-NATIVE** |
| M4-A render | **MOBILE-HEAVY** |
| M8 EffectsComposer / punch stack | **WINDOWS-ONLY** (reference) |
| yt-dlp / desktop OCR batch | **WINDOWS-ONLY** |

---

## 21. Unified Project / Data Flow

```text
[Content Intelligence — MISSING]
        ↓ topic + verified facts
M1 projectId
  → M2 ScriptPackage (MOCK today)
  → M3 AudioPackage (totalDurationMs)
  → M6 media → M7 selection
  → M5-A OCR optional
  → M4-A timeline (voice-first duration) → final.mp4
  → M9-A SEO + publish → analytics
  → [history / next 5 days — MISSING]
```

### Voice-first timing (M4-A)

| Rule | Design |
|------|--------|
| Authority | `AudioPackage.totalDurationMs` |
| Transfer | Persist voice.json; M4-A reads ms → seconds |
| Clips shorter | Loop/freeze last frame or pad still |
| Clips longer | Trim to allocated window |
| Final MP4 duration | ≈ narration (+ optional fixed CTA if product requires) — avoid dual authorities |

---

## 22. UI Requirements

| Screen | Status |
|--------|--------|
| Project / Script / Voice | **EXISTS** (Pro) |
| Capture / Library / Vision badges | **EXISTS** (Creator) |
| Content Intelligence / planner | **MISSING** |
| Edit / Timeline / Preview / Render | **MISSING** (placeholder tiles) |
| SEO / Publish / Analytics / 5-day | **MISSING** |

One APK must expose the full chain without Windows.

---

## 23. Feature Gaps (top)

1. One APK / unified identity  
2. Real M2 Gemini adapter  
3. Content Intelligence + external data contracts  
4. Sheets/Drive/DataLog / 5-day planning  
5. M4-A renderer  
6. M5-A OCR  
7. M7→M4-A handoff  
8. M9-A mock→real publish  
9. TTS pronunciation preprocessor + en-IN locale option  
10. Android Pexels (optional)

---

## 24. Build Roadmap (evidence-adjusted)

| Phase | Focus | Why this order |
|-------|-------|----------------|
| **P0** | One APK + unified project/storage | Without this, nothing is “one product” |
| **P1** | Wire M2/M3 into Creator UX; guided ingest | Unblocks path to media+voice in one project |
| **P2** | M7 selection export → editor contract | Feeds render |
| **P3** | **M4-A MVP** (clips + narration → MP4) | Defines “real video” |
| **P4** | Android QC (streams/duration/audio present) | Gate before publish |
| **P5** | **M9-A mock** SEO/publish | Completes loop without OAuth risk |
| **P6** | Real Gemini M2 (+ safe fallback) | Quality; can parallel after P0 |
| **P7** | TTS quality (locales, preprocessor) + listening tests | Production audio bar |
| **P8** | **M5-A OCR** | Research assist |
| **P9** | Content Intelligence MVP (even 1–2 verified sources) | Daily automation start |
| **P10** | Pexels optional; real publish; Sheets/5-day; full autonomous loop | Scale |

**Note:** Prior plan put Content Intelligence before M4-A; evidence says **renderer is the harder product proof** — CI can start earlier as a **parallel track** after P0 if staffing allows, but **Definition of Done for video** still needs P3.

---

## 25. Definition of Done

### Module DONE
Feature + UI + I/O contract + persistence + required automation + required API + errors + tests + no forbidden manual workaround.

### ANDROID V1 DONE
**One APK** can run:

Topic (manual or CI) → script → voice → media → vision → edit → render → QC → SEO → publish → analytics  

**without Windows**, producing a **real final MP4**.

Full daily autonomous CI + Sheets + real multi-platform publish = **V1.x / V2**, not blocking first “usable” video.

---

## 26. Acceptance Criteria (pre-build gate)

- [x] External services inventoried from code  
- [x] Content Intelligence marked missing  
- [x] Sheets/Drive/DataLog marked missing  
- [x] M2 confirmed non-AI today  
- [x] M3 TTS contract separated CODE vs PHYSICAL  
- [x] Build roadmap adjusted  
- [x] No source/API/module implementation in this phase  

---

## 27. Risks

| Risk | Note |
|------|------|
| Scope explosion (full CI + all commodities + Sheets) before MP4 | Sequence P3 before P10 |
| Device TTS quality variance | Listening matrix; optional cloud TTS later |
| Gemini hallucination of news | Enforce source→verify→AI chain |
| OAuth complexity on mobile | Mock first |
| Dual M6/M7 trees | Prefer M7 as Creator base |

---

## 28. Final Recommendation

1. **Accept Android-only V1 architecture** as the product target.  
2. **Open build gate at P0** (one APK) — not at M4-A coding until P0–P2 land.  
3. **Protect** Windows M4/M5/M8/M9, vendor M4, bridges, working M3/M6/M7 engines.  
4. **Develop new:** app merge, M4-A, M5-A, M9-A, Content Intelligence layer, optional Gemini M2 adapter.  
5. **Do not claim** Sheets/Drive/news/weather/Pexels-on-Android until implemented.  
6. **Semi-auto V1** (operator topic) is acceptable; full daily autonomy is later.

**STOP. Do not start building modules, APIs, or real publishing in this phase.**

---

**END OF FINAL PRE-BUILD AUDIT**
