# M8 Final Caption Patch — Changed Files

## Modified
- `app/engines/effects_composer.py`
  - `_find_caption_font()` Windows/Linux font discovery
  - `_burn_captions()` primary **drawtext** (no libass original_size)
  - ASS secondary with relative path + cwd=work_dir
  - **HARD FAIL** raises CAPTION_ERROR — never silent copy
  - `caption_burn_ok` / `caption_burn_method` in effects meta
- `app/engines/visual_verifier.py` — VERIFIED requires caption_burn_ok
- `app/engines/final_qc.py` — captions FAIL is critical
- `app/m8/controller.py` — preserve M8Error (CAPTION_ERROR) without re-wrap

## Added
- `tests/test_caption_final.py`
- `tests/test_caption_burn_hardfail.py`
- `M8_FINAL_PATCH_*.md` docs

## Untouched
- vendor/m4/** (frozen)
- M7 Android sources
