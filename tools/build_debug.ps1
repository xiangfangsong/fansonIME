param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaCandidates = @($env:JAVA_HOME, 'C:\Program Files\Java\jdk17', 'C:\Program Files\Android\Android Studio\jbr')
$selectedJava = $null
foreach ($javaCandidate in $javaCandidates) {
    if (!$javaCandidate -or !(Test-Path -LiteralPath (Join-Path $javaCandidate 'bin\java.exe'))) { continue }
    $releaseFile = Join-Path $javaCandidate 'release'
    $javaVersion = if (Test-Path -LiteralPath $releaseFile) { Get-Content -LiteralPath $releaseFile | Out-String } else { '' }
    if ($javaVersion -match 'JAVA_VERSION="(17|21)\.') { $selectedJava = $javaCandidate; break }
}
if (!$selectedJava) { throw 'JDK 17 or 21 required. Set JAVA_HOME before building.' }
$env:JAVA_HOME = $selectedJava
$env:Path = "$selectedJava\bin;$env:Path"
Push-Location -LiteralPath $projectRoot
try {
    python tools/verify_assets.py
    if ($LASTEXITCODE -ne 0) { throw 'Offline asset verification failed.' }
    $gradleArgs = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebugAndroidTest', '--console=plain')
    if ($Offline) { $gradleArgs += '--offline' }
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
    Write-Output 'Debug APK: app/build/outputs/apk/debug/app-debug.apk'
} finally { Pop-Location }
