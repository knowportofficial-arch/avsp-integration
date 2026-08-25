# M8 Final Caption Patch — Test Report

## Caption tests (test_caption_final.py)
| Test | Result |
|------|--------|
| 1 ASS/drawtext render exists | PASS |
| 2 Path with spaces | PASS |
| 3 Unicode Bengali | PASS |
| 4 Hard-fail present / silent removed | PASS |
| 5 Frame verification | PASS |
| 6 Pipeline with captions | PASS |

## QA patch tests (test_m8_v2_qa_patch.py)
5/5 PASS (transitions, punch, SFX, emoji, BGM, captions)

## 5-minute Belda
- duration: 299.57s
- caption_burn_ok: true
- caption_burn_method: drawtext
- QC: PASS
- matrix caption: PLANNED/RENDERED/VERIFIED all true
- fades at junctions 2 and 5
