# M8 Final Caption Patch — QC Report

## Root cause
Windows FFmpeg/libass rejected `subtitles=` filter with:
`Error applying option 'original_size' to filter 'subtitles': Invalid argument`
Old code logged a warning and **copied uncaptioned video**, then reported QC PASS.

## Fix
1. Primary: **drawtext** burn (cross-platform, no libass)
2. Font auto-detect: Windows Arial/Segoe/Nirmala; Linux DejaVu
3. Secondary: ASS with relative path + cwd=work_dir
4. Hard fail: CAPTION_ERROR stops pipeline
5. QC: caption_burn_ok required for PASS

## Caption method used
`drawtext` (verified on 5-minute Belda)

## Error propagation
CAPTION_ERROR → stage fails → controller re-raises M8Error → process exit ≠ 0
No silent fallback to uncaptioned MP4.

## Known limitations
- AI Creative Director still FALLBACK without Gemini keys
- Pexels requires PEXELS_API_KEY
- Caption visual verify uses luminance/size (not OCR)
