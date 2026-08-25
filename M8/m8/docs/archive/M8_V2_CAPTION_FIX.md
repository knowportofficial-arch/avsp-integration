# Caption Burn Hard-Fail Fix

## Problem (Windows QA)
FFmpeg error:
```
Error applying option 'original_size' to filter 'subtitles': Invalid argument
```
Pipeline previously logged a warning, copied uncaptioned video, and reported QC PASS.

## Fix
1. **Primary burn method**: `drawtext` filter (cross-platform; no libass `original_size`).
2. **Secondary**: ASS `subtitles=` with `cwd=work_dir` relative path (avoids Windows `C:` colon escape issues).
3. **Hard fail**: if both methods fail → raise `CAPTION_ERROR` / stage `caption_burn`. No silent copy.
4. **Effects meta**: `caption_burn_ok`, `caption_burn_method`.
5. **QC**: `checks.captions = FAIL` when planned captions and `caption_burn_ok is False` → overall QC FAIL.

## Verified
```
Captions burned via drawtext (9 events)
caption_burn True drawtext
QC PASS captions check PASS
matrix caption VERIFIED
```
