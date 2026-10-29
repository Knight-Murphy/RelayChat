param(
    [string]$Serial = "emulator-5554",
    [string]$SdkRoot = "D:\Android\Sdk"
)
$ErrorActionPreference = "Stop"
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$output = Join-Path $workspace "build\icon-tests"
New-Item -ItemType Directory -Force $output, (Join-Path $output "classes"),
    (Join-Path $output "dex") | Out-Null
$tools = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory |
    Where-Object { $_.Name -match "^\d+\.\d+\.\d+$" } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$androidJar = Join-Path $SdkRoot "platforms\android-36\android.jar"
$appClassesDir = Join-Path $workspace "app\build\intermediates\javac\debug\compileDebugJavaWithJavac\classes"
$appClasses = Join-Path $output "app-classes.jar"
$appResources = Join-Path $workspace "app\build\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug\processDebugResources\R.jar"
& jar cf $appClasses -C $appClassesDir .
if ($LASTEXITCODE -ne 0) { throw "Application class packaging failed" }
$compileClasspath = "$androidJar;$appClasses;$appResources"
$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
& (Join-Path $tools.FullName "aapt2.exe") link -o (Join-Path $output "base.apk") `
    -I $androidJar --manifest (Join-Path $PSScriptRoot "AndroidManifest.xml") `
    --min-sdk-version 24 --target-sdk-version 36
if ($LASTEXITCODE -ne 0) { throw "Test resource linking failed" }
& javac -encoding UTF-8 -source 17 -target 17 -classpath $compileClasspath `
    -d (Join-Path $output "classes") (Join-Path $PSScriptRoot "IconInstrumentation.java")
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed" }
& jar cf (Join-Path $output "classes.jar") -C (Join-Path $output "classes") .
if ($LASTEXITCODE -ne 0) { throw "Test jar failed" }
& (Join-Path $tools.FullName "d8.bat") --min-api 24 --lib $androidJar `
    --classpath $compileClasspath --output (Join-Path $output "dex") (Join-Path $output "classes.jar")
if ($LASTEXITCODE -ne 0) { throw "Test dexing failed" }
Copy-Item -LiteralPath (Join-Path $output "base.apk") -Destination (Join-Path $output "unsigned.apk") -Force
& jar uf (Join-Path $output "unsigned.apk") -C (Join-Path $output "dex") classes.dex
if ($LASTEXITCODE -ne 0) { throw "Test APK assembly failed" }
& (Join-Path $tools.FullName "zipalign.exe") -f 4 `
    (Join-Path $output "unsigned.apk") (Join-Path $output "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "Test APK alignment failed" }
$keystore = Join-Path $env:USERPROFILE ".android\debug.keystore"
& (Join-Path $tools.FullName "apksigner.bat") sign --ks $keystore --ks-key-alias androiddebugkey `
    --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false `
    --out (Join-Path $output "tests.apk") (Join-Path $output "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "Test signing failed" }
& $adb -s $Serial install -r (Join-Path $workspace "dist\RelayChat-test.apk")
if ($LASTEXITCODE -ne 0) { throw "App installation failed" }
& $adb -s $Serial install -r (Join-Path $output "tests.apk")
if ($LASTEXITCODE -ne 0) { throw "Test installation failed" }
$result = & $adb -s $Serial shell am instrument -w `
    com.relaychat.app.icons.tests/com.relaychat.app.icons.IconInstrumentation
$result | Tee-Object -FilePath (Join-Path $output "results.txt")
if ($LASTEXITCODE -ne 0 -or -not ($result -match "PASS:")) { throw "Icon tests failed" }
& $adb -s $Serial pull "/sdcard/Android/data/com.relaychat.app.debug/files/icon-tests/." $output
if ($LASTEXITCODE -ne 0) { throw "Screenshot collection failed" }
