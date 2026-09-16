# AVSP M10 — Recovery Procedure

## Repository
knowportofficial-arch/avsp-integration

## Baseline
backup-v6-3

## Windows Project
C:\AVSP_ALL_ANDROID_M1_M9_INTEGRATED\avsp_android_master

## Android SDK
C:\Users\DESKTOP\AppData\Local\Android\Sdk

## Verification
git status
git branch --show-current
git log -1 --oneline
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat clean assembleDebug --no-daemon

## APK Hash
Get-FileHash "app\build\outputs\apk\debug\app-debug.apk" -Algorithm SHA256

## Recovery Rule
Use the verified Git checkpoint first.
Do not substitute an arbitrary old ZIP when the verified Git checkpoint is available.
