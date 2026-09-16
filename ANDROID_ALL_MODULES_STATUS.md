# AVSP Android All-Modules Integration

## Goal
One Android Gradle application containing the accepted M1-M4 baseline plus native Android implementations/adapters for M5-M9. No Python runtime is required by the Android app.

## Integrated
- M1 Core/UI: preserved
- M2 Script AI: preserved
- M3 Audio/TTS: preserved
- M4 Video Engine: preserved and frozen after 4/4 device format tests
- M5: native Android input contract + YouTube URL validation + local media metadata path
- M6: native CameraX AI capture stack imported from the accepted M6/M7 Android source
- M7: native Room dataset/vision stack imported from M7 Android source
- M8: native timeline contract with exact 5-second CTA validation
- M9: native publishing job/state machine contract

## Isolation / safety
M6/M7 use an isolated Room database file `avsp_m67_db` so the accepted M1-M4 Room schema is not overwritten.

The M7 project is synchronized from the existing M1 project when the M6 camera route is opened.

## Device verification required
This source has not been falsely marked as device-verified. The first build on the user's Windows Android environment must run:

`gradlew.bat clean test assembleDebug --no-daemon`

Then install the APK and smoke-test M1-M4 plus the new M5/M6/M8/M9 routes. M7 vision/dataset should be exercised through capture and media review.
