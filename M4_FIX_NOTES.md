# AVSP M4 final QA fix

## Root causes fixed

1. Bundled QA clips were correctly entering Media Library, but M4 used each source clip only once. A 4+4+6+4 second QA set therefore produced an 18,000 ms video while M3 audio was 26,088 ms.
2. M4 now measures each source video's real duration and repeats/trim-crops that source independently until the exact scene duration is filled. The visual timeline therefore follows the M3 scene timeline instead of the raw clip lengths.
3. Bundled M4 QA fixtures are explicitly vertical (9:16) so an older persisted landscape test-project setting cannot invalidate the deterministic QA fixture. Real non-QA project assets continue to use the project's selected aspect ratio.

## Expected QA

For the current sample set, M4 should report:
- 4 source clips in Media / Assets
- 4 M4 scenes
- target 1080 x 1920 for the bundled QA fixture
- final duration equal to the M3 audio duration (example: 26,088 ms)
- no raw 18,000 ms output-duration failure

## Production behavior

Bundled QA clips are only a deterministic fallback when no real video assets exist. When real project video assets exist, they take priority and the project aspect ratio is respected.
