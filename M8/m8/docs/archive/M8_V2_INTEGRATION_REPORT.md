# M8 V2 Integration Report

## M4 (frozen)
Effects that M4 cannot burn (punch overlays, multi-clip xfade, SFX mix, emoji) are composed by `EffectsComposer` (FFmpeg) into the final MP4. M4 adapter remains available for simple assemblies.

## M7 (frozen)
M7Adapter continues to enforce KEEP > REVIEW, never RETAKE.

## Architecture
Timeline/EDL → EffectsComposer → final.mp4 → FinalQC
