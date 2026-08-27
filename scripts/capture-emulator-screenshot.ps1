$ErrorActionPreference = 'Stop'

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$adb = 'C:\Users\Tyler\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$captureDirectory = Join-Path $workspaceRoot 'captures'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputPath = Join-Path $captureDirectory "scenegram-emulator-$timestamp.png"

New-Item -ItemType Directory -Path $captureDirectory -Force | Out-Null
& $adb -e exec-out screencap -p > $outputPath
if ($LASTEXITCODE -ne 0) {
    throw 'Could not capture the emulator. Start it first.'
}

Write-Output $outputPath

$code = 'C:\Users\Tyler\AppData\Local\Programs\Microsoft VS Code\bin\code.cmd'
if (Test-Path -LiteralPath $code) {
    & $code --reuse-window $outputPath | Out-Null
}
