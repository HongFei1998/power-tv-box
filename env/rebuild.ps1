# rebuild.ps1 - rebuild FongMi TV APK (run inside env/ of the kit)
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\rebuild.ps1
#   powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -Variants all
#   powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -SkipAar
#   powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -Abi Armeabi_v7a
#   powershell -ExecutionPolicy Bypass -File .\rebuild.ps1 -Root D:\ai\env

[CmdletBinding()]
param(
    [ValidateSet("release","all","debug")] [string]$Variants = "release",
    [ValidateSet("Arm64_v8a","Armeabi_v7a")] [string]$Abi = "Arm64_v8a",
    [switch]$SkipAar,
    [string]$Root = "D:\ai\env"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Kit     = Split-Path $PSScriptRoot -Parent
$TvDir   = Join-Path $Kit "src\TV"
$M3Dir   = Join-Path $Kit "src\media"
$EnvDir  = Join-Path $Kit "env"
$ApkOut  = Join-Path $Kit "apk-rebuilt"

$Jdk      = Join-Path $Root "jdk21"
$Sdk      = Join-Path $Root "android-sdk"
$GradleTv = Join-Path $Root "gradle-9.7.1\bin\gradle.bat"
$GradleM3 = Join-Path $Root "gradle-9.1.0\bin\gradle.bat"
$Python   = Join-Path $Root "Python310\python.exe"

function Info($m) { Write-Host "[build] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[  ok ] $m" -ForegroundColor Green }

# ---- preflight -------------------------------------------------------------
foreach ($p in @($Jdk, $Sdk, $GradleTv, $GradleM3, $Python, $TvDir, $M3Dir)) {
    if (-not (Test-Path $p)) { throw "missing path: $p (run setup-env.ps1 first, or pass -Root)" }
}

$env:JAVA_HOME        = $Jdk
$env:ANDROID_HOME     = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:PATH             = (Split-Path $Python) + ";" + (Split-Path $Python) + "\Scripts;" + $env:PATH

# ---- local.properties + keystore -------------------------------------------
$lp = Join-Path $TvDir "local.properties"
$sdkEsc = $Sdk.Replace("\", "\\")
$lines = @(
    "sdk.dir=$sdkEsc",
    "storeFile=../fongmi.jks",
    "keyAlias=fongmi",
    "storePassword=fongmi123"
)
if ((-not (Test-Path $lp)) -or ((Get-Content $lp -Raw) -notmatch [regex]::Escape($sdkEsc))) {
    Set-Content -Path $lp -Value ($lines -join "`n") -Encoding ASCII -NoNewline
    Info "wrote local.properties"
}
Copy-Item (Join-Path $EnvDir "fongmi.jks") (Join-Path $TvDir "fongmi.jks") -Force

# ---- 1) media3 aars --------------------------------------------------------
if ($SkipAar) {
    Info "skip media3 aar, use existing files in src\TV\app\libs"
} else {
    Info "building media3 aars with Gradle 9.1.0 (~10 min)"
    $mods = @(
        "common","container","database","datasource","datasource-okhttp","datasource-rtmp",
        "decoder","decoder-ffmpeg","decoder-mpegh","effect","exoplayer","exoplayer-dash",
        "exoplayer-hls","exoplayer-rtsp","exoplayer-smoothstreaming","extractor",
        "mpvplayer","session","ui","ui-danmaku"
    ) | ForEach-Object { ":lib-" + $_ + ":assembleRelease" }

    Push-Location $M3Dir
    try {
        & $GradleM3 --console=plain @mods
        if ($LASTEXITCODE -ne 0) { throw "media3 aar build failed" }
    } finally { Pop-Location }

    Info "copying aars to src\TV\app\libs"
    Get-ChildItem (Join-Path $M3Dir "libraries") -Recurse -File -Filter "lib-*-release.aar" |
        Where-Object { $_.FullName -match "\\buildout\\outputs\\aar\\" } |
        ForEach-Object { Copy-Item $_.FullName (Join-Path $TvDir "app\libs") -Force }
    Ok "aars refreshed"
}

# ---- 2) TV apk -------------------------------------------------------------
$tasks = @()
switch ($Variants) {
    "release" { $tasks = @(":app:assembleLeanback" + $Abi + "Release", ":app:assembleMobile" + $Abi + "Release") }
    "debug"   { $tasks = @(":app:assembleLeanback" + $Abi + "Debug",   ":app:assembleMobile" + $Abi + "Debug") }
    "all"     { $tasks = @(
        ":app:assembleLeanback" + $Abi + "Release", ":app:assembleMobile" + $Abi + "Release",
        ":app:assembleLeanback" + $Abi + "Debug",   ":app:assembleMobile" + $Abi + "Debug") }
}

Info ("building TV with Gradle 9.7.1: " + ($tasks -join " "))
Push-Location $TvDir
try {
    & $GradleTv --console=plain ("-Pchaquopy.buildPython=" + $Python) @tasks
    if ($LASTEXITCODE -ne 0) { throw "TV build failed" }
} finally { Pop-Location }

# ---- 3) collect apks -------------------------------------------------------
New-Item -ItemType Directory -Force $ApkOut | Out-Null
Get-ChildItem (Join-Path $TvDir "app\build\outputs\apk") -Recurse -File -Filter "*.apk" |
    ForEach-Object {
        Copy-Item $_.FullName (Join-Path $ApkOut $_.Name) -Force
        Ok $_.Name
    }

Write-Host ""
Write-Host "================ done ================" -ForegroundColor Green
Write-Host ("APK dir: " + $ApkOut)
Write-Host ("install: adb install -r ""<apk file>""")