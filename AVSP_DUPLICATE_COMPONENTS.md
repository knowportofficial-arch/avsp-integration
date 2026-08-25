# AVSP Duplicate Components

**Phase:** 1 — Audit Only (read-only)  
**Date:** 2026-08-25  
**Method:** Actual filesystem + `git ls-files` + `sha256` / `cmp` — not filenames alone  

```
ENVIRONMENT LIMITATION
- .cursor/install.sh unavailable in the audited environment
- .cursor/start.sh unavailable in the audited environment
- therefore affected runtime checks cannot be treated as module failures
- environment was NOT modified during Phase 1
```

**Rule:** Do not delete, merge, or choose a survivor unless evidence requires it. Prefer stating uncertainty.

---

## 1. Exact comparison: `M4/` vs `M8/m8/vendor/m4/`

### 1.1 Package roots compared

| Label | Path |
|-------|------|
| A (standalone package) | `M4/AVSP_M4_Video_Engine/` |
| B (vendored under M8) | `M8/m8/vendor/m4/` |

Note: `M4/` contains only the `AVSP_M4_Video_Engine/` package directory.

### 1.2 Git-tracked files (authoritative package contents)

Both trees have **exactly 10** tracked files with **identical relative paths**:

| Relative path | Size (bytes) | SHA-256 (both sides) | cmp |
|---------------|--------------|----------------------|-----|
| `app/__init__.py` | 0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 | IDENTICAL |
| `app/engines/__init__.py` | 0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 | IDENTICAL |
| `app/engines/autonomous_video_engine.py` | 3353 | bdcde524e9b03d209e739e2d3abb7408f490e97aaf53affbe7ade9f5e388e875 | IDENTICAL |
| `app/engines/video_engine.py` | 43951 | 4bd8589cb8956890f020de71665031fb665ed2ad0a9ea12ecc4adf205d19bf8f | IDENTICAL |
| `manifest.json` | 155 | 2ded44b651e48159a85bc241871753b39ef502af77111c312111e661dacd29d9 | IDENTICAL |
| `README.md` | 4210 | 24f420494bd9d5df7e5c8e356cd691350157c76cbc1f22a40a3074b981bf65f3 | IDENTICAL |
| `requirements.txt` | 15 | e36848f673847de9ea26fbde287f64d9f34bb0fe7b0546beeeb612148544c4d8 | IDENTICAL |
| `templates/default_landscape.json` | 1128 | dec21963c1488d29b51f453ede8ca052cf784775d5ed73324ee097835aa9915e | IDENTICAL |
| `templates/default_shorts.json` | 1127 | 6170c091233f05cffa65a6de41a0bdad21c85b7caa82984caf5b85a55c49fa18 | IDENTICAL |
| `tests/test_video_engine.py` | 11709 | 9fb1cd48a081fa98d367c0552e8236844ea6e48388c4da2d0959033311509210 | IDENTICAL |

`diff -rq` on source trees (excluding `__pycache__`, `temp`, `output`, `.venv`): **exit 0**.

### 1.3 Files missing / added (tracked source)

| Side | Extra tracked source files |
|------|----------------------------|
| Only in A | **none** |
| Only in B | **none** |

### 1.4 Non-identical source files

**None.** Zero divergent implementation/API/test/config files between the tracked sets.

### 1.5 Untracked / runtime-only files under A (not package content)

Working tree under `M4/AVSP_M4_Video_Engine/` may contain additional **untracked** artifacts from opportunistic test runs (`temp/`, `output/`, `__pycache__/`). These are listed by `.gitignore` (`**/temp/`, `**/output/`, `**/__pycache__/`) and are **not** present under B. They are **not** evidence of source divergence.

Vendor tree B has **no** such runtime artifacts in the clean package layout (10 files).

### 1.6 Frozen-copy claim

M8 docs state `vendor/m4/** = copy of frozen M4 (unmodified source)` (`M8/m8/docs/archive/M8_CHANGED_FILES.md`).  
Byte comparison **supports** “identical copy,” but does **not** by itself establish which directory is the integration “canonical” survivor.

### 1.7 Additional functionality?

| Question | Answer |
|----------|--------|
| Does A contain features B lacks (source)? | **No** |
| Does B contain features A lacks (source)? | **No** |
| Obsolete/divergent code between A and B? | **No** (sources) |
| Safely regarded as **equivalent** implementations? | **Yes** — tracked sources are byte-equivalent |

### 1.8 Which copy is referenced where?

| Consumer | Path referenced | Evidence |
|----------|-----------------|----------|
| M8 runtime adapter | **`M8/m8/vendor/m4`** | `M4Adapter(m4_root=self.root / "vendor" / "m4")` in `controller.py`; importlib `m4_frozen` in `m4_adapter.py` |
| M8 live final MP4 | **Neither** — uses `EffectsComposer` | `controller.py` stage `call_m4_renderer` |
| Standalone M4 tests/CLI | **`M4/AVSP_M4_Video_Engine`** | `tests/test_video_engine.py`, `python -m app.engines.video_engine` from that root |
| Workspace `M4/` outside vendor | Not imported by M8 Python code | no references to `AVSP_M4_Video_Engine` path in M8 app code |

### 1.9 Should both remain?

**Yes, for Phase 1.** Evidence shows equivalence, not a defect requiring deletion. Removing either without a packaging policy would break either standalone M4 testing or M8’s `M4Adapter` load path.

### 1.10 Canonicality determination

**Canonical M4 copy cannot be established from repository evidence.**

Reasons:
- Content is identical — neither is “more complete.”  
- Directory naming (`M4/` vs `vendor/m4`) is organizational, not proof of authority.  
- M8 docs call vendor a “frozen copy,” which describes origin, not a mandate to delete `M4/`.  
- Runtime final render currently uses **EffectsComposer**, not either tree’s `VideoEngine` on the happy path.  
- Choosing one survivor would be an implementation decision, not an audit finding.

---

## 2. Other duplicates / overlaps

### 2.1 Dual Android applications

| App | applicationId | Modules |
|-----|---------------|---------|
| AVSP Pro | `com.avsp.pro` | M1–M3 |
| AVSP Creator | `com.avsp.creator` | M6–M7 |

**Implication:** Not mergeable without explicit design. Do not merge in Phase 1.

### 2.2 Dual render paths (M8)

| Path | Role |
|------|------|
| `VideoEngine` (vendor/m4 via `M4Adapter`) | Available; used in some tests |
| `EffectsComposer` | **Live** producer of `render/final.mp4` |

**Implication:** Parallel FFmpeg composers; naming stage `call_m4_renderer` is misleading. Do not delete either in Phase 1.

### 2.3 M6 / M7 overlap

M7 android tree **includes** M6 capture sources (superset) plus `dataset/**`. Prefer M7 when discussing capture+dataset; keep M6 as capture-only snapshot. Do not delete.

### 2.4 Script / research parallels

| Producer | Location |
|----------|----------|
| M2 Script AI | Android |
| M5 research JSON | Desktop |
| M8 CreativeDirector | Desktop |

Overlapping product roles; different platforms. No deletion.

### 2.5 Publishing / QC

| Item | Status |
|------|--------|
| M9 | real publishers |
| `CURRENT_M1_M3/publishing/` | stub |
| M8 FinalQC | interim QC |
| M10 | **MISSING** |

---

## 3. Duplicate inventory (actions deferred)

| ID | Pair | Evidence relationship | Phase 1 action |
|----|------|----------------------|----------------|
| D1 | M4 ↔ vendor/m4 | Byte-identical tracked sources | Keep both; **no canonical pick** |
| D2 | VideoEngine ↔ EffectsComposer | Complementary features | Keep both; clarify later |
| D3 | Pro ↔ Creator apps | Parallel products | No merge |
| D4 | M6 ↔ M7 | Superset | Prefer M7 for dataset work |
| D5 | Stubs ↔ real modules | Placeholders | Leave stubs empty |

---

## 4. Must not change (duplicates)

1. Do not delete `M4/` or `M8/m8/vendor/m4`.  
2. Do not declare a canonical M4 by directory name alone.  
3. Do not replace EffectsComposer with M4 (or reverse) for preference.  
4. Do not merge Android apps.  
5. Do not fill CURRENT_M1_M3 stub folders with rewrites.
