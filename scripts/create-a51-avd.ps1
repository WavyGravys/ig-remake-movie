$ErrorActionPreference = 'Stop'

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$androidSdk = 'C:\Users\Tyler\AppData\Local\Android\Sdk'
$javaHome = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$androidUserHome = Join-Path $workspaceRoot '.android-user'
$avdManager = Join-Path $androidSdk 'cmdline-tools\latest\bin\avdmanager.bat'
$avdName = 'SceneGram_Galaxy_A51_API_33'
$systemImage = 'system-images;android-33;google_apis;x86_64'
$avdDirectory = Join-Path $androidUserHome "avd\$avdName.avd"
$configPath = Join-Path $avdDirectory 'config.ini'

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
$env:ANDROID_USER_HOME = $androidUserHome
$env:ANDROID_AVD_HOME = Join-Path $androidUserHome 'avd'
$env:Path = "$javaHome\bin;$androidSdk\platform-tools;$env:Path"

if (-not (Test-Path -LiteralPath $configPath)) {
    New-Item -ItemType Directory -Path $androidUserHome -Force | Out-Null
    'no' | & $avdManager create avd `
        --force `
        --name $avdName `
        --package $systemImage `
        --device 'pixel_6'
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not create the Galaxy A51 Android Virtual Device.'
    }
}

$profile = [ordered]@{
    'AvdId' = $avdName
    'avd.ini.displayname' = 'SceneGram Galaxy A51 - Android 13'
    # Keep the built-in Pixel 6 hardware-profile identifier so avdmanager can reload it.
    # The display name and the overrides below provide the A51 test characteristics.
    'hw.device.manufacturer' = 'Google'
    'hw.device.name' = 'pixel_6'
    'hw.lcd.width' = '1080'
    'hw.lcd.height' = '2400'
    'hw.lcd.density' = '420'
    'hw.ramSize' = '4096'
    'hw.cpu.ncore' = '4'
    'hw.gpu.enabled' = 'yes'
    'hw.gpu.mode' = 'auto'
    'hw.initialOrientation' = 'Portrait'
    'hw.keyboard' = 'yes'
    'hw.mainKeys' = 'no'
    'hw.camera.front' = 'emulated'
    'hw.camera.back' = 'virtualscene'
    'disk.dataPartition.size' = '8G'
    'runtime.network.latency' = 'none'
    'runtime.network.speed' = 'full'
    'showDeviceFrame' = 'yes'
    'fastboot.forceColdBoot' = 'no'
    'fastboot.forceFastBoot' = 'yes'
}

$profileKeys = $profile.Keys | ForEach-Object { [Regex]::Escape($_) }
$profilePattern = '^(' + ($profileKeys -join '|') + ')='
$configLines = @(Get-Content -LiteralPath $configPath) |
    Where-Object { $_ -notmatch $profilePattern }
foreach ($entry in $profile.GetEnumerator()) {
    $configLines += "$($entry.Key)=$($entry.Value)"
}
$configLines | Set-Content -LiteralPath $configPath -Encoding ascii

Write-Output "Configured $avdName at $avdDirectory"
