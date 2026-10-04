param(
    [string]$SdkRoot = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "D:\Android\Sdk" })
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$build = Join-Path $root "build\manual"
$dist = Join-Path $root "dist"
$versionFile = Join-Path $root "version.properties"

function Require-Path([string]$path, [string]$label) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "$label not found: $path"
    }
}

Push-Location $root
try {
    Require-Path $SdkRoot "Android SDK"
    Require-Path $versionFile "version.properties"
    $versionProperties = @{}
    foreach ($line in Get-Content -LiteralPath $versionFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $parts = $trimmed.Split("=", 2)
        if ($parts.Count -ne 2) {
            throw "Invalid version.properties line: $line"
        }
        $versionProperties[$parts[0].Trim()] = $parts[1].Trim()
    }
    [int]$versionCode = 0
    if (-not [int]::TryParse($versionProperties["versionCode"], [ref]$versionCode) -or $versionCode -le 0) {
        throw "version.properties versionCode must be a positive integer"
    }
    $versionName = $versionProperties["versionName"]
    if ([string]::IsNullOrWhiteSpace($versionName)) {
        throw "version.properties versionName must not be empty"
    }

    $buildTools = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory |
        Where-Object { $_.Name -match "^\d+\.\d+\.\d+" -and (Test-Path (Join-Path $_.FullName "aapt2.exe")) } |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if (-not $buildTools) {
        throw "No usable Android build-tools installation found under $SdkRoot"
    }

    $platformRoot = Join-Path $SdkRoot "platforms"
    $platform = Get-ChildItem $platformRoot -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName "android.jar") } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $platform) {
        throw "No Android platform android.jar found under $platformRoot"
    }

    $aapt2 = Join-Path $buildTools.FullName "aapt2.exe"
    $d8 = Join-Path $buildTools.FullName "d8.bat"
    $zipalign = Join-Path $buildTools.FullName "zipalign.exe"
    $apksigner = Join-Path $buildTools.FullName "apksigner.bat"
    $androidJar = Join-Path $platform.FullName "android.jar"
    Require-Path $d8 "d8"
    Require-Path $zipalign "zipalign"
    Require-Path $apksigner "apksigner"

    $javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Split-Path (Get-Command java.exe).Source -Parent | Split-Path -Parent }
    $javac = Join-Path $javaHome "bin\javac.exe"
    $jar = Join-Path $javaHome "bin\jar.exe"
    $keytool = Join-Path $javaHome "bin\keytool.exe"
    Require-Path $javac "javac"
    Require-Path $jar "jar"
    Require-Path $keytool "keytool"

    if (Test-Path -LiteralPath $build) {
        Remove-Item -LiteralPath $build -Recurse -Force
    }
    New-Item -ItemType Directory -Force $build, (Join-Path $build "classes"), (Join-Path $build "dex") | Out-Null

    $manifest = Get-Content -Raw (Join-Path $root "app\src\main\AndroidManifest.xml")
    $manifest = $manifest.Replace(
        '<manifest xmlns:android=',
        '<manifest package="com.relaychat.app" xmlns:android=')
    [System.IO.File]::WriteAllText(
        (Join-Path $build "AndroidManifest.xml"),
        $manifest,
        [System.Text.UTF8Encoding]::new($false))

    & $aapt2 compile --dir (Join-Path $root "app\src\main\res") -o (Join-Path $build "res.zip")
    if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

    & $aapt2 link `
        -o (Join-Path $build "base.apk") `
        -I $androidJar `
        --manifest (Join-Path $build "AndroidManifest.xml") `
        --java (Join-Path $build "gen") `
        --min-sdk-version 23 `
        --target-sdk-version 36 `
        --version-code $versionCode `
        --version-name $versionName `
        --auto-add-overlay `
        -R (Join-Path $build "res.zip")
    if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

    $buildConfigDirectory = Join-Path $build "gen\com\relaychat\app"
    New-Item -ItemType Directory -Force $buildConfigDirectory | Out-Null
    $buildConfigSource = @"
package com.relaychat.app;

public final class BuildConfig {
    public static final String VERSION_NAME = "$versionName";

    private BuildConfig() {
    }
}
"@
    [System.IO.File]::WriteAllText(
        (Join-Path $buildConfigDirectory "BuildConfig.java"),
        $buildConfigSource,
        [System.Text.UTF8Encoding]::new($false))

    $javaFiles = @(Get-ChildItem (Join-Path $root "app\src\main\java") -Recurse -Filter *.java | ForEach-Object FullName)
    $javaFiles += (Get-ChildItem (Join-Path $build "gen") -Recurse -Filter *.java | ForEach-Object FullName)
    & $javac -encoding UTF-8 -source 17 -target 17 -Xlint:-options -classpath $androidJar -d (Join-Path $build "classes") $javaFiles
    if ($LASTEXITCODE -ne 0) { throw "javac failed" }

    & $jar cf (Join-Path $build "classes.jar") -C (Join-Path $build "classes") .
    if ($LASTEXITCODE -ne 0) { throw "jar failed" }
    & $d8 --release --min-api 23 --lib $androidJar --output (Join-Path $build "dex") (Join-Path $build "classes.jar")
    if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

    Copy-Item (Join-Path $build "base.apk") (Join-Path $build "unsigned.apk") -Force
    & $jar uf (Join-Path $build "unsigned.apk") -C (Join-Path $build "dex") classes.dex
    if ($LASTEXITCODE -ne 0) { throw "adding classes.dex failed" }
    & $zipalign -f -p 4 (Join-Path $build "unsigned.apk") (Join-Path $build "aligned.apk")
    if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

    $debugKeyStore = Join-Path $env:USERPROFILE ".android\debug.keystore"
    if (-not (Test-Path -LiteralPath $debugKeyStore)) {
        $keystoreDirectory = Join-Path $root "keystore"
        New-Item -ItemType Directory -Force $keystoreDirectory | Out-Null
        $debugKeyStore = Join-Path $keystoreDirectory "debug.keystore"
    }
    if (-not (Test-Path -LiteralPath $debugKeyStore)) {
        & $keytool -genkeypair -keystore $debugKeyStore -storepass android -keypass android `
            -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" `
            -keyalg RSA -keysize 2048 -validity 10000
        if ($LASTEXITCODE -ne 0) { throw "debug keystore generation failed" }
    }

    New-Item -ItemType Directory -Force $dist | Out-Null
    Get-ChildItem $dist -Filter "*.idsig" -ErrorAction SilentlyContinue | Remove-Item -Force
    $apk = Join-Path $dist "RelayChat-debug.apk"
    & $apksigner sign --ks $debugKeyStore --ks-key-alias androiddebugkey `
        --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false `
        --out $apk (Join-Path $build "aligned.apk")
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
    & $apksigner verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw "APK verification failed" }

    Write-Host "Built: $apk"
} finally {
    Pop-Location
}
