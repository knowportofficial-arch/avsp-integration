# AVSP M10 — Release Acceptance

## Baseline
Repository: knowportofficial-arch/avsp-integration
Branch: backup-v6-3
Baseline commit: a4ae9c2aff1359843a79aad525a1b51bb68cb317

## Android Identity
Application ID: com.avsp.pro
versionCode: 1
versionName: 1.0.0-m4-android

## Build Evidence
Unit tests: PASS
Clean assembleDebug: PASS

APK:
C:\AVSP_ALL_ANDROID_M1_M9_INTEGRATED\avsp_android_master\app\build\outputs\apk\debug\app-debug.apk

APK size:
74405372 bytes

APK build timestamp:
09/16/2026 16:45:02

APK SHA-256:
F1868AC070BDDBDC7B4985FC8F236EE0C2A84A916B392956F751A73529624A92

## Acceptance Gates

| Gate | Status |
|---|---|
| Git baseline | PASS |
| Working tree clean before M10 | PASS |
| Debug unit tests | PASS |
| Clean debug APK build | PASS |
| APK SHA-256 recorded | PASS |
| APK size/timestamp recorded | PASS |
| Device release-candidate test | PENDING |
| M1-M4 regression | PASS |
| M5-M9 regression | PASS |
| E2E-01 to E2E-08 | PASS |
| M9 real platform publish | PENDING |
| Recovery rehearsal | PENDING |

## M10 Scope
Release governance, artifact verification, acceptance tracking and recovery/rebuild documentation.

M10 does not rewrite accepted M1-M9 functionality unless a reproducible release blocker is discovered.
