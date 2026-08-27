param(
    [switch]$ColdBoot
)

$ErrorActionPreference = 'Stop'

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$androidSdk = 'C:\Users\Tyler\AppData\Local\Android\Sdk'
$androidUserHome = Join-Path $workspaceRoot '.android-user'
$adb = Join-Path $androidSdk 'platform-tools\adb.exe'
$emulator = Join-Path $androidSdk 'emulator\emulator.exe'
$avdName = 'SceneGram_Galaxy_A51_API_33'
$avdConfig = Join-Path $androidUserHome "avd\$avdName.avd\config.ini"

$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
$env:ANDROID_USER_HOME = $androidUserHome
$env:ANDROID_AVD_HOME = Join-Path $androidUserHome 'avd'

if (-not (Test-Path -LiteralPath $emulator)) {
    throw 'Android Emulator is not installed. See README.md for setup details.'
}
if (-not (Test-Path -LiteralPath $avdConfig)) {
    & (Join-Path $PSScriptRoot 'create-a51-avd.ps1')
}

$runningEmulator = (& $adb devices) -match '^emulator-\d+\s+device$'
if (-not $runningEmulator) {
    $emulatorArgs = @(
        '-avd', $avdName,
        '-gpu', 'auto',
        '-netdelay', 'none',
        '-netspeed', 'full',
        '-no-boot-anim'
    )
    if ($ColdBoot) {
        $emulatorArgs += '-no-snapshot-load'
    }

    # The emulator is intentionally visible: it is the interactive preview window.
    Start-Process -FilePath $emulator -ArgumentList $emulatorArgs
}

Write-Output 'Waiting for the Galaxy A51 emulator to finish booting...'
& $adb -e wait-for-device

$deadline = [DateTime]::UtcNow.AddMinutes(4)
do {
    $bootCompleted = ((& $adb -e shell getprop sys.boot_completed) -join '').Trim()
    if ($bootCompleted -eq '1') {
        break
    }
    if ([DateTime]::UtcNow -ge $deadline) {
        throw 'The emulator did not finish booting within four minutes.'
    }
    Start-Sleep -Seconds 1
} while ($true)

& $adb -e shell settings put system accelerometer_rotation 0 | Out-Null
& $adb -e shell settings put system user_rotation 0 | Out-Null
& $adb -e shell settings put system screen_off_timeout 2147483647 | Out-Null
& $adb -e shell svc power stayon true | Out-Null

Write-Output 'Galaxy A51 emulator is ready.'
