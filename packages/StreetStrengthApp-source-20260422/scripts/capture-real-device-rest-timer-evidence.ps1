param(
    [string]$DeviceSerial = "bbc478f4",
    [int]$CaptureSeconds = 180,
    [string]$Scenario = "manual-real-device-rest",
    [string]$OutputRoot
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$adb = Join-Path $repoRoot ".local-tools\android-sdk\platform-tools\adb.exe"
$packageName = "com.codex.streetstrength"
$allowedSerial = "bbc478f4"

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot "work\thread-results\7-quality-env\20260504-real-device-background-rest-timer-evidence"
}

if ($DeviceSerial -ne $allowedSerial) {
    throw "This capture is restricted to $allowedSerial by 20260504 task instructions. Refusing serial: $DeviceSerial"
}
if (-not (Test-Path $adb)) {
    throw "Missing adb: $adb"
}

$safeScenario = $Scenario -replace "[^A-Za-z0-9_.-]", "-"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path $OutputRoot "$timestamp-$safeScenario"
$null = New-Item -ItemType Directory -Force -Path $outputDir

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Save-AdbOutput {
    param(
        [string]$Path,
        [string[]]$AdbArgs,
        [switch]$Required
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $result = & $adb @AdbArgs 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $result | Out-File -FilePath $Path -Encoding UTF8
    if ($Required -and $exitCode -ne 0) {
        throw "adb failed with exit code $exitCode for: $($AdbArgs -join ' '). Output: $Path"
    }
}

function Capture-State {
    param([string]$Phase)

    Write-Step "Capturing $Phase state"
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-adb-devices.txt") -AdbArgs @("devices", "-l")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-getprop.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "getprop")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-package.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "package", $packageName)
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-appops.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "cmd", "appops", "get", $packageName)
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-alarm.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "alarm")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-services.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "activity", "services", $packageName)
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-notification.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "notification")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-deviceidle.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "deviceidle")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-battery.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "battery")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-power.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "power")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-vibrator.txt") -AdbArgs @("-s", $DeviceSerial, "shell", "dumpsys", "vibrator")
}

function Write-Summary {
    param([string]$LogcatPath)

    $logText = if (Test-Path $LogcatPath) { Get-Content -Raw -Encoding UTF8 $LogcatPath } else { "" }
    $patterns = @(
        "RestTimer",
        "RestTimerService",
        "RestTimerReceiver",
        "Vibrator",
        "AlarmManager",
        "ForegroundService",
        "AndroidRuntime",
        "FATAL",
        "Exception",
        "BackgroundServiceStartNotAllowed",
        "ForegroundServiceStartNotAllowed",
        "SecurityException",
        "Exact alarm"
    )

    $firstProductionStack = $null
    if (Test-Path $LogcatPath) {
        $firstProductionStack = Select-String -Path $LogcatPath -Pattern "at com\.codex\.streetstrength" |
            Where-Object { $_.Line -notmatch "androidTest|InstrumentedTest|\.test" } |
            Select-Object -First 1
    }

    $summary = New-Object System.Collections.Generic.List[string]
    $summary.Add("# Real Device Background Rest Timer Evidence Summary")
    $summary.Add("")
    $summary.Add("- Output directory: $outputDir")
    $summary.Add("- Device serial: $DeviceSerial")
    $summary.Add("- Scenario: $Scenario")
    $summary.Add("- Capture seconds: $CaptureSeconds")
    $summary.Add("")
    $summary.Add("## Logcat Pattern Check")
    foreach ($pattern in $patterns) {
        $summary.Add("- ${pattern}: $($logText -match [regex]::Escape($pattern))")
    }
    $summary.Add("")
    $summary.Add("## Crash Attribution")
    $summary.Add("- FATAL present: $($logText -match 'FATAL')")
    $summary.Add("- First production app stack frame present: $($null -ne $firstProductionStack)")
    if ($firstProductionStack) {
        $summary.Add("- First production app stack frame: $($firstProductionStack.Line.Trim())")
    } else {
        $summary.Add("- First production app stack frame: Not found in captured logcat.")
    }
    $summary.Add("")
    $summary.Add("## Required Review")
    $summary.Add("- Check after-dumpsys-alarm.txt for com.codex.streetstrength timer alarms.")
    $summary.Add("- Check after-dumpsys-services.txt for RestTimerService state.")
    $summary.Add("- Check after-dumpsys-notification.txt and after-dumpsys-vibrator.txt for alert/vibration state.")
    $summary.Add("- If failure reproduces, hand this folder to 1-training-timer.")
    $summary | Out-File -FilePath (Join-Path $outputDir "summary.md") -Encoding UTF8
}

Write-Step "Evidence output: $outputDir"
Save-AdbOutput -Path (Join-Path $outputDir "adb-get-state.txt") -Required -AdbArgs @("-s", $DeviceSerial, "get-state")
Capture-State -Phase "before"

Write-Step "Clearing logcat"
Save-AdbOutput -Path (Join-Path $outputDir "logcat-clear.txt") -Required -AdbArgs @("-s", $DeviceSerial, "logcat", "-c")

if ($CaptureSeconds -gt 0) {
    Write-Step "Waiting $CaptureSeconds seconds for manual reproduction"
    Start-Sleep -Seconds $CaptureSeconds
}

$logcatPath = Join-Path $outputDir "logcat.txt"
Write-Step "Dumping logcat"
Save-AdbOutput -Path $logcatPath -AdbArgs @("-s", $DeviceSerial, "logcat", "-d", "-v", "time")
Capture-State -Phase "after"
Write-Summary -LogcatPath $logcatPath
Write-Step "Evidence capture completed: $outputDir"
