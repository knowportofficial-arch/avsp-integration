# AVSP Phase 2C — M3 / M8 contracts (from actual code)

Frozen for the `bridges/m3_m8` adapter. Engines are not rewritten.

## M3 output (AudioRepositoryImpl)

| Artifact | Path |
|----------|------|
| Segment WAV | `generated/audio/{audioPackageId}/aseg_NN.wav` |
| Package JSON | `generated/audio/{audioPackageId}/package.json` |
| Latest package | `generated/audio/voice.json` (`AudioPackage`) |

Encoding: WAV / pcm_s16le / 16000 Hz / mono / 16-bit.

Timing: contiguous `startMs`/`endMs`; `totalDurationMs` = sum of segment durations.
No `voice.mp3`. No single mixdown file.

## M8 narration input (EffectsComposer)

| Input | Notes |
|-------|-------|
| `narration_audio: Optional[Path]` | One complete narration file |
| Live controller | Hardcodes `narration_audio=None` (simulated VO) |
| Duration authority | Topic duration string → timeline; audio trimmed with `-t timeline.duration` |

## Adapter mapping

| M3 | Bridge | M8 |
|----|--------|-----|
| `voice.json` + `aseg_*.wav` | discover + validate | — |
| ordered WAVs | lossless PCM concat | single `narration.wav` |
| `totalDurationMs` | `/1000` → `Xs` duration | `AutonomousProductionController.run(duration=...)` |
| — | inject via temporary EffectsComposer patch | real VO mux (no engine edit) |
| `projectId` / `audioPackageId` | recorded in bridge report | M8 `project_id` (separate; linked in report) |

## Out of scope

- Rewriting M3 / M8 / M4 / vendor M4 / M9
- WAV→MP3 conversion
- Real YouTube publishing
- Full per-scene EDL remap from `startMs`/`endMs` (duration-aligned MVP only)
