$ErrorActionPreference = "Stop"
Write-Host "=== AVSP M4 ANDROID FINAL QA ===" -ForegroundColor Cyan
Write-Host "PROJECT: $((Get-Location).Path)" -ForegroundColor Cyan
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
if (!(Test-Path $sdk)) { $sdk = "$env:USERPROFILE\AppData\Local\Android\Sdk" }
if (!(Test-Path $sdk)) { throw "ANDROID SDK NOT FOUND" }
Set-Content ".\local.properties" "sdk.dir=$($sdk.Replace('\','\\'))" -Encoding ASCII

Write-Host "=== M4 + MEDIA + FORMAT UNIT TESTS ===" -ForegroundColor Yellow
& .\gradlew.bat testDebugUnitTest --no-daemon --console=plain --tests "com.avsp.pro.video.*" --tests "com.avsp.pro.media.*"
if ($LASTEXITCODE -ne 0) { throw "M4/MEDIA UNIT TESTS FAILED - APK NOT BUILT" }

Write-Host "=== APK BUILD ===" -ForegroundColor Yellow
& .\gradlew.bat assembleDebug --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "APK BUILD FAILED" }

$apk = Get-ChildItem ".\app\build\outputs\apk" -Recurse -Filter "*.apk" -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (!$apk) { throw "APK NOT FOUND" }
$out = "C:\AVSP_M4\AVSP_M4_FINAL_MEDIA_DURATION_FIX.apk"
Copy-Item $apk.FullName $out -Force
Write-Host "========================================" -ForegroundColor Green
Write-Host "AVSP M4 FINAL OUTPUT-FORMAT + DURATION FIX: PASS" -ForegroundColor Green
Write-Host "APK: $out" -ForegroundColor Cyan
Write-Host "SIZE: $([math]::Round((Get-Item $out).Length/1MB,2)) MB" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Green
