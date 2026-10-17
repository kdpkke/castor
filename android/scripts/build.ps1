#requires -Version 7.0
param(
    [switch]$SkipNative
)
$ErrorActionPreference = 'Stop'
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
Push-Location $androidRoot
try {
    if (-not $SkipNative) {
        $linuxRoot = (& wsl.exe --exec wslpath -a $androidRoot).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'WSL could not resolve the Android project path.' }
        & wsl.exe --exec bash "$linuxRoot/scripts/build-native.sh" arm64-v8a x86_64
        if ($LASTEXITCODE -ne 0) { throw 'Native build failed.' }
    }
    & .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android checks or APK build failed.' }
} finally { Pop-Location }
