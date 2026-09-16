# AVSP M4 Final Fix — Output Format + Source Adaptation

## Root causes found

1. `VideoRepositoryImpl` contained a QA-only override that forced bundled M4 clips to 9:16. This was incorrect because output format is a **project property**, not a property of the source clips.
2. Media3 `Transformer` was allowed to use encoder fallback. That can silently change the requested output resolution. M4 then correctly rejected the resulting file during post-render validation, producing errors such as `1920x1080 != 1080x1920`.
3. Source clip duration must never determine scene duration. `VideoClipPlanner` already provides exact repeat/trim math; the render path keeps using that planner for every video scene.

## Final architecture

`Project.aspectRatio -> VideoRenderPlan.width/height -> Presentation.createForWidthAndHeight -> exact encoder output`

Source clips are scaled/cropped to the selected project canvas. They do not dictate the output canvas.

## Supported project formats

- 9:16 -> 1080x1920
- 16:9 -> 1920x1080
- 1:1 -> 1080x1080
- 4:5 -> 1080x1350

The Project Detail screen now allows changing the project output format. Changing it clears the old rendered output metadata and returns the project to DRAFT so an old video cannot be mistaken for the new format.

## Encoder behavior

`DefaultEncoderFactory.setEnableFallback(false)` is used so M4 never silently substitutes another resolution. If the device cannot encode the requested project resolution, M4 reports a real error instead of producing the wrong canvas.

## Duration behavior

For a source clip shorter than a scene, M4 repeats the clip and trims the final repetition to the exact scene duration. For a source longer than the scene, M4 clips it. The sum of video pieces therefore equals the M3 scene timeline.

## QA fixture behavior

Bundled QA clips remain normal media assets. They are selected only when no real project media is present. Real M6/camera assets retain priority.
