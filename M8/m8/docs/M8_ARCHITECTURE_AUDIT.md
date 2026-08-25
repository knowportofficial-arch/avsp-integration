# M8 Architecture Audit

## M4 interface discovered
- Class: `VideoEngine` in `app/engines/video_engine.py`
- Method: `render(script, audio_path, media, template, project_id, output_path, subtitles, music_path, intro, outro, force_duration) -> RenderResult`
- Templates: `default_shorts` (1080x1920), `default_landscape` (1920x1080)
- Media items: `{path, duration, start?, end?}` or `{color, duration}`
- Output: H.264 + AAC MP4 + render.json
- **Frozen — not modified**

## M7 interface discovered
- Android Kotlin: `MediaSelectionApi.findBestMedia`, `DatasetAutomationContract.snapshot/findKeepable`
- Recommendation: KEEP | REVIEW | RETAKE
- QualityResult JSON with score, recommendation, duplicate flags
- **Frozen — not modified**
- M8 consumes via file/JSON snapshot adapter

## M8 integration points
1. M7Adapter ← snapshot JSON / local_media folder
2. CreativeDirector → CreativeEDL JSON
3. TimelineComposer → Timeline JSON
4. M4Adapter → VideoEngine.render()
5. FinalQC → ffprobe on final MP4

## Compatibility adapters
- `app/adapters/m4_adapter.py` (importlib isolation to avoid `app` package clash)
- `app/adapters/m7_adapter.py` (JSON + filesystem mirror of M7 rules)

## Untouched files
- Entire `vendor/m4/**`
- All M7 Android sources (not vendored into M8)

## M8 added
- `main.py`, `app/m8/**`, `app/engines/**`, `app/adapters/**`, `app/schemas/**`, `tests/**`, `docs/**`, `assets/**`
