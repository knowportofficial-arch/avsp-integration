# AVSP M8 — Autonomous Video Automation & AI Creative Director

Final release package. M4 and M7 V3 are frozen and vendored/adapted only.

## Requirements
- Python 3.10+
- FFmpeg + ffprobe on PATH
- Optional: `GEMINI_API_KEY`, `PEXELS_API_KEY`

```bat
pip install -r requirements.txt
```

## Windows quick start
```bat
cd m8
set PYTHONPATH=.
python -m unittest tests.test_caption_burn_hardfail -v
python main.py --topic "Belda Railway Station" --duration 5m --format 9:16
```

Output lands in `projects/<project_id>/render/final.mp4`.

## Caption rendering
Primary: `drawtext` (cross-platform).  
Fallback: `ass_subtitles` (relative path, cwd=work_dir).  
If **both** fail → `CAPTION_ERROR` and pipeline **FAIL** (no silent uncaptioned copy).

## AI Creative Director
- With valid `GEMINI_API_KEY`: may set `decision_source=AI`
- Without: `decision_source=DETERMINISTIC_FALLBACK` (honest, never claimed as AI)

## Layout
```
m8/
  main.py
  requirements.txt
  app/           source (controllers, engines, adapters, schemas)
  tests/
  assets/        local media, punch, sfx, music, m7 snapshot
  config/
  docs/
  examples/
  vendor/m4/     frozen M4 Video Engine
```

## One-command pipeline
```bat
python main.py --topic "YOUR TOPIC" --duration 5m --format 9:16
```
