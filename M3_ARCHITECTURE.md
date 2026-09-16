# M3 Architecture — Audio / TTS

## Responsibility

M3 converts an approved M2 script (`ScriptToTtsContract` / `ScriptNarrationHandoff`) into a scene-aligned `AudioPackage` with playable segment files and a contiguous timeline.

M3 does **not** implement video rendering (M4) and does not auto-invoke M4–M9.

## Package

`com.avsp.pro.audio`

| Layer | Contents |
|-------|----------|
| `contract/` | `AudioPackage`, `AudioSegment`, `VoiceSettings`, format metadata |
| `language/` | `AudioLanguageRegistry` (en/bn/hi) |
| `engine/` | `TtsEngine`, `MockTtsEngine`, `AndroidTtsEngine`, `DefaultTtsEngineRegistry` |
| `wav/` | Deterministic PCM WAV encoder (16 kHz mono s16le) |
| `validation/` | Handoff + package validation |
| `repository/` | Generate/save/load/regenerate via M1 storage |
| `integration/` | `AudioToVideoContract` (M3→M4) |
| `error/` | Structured `AudioErrorCode` |
| `ui/` | Audio/TTS screen + preview |

## Provider model

```
TtsEngine
 ├── MockTtsEngine (deterministic tone WAV; provider=mock)
 ├── AndroidTtsEngine (platform TTS when available/language installed)
 └── future: Piper / Coqui / Edge / cloud adapters
```

No paid API is required. Credentials show only CONFIGURED / NOT CONFIGURED.

## Storage

Uses M1 `AvspStorage` under `generated/audio/{audioPackageId}/`:

- `{segmentId}.wav`
- `package.json`
- canonical `voice.json` pointer

## UI flow

Project → Script AI → **Audio / TTS** → provider → generate → preview segments → regenerate → save.

## Audio format

- Container: WAV
- Encoding: PCM signed 16-bit little-endian
- Sample rate: 16000 Hz
- Channels: 1 (mono)
