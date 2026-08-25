# M1 Integration Contract

Master communication principle (interfaces only in M1):

## Camera (future M6)

Outputs: `clip.mp4`, `thumbnail.jpg`, `metadata.json`

## Script (future M2)

Output: `script.json`

## TTS (future M3)

Outputs: `voice.mp3`, `voice.json`

## Video assembler (future M4)

Accepts: video assets, audio assets, script/scene info, template → `final.mp4`

## Publishing (future M9)

Accepts: `final.mp4`, metadata, publishing configuration

## Project relative layout

```
projects/<projectId>/
  originals/camera|media
  working/
  generated/script|audio|video
  published/
  logs/
```

## Extension points (interfaces only)

- `TtsProvider`
- `PublishingPlatformAdapter`
- `OcrEngine`
- `AiModelProvider`
- `AutomationTrigger`

Do not implement providers in M1.
