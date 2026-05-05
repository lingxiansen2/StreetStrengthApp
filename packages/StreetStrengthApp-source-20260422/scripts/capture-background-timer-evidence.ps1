param(
    [string]$DeviceSerial,
    [switch]$StartEmulator,
    [switch]$KeepEmulator,
    [switch]$WindowedEmulator,
    [switch]$RestartEmulator,
    [switch]$RunReceiverInstrumentation,
    [switch]$RunUiFlowInstrumentation,
    [switch]$InstallDebugApks,
    [switch]$AllowPhysicalDevice,
    [switch]$SkipBuild,
    [int]$CaptureSeconds = 0,
    [int]$EmulatorPort = 5584,
    [int]$EmulatorBootTimeoutSec = 240,
    [string]$AvdName = "StreetStrengthApi34",
    [string]$OutputRoot,
    [string]$Scenario = "background-timer-crash"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradleScript = Join-Path $PSScriptRoot "gradlew-local.ps1"
$emulatorStartScript = Join-Path $PSScriptRoot "start-emulator-local.ps1"
$adb = Join-Path $repoRoot ".local-tools\android-sdk\platform-tools\adb.exe"
$powershellExe = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
$packageName = "com.codex.streetstrength"
$testRunner = "com.codex.streetstrength.test/androidx.test.runner.AndroidJUnitRunner"
$receiverInstrumentationClass = "com.codex.streetstrength.timer.RestTimerReceiverInstrumentedTest"
$uiFlowInstrumentationClass = "com.codex.streetstrength.timer.BackgroundRestTimerUiFlowInstrumentedTest"

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot "work\thread-results\7-quality-env"
}

foreach ($requiredPath in @($adb, $gradleScript)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Missing required path: $requiredPath"
    }
}

$safeScenario = $Scenario -replace "[^A-Za-z0-9_.-]", "-"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path $OutputRoot "20260502-$safeScenario-$timestamp"
$null = New-Item -ItemType Directory -Force -Path $outputDir

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Invoke-Adb {
    param([string[]]$AdbArgs)

    & $adb @AdbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code $LASTEXITCODE for: $($AdbArgs -join ' ')"
    }
}

function Save-CommandOutput {
    param(
        [string]$Path,
        [scriptblock]$Command,
        [switch]$Required
    )

    $result = & $Command 2>&1
    $exitCode = $LASTEXITCODE
    $result | Out-File -FilePath $Path -Encoding UTF8
    if ($Required -and $exitCode -ne 0) {
        throw "Command failed with exit code $exitCode. Output: $Path"
    }
}

function Save-AdbOutput {
    param(
        [string]$Path,
        [string[]]$AdbArgs,
        [switch]$Required
    )

    Save-CommandOutput -Path $Path -Required:$Required -Command {
        & $adb @AdbArgs
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

function Test-EmulatorSerial {
    param([string]$Serial)

    return $Serial -like "emulator-*"
}

function Wait-ForEmulatorBoot {
    param(
        [string]$Serial,
        [int]$TimeoutSec
    )

    Write-Step "Waiting for $Serial"
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $state = Get-AdbDeviceState -Serial $Serial
        if ($state -eq "device") {
            $bootCompleted = (& $adb "-s" $Serial "shell" "getprop" "sys.boot_completed" | Out-String).Trim()
            if ($LASTEXITCODE -eq 0 -and $bootCompleted -eq "1") {
                Write-Step "$Serial boot_completed=1"
                return
            }
            Write-Step "$Serial state=device boot_completed=$bootCompleted"
        } elseif ([string]::IsNullOrWhiteSpace($state)) {
            Write-Step "$Serial not listed yet"
        } else {
            Write-Step "$Serial state=$state"
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for $Serial to boot."
}

function Stop-StartedEmulator {
    param([string]$Serial)

    if ([string]::IsNullOrWhiteSpace($Serial)) {
        return
    }
    if ((Get-AdbDeviceState -Serial $Serial) -eq "device") {
        Write-Step "Stopping $Serial"
        & $adb "-s" $Serial "emu" "kill" | Out-Null
        for ($i = 0; $i -lt 30; $i++) {
            if ([string]::IsNullOrWhiteSpace((Get-AdbDeviceState -Serial $Serial))) {
                Write-Step "$Serial removed"
                return
            }
            Start-Sleep -Seconds 2
        }
    }
}

function Start-LocalEmulator {
    param(
        [string]$Name,
        [int]$Port
    )

    $serial = "emulator-$Port"
    $existingState = Get-AdbDeviceState -Serial $serial
    if ($RestartEmulator -and -not [string]::IsNullOrWhiteSpace($existingState)) {
        Write-Step "Restart requested; stopping existing $serial before launch"
        & $adb "-s" $serial "emu" "kill" | Out-Null
        for ($i = 0; $i -lt 30; $i++) {
            if ([string]::IsNullOrWhiteSpace((Get-AdbDeviceState -Serial $serial))) {
                $existingState = $null
                break
            }
            Start-Sleep -Seconds 2
        }
    }
    if ($existingState -eq "device") {
        Write-Step "Reusing already connected $serial"
        if ($WindowedEmulator) {
            Write-Step "Existing emulator window mode cannot be changed while reusing it; pass -RestartEmulator to force a visible relaunch."
        }
        Wait-ForEmulatorBoot -Serial $serial -TimeoutSec $EmulatorBootTimeoutSec
        return $serial
    }
    if (-not [string]::IsNullOrWhiteSpace($existingState)) {
        Write-Step "Existing $serial is $existingState; requesting shutdown before restart"
        & $adb "-s" $serial "emu" "kill" | Out-Null
        Start-Sleep -Seconds 10
    }

    if (-not (Test-Path $emulatorStartScript)) {
        throw "Missing emulator start script: $emulatorStartScript"
    }

    Write-Step "Starting AVD $Name on $serial"
    $startArgs = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $emulatorStartScript,
        "-AvdName", $Name,
        "-EmulatorPort", $Port
    )
    if ($WindowedEmulator) {
        $startArgs += "-WindowedEmulator"
    }
    if ($RestartEmulator) {
        $startArgs += "-RestartEmulator"
    }
    $startOutput = & $powershellExe @startArgs 2>&1
    $startExitCode = $LASTEXITCODE
    $startOutput | Out-File -FilePath (Join-Path $outputDir "emulator-start.txt") -Encoding UTF8
    $startOutput | ForEach-Object { Write-Host $_ }
    if ($startExitCode -ne 0) {
        throw "Failed to start emulator helper with exit code $startExitCode."
    }

    Wait-ForEmulatorBoot -Serial $serial -TimeoutSec $EmulatorBootTimeoutSec
    return $serial
}

function Invoke-Gradle {
    param([string[]]$GradleArgs)

    Write-Step "Gradle $($GradleArgs -join ' ')"
    $logName = "gradle-" + (($GradleArgs -join "-") -replace "[^A-Za-z0-9_.-]", "-") + ".txt"
    $logPath = Join-Path $outputDir $logName
    $stdoutPath = "$logPath.stdout"
    $stderrPath = "$logPath.stderr"
    $processArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $gradleScript) + $GradleArgs
    $process = Start-Process `
        -FilePath $powershellExe `
        -ArgumentList $processArgs `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -Wait `
        -PassThru
    $exitCode = $process.ExitCode
    $stdout = if (Test-Path $stdoutPath) { Get-Content -Encoding UTF8 $stdoutPath } else { @() }
    $stderr = if (Test-Path $stderrPath) { Get-Content -Encoding UTF8 $stderrPath } else { @() }
    @($stdout + $stderr) | Tee-Object -FilePath $logPath
    if ($exitCode -ne 0) {
        throw "Gradle failed with exit code $exitCode. Output: $logPath"
    }
}

function Install-DebugApks {
    param([string]$Serial)

    if (-not $SkipBuild) {
        Invoke-Gradle -GradleArgs @("--max-workers=1", "assembleDebug")
        Invoke-Gradle -GradleArgs @("--max-workers=1", "assembleDebugAndroidTest")
    }

    $debugApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    $testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    foreach ($apk in @($debugApk, $testApk)) {
        if (-not (Test-Path $apk)) {
            throw "Missing APK: $apk"
        }
    }

    Write-Step "Installing debug APK on $Serial"
    Save-AdbOutput -Path (Join-Path $outputDir "adb-install-debug.txt") -Required -AdbArgs @("-s", $Serial, "install", "-r", $debugApk)
    Write-Step "Installing androidTest APK on $Serial"
    Save-AdbOutput -Path (Join-Path $outputDir "adb-install-androidTest.txt") -Required -AdbArgs @("-s", $Serial, "install", "-r", $testApk)
    Save-AdbOutput -Path (Join-Path $outputDir "pm-grant-post-notifications.txt") -AdbArgs @("-s", $Serial, "shell", "pm", "grant", $packageName, "android.permission.POST_NOTIFICATIONS")
}

function Capture-SystemState {
    param(
        [string]$Serial,
        [string]$Phase
    )

    Write-Step "Capturing system state: $Phase"
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-adb-devices.txt") -Required -AdbArgs @("devices", "-l")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-getprop.txt") -AdbArgs @("-s", $Serial, "shell", "getprop")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-alarm.txt") -AdbArgs @("-s", $Serial, "shell", "dumpsys", "alarm")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-deviceidle.txt") -AdbArgs @("-s", $Serial, "shell", "dumpsys", "deviceidle")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-package.txt") -AdbArgs @("-s", $Serial, "shell", "dumpsys", "package", $packageName)
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-appops.txt") -AdbArgs @("-s", $Serial, "shell", "cmd", "appops", "get", $packageName)
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-power.txt") -AdbArgs @("-s", $Serial, "shell", "dumpsys", "power")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-battery.txt") -AdbArgs @("-s", $Serial, "shell", "dumpsys", "battery")
    Save-AdbOutput -Path (Join-Path $outputDir "$Phase-dumpsys-notification.txt") -AdbArgs @("-s", $Serial, "shell", "dumpsys", "notification", "--noredact")
}

function Run-InstrumentationClass {
    param(
        [string]$Serial,
        [string]$ClassName,
        [string]$OutputName
    )

    Write-Step "Running instrumentation $ClassName on $Serial"
    $instrumentArgs = @(
        "-s", $Serial,
        "shell", "am", "instrument", "-w",
        "-e", "class", $ClassName,
        $testRunner
    )
    Save-AdbOutput -Path (Join-Path $outputDir $OutputName) -Required -AdbArgs $instrumentArgs
}

function Write-Analysis {
    param(
        [string]$LogcatPath,
        [string]$DeviceKind
    )

    $logText = ""
    if (Test-Path $LogcatPath) {
        $logText = Get-Content -Raw -Encoding UTF8 $LogcatPath
    }

    $patterns = @(
        "RestTimerReceiver",
        "ForegroundServiceStartNotAllowedException",
        "SecurityException",
        "SQLiteException",
        "IllegalStateException",
        "IndexOutOfBoundsException",
        "NullPointerException",
        "FATAL EXCEPTION"
    )
    $summary = New-Object System.Collections.Generic.List[string]
    $summary.Add("# Background Timer Evidence Summary")
    $summary.Add("")
    $summary.Add("- Output directory: $outputDir")
    $summary.Add("- Device serial: $DeviceSerial")
    $summary.Add("- Device kind: $DeviceKind")
    $summary.Add("- Scenario: $Scenario")
    $summary.Add("- Physical device explicitly allowed: $AllowPhysicalDevice")
    $summary.Add("- Receiver instrumentation: $RunReceiverInstrumentation")
    $summary.Add("- UI flow instrumentation: $RunUiFlowInstrumentation")
    $summary.Add("- Windowed emulator requested: $WindowedEmulator")
    $summary.Add("- Emulator restart requested: $RestartEmulator")
    $summary.Add("- Capture seconds: $CaptureSeconds")
    $summary.Add("")
    $summary.Add("## Global Logcat Pattern Check")
    foreach ($pattern in $patterns) {
        $present = $logText -match [regex]::Escape($pattern)
        $summary.Add("- ${pattern}: $present")
    }

    $firstAppStack = $null
    $firstProductionAppStack = $null
    if (Test-Path $LogcatPath) {
        $firstAppStack = Select-String -Path $LogcatPath -Pattern "at com\.codex\.streetstrength" | Select-Object -First 1
        $firstProductionAppStack = Select-String -Path $LogcatPath -Pattern "at com\.codex\.streetstrength" |
            Where-Object {
                $_.Line -notmatch "BackgroundRestTimerUiFlowInstrumentedTest|RestTimerReceiverInstrumentedTest|com\.codex\.streetstrength\.test"
            } |
            Select-Object -First 1
    }
    $summary.Add("")
    $summary.Add("## App Crash Attribution")
    $summary.Add("- FATAL EXCEPTION present: $($logText -match 'FATAL EXCEPTION')")
    $summary.Add("- First app stack frame present: $($null -ne $firstAppStack)")
    if ($firstAppStack) {
        $summary.Add("- First app stack frame: $($firstAppStack.Line.Trim())")
    } else {
        $summary.Add("- First app stack frame: Not found in captured logcat.")
    }
    $summary.Add("- First production app stack frame present: $($null -ne $firstProductionAppStack)")
    if ($firstProductionAppStack) {
        $summary.Add("- First production app stack frame: $($firstProductionAppStack.Line.Trim())")
    } else {
        $summary.Add("- First production app stack frame: Not found in captured logcat.")
    }
    $summary.Add("")
    $summary.Add("## Required Follow-up")
    $summary.Add("- If this capture was not taken during a real click crash, hand this folder plus a crash-time logcat to thread 1 before business-code changes.")
    $summary.Add("- If this capture was taken on a physical device, hand it to thread 1 even when emulator evidence passes.")
    $summary.Add("- If a crash-time first app stack frame points to Room or repository code, involve thread 4.")
    $summary.Add("- If a crash-time first app stack frame points to task ordering or set index calculation, involve thread 2.")

    $summary | Out-File -FilePath (Join-Path $outputDir "summary.md") -Encoding UTF8
}

$startedEmulator = $false
try {
    Write-Step "Evidence output: $outputDir"

    if ($StartEmulator) {
        $DeviceSerial = Start-LocalEmulator -Name $AvdName -Port $EmulatorPort
        $startedEmulator = $true
    } elseif ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        Save-AdbOutput -Path (Join-Path $outputDir "adb-devices-before-failure.txt") -AdbArgs @("devices", "-l")
        throw "Specify -DeviceSerial emulator-xxxx or use -StartEmulator."
    }

    $isEmulator = Test-EmulatorSerial -Serial $DeviceSerial
    $deviceKind = if ($isEmulator) { "emulator" } else { "physical" }
    if (-not $isEmulator -and -not $AllowPhysicalDevice) {
        throw "Refusing to capture evidence from non-emulator device: $DeviceSerial. Re-run with -AllowPhysicalDevice only after the user explicitly approves physical-device capture."
    }
    if (-not $isEmulator -and ($InstallDebugApks -or $RunReceiverInstrumentation -or $RunUiFlowInstrumentation)) {
        throw "Refusing to install APKs or run instrumentation on physical device $DeviceSerial. Use physical mode only for log/dumpsys capture against an already installed app."
    }

    Invoke-Adb -AdbArgs @("-s", $DeviceSerial, "get-state")
    Capture-SystemState -Serial $DeviceSerial -Phase "before"

    if ($InstallDebugApks -or $RunReceiverInstrumentation -or $RunUiFlowInstrumentation) {
        Install-DebugApks -Serial $DeviceSerial
    }

    Write-Step "Clearing logcat"
    Invoke-Adb -AdbArgs @("-s", $DeviceSerial, "logcat", "-c")

    if ($RunReceiverInstrumentation) {
        Run-InstrumentationClass `
            -Serial $DeviceSerial `
            -ClassName $receiverInstrumentationClass `
            -OutputName "instrumentation-receiver.txt"
    }

    if ($RunUiFlowInstrumentation) {
        Run-InstrumentationClass `
            -Serial $DeviceSerial `
            -ClassName $uiFlowInstrumentationClass `
            -OutputName "instrumentation-ui-flow.txt"
    }

    if ($CaptureSeconds -gt 0) {
        Write-Step "Waiting $CaptureSeconds seconds for manual reproduction"
        Start-Sleep -Seconds $CaptureSeconds
    }

    $logcatPath = Join-Path $outputDir "20260502-background-timer-crash-logcat.txt"
    Write-Step "Dumping logcat"
    Save-AdbOutput -Path $logcatPath -AdbArgs @("-s", $DeviceSerial, "logcat", "-d", "-v", "time")
    Capture-SystemState -Serial $DeviceSerial -Phase "after"
    Write-Analysis -LogcatPath $logcatPath -DeviceKind $deviceKind
    Write-Step "Evidence capture completed: $outputDir"
} finally {
    if ($startedEmulator -and -not $KeepEmulator) {
        Stop-StartedEmulator -Serial $DeviceSerial
    }
}
