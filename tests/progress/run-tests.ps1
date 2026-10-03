param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$SdkRoot = "D:\Android\Sdk"
)
$ErrorActionPreference = "Stop"
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$output = Join-Path $workspace "build\progress-tests"
New-Item -ItemType Directory -Force $output, (Join-Path $output "classes"),
    (Join-Path $output "dex") | Out-Null
$tools = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory |
    Where-Object { $_.Name -match "^\d+\.\d+\.\d+$" } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$platform = Get-ChildItem (Join-Path $SdkRoot "platforms") -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
$androidJar = Join-Path $platform.FullName "android.jar"
$appClasses = Join-Path $workspace "build\manual\classes.jar"
$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$bootDeadline = [DateTime]::UtcNow.AddSeconds(45)
while ((& $adb -s $Serial shell getprop sys.boot_completed 2>$null) -ne "1") {
    if ([DateTime]::UtcNow -ge $bootDeadline) { throw "Emulator did not finish booting" }
    Start-Sleep -Milliseconds 500
}
& (Join-Path $tools.FullName "aapt2.exe") link -o (Join-Path $output "base.apk") `
    -I $androidJar --manifest (Join-Path $PSScriptRoot "AndroidManifest.xml") `
    --min-sdk-version 23 --target-sdk-version 36
if ($LASTEXITCODE -ne 0) { throw "Test resource linking failed" }
& javac -encoding UTF-8 -source 17 -target 17 -classpath "$androidJar;$appClasses" `
    -d (Join-Path $output "classes") (Join-Path $PSScriptRoot "ProgressInstrumentation.java")
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed" }
& jar cf (Join-Path $output "classes.jar") -C (Join-Path $output "classes") .
if ($LASTEXITCODE -ne 0) { throw "Test jar failed" }
& (Join-Path $tools.FullName "d8.bat") --min-api 23 --lib $androidJar `
    --classpath $appClasses --output (Join-Path $output "dex") (Join-Path $output "classes.jar")
if ($LASTEXITCODE -ne 0) { throw "Test dexing failed" }
Copy-Item -LiteralPath (Join-Path $output "base.apk") -Destination (Join-Path $output "unsigned.apk") -Force
& jar uf (Join-Path $output "unsigned.apk") -C (Join-Path $output "dex") classes.dex
if ($LASTEXITCODE -ne 0) { throw "Test APK assembly failed" }
& (Join-Path $tools.FullName "zipalign.exe") -f 4 `
    (Join-Path $output "unsigned.apk") (Join-Path $output "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "Test APK alignment failed" }
$keystore = Join-Path $env:USERPROFILE ".android\debug.keystore"
if (-not (Test-Path -LiteralPath $keystore)) { $keystore = Join-Path $workspace "keystore\debug.keystore" }
& (Join-Path $tools.FullName "apksigner.bat") sign --ks $keystore --ks-key-alias androiddebugkey `
    --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false `
    --out (Join-Path $output "tests.apk") (Join-Path $output "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "Test signing failed" }
& $adb -s $Serial install -r (Join-Path $workspace "dist\RelayChat-debug.apk")
if ($LASTEXITCODE -ne 0) { throw "App installation failed" }
& $adb -s $Serial install -r (Join-Path $output "tests.apk")
if ($LASTEXITCODE -ne 0) { throw "Test installation failed" }
$result = & $adb -s $Serial shell am instrument -w `
    com.relaychat.app.progress.tests/com.relaychat.app.progress.ProgressInstrumentation
$result | Tee-Object -FilePath (Join-Path $output "results.txt")
if ($LASTEXITCODE -ne 0 -or -not ($result -match "PASS:")) { throw "Progress tests failed" }
foreach ($stage in @("waiting", "thinking", "answering", "completed",
    "no-reasoning-waiting", "no-reasoning-completed")) {
    & $adb -s $Serial pull "/sdcard/Android/data/com.relaychat.app/files/progress-$stage.png" $output
    if ($LASTEXITCODE -ne 0) { throw "Screenshot collection failed" }
}
