# AVSP — AI Video Studio Pro

## M4 — Video Engine & Assembler (COMPLETE)

Reusable FFmpeg-based video generation engine that produces real final MP4 files.

### Status

**Module frozen after acceptance.** All tests A–H passed.

| Item | Value |
|------|--------|
| Output | H.264 + AAC MP4 |
| Shorts | 1080×1920 @ 30 fps (9:16) |
| Landscape | 1920×1080 @ 30 fps (16:9) |
| Engine | `app/engines/video_engine.py` |
| Templates | `templates/default_shorts.json`, `templates/default_landscape.json` |

### Capabilities

1. FFmpeg execution (safe argv, no shell injection)
2. Video / image / audio input
3. Clip concatenation & trimming
4. Scaling / cropping / aspect-ratio conversion (cover & contain)
5. Audio/video synchronization & duration mismatch padding
6. Subtitle burning (ASS + SRT fallback)
7. Text overlay cards (intro / outro)
8. Music mix with configurable volume (~8%) and ducking
9. H.264 + AAC encoding, `+faststart`
10. Template system (resolution, intro, outro, subtitles, music, logo)
11. Render progress callback (for M1)
12. Structured errors + `render.json`

### Input contract

```python
from app.engines.video_engine import VideoEngine

engine = VideoEngine(root=".")  # project root
result = engine.render(
    script=None,                    # optional M2 script JSON dict
    audio_path="narration.wav",     # M3 audio
    media=[                         # visual items
        {"path": "clip.mp4", "duration": 3.0, "start": 0.0, "end": 3.0},
        {"path": "photo.jpg", "duration": 2.5},
        {"color": "black", "duration": 1.0},
    ],
    template="default_shorts",      # or path / dict
    project_id="my_project",
    output_path="output/final.mp4",
    subtitles=[
        {"start": 0.0, "end": 2.0, "text": "Hello Belda"},
    ],
    music_path=None,
    intro={"text": "AVSP", "duration": 1.5},
    outro={"text": "Thanks for watching", "duration": 1.5},
    force_duration=None,
)
# result.status == "success" | "error"
# result.file → path to MP4
# result.to_dict() / render.json written automatically
```

### Output: `render.json`

```json
{
  "project_id": "test_a",
  "file": "/path/to/final.mp4",
  "width": 1080,
  "height": 1920,
  "fps": 30.0,
  "duration": 3.5,
  "video_codec": "h264",
  "audio_codec": "aac",
  "status": "success",
  "error": null,
  "progress": 1.0,
  "render_time_sec": 4.2
}
```

### Build / run

```bash
# From project root
python3 -m app.engines.video_engine \
  --audio path/to/narration.wav \
  --media path/to/img1.png path/to/clip.mp4 \
  --template default_shorts \
  --output output/demo.mp4 \
  --project-id demo

# Full acceptance suite (generates synthetic assets)
python3 tests/test_video_engine.py
```

### Test results (2026-08-23)

```
TEST A  black + narration          PASS  1080x1920 H.264/AAC
TEST B  images + narration        PASS
TEST C  video clips + narration     PASS
TEST D  intro + narration + outro PASS  (~6s with 1.5s intro/outro)
TEST E  subtitles                   PASS
TEST F  1080x1920 Shorts            PASS
TEST G  invalid input               PASS  controlled INPUT_NOT_FOUND
TEST H  A/V duration mismatch       PASS  video padded to audio
BONUS   16:9 landscape              PASS  1920x1080
```

All generated MP4s open, play, contain audio+video, correct dimensions, valid codecs, no corruption.

### Integration notes

- Preserves existing `app/engines/autonomous_video_engine.py` (planner only).
- Does **not** implement camera, YouTube, OCR, publishing, or social APIs.
- Local-first; no external network calls inside M4.
- Progress: pass `progress_callback=fn(progress: float, message: str)` to `VideoEngine(...)`.
- For the Belda Railway Station 5-minute QC target, M4 is the compositor stage that receives the timeline from upstream modules and writes the final MP4.

### Production principles (from QC roadmap)

- Punch clips / SFX / emoji / transitions are timeline overlays (wired by upstream EDL → media list).
- Captions remain enabled.
- BGM target ~8% with voice priority (supported via template `music.volume` + ducking).
- Final QC must inspect the rendered MP4 (this module validates streams, dimensions, duration).

---

*AVSP M4 Video Engine — accepted and frozen.*
