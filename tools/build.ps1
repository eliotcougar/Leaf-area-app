[CmdletBinding()]
param(
    [string[]]$Tasks = @(':app:assembleDebug'),
    [string]$ProjectPath = (Split-Path -Parent $PSScriptRoot),
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$AndroidSdk = 'C:\Users\eliot\AppData\Local\Android\Sdk',
    [string]$GradleUserHome = 'E:\programming\v2rayng\.gradle-user-home',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$resolvedProject = (Resolve-Path -LiteralPath $ProjectPath).Path
$wrapper = Join-Path $workspacePath 'gradlew.bat'
foreach ($required in @($wrapper, "$JavaHome\bin\java.exe", "$AndroidSdk\platform-tools\adb.exe")) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing required tool: $required" }
}
if (-not ((Test-Path -LiteralPath "$resolvedProject\settings.gradle.kts") -or
          (Test-Path -LiteralPath "$resolvedProject\settings.gradle"))) {
    throw 'No Android application has been scaffolded here yet. Use tools/smoke-build.ps1 to verify the toolchain.'
}
if (Test-Path -LiteralPath "$resolvedProject\local.properties") {
    Write-Host 'local.properties exists; its sdk.dir overrides the SDK environment. Check it if SDK resolution fails.'
}
if (-not (Test-Path -LiteralPath $GradleUserHome -PathType Container)) {
    throw "Gradle cache not found: $GradleUserHome. Pass -GradleUserHome to select another cache."
}

# Separate Gradle project state even when this launcher builds a fixture or another checkout.
$sha = [System.Security.Cryptography.SHA256]::Create()
try { $cacheKey = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($resolvedProject.ToLowerInvariant())))).Replace('-', '').Substring(0, 16) }
finally { $sha.Dispose() }
$projectCache = Join-Path $workspacePath ".gradle-project-cache\$cacheKey"
$androidUserHome = Join-Path $workspacePath '.android-user-home'
$logDir = Join-Path $workspacePath '.build-logs'
foreach ($directory in @($projectCache, $androidUserHome, $logDir)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$logPath = Join-Path $logDir ("gradle-{0}-{1}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $PID)
$environment = @{
    JAVA_HOME = $JavaHome
    ANDROID_HOME = $AndroidSdk
    ANDROID_SDK_ROOT = $AndroidSdk
    ANDROID_USER_HOME = $androidUserHome
    GRADLE_USER_HOME = $GradleUserHome
    PATH = "$JavaHome\bin;$AndroidSdk\platform-tools;$env:PATH"
}
$previous = @{}
foreach ($name in $environment.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $environment[$name], 'Process')
}
try {
    # Typed splatting prevents empty optional arguments from becoming a stray Gradle task.
    [string[]]$gradleArgs = @('--no-daemon', '--console=plain', '--project-dir', $resolvedProject,
        '--project-cache-dir', $projectCache)
    if ($Offline) { $gradleArgs += '--offline' }
    $gradleArgs += $Tasks
    Write-Host "Project: $resolvedProject"
    Write-Host "Log: $logPath"
    & $wrapper @gradleArgs 2>&1 | Tee-Object -FilePath $logPath
    $gradleExit = $LASTEXITCODE
    if ($gradleExit -ne 0) { throw "Gradle failed ($gradleExit). See $logPath" }
}
finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
