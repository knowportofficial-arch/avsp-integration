# M8 FINAL RELEASE REPORT

## Command
```
python main.py --topic "Belda Railway Station" --duration 5m --format 9:16 --project-id belda_final_release
```

## Environment
- Python: 3.12.3
- FFmpeg: ffmpeg version 6.1.1-3ubuntu5 Copyright (c) 2000-2023 the FFmpeg developers

## Results
| Item | Value |
|------|-------|
| Duration | 299.57s |
| Resolution | 1080x1920 |
| Codecs | h264 / aac |
| Caption burn | True / drawtext |
| Punch / SFX / Emoji | 2 / 3 / 1 |
| BGM | True |
| Transitions | fades at junctions 2 & 5 |
| QC | PASS |
| Caption matrix | {'PLANNED': True, 'RENDERED': True, 'VERIFIED': True} |

## AI Creative Director
DETERMINISTIC_FALLBACK (no GEMINI_API_KEY — not claimed as AI)

## Pexels
NOT VERIFIED (local/M7 media sufficient; set PEXELS_API_KEY to enable)

## Caption method
drawtext (valid: drawtext | ass_subtitles)

## Known limitations
- Real Gemini needs GEMINI_API_KEY
- Pexels needs PEXELS_API_KEY  
- Caption verify uses luminance, not OCR
