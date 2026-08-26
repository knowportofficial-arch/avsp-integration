# AVSP Android V1 — Final APK Owner / Base Architecture

**Status:** READ-ONLY ARCHITECTURE DECISION — NO IMPLEMENTATION  
**Date:** 2026-03-26  
**Repository:** `knowportofficial-arch/avsp-integration`  

**Constraint:** No merges, no Gradle/package/source changes, no OAuth creation, no APK build.

---

## 1. Objective

Select **ONE engineering owner/base** for the final single Android-only AVSP V1 APK:

```text
M1 → M2 → M3 → M4-A → M5-A → M6 → M7 → M9-A → ANDROID OUTPUT
```

Windows must not be required for normal Android production. Hybrid (Phase 2D) remains later.

---

## 2. Candidate Android projects

### Named paths in the request vs this repository

| Requested name | Present in `/workspace`? | Mapped tree |
|----------------|--------------------------|-------------|
| `AVSP_M1_ORIGIN` | **No** such directory | → **`CURRENT_M1_M3/`** (`com.avsp.pro`) |
| `AVSP_M6_M62_TEST/android` | **No** | → **`M6/android/`** (`com.avsp.creator`) |
| `AVSP_M18_FINAL_AI_CAPTURE_UPGRADE/avsp-m17/android` | **No** | → closest capture+vision lineage: **`M7/android/`** (`com.avsp.creator`, `0.1.8-m7`) |

**Only three Android application trees exist in this integration repo.** There is no separate M18 project to choose.

---

## 3. Actual architecture of each candidate

| Dimension | `CURRENT_M1_M3` | `M6/android` | `M7/android` |
|-----------|----------------|--------------|--------------|
| Product role | Pro shell + Script + Voice | Creator capture | Creator capture + dataset |
| `applicationId` | `com.avsp.pro` | `com.avsp.creator` | `com.avsp.creator` |
| `namespace` | `com.avsp.pro` | `com.avsp.creator` | `com.avsp.creator` |
| `versionName` / `versionCode` | `1.0.0-m1` / 1 | `0.1.7` / 7 | `0.1.8-m7` / 8 |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 35 / 35 | 26 / 34 / 34 | 26 / 34 / 34 |
| Kotlin / AGP | 2.0.21 / 8.7.2 | 1.9.22 / 8.2.2 | 1.9.22 / 8.2.2 |
| Compose | BOM 2024.10.01 + Kotlin Compose plugin | BOM 2024.02.00 + compiler ext 1.5.8 | same as M6 |
| Application | `com.avsp.pro.AvspApplication` | `com.avsp.creator.AvspApplication` | same + dataset APIs |
| Launcher | `MainActivity` | `MainActivity` + `GuidedCaptureActivity` | same as M6 |
| Gradle wrapper | yes (`8.9`) | yes (`8.5`) | yes (`8.5`) |
| Signing configs | **none** (debug default) | **none** | **none** |
| `local.properties` | often absent in clean trees | present in this env | present |

---

## 4. M1–M3 comparison

| Capability | CURRENT_M1_M3 | M6 | M7 |
|------------|---------------|----|----|
| M1 projects / settings / logs / modules UI | **Yes** | Creator projects UI only | Creator projects UI only |
| `FileAvspStorage` / `ProjectPaths` | **Yes** | No | No |
| M2 `ScriptPackage` + Script AI UI | **Yes** | No | No |
| M3 `AudioPackage` + TTS UI | **Yes** | No | No |
| Encrypted credential store (AI/YT/Meta/TG/Web) | **Yes** | No (plain prefs) | No |
| WorkManager hook | **Yes** (stub worker) | No | No |
| Unit tests (approx) | 9 files / ~69 recorded | 7 / ~39 | 12 / ~63 |

**Verdict:** Only **`CURRENT_M1_M3`** owns M1–M3. M6/M7 cannot host Script/Voice without importing that codebase.

---

## 5. M6–M7 comparison

| Capability | CURRENT_M1_M3 | M6 | M7 |
|------------|---------------|----|----|
| CameraX capture | No (stub README) | **Yes** | **Yes** (superset) |
| Guided capture | No | **Yes** | **Yes** |
| Media Library | Placeholder empty | **Yes** | **Yes** + quality badges |
| ML Kit object/label | No | **Yes** | **Yes** |
| M7 dataset / KEEP / best | Stub README | No | **Yes** |
| Room media DB | `MediaAssetEntity` (different schema) | `avsp_creator_db` v2 | `avsp_creator_db` v3 |
| Workspace Script/Voice tiles | Live routes exist | Placeholders | Placeholders |

**Verdict:** **`M7/android` supersets `M6/android`**. M6 must not be the final base. Capture+vision reusable source of truth = **M7**.

---

## 6. M4-A / M5-A / M9-A readiness

| Module | Any candidate today? | Fit into final APK |
|--------|----------------------|--------------------|
| M4-A | **Missing** everywhere | New packages under final base; consume M3 `AudioPackage` + M7 selection |
| M5-A | **Missing** (no text-OCR dep) | New; ML Kit Text Recognition + research JSON |
| M9-A | **Missing** on Android | New; reuse Pro encrypted credential *slots*; mock→real later |

**Integration boundary (design only):**

```text
CURRENT_M1_M3 shell (base)
  ├── existing: script/, audio/, storage/, settings/
  ├── integrate from M7: capture/, dataset/ (repackaged)
  └── new: video/ (M4-A), research/ (M5-A), publishing/ (M9-A)
```

---

## 7. Dependency comparison

| Stack | Prefer for final base | Note |
|-------|----------------------|------|
| Newer AGP/Kotlin/Compose | **CURRENT_M1_M3** | Avoid downgrading Pro to M7’s 1.9.22/8.2.2 |
| CameraX + ML Kit | Bring from **M7** into base | Additive deps |
| Security-crypto | Already on Pro | Required for OAuth/API secrets |
| Media3 (future M4-A) | Add to base later | Not present anywhere yet |

**Conflict risk:** Compose plugin style differs (Kotlin 2 Compose plugin vs `kotlinCompilerExtensionVersion`). Unification must standardize on **Pro’s toolchain**, not M7’s older one.

---

## 8. UI / navigation comparison

| | CURRENT_M1_M3 | M7 (and M6) |
|--|---------------|-------------|
| Graph | Home, Projects, Detail, Script, Audio, Media, Modules, Settings, Logs | Splash, Home, Projects, Create, Workspace, Camera, MediaLibrary, Settings |
| Script/Voice | **Live** | Placeholder tiles |
| Camera/Library | Weak/empty Media | **Live** |

**Final UX:** one nav host (Pro shell extended) that routes to Script/Audio **and** Camera/Library/Vision/Edit/Publish. Do not keep two apps.

---

## 9. Data / database comparison

| | CURRENT_M1_M3 | M7 |
|--|---------------|----|
| DB file | `avsp_m1.db` | `avsp_creator_db` |
| Version | 2 | 3 |
| Migration style | Non-destructive `MIGRATION_1_2` | `fallbackToDestructiveMigration()` |
| Project storage files | `filesDir/projects/{id}/…` via `FileAvspStorage` | MediaStore URIs + guided files + Room |

**Final storage authority:** **Pro `FileAvspStorage` + `ProjectPaths`** (already aligned with Phase 2D / mobile V1 plans). Creator Room media schema must be **ported/adapted**, not used as the sole project root.

---

## 10. Package / application identity comparison

| ID | Owner today | Collision |
|----|-------------|-----------|
| `com.avsp.pro` | CURRENT_M1_M3 only | Unique |
| `com.avsp.creator` | **Both** M6 and M7 | **Cannot install both APKs** |

Choosing Creator ID without retiring M6/M7 dual trees continues confusion. Pro ID is unique and already wired to credential keys.

---

## 11. Integration risks

| Risk | Severity |
|------|----------|
| Porting M7 camera/dataset into Pro packages | High effort — expected |
| Porting entire M1–M3 into Creator instead | High effort **plus** older toolchain + no FileAvspStorage + weak secrets |
| Keeping M6 as base | **Reject** — loses M7 dataset |
| Dual Room DBs / dual project models during merge | High — needs one identity |
| applicationId change after OAuth registered | High — avoid flip-flop |
| Resource/name clashes (`strings`, themes, nav) | Medium — resolve at merge time |

---

## 12. Recommended final APK base

### Decision

**Engineering BASE (owner) = `CURRENT_M1_M3/`**

**Capture/vision SOURCE to integrate later = `M7/android/` (not M6).**

**Do not use M6 as base.**  
**Do not use a non-existent M18 path.**  
**Do not treat “merge both equally” as ownership — Cursor needs one Gradle root.**

### Why Pro base (evidence)

1. Owns **M1–M3** contracts (`ScriptPackage`, `AudioPackage`, `ProjectPaths`) required before M4-A.  
2. Owns **encrypted credential infrastructure** required before M9-A.  
3. Newer **SDK/toolchain** (compile/target 35, Kotlin 2.0, AGP 8.7).  
4. Storage model already chosen in mobile V1 / Phase 2D designs.  
5. M7 is the correct **donor** for CameraX/ML Kit/dataset — additive to Pro, not a replacement shell.  
6. Absorbing Script/Voice into Creator would rewrite more of the contract spine onto an older stack.

---

## 13. Final applicationId recommendation

**`com.avsp.pro`**

| Option | Recommendation |
|--------|----------------|
| Keep `com.avsp.pro` | **YES** — matches base; unique; credential slots already keyed for this app |
| Switch to `com.avsp.creator` | **Not for V1** — forces applicationId migration, Play/OAuth rebind, and package rename of entire Pro tree |
| Invent new ID | **Forbidden** by task |

If product branding later requires “Creator” on store listing, that is a **marketing/label** decision; technical `applicationId` should remain `com.avsp.pro` unless a deliberate migration project is funded.

---

## Explicit final statements

> **THE FINAL ANDROID APK SHOULD BE BUILT FROM: `CURRENT_M1_M3/`**

> **THE FINAL ANDROID APPLICATION ID SHOULD BE: `com.avsp.pro`**

> **Cursor/Cloud Agent will use this project as the final Android V1 build base.**

Capture/vision code will be **integrated from `M7/android/` into that base** in a later implementation phase (not now).

---

## Google OAuth / identity block

```text
FINAL_ANDROID_APPLICATION_ID:
com.avsp.pro

FINAL_ANDROID_BASE_PROJECT:
CURRENT_M1_M3/

FINAL_ANDROID_SIGNING_CONFIGURATION:
NOT CONFIGURED IN REPO (no signingConfigs / keystore in Gradle; debug signing only today)

SHA-1:
UNKNOWN / NOT YET CREATED (not verified in this audit; do not invent)
```

**Do not create OAuth clients in this phase.** When created, bind them to **`com.avsp.pro`** and the eventual release keystore SHA-1.

---

## 14. Modules to preserve

| Module | Preserve as |
|--------|-------------|
| M1–M3 in `CURRENT_M1_M3` | **Functional core of final APK** — keep contracts/tests green |
| M7 camera + dataset | **Reusable source** — integrate without losing GuidedCapture / KEEP/best |
| M6 | Reference only if M7 lacks a fix; otherwise **do not dual-maintain** |
| Windows M4/M5/M8/M9 + bridges | Untouched; not APK hosts |

---

## 15. Modules to integrate later

| Item | Into base |
|------|-----------|
| M7 `capture/` + `dataset/` (+ resources/permissions) | Yes |
| M4-A / M5-A / M9-A | New packages under `com.avsp.pro` |
| Content Intelligence | Later (prebuild audit) |
| M6-only deltas | Only if missing from M7 |

---

## 16. Files that must remain untouched (until an explicit merge PR)

**Do not edit in this decision phase (and prefer stability during early merge):**

- `CURRENT_M1_M3/.../script/**`, `audio/**`, `storage/FileAvspStorage.kt`, `core/integration/IntegrationContracts.kt`
- M2/M3 unit tests under `CURRENT_M1_M3/app/src/test/**`
- Entire Windows trees and `bridges/**`
- Do not delete M7 camera/dataset sources when integrating — port/copy with attribution in a dedicated PR

M7 `AvspApplication` / Room / nav will **conflict** with Pro equivalents — resolution is future merge work, not this audit.

---

## 17. Proposed integration sequence (no implementation now)

1. Freeze this decision (base = `CURRENT_M1_M3`, id = `com.avsp.pro`).  
2. Inventory M7 packages/resources/permissions to import.  
3. Add CameraX/ML Kit deps to Pro Gradle (future PR).  
4. Port M7 capture + dataset under `com.avsp.pro…` (or clear subpackages) with one Room migration strategy.  
5. Extend Pro nav: Camera, Library, Vision, Edit, Publish.  
6. Add M4-A / M5-A / M9-A.  
7. Retire shipping M6/M7 standalone APKs (same store listing as Pro).  
8. Create release signing + SHA-1 → then Google OAuth clients.

---

## 18. Google OAuth consequence

All future Google Cloud OAuth / YouTube / Sheets clients for Android V1 must use:

- Package name: **`com.avsp.pro`**
- SHA-1 from the **release** (and debug, if needed) keystore — **not yet in repo**

Creating OAuth against `com.avsp.creator` would target the **wrong** final APK under this decision.

---

## 19. Final APK build responsibility

| Role | Owner |
|------|-------|
| Gradle root / CI assemble | **`CURRENT_M1_M3/`** |
| Feature donors | `M7/android/` (capture/vision) |
| Not a build root | `M6/android/` (obsolete as product base) |
| Cloud Agent default checkout for Android V1 work | **`CURRENT_M1_M3/`** |

---

## 20. Acceptance criteria (for this decision doc)

- [x] Only existing trees considered; missing named paths mapped  
- [x] One base chosen (`CURRENT_M1_M3/`)  
- [x] One `applicationId` chosen (`com.avsp.pro`)  
- [x] M7 protected as capture/vision donor; M6 not base  
- [x] M1–M3 contracts identified as stable  
- [x] OAuth consequence stated; no credentials created  
- [x] No source/Gradle modifications  

---

## Summary box

| Field | Value |
|-------|--------|
| **THE FINAL ANDROID APK SHOULD BE BUILT FROM** | **`CURRENT_M1_M3/`** |
| **THE FINAL ANDROID APPLICATION ID SHOULD BE** | **`com.avsp.pro`** |
| Capture/vision integrate from | `M7/android/` |
| Discard as base | `M6/android/`; non-existent M18 path |
| Cursor/Cloud Agent build base | **`CURRENT_M1_M3/`** |

**STOP. Do not build the APK. Do not merge projects. Do not create Google OAuth clients.**

---

**END OF ARCHITECTURE DECISION**
