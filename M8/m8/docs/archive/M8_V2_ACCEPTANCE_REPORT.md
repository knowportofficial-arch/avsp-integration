# M8 V2 Acceptance Report

## Command executed
```
PYTHONPATH=. python main.py --topic "Belda Railway Station" --duration 5m --format 9:16 --project-id belda_5m_v2
```

## Result
- **PASS**
- Final MP4: `projects/belda_5m_v2/render/final.mp4`
- Duration: **300.0 s**
- Resolution: **1080×1920**
- Video: **h264**
- Audio: **aac**
- QC: **PASS**
- decision_source: **DETERMINISTIC_FALLBACK** (no GEMINI_API_KEY in environment)
- media_sources: local (M7 KEEP available)
- Effects in render.json:
  - punch_count: 2
  - emoji_count: 1
  - transition_count: 7
  - sfx_count: 3
  - caption_count: 9
  - bgm: true, ducking_applied: true

## AI
Not invoked (no API key). Explicitly reported as DETERMINISTIC_FALLBACK.

## Pexels
Not used (local KEEP media present). Client implemented; requires PEXELS_API_KEY.
