$ErrorActionPreference = 'Stop'

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$adb = 'C:\Users\Tyler\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$package = 'com.tyler.scenegram'
$activity = "$package/.MainActivity"

function Get-UiDocument {
    & $adb -e shell uiautomator dump /sdcard/scenegram-window.xml | Out-Null
    $xmlText = ((& $adb -e exec-out cat /sdcard/scenegram-window.xml) -join "`n")
    return [xml]$xmlText
}

function Get-TextNode([string]$Text) {
    $document = Get-UiDocument
    return $document.SelectNodes('//node') |
        Where-Object { $_.GetAttribute('text') -eq $Text } |
        Select-Object -First 1
}

function Tap-Text([string]$Text, [int]$Repeat = 1) {
    $node = Get-TextNode -Text $Text
    if ($null -eq $node) {
        throw "Could not find '$Text' on the emulator screen."
    }
    $bounds = $node.GetAttribute('bounds')
    if ($bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "Could not parse bounds for '$Text': $bounds"
    }
    $x = [int](([int]$matches[1] + [int]$matches[3]) / 2)
    $y = [int](([int]$matches[2] + [int]$matches[4]) / 2)
    1..$Repeat | ForEach-Object {
        & $adb -e shell input tap $x $y | Out-Null
        Start-Sleep -Milliseconds 120
    }
}

& $adb -e shell am force-stop $package
& $adb -e shell pm clear $package | Out-Null
& $adb -e shell pm grant $package android.permission.POST_NOTIFICATIONS
& $adb -e shell am start -W -n $activity | Out-Null
Start-Sleep -Seconds 1

Tap-Text -Text 'Moment' -Repeat 5
Start-Sleep -Milliseconds 800
Tap-Text -Text 'Start demo cue'

Start-Sleep -Seconds 4
$textMessage = Get-TextNode -Text 'Are you still coming tonight?'
if ($null -eq $textMessage) {
    throw 'The scripted incoming text did not arrive.'
}

Start-Sleep -Seconds 6
$voiceDuration = Get-TextNode -Text '0:07'
if ($null -eq $voiceDuration) {
    throw 'The scripted voice message did not arrive.'
}

$activeNotifications = ((& $adb -e shell cmd notification list) -join "`n")
if ($activeNotifications -notmatch [Regex]::Escape($package)) {
    throw 'No active Moment Android notification was found.'
}

$captureDirectory = Join-Path $workspaceRoot 'captures'
$capturePath = Join-Path $captureDirectory 'moment-chat-smoke-test.png'
New-Item -ItemType Directory -Path $captureDirectory -Force | Out-Null
& $adb -e exec-out screencap -p > $capturePath

Write-Output 'PASS: Moment -> director -> timed text -> timed voice -> Android notification'
Write-Output $capturePath
