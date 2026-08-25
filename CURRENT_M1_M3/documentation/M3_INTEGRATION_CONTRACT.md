# M3 Integration Contract

## Consumes (frozen M2)

Uses existing `ScriptToTtsContract.fromPackage(ScriptPackage)` → `ScriptNarrationHandoff`.

Does **not** invent a competing script-to-audio contract.

## Produces

- `generated/audio/**/*.wav` + `package.json` / `voice.json`
- Module status **M3 = READY**

## M3 → M4 (`AudioToVideoContract`)

```text
AudioToVideoHandoff
 ├── projectId, scriptId, audioPackageId, language, provider, voiceId
 ├── totalDurationMs, audioPackageVersion
 └── segments[]: sceneId, paths, start/end/duration
```

M4 may consume this later. **M3 does not start M4 automatically.**

## Status baseline after M3

| Module | Status |
|--------|--------|
| M1 | FROZEN |
| M2 | FROZEN |
| M3 | READY |
| M4–M9 | FROZEN |
