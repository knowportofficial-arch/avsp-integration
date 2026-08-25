# M2 Integration Contract

## Consumes (from M1)

- `Project` / `ProjectRepository`
- `AvspStorage` + `ProjectPaths.SCRIPT`
- `SecureConfigStore` / `SecureConfigKeys.AI_API` (CONFIGURED / NOT_CONFIGURED only)
- `ModuleStatusRepository` registration
- Navigation shell

## Produces

- `generated/script/script.json` (`ArtifactNames.SCRIPT_JSON`)
- `ScriptReference`-compatible artifacts on disk
- Module status: **M2 = READY**

## M2 → M3 handoff (`ScriptToTtsContract`)

```text
ScriptNarrationHandoff
 ├── projectId, scriptId, language, title, scriptVersion
 ├── totalNarrationDurationMs
 └── segments[]: sceneId, order, narration, language, durationMs, pauseAfterMs
```

M3 must later consume narration + timing. **M2 does not implement TTS.**

## Does not touch

- M3 Audio/TTS implementation
- M4–M9 frozen modules
- Video rendering / publishing
