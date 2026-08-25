# AVSP Phase 2A — M8 / M9 contracts (from actual code)

## M8 final output (AutonomousProductionController.run → pipeline_report)

Observed keys in `projects/<id>/logs/pipeline_report.json`:

| Field | Source |
|-------|--------|
| `project_id` | controller |
| `topic` | input |
| `duration_target` | seconds |
| `format` | e.g. `9:16` |
| `decision_source` | AI / DETERMINISTIC_FALLBACK |
| `media_sources` | local / pexels / placeholder |
| `stages[]` | stage log |
| `edl`, `timeline` | paths |
| `render` | EffectsComposer result dict |
| `qc` | FinalQC report |
| `final_mp4` | absolute path to `projects/<id>/render/final.mp4` |

`render/render.json` (EffectsComposer) includes: `status`, `file`, `width`, `height`, `fps`, `duration`, `video_codec`, `audio_codec`, `audio_meta`, `effects` (incl. `caption_burn_ok`, counts).

`qc/final_qc.json` includes: `status`, `file_exists`, `playable`, `duration`, `resolution`, `fps`, codecs, `audio_present`, `caption_presence`, errors/warnings.

On-disk MP4: `projects/<id>/render/final.mp4`.

## M9 input (PublishingJobCreate)

| Field | Required |
|-------|----------|
| `project_id` | yes |
| `video_path` | yes |
| `title` | yes (non-empty) |
| `description` | optional |
| `tags` | optional list |
| `hashtags` | optional list |
| `thumbnail_path` | optional |
| `category` | optional |
| `privacy` | `private` \| `unlisted` \| `public` |
| `scheduled_time` | optional ISO-8601 |
| `target_platforms` | yes (≥1 of youtube/facebook/instagram/telegram/web) |
| `platform_metadata` | optional |
| `language` | default `en` |

## Adapter mapping

| M8 | M9 |
|----|-----|
| `final_mp4` / `render/final.mp4` | `video_path` |
| `project_id` | `project_id` |
| script.topic / topic | `title` |
| script.full_text | `description` |
| topic tokens | `tags` / `hashtags` |
| `qc/frames/*.jpg` (first) | `thumbnail_path` (optional) |
| script.language | `language` |
| (default) | `privacy=private`, platforms youtube+telegram for mock E2E |
