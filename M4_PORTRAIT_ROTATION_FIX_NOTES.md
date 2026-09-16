# M4 Portrait Orientation Validation Fix

## Verified root cause

Media3 Transformer renders the requested portrait canvas successfully, but Android media metadata can expose the encoded video dimensions in codec orientation (for example `1920x1080`) while the presentation rotation is `90°`. The previous M4 validator compared raw encoded dimensions directly with the project canvas (`1080x1920`) and incorrectly failed the render.

## Fix

1. Read `METADATA_KEY_VIDEO_ROTATION` from the rendered MP4.
2. Normalize rotation to `0..359`.
3. Convert encoded dimensions to display dimensions for `90°`/`270°` rotation.
4. Validate the display dimensions against the project canvas.
5. Return display dimensions in `VideoRenderResult`, so the UI reports the real project orientation.
6. Keep `0°` validation strict; an unrotated `1920x1080` file is still rejected for a `1080x1920` project.

## Regression coverage

- Rotated `1920x1080`, 90° -> accepted as `1080x1920`.
- Rotated `1350x1080`, 270° -> accepted as `1080x1350`.
- Unrotated `1920x1080` for a `1080x1920` project -> rejected.
- Existing landscape/square and duration validation remain unchanged.

## Build status

Source patch completed. Full Gradle build/test could not be executed in this Linux environment because the bundled Gradle 8.9 distribution is not cached and outbound access to `services.gradle.org` is unavailable. The supplied Windows PowerShell build script remains the authoritative APK build/QA path.
