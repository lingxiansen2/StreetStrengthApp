param(
    [string]$AvdName = "StreetStrengthApi34",
    [int]$EmulatorPort = 5584,
    [switch]$WindowedEmulator,
    [switch]$RestartEmulator,
    [switch]$ColdBootEmulator,
    [switch]$WipeEmulatorData
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$sdkRoot = Join-Path $repoRoot ".local-tools\android-sdk"
$androidUserHome = Join-Path $repoRoot ".local-tools\android-user"
$androidAvdHome = Join-Path $repoRoot ".local-tools\android-avd"
$logDir = Join-Path $repoRoot "work"
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"

foreach ($requiredPath in @($sdkRoot, $androidAvdHome, $emulator)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Missing required path: $requiredPath"
    }
}

$null = New-Item -ItemType Directory -Force -Path $androidUserHome
$null = New-Item -ItemType Directory -Force -Path $logDir

$hostLocalAppData = Join-Path $env:USERPROFILE "AppData\Local"
$hostRoamingAppData = Join-Path $env:USERPROFILE "AppData\Roaming"
$hostTemp = Join-Path $hostLocalAppData "Temp"

$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_AVD_HOME = $androidAvdHome
$env:ANDROID_USER_HOME = $androidUserHome
$env:HOME = $androidUserHome
if (Test-Path $hostLocalAppData) {
    $env:LOCALAPPDATA = $hostLocalAppData
}
if (Test-Path $hostRoamingAppData) {
    $env:APPDATA = $hostRoamingAppData
}
if (Test-Path $hostTemp) {
    $env:TEMP = $hostTemp
    $env:TMP = $hostTemp
}

$emulatorArgs = @(
    "-avd", $AvdName,
    "-port", "$EmulatorPort",
    "-no-audio",
    "-no-boot-anim",
    "-gpu", "swiftshader_indirect",
    "-no-snapshot-save",
    "-verbose"
)
if (-not $WindowedEmulator) {
    $emulatorArgs += "-no-window"
}
if ($ColdBootEmulator) {
    $emulatorArgs += "-no-snapshot-load"
}
if ($WipeEmulatorData) {
    $emulatorArgs += "-wipe-data"
}

Write-Host "Starting AVD $AvdName on emulator-$EmulatorPort"
$serial = "emulator-$EmulatorPort"
if ($RestartEmulator) {
    $adb = Join-Path $sdkRoot "platform-tools\adb.exe"
    if (Test-Path $adb) {
        $devices = (& $adb "devices" | Out-String)
        if ($devices -match "(?m)^$([regex]::Escape($serial))\s+") {
            Write-Host "Restart requested; stopping existing $serial"
            $killProcess = Start-Process `
                -FilePath $adb `
                -ArgumentList @("-s", $serial, "emu", "kill") `
                -WindowStyle Hidden `
                -PassThru
            if (-not $killProcess.WaitForExit(8000)) {
                Write-Host "adb emu kill timed out for $serial; stopping matching emulator processes"
                $killProcess.Kill()
            }
            Start-Sleep -Seconds 5
        }
    }
    Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -in @("emulator.exe", "qemu-system-x86_64.exe", "qemu-system-x86_64-headless.exe") -and
            $_.CommandLine -like "*-avd $AvdName*" -and
            $_.CommandLine -like "*-port $EmulatorPort*"
        } |
        ForEach-Object {
            Write-Host "Stopping stale $($_.Name) pid=$($_.ProcessId)"
            Stop-Process -Id $_.ProcessId -Force
        }
    Start-Sleep -Seconds 2
}
$windowStyle = if ($WindowedEmulator) { "Normal" } else { "Hidden" }
$process = Start-Process `
    -FilePath $emulator `
    -ArgumentList $emulatorArgs `
    -WorkingDirectory $repoRoot `
    -WindowStyle $windowStyle `
    -PassThru
Write-Host "Emulator process id: $($process.Id)"
