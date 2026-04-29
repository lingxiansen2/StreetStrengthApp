param(
    [switch]$NoClean,
    [switch]$StopDaemons,
    [switch]$NoDaemon,
    [switch]$SkipUnitTest,
    [switch]$AndroidTest,
    [switch]$StartEmulator,
    [switch]$KeepEmulator,
    [int]$MaxWorkers = 1,
    [int]$EmulatorPort = 5584,
    [int]$EmulatorBootTimeoutSec = 240,
    [string]$AvdName = "StreetStrengthApi34",
    [string]$DeviceSerial,
    [string]$InstrumentationClass = "com.codex.streetstrength.timer.RestTimerReceiverInstrumentedTest"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradleScript = Join-Path $PSScriptRoot "gradlew-local.ps1"
$adb = Join-Path $repoRoot ".local-tools\android-sdk\platform-tools\adb.exe"
$emulator = Join-Path $repoRoot ".local-tools\android-sdk\emulator\emulator.exe"
$powershellExe = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
$androidUserHome = Join-Path $repoRoot ".local-tools\android-user"
$androidAvdHome = Join-Path $repoRoot ".local-tools\android-avd"
$emulatorLogDir = Join-Path $repoRoot "work"
$localAppData = Join-Path $repoRoot ".local-tools\appdata-local"
$roamingAppData = Join-Path $repoRoot ".local-tools\appdata-roaming"

$null = New-Item -ItemType Directory -Force -Path $androidUserHome
$null = New-Item -ItemType Directory -Force -Path (Join-Path $androidUserHome ".android")
$null = New-Item -ItemType Directory -Force -Path $androidAvdHome
$null = New-Item -ItemType Directory -Force -Path $emulatorLogDir
$null = New-Item -ItemType Directory -Force -Path $localAppData
$null = New-Item -ItemType Directory -Force -Path $roamingAppData
$env:ANDROID_USER_HOME = $androidUserHome
$env:ANDROID_AVD_HOME = $androidAvdHome
$env:HOME = $androidUserHome
$env:LOCALAPPDATA = $localAppData
$env:APPDATA = $roamingAppData

function Invoke-Gradle {
    param(
        [string[]]$Tasks,
        [switch]$NoDaemon
    )

    $gradleArgs = @()
    if ($NoDaemon) {
        $gradleArgs += "--no-daemon"
    }
    $daemonCommand = ($Tasks | Where-Object { $_ -in @("--status", "--stop") }).Count -gt 0
    if ($MaxWorkers -gt 0 -and -not $daemonCommand) {
        $gradleArgs += "--max-workers=$MaxWorkers"
    }
    $gradleArgs += $Tasks

    Write-Host "==> Gradle $($gradleArgs -join ' ')"
    & $powershellExe -NoProfile -ExecutionPolicy Bypass -File $gradleScript @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE for: $($gradleArgs -join ' ')"
    }
}

function Invoke-Adb {
    param([string[]]$AdbArgs)

    & $adb @AdbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code $LASTEXITCODE for: $($AdbArgs -join ' ')"
    }
}

function Get-AdbDeviceState {
    param([string]$Serial)

    $devices = (& $adb "devices" | Out-String)
    $match = [regex]::Match($devices, "(?m)^$([regex]::Escape($Serial))\s+(\S+)")
    if ($match.Success) {
        return $match.Groups[1].Value
    }
    return $null
}

function Wait-ForEmulatorBoot {
    param(
        [string]$Serial,
        [int]$TimeoutSec
    )

    Write-Host "==> Waiting for $Serial"
    Invoke-Adb -AdbArgs @("-s", $Serial, "wait-for-device")

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $bootCompleted = (& $adb "-s" $Serial "shell" "getprop" "sys.boot_completed" | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $bootCompleted -eq "1") {
            Write-Host "==> $Serial boot_completed=1"
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for $Serial to boot."
}

function Start-LocalEmulator {
    param(
        [string]$Name,
        [int]$Port
    )

    if (-not (Test-Path $emulator)) {
        throw "Missing emulator: $emulator"
    }

    $serial = "emulator-$Port"
    $existingState = Get-AdbDeviceState -Serial $serial
    if ($existingState -eq "device") {
        Write-Host "==> Reusing already connected $serial"
        Wait-ForEmulatorBoot -Serial $serial -TimeoutSec $EmulatorBootTimeoutSec
        return $serial
    }
    if (-not [string]::IsNullOrWhiteSpace($existingState)) {
        Write-Host "==> Existing $serial is $existingState; requesting shutdown before restart"
        & $adb "-s" $serial "emu" "kill" | Out-Null
        Start-Sleep -Seconds 10
    }

    Start-Sleep -Seconds 5
    Write-Host "==> Starting AVD $Name on $serial"
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $stdoutLog = Join-Path $emulatorLogDir "emulator-$Port-$stamp.out.log"
    $stderrLog = Join-Path $emulatorLogDir "emulator-$Port-$stamp.err.log"
    $process = Start-Process `
        -FilePath $emulator `
        -ArgumentList @(
            "-avd", $Name,
            "-port", "$Port",
            "-no-window",
            "-no-audio",
            "-no-boot-anim",
            "-gpu", "swiftshader_indirect",
            "-no-snapshot-save"
        ) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    Start-Sleep -Seconds 5
    if ($process.HasExited) {
        throw "Emulator exited early with code $($process.ExitCode)."
    }

    try {
        Wait-ForEmulatorBoot -Serial $serial -TimeoutSec $EmulatorBootTimeoutSec
    } catch {
        Write-Host "==> Emulator logs: $stdoutLog ; $stderrLog"
        & $adb "-s" $serial "emu" "kill" | Out-Null
        throw
    }
    return $serial
}

Write-Host "StreetStrength local verification"
Write-Host "Project: $projectRoot"
Invoke-Gradle -Tasks @("--status")
if (-not $NoClean) {
    if ($StopDaemons) {
        Invoke-Gradle -Tasks @("--stop")
        Start-Sleep -Seconds 2
    }
    Invoke-Gradle -Tasks @("clean") -NoDaemon:$NoDaemon
}
Invoke-Gradle -Tasks @("assembleDebug") -NoDaemon:$NoDaemon
if (-not $SkipUnitTest) {
    Invoke-Gradle -Tasks @("testDebugUnitTest") -NoDaemon:$NoDaemon
}

if ($AndroidTest) {
    $startedEmulator = $false
    if ($StartEmulator) {
        if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
            $DeviceSerial = "emulator-$EmulatorPort"
        }
        $DeviceSerial = Start-LocalEmulator -Name $AvdName -Port $EmulatorPort
        $startedEmulator = $true
    } elseif ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        throw "Android instrumentation requires -DeviceSerial, for example: -DeviceSerial emulator-5554"
    }
    if ($DeviceSerial -notlike "emulator-*") {
        throw "Refusing to run Android instrumentation on non-emulator device: $DeviceSerial"
    }
    if (-not (Test-Path $adb)) {
        throw "Missing adb: $adb"
    }

    try {
        Write-Host "==> Checking device $DeviceSerial"
        Invoke-Adb -AdbArgs @("-s", $DeviceSerial, "get-state")

        Invoke-Gradle -Tasks @("assembleDebugAndroidTest") -NoDaemon:$NoDaemon

        $debugApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
        $testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
        foreach ($apk in @($debugApk, $testApk)) {
            if (-not (Test-Path $apk)) {
                throw "Missing APK: $apk"
            }
        }

        Write-Host "==> Installing APKs on $DeviceSerial"
        Invoke-Adb -AdbArgs @("-s", $DeviceSerial, "install", "-r", $debugApk)
        Invoke-Adb -AdbArgs @("-s", $DeviceSerial, "install", "-r", $testApk)

        $runner = "com.codex.streetstrength.test/androidx.test.runner.AndroidJUnitRunner"
        $instrumentArgs = @("-s", $DeviceSerial, "shell", "am", "instrument", "-w")
        if (-not [string]::IsNullOrWhiteSpace($InstrumentationClass)) {
            $instrumentArgs += @("-e", "class", $InstrumentationClass)
        }
        $instrumentArgs += $runner

        Write-Host "==> Running instrumentation on $DeviceSerial"
        Invoke-Adb -AdbArgs $instrumentArgs
    } finally {
        if ($startedEmulator -and -not $KeepEmulator) {
            Write-Host "==> Stopping $DeviceSerial"
            & $adb "-s" $DeviceSerial "emu" "kill" | Out-Null
        }
    }
}

Write-Host "Verification completed."
