$ErrorActionPreference="Stop"
Write-Host "=== AVSP M4 BUNDLED CLIP BUILD ===" -ForegroundColor Cyan
$root=(Get-Location).Path
$sdk="$env:LOCALAPPDATA\Android\Sdk"
if(!(Test-Path $sdk)){$sdk="$env:USERPROFILE\AppData\Local\Android\Sdk"}
if(!(Test-Path $sdk)){throw "ANDROID SDK NOT FOUND"}
Set-Content ".\local.properties" "sdk.dir=$($sdk.Replace('\','\\'))" -Encoding ASCII
Write-Host "=== FULL UNIT REGRESSION ===" -ForegroundColor Yellow
& .\gradlew.bat testDebugUnitTest --no-daemon --console=plain
if($LASTEXITCODE -ne 0){throw "FULL TEST SUITE FAILED - APK NOT BUILT"}
Write-Host "=== APK BUILD ===" -ForegroundColor Yellow
& .\gradlew.bat assembleDebug --no-daemon --console=plain
if($LASTEXITCODE -ne 0){throw "APK BUILD FAILED"}
$apk=Get-ChildItem ".\app\build\outputs\apk" -Recurse -Filter "*.apk" -File|Sort-Object LastWriteTime -Descending|Select-Object -First 1
if(!$apk){throw "APK NOT FOUND"}
$out="C:\AVSP_M4\AVSP_M4_ANDROID_BUNDLED_CLIPS.apk"
Copy-Item $apk.FullName $out -Force
Write-Host "========================================" -ForegroundColor Green
Write-Host "AVSP M4 BUNDLED CLIPS BUILD: PASS" -ForegroundColor Green
Write-Host "APK: $out" -ForegroundColor Cyan
Write-Host "SIZE: $([math]::Round((Get-Item $out).Length/1MB,2)) MB" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green
