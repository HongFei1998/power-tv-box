# setup-env.ps1 - install FongMi TV build environment (Windows x64)
# Usage: powershell -ExecutionPolicy Bypass -File .\setup-env.ps1
#        powershell -ExecutionPolicy Bypass -File .\setup-env.ps1 -Root D:\ai\env -Proxy http://127.0.0.1:7890

[CmdletBinding()]
param(
    [string]$Root  = "D:\ai\env",
    [string]$Proxy = ""
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$JdkDir    = Join-Path $Root "jdk21"
$SdkDir    = Join-Path $Root "android-sdk"
$GradleTv  = Join-Path $Root "gradle-9.7.1"
$GradleM3  = Join-Path $Root "gradle-9.1.0"
$Python310 = Join-Path $Root "Python310"
$Tmp       = Join-Path $Root "_dl"

function Info($m) { Write-Host "[env] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[ ok] $m" -ForegroundColor Green }

function Curl($url, $out) {
    if ($Proxy) {
        & curl.exe -sS -L -x $Proxy --retry 5 --retry-delay 2 -o $out $url
    } else {
        & curl.exe -sS -L --retry 5 --retry-delay 2 -o $out $url
    }
    if ($LASTEXITCODE -ne 0) { throw ("download failed: " + $url) }
}

New-Item -ItemType Directory -Force -Path $Root, $Tmp | Out-Null

# ---- 1) JDK 21 -------------------------------------------------------------
if (Test-Path (Join-Path $JdkDir "bin\java.exe")) {
    Ok ("JDK21 exists: " + $JdkDir)
} else {
    Info "downloading Temurin JDK 21"
    $meta = Invoke-RestMethod "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse"
    $pkg  = $meta[0].binary.package
    $zip  = Join-Path $Tmp "jdk21.zip"
    Curl $pkg.link $zip
    $ex = Join-Path $Tmp "jdk21x"
    if (Test-Path $ex) { Remove-Item $ex -Recurse -Force }
    Expand-Archive $zip -DestinationPath $ex -Force
    if (Test-Path $JdkDir) { Remove-Item $JdkDir -Recurse -Force }
    Move-Item (Get-ChildItem $ex -Directory | Select-Object -First 1).FullName $JdkDir
    Ok ("JDK21 -> " + $JdkDir)
}
$env:JAVA_HOME = $JdkDir
& (Join-Path $JdkDir "bin\java.exe") -version 2>&1 | Select-Object -First 1 | ForEach-Object { Ok $_ }

# ---- 2) Android SDK --------------------------------------------------------
$SdkManager = Join-Path $SdkDir "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $SdkManager)) {
    Info "downloading Android cmdline-tools"
    $zip = Join-Path $Tmp "cmdline-tools.zip"
    Curl "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip" $zip
    $ex = Join-Path $Tmp "ct"
    if (Test-Path $ex) { Remove-Item $ex -Recurse -Force }
    Expand-Archive $zip -DestinationPath $ex -Force
    New-Item -ItemType Directory -Force (Join-Path $SdkDir "cmdline-tools") | Out-Null
    $target = Join-Path $SdkDir "cmdline-tools\latest"
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    Move-Item (Join-Path $ex "cmdline-tools") $target
    Ok ("cmdline-tools -> " + $target)
} else {
    Ok "cmdline-tools exists"
}

$env:ANDROID_HOME     = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir

Info "accepting SDK licenses"
$yes = ("y" + [Environment]::NewLine) * 40
$yes | & $SdkManager --licenses --sdk_root=$SdkDir 2>&1 | Select-Object -Last 1

Info "installing SDK packages (~3 GB, be patient)"
& $SdkManager --sdk_root=$SdkDir "platform-tools" "platforms;android-36" "platforms;android-37.0" `
    "build-tools;36.0.0" "build-tools;37.0.0" "cmake;3.22.1" "ndk;28.2.13676358" 2>&1 |
    Select-String -Pattern "100%|Installing|Unzipping" | Select-Object -Last 8
Ok "SDK packages installed"

# ---- 3) Gradle -------------------------------------------------------------
function GetGradle($ver, $target) {
    if (Test-Path (Join-Path $target "bin\gradle.bat")) { Ok ("Gradle " + $ver + " exists"); return }
    Info ("downloading Gradle " + $ver + " from tencent mirror")
    $zip = Join-Path $Tmp ("gradle-" + $ver + "-bin.zip")
    Curl ("https://mirrors.cloud.tencent.com/gradle/gradle-" + $ver + "-bin.zip") $zip
    Expand-Archive $zip -DestinationPath $Root -Force
    Ok ("Gradle " + $ver + " -> " + $target)
}
GetGradle "9.7.1" $GradleTv
GetGradle "9.1.0" $GradleM3

# ---- 4) Python 3.10 for Chaquopy ------------------------------------------
if (Test-Path (Join-Path $Python310 "python.exe")) {
    Ok "Python 3.10 exists"
} else {
    Info "downloading CPython 3.10.11 (nuget package)"
    $zip = Join-Path $Tmp "python310.zip"
    Curl "https://www.nuget.org/api/v2/package/python/3.10.11" $zip
    $ex = Join-Path $Tmp "py310x"
    if (Test-Path $ex) { Remove-Item $ex -Recurse -Force }
    Expand-Archive $zip -DestinationPath $ex -Force
    if (Test-Path $Python310) { Remove-Item $Python310 -Recurse -Force }
    Move-Item (Join-Path $ex "tools") $Python310
    Ok ("Python -> " + $Python310)
}
& (Join-Path $Python310 "python.exe") -V 2>&1 | ForEach-Object { Ok $_ }

# ---- 5) Gradle mirror config ----------------------------------------------
$gradleHome = Join-Path $env:USERPROFILE ".gradle"
New-Item -ItemType Directory -Force $gradleHome | Out-Null
$initSrc = Join-Path $PSScriptRoot "init.gradle"
$initDst = Join-Path $gradleHome "init.gradle"
if (Test-Path $initSrc) {
    # must be UTF-8 without BOM, otherwise Gradle fails with "Unexpected character '\ufeff'"
    $text = [System.IO.File]::ReadAllText($initSrc)
    [System.IO.File]::WriteAllText($initDst, $text, (New-Object System.Text.UTF8Encoding($false)))
    Ok ("init.gradle -> " + $initDst)
}

# ---- summary ---------------------------------------------------------------
Write-Host ""
Write-Host "================ env ready ================" -ForegroundColor Green
Write-Host ("JAVA_HOME         = " + $JdkDir)
Write-Host ("ANDROID_HOME      = " + $SdkDir)
Write-Host ("GRADLE (TV)       = " + $GradleTv)
Write-Host ("GRADLE (media3)   = " + $GradleM3)
Write-Host ("PYTHON (chaquopy) = " + $Python310)
Write-Host ""
Write-Host "next: powershell -ExecutionPolicy Bypass -File .\rebuild.ps1" -ForegroundColor Yellow