# M3 Data Contract

## AudioPackage

| Field | Description |
|-------|-------------|
| version | `1.0` |
| projectId / scriptId / audioPackageId | Identifiers |
| language / provider / voice | Generation settings |
| segments[] | Scene-aligned audio clips |
| totalDurationMs | Sum of segment durations |
| validation | VALID / WARNING / INVALID |
| metadata | format, timestamps, timing drift warnings |

## AudioSegment

`segmentId`, `sceneId`, `order`, `sourceText`, `relativeAudioPath`, `durationMs`, `startMs`, `endMs`, `provider`, `language`, `status`, optional `plannedDurationMs` / `durationDeltaMs`

## VoiceSettings

`language`, `voiceId`, `speechRate`, `pitch`, `volume`, `providerId`

## Format

WAV / pcm_s16le / 16 kHz / mono
