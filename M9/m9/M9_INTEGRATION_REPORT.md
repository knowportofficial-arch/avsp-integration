# M9 Integration Report

## Upstream modules

| Module | Status (per prompt) | M9 interaction |
|--------|---------------------|----------------|
| M1 Core & UI | PENDING | Not implemented; M9 CLI/Python API ready for future shell |
| M2 Script | PENDING | Not required |
| M3 Audio/TTS | PENDING | Not required |
| M4 Video Engine | FROZEN | Not modified. Consumes final MP4 path only |
| M5 YouTube/Screen Input | FROZEN | Not modified |
| M6 Camera | FROZEN | Not modified |
| M7 Dataset | FROZEN | Not modified |
| M8 Automation | FROZEN / ACCEPTED | Consumes M8 `final.mp4` + metadata. No M8 source changes |

## Adapter layer

M9 does not call into M8 code. Integration is file/metadata based:

```
M8 project/render/final.mp4
M8 project metadata (title, description, ...)
        ↓
PublishingJobCreate(...)
        ↓
M9 queue + publishers
```

If a future M8 workflow wants to auto-publish, it should:

1. After successful render, construct `PublishingJobCreate`
2. Call `PublishingController.create_job` + `process_job` (or enqueue and let a worker call `process_queue`)

## Frozen module protection

- No files under M4/M5/M6/M7/M8 trees were modified.
- M9 is a new top-level package.

## Future M1 UI hooks

Suggested endpoints/actions for M1:

- Create publish job
- List queue
- Job status detail
- Retry / Cancel
- Analytics summary

These map 1:1 to `PublishingController` methods.
