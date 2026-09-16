# AVSP M10 — Release, Recovery & Production Readiness

## Baseline
Project: AI Video Studio Pro (AVSP)
Platform: Android
Baseline branch: backup-v6-3
Baseline commit: a4ae9c2
Application ID: com.avsp.pro
versionCode: 1
versionName: 1.0.0-m4-android

## Purpose
M10 provides release governance, artifact verification, acceptance tracking,
and deterministic recovery/rebuild documentation around the verified M1-M9 application.

## Protected Scope
M1-M9 application functionality must not be rewritten as part of M10
unless a reproducible release blocker is found.

## Release Gates
1. Git baseline verified
2. Working tree clean
3. Debug unit tests pass
4. Clean APK build passes
5. APK SHA-256 recorded
6. APK installed and smoke-tested
7. E2E regression recorded
8. Publishing state recorded
9. Recovery procedure verified
10. Final release checkpoint committed

## Current Artifact
Expected APK:
app\build\outputs\apk\debug\app-debug.apk

SHA-256:
TO BE RECORDED AFTER FINAL BUILD

Build timestamp:
TO BE RECORDED AFTER FINAL BUILD
