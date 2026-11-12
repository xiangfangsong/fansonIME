param([string]$Serial = 'emulator-5580', [ValidateSet('all', 'keyboard_shortcuts', 'pinyin')][string]$Focus = 'all')
$ErrorActionPreference = 'Stop'
# Never use a physical phone, and never pick a device implicitly.
if ($Serial -notmatch '^emulator-\d+$') { throw 'This smoke test is restricted to emulators.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
function Invoke-Adb {
    param([string[]]$AdbArgs)
    & adb -s $Serial @AdbArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed for emulator $Serial" }
}
$isEmulator = & adb -s $Serial shell getprop ro.kernel.qemu
if ($LASTEXITCODE -ne 0 -or $isEmulator.Trim() -ne '1') { throw 'The target is not a ready Android emulator.' }
$hardware = & adb -s $Serial shell getprop ro.product.model
Write-Output "Running disposable-emulator tests on $Serial ($hardware)."
Invoke-Adb -AdbArgs @('shell', 'svc', 'wifi', 'disable')
Invoke-Adb -AdbArgs @('shell', 'svc', 'data', 'disable')
Invoke-Adb -AdbArgs @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'))
Invoke-Adb -AdbArgs @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
Invoke-Adb -AdbArgs @('shell', 'ime', 'enable', 'com.fanson.ime/.ime.FansonInputMethodService')
Invoke-Adb -AdbArgs @('shell', 'ime', 'set', 'com.fanson.ime/.ime.FansonInputMethodService')
$instrumentArgs = @('shell', 'am', 'instrument', '-w')
if ($Focus -ne 'all') { $instrumentArgs += @('-e', 'focus', $Focus) }
$instrumentArgs += 'com.fanson.ime.test/com.fanson.ime.SmokeInstrumentation'
$result = & adb -s $Serial @instrumentArgs
$result | Write-Output
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'INSTRUMENTATION_CODE: -1' -or ($result -join "`n") -match 'failure=') {
    throw 'Emulator smoke tests failed.'
}
