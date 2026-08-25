# AVSP M8 — Autonomous Video Automation & AI Creative Director

## Status
M8 orchestration layer connecting frozen M4 (Video Engine) and M7 (Personal Dataset / Media Intelligence).

## One-command pipeline
```bash
cd /path/to/m8
PYTHONPATH=. python main.py --topic "Belda Railway Station" --duration 5m --format 9:16
```

Short test:
```bash
PYTHONPATH=. python main.py --topic "Belda Railway Station" --duration 12s --format 9:16
```

## Architecture
- `app/m8/controller.py` — P1 Autonomous Production Controller
- `app/engines/creative_director.py` — P2 AI Creative Director (deterministic + AI-ready)
- `app/adapters/m7_adapter.py` — M7 contract consumer (KEEP/REVIEW/RETAKE)
- `app/adapters/m4_adapter.py` — M4 VideoEngine.render adapter (importlib isolated)
- `app/engines/timeline_composer.py` — P11 Timeline
- `app/engines/final_qc.py` — P13 Final QC on real MP4
- `app/schemas/edl.py` / `timeline.py` — machine-readable contracts
- `vendor/m4/` — **frozen** M4 (untouched)

## Local-first
Prefers M7 KEEP assets. RETAKE never auto-selected. External media is fallback only.

## CTA
Scene 9 CTA is forced to exactly 5.0 seconds in EDL and timeline.

## Tests
```bash
PYTHONPATH=. python tests/test_m8_pipeline.py
```
All TEST A–T must pass.

## Notes
- Gemini/AI creative upgrades are optional; deterministic director is default.
- Narration TTS / Pexels download are hooks for future modules (not required for core timeline→M4).
- M7 is Android; this adapter consumes exported JSON snapshots + local media folders.
