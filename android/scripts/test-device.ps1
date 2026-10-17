#requires -Version 7.0
param([Parameter(Mandatory)][string]$Serial)
$ErrorActionPreference = 'Stop'
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$deadline = [DateTime]::UtcNow.AddMinutes(3)
while ([string](& $adb -s $Serial shell getprop sys.boot_completed 2>$null) -notmatch '^\s*1\s*$') {
    if ([DateTime]::UtcNow -ge $deadline) { throw "Device $Serial did not finish booting." }
    Start-Sleep -Seconds 2
}
$previousSerial = $env:ANDROID_SERIAL
$env:ANDROID_SERIAL = $Serial
$started = Get-Date
Push-Location $androidRoot
try {
    & .\gradlew.bat :app:connectedDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Device tests failed.' }
    $reports = Get-ChildItem -LiteralPath 'app\build\outputs\androidTest-results\connected\debug' -Recurse -Filter '*.xml' |
        Where-Object { $_.LastWriteTime -ge $started }
    $tests = 0
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        foreach ($suite in $document.testsuites.testsuite) {
            $device = $suite.properties.property | Where-Object { $_.name -eq 'device' }
            if ($device.value -ne $Serial) { continue }
            if ([int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) { throw "Failed or skipped tests in $($suite.name)." }
            $tests += [int]$suite.tests
        }
    }
    if ($tests -lt 8) { throw "Only $tests tests were reported for $Serial; a successful build alone is not a device-test pass." }
    Write-Output "Verified $tests device tests on $Serial."
} finally {
    $env:ANDROID_SERIAL = $previousSerial
    Pop-Location
}
