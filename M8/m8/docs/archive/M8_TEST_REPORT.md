# M8 Test Report

## TEST A–T

| Test | Result |
|------|--------|
| TEST A — project created | PASS |
| TEST B — scene plan generated | PASS |
| TEST C — local media before external | PASS |
| TEST D — external fallback stub | PASS |
| TEST E — RETAKE rejected | PASS |
| TEST F — KEEP preferred | PASS |
| TEST G — Creative EDL | PASS |
| TEST H — timeline generated | PASS |
| TEST I — punch no duration extend | PASS |
| TEST J — transition timing | PASS |
| TEST K — SFX timing | PASS |
| TEST L — BGM ducking | PASS |
| TEST M — captions | PASS |
| TEST N — CTA exactly 5s | PASS |
| TEST O — 9:16 timeline | PASS |
| TEST P — 16:9 timeline | PASS |
| TEST Q — M4 adapter | PASS |
| TEST R — final MP4 exists | PASS |
| TEST S — QC detects missing | PASS |
| TEST T — one-command pipeline | PASS |

Unit/Integration: 20/20 passed (python tests/test_m8_pipeline.py)

Acceptance (Belda short 12s): PASS — MP4 1080x1920 H.264/AAC, QC PASS

Full 5-minute Belda render: NOT RUN in this environment (time/resource); pipeline verified with short duration equivalent.
