# M4 bundled source-clip integration

The four QA MP4 clips are packaged in `app/src/main/assets/m4_test_clips/`.
When a project is opened, Media Library is refreshed, or M4 prepares a render, `BundledMediaSeeder` idempotently copies them into that project's `originals/media/` directory and registers them in the Room `media_assets` inventory.

M4 therefore consumes the same media-asset contract intended for M6 camera imports; this is not a render-only bypass.
