param([string]$Sdk = $env:ANDROID_HOME, [int]$Port = 5580)
$ErrorActionPreference = 'Stop'
if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (!$Sdk -and (Test-Path -LiteralPath 'E:\Android\SDK')) { $Sdk = 'E:\Android\SDK' }
if (!$Sdk) { throw 'Set ANDROID_HOME or pass -Sdk.' }
if ($Port -lt 5554 -or $Port -gt 5682 -or $Port % 2 -ne 0) { throw 'Use an even emulator port between 5554 and 5682.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$image = Join-Path $Sdk 'system-images\android-31\google_apis_playstore\x86_64'
if (!(Test-Path -LiteralPath (Join-Path $image 'system.img'))) { throw 'Android 31 Google Play x86_64 system image is required.' }
$avdRoot = Join-Path $projectRoot '.local\avd'
$avdPath = Join-Path $avdRoot 'fansonIME_API31.avd'
New-Item -ItemType Directory -Path $avdPath -Force | Out-Null
$config = @'
AvdId=fansonIME_API31
avd.ini.displayname=fansonIME Android 12 Test
PlayStore.enabled=true
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.audioInput=no
image.sysdir.1=system-images\android-31\google_apis_playstore\x86_64\
tag.id=google_apis_playstore
tag.display=Google Play
disk.dataPartition.size=2G
showDeviceFrame=no
fastboot.forceColdBoot=yes
'@
if (!(Test-Path -LiteralPath (Join-Path $avdPath 'config.ini'))) { Set-Content -LiteralPath (Join-Path $avdPath 'config.ini') -Value $config -Encoding ASCII }
Set-Content -LiteralPath (Join-Path $avdRoot 'fansonIME_API31.ini') -Value "avd.ini.encoding=UTF-8`npath=$avdPath`ntarget=android-31" -Encoding ASCII
$env:ANDROID_AVD_HOME = $avdRoot
$env:ANDROID_SDK_ROOT = $Sdk
# Keep this emulator independent from the user's AVDs. Disable optional network
# simulation backends that stalled startup with Emulator 37.2.13 on this PC.
$process = Start-Process -FilePath (Join-Path $Sdk 'emulator\emulator.exe') -ArgumentList @(
    '-avd', 'fansonIME_API31', '-no-window', '-no-audio', '-no-snapshot', '-no-boot-anim',
    '-port', $Port, '-gpu', 'swiftshader', '-feature', '-Vulkan,-WiFiPacketStream,-Uwb'
) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $avdRoot 'stdout.log') -RedirectStandardError (Join-Path $avdRoot 'stderr.log')
Write-Output "Started isolated emulator-$Port (PID $($process.Id))."
Write-Output "Check readiness: adb -s emulator-$Port shell getprop sys.boot_completed"
Write-Output "Stop it: adb -s emulator-$Port emu kill"
