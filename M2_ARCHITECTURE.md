# M2 Architecture — Script AI

## Responsibility

M2 turns a topic + language + duration (+ optional instructions) into a structured, editable, persistable `ScriptPackage`. It does **not** implement TTS (M3) or video rendering.

## Package

`com.avsp.pro.script`

| Layer | Contents |
|-------|----------|
| `contract/` | `ScriptPackage`, `ScriptScene`, `ScriptGenerationRequest`, duration presets |
| `language/` | `ScriptLanguagePack` + EN/BN/HI packs via `ScriptLanguageRegistry` |
| `generator/` | `ScriptGenerator` interface, `MockScriptGenerator`, `DefaultScriptGeneratorRegistry` |
| `validation/` | `ScriptValidator` (request + package, duration tolerance) |
| `repository/` | `ScriptRepository` — generate/save/load/edit via M1 `AvspStorage` |
| `integration/` | `ScriptToTtsContract` — M2→M3 narration handoff |
| `ui/` | `ScriptAiViewModel`, `ScriptAiScreen` |

## Generator abstraction

```
ScriptGenerator
 ├── MockScriptGenerator (shipped, deterministic)
 └── future: Local / OpenAI / Gemini / Grok adapters
```

When AI credentials are `NOT_CONFIGURED`, the app still operates via Mock. No paid API is mandatory.

## Persistence

Uses M1 `AvspStorage` under `generated/script/`:

- `{scriptId}.json`
- canonical `script.json` for integration contract

No second database system.

## UI flow

Projects → Project Detail → **Script AI** → generate → review/edit → save.

## Minimal M1 touch points (compatibility)

- `AvspModules` M2 → READY / 1.0.0
- `DatabaseProvider` / `ModuleStatusRepositoryImpl` seeding for M2 READY
- Navigation route `script/{projectId}`
- `AppContainer` wires `ScriptRepository`
- Project Detail CTA + Modules copy

M1 behavior (projects, settings, storage, secure config) unchanged.
