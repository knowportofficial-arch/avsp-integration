# M8 Integration Contract

## CLI
`python main.py --topic TEXT --duration 5m|30s --format 9:16|16:9 [--skip-render] [--project-id ID]`

## Creative EDL
See `app/schemas/edl.py` and `docs/M8_EDL_SCHEMA.md`.

## Timeline
See `app/schemas/timeline.py`. CTA duration must be 5.0 ± 0.15s.

## M4 handoff
Timeline.visual → media list; Timeline.captions → subtitles; Timeline.music → music_path.

## M7 handoff
Snapshot JSON items with recommendation/quality_score/path. RETAKE excluded from selection.
