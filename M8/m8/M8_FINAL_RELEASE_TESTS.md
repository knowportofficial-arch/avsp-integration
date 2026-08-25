# M8 FINAL RELEASE — Test Report

## Caption tests (`tests.test_caption_burn_hardfail`)
| Test | Result |
|------|--------|
| test_caption_burn_succeeds (drawtext OR ass_subtitles) | PASS |
| test_no_silent_skip_when_captions_planned | PASS |

## Caption final (`tests.test_caption_final`)
6/6 PASS (spaces path, Bengali unicode, frame verify, pipeline)

## QA patch (`tests.test_m8_v2_qa_patch`)
| Test | Result |
|------|--------|
| AI decision_source honesty | PASS |
| Caption visual in frames | PASS |
| Punch/SFX/emoji/BGM verified | PASS |
| Transition fade must render | PASS |
| Transition failure must raise | PASS |

## Effects (`tests.test_m8_v2_effects`) — partial run observed
KEEP selection, RETAKE rejection, Pexels client, AI source, punch/transition/SFX renders — OK

## 5-minute Belda acceptance
- Duration: 299.57s
- 1080x1920 H.264/AAC
- caption_burn_ok=true method=drawtext
- fades at junctions 2 & 5
- QC PASS
