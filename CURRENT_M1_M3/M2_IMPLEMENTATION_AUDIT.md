# M2 Implementation Audit — Real Gemini Integration Gate

**Date:** 2026-08-26  
**App:** `com.avsp.pro`  
**Scope:** Actual source under `CURRENT_M1_M3/app/src/main/java/com/avsp/pro/script/`  
**Rule:** Documentation claims are ignored unless backed by code.

## Classification legend

| Tag | Meaning |
|-----|---------|
| IMPLEMENTED | Present and usable in production path |
| PARTIAL | Exists but incomplete / not wired |
| MISSING | Not present |
| MOCK-ONLY | Only mock/placeholder behavior |

---

## 1. M2 package / files

| Path | Role | Status |
|------|------|--------|
| `script/contract/ScriptContracts.kt` | ScriptPackage, ScriptScene, request | IMPLEMENTED |
| `script/generator/ScriptGenerator.kt` | Provider interface + registry | IMPLEMENTED |
| `script/generator/MockScriptGenerator.kt` | Deterministic mock | MOCK-ONLY (production path) |
| `script/generator/DefaultScriptGeneratorRegistry.kt` | Always resolves Mock | MOCK-ONLY |
| `script/generator/GeminiScriptGenerator.kt` | — | MISSING (pre-implementation) |
| `script/repository/ScriptRepository.kt` | Generate/save/load/edit | IMPLEMENTED |
| `script/validation/ScriptValidator.kt` | Request + package validation | IMPLEMENTED |
| `script/language/ScriptLanguage.kt` | en/bn/hi packs | IMPLEMENTED |
| `script/integration/ScriptToTtsContract.kt` | M2→M3 handoff | IMPLEMENTED |
| `script/ui/ScriptAiScreen.kt` / `ScriptAiViewModel.kt` | UI | IMPLEMENTED |

---

## 2. ScriptPackage contract

**IMPLEMENTED** in `ScriptContracts.kt`:

- `projectId`, `scriptId`, `topic`, `language`, `title`
- `hook`, `introduction`, `cta`, `ending`
- `scenes[]` with `sceneId`, `order`, `durationMs`, `narration`, `onScreenText`, `visualDescription`, `shotType`, `cameraDirection`, `bRollSuggestion`, `transition`, `notes`
- `estimatedDurationMs`, `targetDurationMs`, `estimatedNarrationDurationMs`
- `validation`, `metadata` (generatorId/mode, contentType, audience, platform, timestamps)

**MISSING from contract (do not invent):** tags, hashtags, free-form `description` field.

---

## 3. M1 project → script flow

**IMPLEMENTED:**

`ProjectDetail` → `script/{projectId}` → `ScriptAiViewModel.start(projectId)` → `ScriptRepository.generate(ScriptGenerationRequest(projectId=…))` → storage under `generated/script/`.

---

## 4. TTS / audio contract

**IMPLEMENTED (consume-only from M2):** `ScriptToTtsContract.fromPackage(ScriptPackage)` → `ScriptNarrationHandoff`.  
M3 `AudioRepository` loads script via `scriptRepository.load(projectId)`.  
**Do not modify M3 in this task.**

---

## 5. Existing Gemini / API abstraction

| Item | Status |
|------|--------|
| M2 `ScriptGenerator` remote adapter | MISSING |
| M6 `GeminiShotPlanner` (camera shot plans) | PARTIAL — exists but **not** M2 script path; silent Local fallback |
| Shared Gemini SDK dependency | MISSING (HttpURLConnection only in M6 planner) |

---

## 6. Configuration / API key

| Item | Status |
|------|--------|
| `SecureConfigStore` + `SecureConfigKeys.AI_API` | IMPLEMENTED |
| Encrypted prefs storage | IMPLEMENTED |
| Settings UI shows CONFIGURED / NOT_CONFIGURED | IMPLEMENTED |
| Settings UI to **enter** AI key | MISSING (display-only) |
| Key in source / Git | Not present (good) |

---

## 7. Existing M2 tests

| Test | Status |
|------|--------|
| `M2ScriptAiTest` (mock generate/validate/save/edit/handoff) | IMPLEMENTED |
| `M2NoSecretsScanTest` | IMPLEMENTED |
| Gemini parse / missing key / HTTP failure tests | MISSING |

---

## 8. Placeholder / fake Gemini

| Item | Status |
|------|--------|
| Production generator path | MOCK-ONLY (`DefaultScriptGeneratorRegistry.resolve` always Mock, even when AI CONFIGURED) |
| UI when NOT_CONFIGURED | Correctly states Mock |
| UI when CONFIGURED | PARTIAL — implies remote available but still runs Mock |

---

## Requirement gate (pre-implementation)

| Requirement | Classification |
|-------------|----------------|
| Actual M2 package | IMPLEMENTED |
| Script contract | IMPLEMENTED |
| Real Gemini script provider | MISSING |
| Registry selects Gemini when configured | MOCK-ONLY |
| No hard-coded API key | IMPLEMENTED |
| Structured parse → ScriptPackage | MISSING |
| Error handling for Gemini failures | MISSING |
| Explicit provider labeling | PARTIAL (metadata exists; always mock) |
| M6/M7 untouched | IMPLEMENTED (baseline freeze) |

**Verdict before implementation:** M2 foundation is real; **M2 Real Gemini is NOT complete** (MOCK-ONLY production path).
