param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @("assembleDebug")
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..\\..")).Path
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$javaHome = Join-Path $repoRoot ".local-tools\\jdk\\current"
$sdkRoot = Join-Path $repoRoot ".local-tools\\android-sdk"
$androidUserHome = Join-Path $repoRoot ".local-tools\\android-user"
$localAppData = Join-Path $repoRoot ".local-tools\\appdata-local"
$roamingAppData = Join-Path $repoRoot ".local-tools\\appdata-roaming"
$gradleUserHome = Join-Path $repoRoot ".gradle-local"
$gradleWrapper = Join-Path $projectRoot "gradlew.bat"

foreach ($requiredPath in @($javaHome, $sdkRoot, $gradleWrapper)) {
    if (-not (Test-Path $requiredPath)) {
        throw "Missing required path: $requiredPath"
    }
}

$null = New-Item -ItemType Directory -Force -Path $gradleUserHome
$null = New-Item -ItemType Directory -Force -Path $androidUserHome
$null = New-Item -ItemType Directory -Force -Path (Join-Path $androidUserHome ".android")
$null = New-Item -ItemType Directory -Force -Path $localAppData
$null = New-Item -ItemType Directory -Force -Path $roamingAppData

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = $androidUserHome
$env:GRADLE_USER_HOME = $gradleUserHome
$env:HOME = $androidUserHome
$env:LOCALAPPDATA = $localAppData
$env:APPDATA = $roamingAppData
$tlsOptions = "-Djavax.net.ssl.trustStoreType=Windows-ROOT -Dhttps.protocols=TLSv1.2,TLSv1.3"
$userHomeOption = "-Duser.home=$androidUserHome"
if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
    $env:JAVA_TOOL_OPTIONS = "$userHomeOption $tlsOptions"
} elseif ($env:JAVA_TOOL_OPTIONS -notmatch "trustStoreType=Windows-ROOT") {
    $env:JAVA_TOOL_OPTIONS = "$tlsOptions $env:JAVA_TOOL_OPTIONS"
}
if ($env:JAVA_TOOL_OPTIONS -notmatch [regex]::Escape($userHomeOption)) {
    $env:JAVA_TOOL_OPTIONS = "$userHomeOption $env:JAVA_TOOL_OPTIONS"
}
$env:PATH = "$javaHome\\bin;$sdkRoot\\platform-tools;$env:PATH"

Push-Location $projectRoot
try {
    & $gradleWrapper @GradleArgs
    $gradleExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $gradleExitCode
