param(
    [string]$SdkRoot = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "D:\Android\Sdk" }),
    [switch]$TestBuild
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$dist = Join-Path $root "dist"
$gradle = Join-Path $root "gradlew.bat"
$gradleApk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$manifest = Join-Path $root "app\src\main\AndroidManifest.xml"

function Require-Path([string]$path, [string]$label) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "$label not found: $path"
    }
}

function Get-ApplicationId([string]$apk) {
    $buildTools = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName "aapt2.exe") } |
        Sort-Object { try { [version]$_.Name } catch { [version]"0.0.0" } } -Descending |
        Select-Object -First 1
    if (-not $buildTools) {
        throw "No Android build-tools installation found under $SdkRoot"
    }
    $badging = & (Join-Path $buildTools.FullName "aapt2.exe") dump badging $apk
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect APK identity"
    }
    $match = $badging | Select-String -Pattern "package: name='([^']+)'" | Select-Object -First 1
    if (-not $match) {
        throw "APK package name not found"
    }
    return $match.Matches[0].Groups[1].Value
}

Push-Location $root
try {
    Require-Path $SdkRoot "Android SDK"
    Require-Path $gradle "Gradle wrapper"
    Require-Path $manifest "Android manifest"

    $arguments = @(":app:assembleDebug", "--no-daemon", "--stacktrace")
    if (-not $TestBuild) {
        $arguments += "-PnormalBuild=true"
    }
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle APK build failed"
    }
    Require-Path $gradleApk "Gradle APK output"

    New-Item -ItemType Directory -Force $dist | Out-Null
    Get-ChildItem $dist -Filter "*.idsig" -ErrorAction SilentlyContinue | Remove-Item -Force
    $apkName = if ($TestBuild) { "RelayChat-test.apk" } else { "RelayChat-debug.apk" }
    $apk = Join-Path $dist $apkName

    $configuredSigning = @(
        [string]$env:RELAYCHAT_KEYSTORE_PATH,
        [string]$env:RELAYCHAT_KEYSTORE_PASSWORD,
        [string]$env:RELAYCHAT_KEY_ALIAS,
        [string]$env:RELAYCHAT_KEY_PASSWORD
    )
    $hasConfiguredSigning = $configuredSigning | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_)
    }
    if ($hasConfiguredSigning -and ($configuredSigning | Where-Object {
        [string]::IsNullOrWhiteSpace($_)
    })) {
        throw "All RELAYCHAT_KEYSTORE_* signing settings must be provided together"
    }

    if ($hasConfiguredSigning) {
        $buildTools = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory |
            Where-Object {
                (Test-Path (Join-Path $_.FullName "apksigner.bat")) -and
                (Test-Path (Join-Path $_.FullName "zipalign.exe"))
            } |
            Sort-Object { try { [version]$_.Name } catch { [version]"0.0.0" } } -Descending |
            Select-Object -First 1
        if (-not $buildTools) {
            throw "No Android build-tools signing utilities found under $SdkRoot"
        }
        $aligned = Join-Path $root "build\gradle-apk-aligned.apk"
        & (Join-Path $buildTools.FullName "zipalign.exe") -f -p 4 $gradleApk $aligned
        if ($LASTEXITCODE -ne 0) {
            throw "APK alignment failed"
        }
        & (Join-Path $buildTools.FullName "apksigner.bat") sign `
            --ks $env:RELAYCHAT_KEYSTORE_PATH `
            --ks-key-alias $env:RELAYCHAT_KEY_ALIAS `
            --ks-pass "pass:$env:RELAYCHAT_KEYSTORE_PASSWORD" `
            --key-pass "pass:$env:RELAYCHAT_KEY_PASSWORD" `
            --v4-signing-enabled false `
            --out $apk $aligned
        if ($LASTEXITCODE -ne 0) {
            throw "APK signing failed"
        }
    } else {
        Copy-Item -LiteralPath $gradleApk -Destination $apk -Force
    }

    $applicationId = Get-ApplicationId $apk
    $expectedId = if ($TestBuild) { "com.relaychat.app.debug" } else { "com.relaychat.app" }
    if ($applicationId -ne $expectedId) {
        throw "Unexpected application ID: $applicationId (expected $expectedId)"
    }

    Write-Host "Built: $apk"
    Write-Host "Application ID: $applicationId"
} finally {
    Pop-Location
}
