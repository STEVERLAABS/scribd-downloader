# builds the APK locally after bootstrapping the toolchain (JDK17 + Gradle + SDK).
# Run:  powershell -ExecutionPolicy Bypass -File tools\build-apk-local.ps1
# Re-running resumes interrupted downloads (curl -C -). Needs only curl.exe (built into Windows).
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot
$root = Join-Path $env:LOCALAPPDATA 'spd-toolchain'
$sdk  = Join-Path $root 'android-sdk'
New-Item -ItemType Directory -Force -Path $root, $sdk | Out-Null

function Get-Fetch([string]$url, [string]$out) {
    Write-Host ">> $url"
    & curl.exe -sS -L --fail --retry 12 --retry-delay 3 --retry-all-errors -C - -o $out $url
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $out)) { throw "Download failed: $url  (re-run the script to resume)" }
    Write-Host ("   OK  " + [math]::Round((Get-Item $out).Length / 1MB, 1) + " MB")
}

# 1. JDK 17 (Microsoft OpenJDK)
if (-not (Test-Path (Join-Path $root 'jdk'))) {
    $jdkZip = Join-Path $root 'jdk17.zip'
    Get-Fetch 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' $jdkZip
    Expand-Archive $jdkZip $root -Force
    $jdkDir = (Get-ChildItem $root -Directory | Where-Object Name -like 'jdk-17*' | Select-Object -First 1).FullName
    Rename-Item $jdkDir 'jdk'
    Remove-Item $jdkZip -Force
}

# 2. Gradle 8.11.1
if (-not (Test-Path (Join-Path $root 'gradle-8.11.1'))) {
    $gradleZip = Join-Path $root 'gradle.zip'
    Get-Fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' $gradleZip
    Expand-Archive $gradleZip $root -Force
    Remove-Item $gradleZip -Force
}

# 3. Android cmdline-tools
if (-not (Test-Path (Join-Path $sdk 'cmdline-tools\latest'))) {
    $cmZip = Join-Path $root 'cmdtools.zip'
    Get-Fetch 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' $cmZip
    Expand-Archive $cmZip $root -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $root 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
    Remove-Item $cmZip -Force
}

# env + licenses + SDK packages
$env:JAVA_HOME = Join-Path $root 'jdk'
$env:PATH      = "$env:JAVA_HOME\bin;$env:PATH"
$sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'

Write-Host '>> accepting Android SDK licenses'
1..30 | ForEach-Object { 'y' } | & $sdkmanager --licenses | Out-Null

Write-Host '>> installing platforms;android-35 + build-tools;35.0.0'
& $sdkmanager 'platforms;android-35' 'build-tools;35.0.0' | Out-Null

# local.properties for AGP
$sdkDirFwd = $sdk -replace '\\', '/'
Set-Content -Path (Join-Path $proj 'local.properties') -Value "sdk.dir=$sdkDirFwd" -Encoding ascii

# build
$gradle = Join-Path $root 'gradle-8.11.1\bin\gradle.bat'
Push-Location $proj
try {
    Write-Host '== testDebugUnitTest =='
    & $gradle testDebugUnitTest --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed.' }
    Write-Host '== assembleDebug =='
    & $gradle assembleDebug --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'APK assembly failed.' }
} finally { Pop-Location }

$apkSrc = Join-Path $proj 'app\build\outputs\apk\debug\app-debug.apk'
$apkDst = Join-Path $env:USERPROFILE 'Desktop\ScribdDownloader-steVeR-debug.apk'
Copy-Item $apkSrc $apkDst -Force
Write-Host ''
Write-Host ("APK ready: " + $apkDst)
