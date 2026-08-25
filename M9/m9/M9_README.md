# AVSP M9 — Publishing & Web

**Status:** COMPLETE (Mock-verified)  
**Module:** M9 Publishing & Web  
**Depends on:** Upstream final video (M4/M8 output). Does **not** modify M4–M8.

## Purpose

M9 receives an accepted final video + metadata and publishes it to:

| Platform   | Adapter              | Real API | Mock |
|------------|----------------------|----------|------|
| YouTube    | YouTubePublisher     | Yes*     | Yes  |
| Facebook   | FacebookPublisher    | Yes*     | Yes  |
| Instagram  | InstagramPublisher   | Yes*     | Yes  |
| Telegram   | TelegramPublisher    | Yes*     | Yes  |
| Web/API    | WebPublisher         | Yes*     | Yes  |

\* Real API requires external credentials (env vars). Default mode is **MOCK**.

## Quick start (Windows / Linux / macOS)

```bash
cd m9
pip install -r requirements.txt   # pytest only needed for tests

# Create a dummy video for testing
python -c "open('test.mp4','wb').write(b'\\x00\\x00\\x00\\x18ftypmp42'+b'\\x00'*200)"

# Publish (mock)
set PYTHONPATH=.
python main.py publish --project-id demo1 --video test.mp4 --title "Hello AVSP" --platforms youtube,telegram --mock

# Process queue
python main.py process-queue --mock

# Analytics
python main.py analytics

# Status
python main.py status --job-id <job_id>
```

## Architecture

See `M9_ARCHITECTURE.md`.

- **Controller** — create / validate / process / retry / cancel
- **Queue** — SQLite-backed, survives restart
- **Publishers** — common `Publisher` interface + platform adapters + mocks
- **Validators** — media + thumbnail (ffprobe when available)
- **Analytics** — local job counters (no invented platform metrics)

## Input contract (from M8 / automation)

```json
{
  "project_id": "project001",
  "video_path": "/path/to/final.mp4",
  "title": "Today's Kharagpur Update",
  "description": "...",
  "tags": ["news", "local"],
  "hashtags": ["shorts"],
  "thumbnail_path": "/path/to/thumb.jpg",
  "privacy": "private",
  "scheduled_time": null,
  "target_platforms": ["youtube", "telegram"],
  "platform_metadata": {},
  "language": "bn"
}
```

## Output status

```json
{
  "job_id": "...",
  "status": "PUBLISHED",
  "platform_statuses": {
    "youtube": {"status": "PUBLISHED", "platform_id": "mock_youtube_...", "extra": {"is_mock": true}},
    "telegram": {"status": "PUBLISHED", "platform_id": "mock_telegram_...", "extra": {"is_mock": true}}
  }
}
```

## Security

- No secrets in source code.
- Credentials via environment variables only (see `.env.example`).
- Default `USE_MOCK=true` for every platform.

## Tests

```bash
cd m9
PYTHONPATH=. pytest tests/test_m9_all.py -v
```

All TEST A–Z are mock-based and must pass without real credentials.

## Known limitations

1. Real YouTube upload requires `google-api-python-client` + valid OAuth token.
2. Real Instagram requires a publicly reachable `video_url` in `platform_metadata` (or resumable upload session — not fully automated for pure local files).
3. Real Facebook/Telegram/Web require `requests` and valid tokens/endpoints.
4. ffprobe is optional; without it, stream-level validation is skipped (file existence/size/extension still enforced).
5. M1–M3 UI integration is out of scope; M9 exposes CLI + Python API.

## Integration with M8

```
M8 pipeline → final.mp4 + metadata
           ↓
M9 PublishingJobCreate
           ↓
queue → process → platform adapters → status / analytics
```

M9 does **not** call M8 internals. It only consumes the accepted final video path and metadata.
