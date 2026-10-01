<#
  One-shot dev setup for building the KlipPocket APK on Windows (PowerShell 5+).

    .\scripts\setup.ps1            # install SDK/NDK/CMake + Python, write local.properties
    .\scripts\setup.ps1 -Build    # ... then build the arm64 debug APK

  Steps:
    1. checks for a JDK (17+)
    2. bootstraps the Android command-line tools if no SDK is found
    3. installs the exact SDK packages this project pins
    4. makes sure a Python 3.10 interpreter exists (Chaquopy buildPython)
    5. writes local.properties (sdk.dir + chaquopy.python)

  Linux / macOS: use scripts/setup.sh instead.
#>
param([switch]$Build)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path
Set-Location $RepoRoot

# Pinned versions — keep in sync with app/build.gradle
$NdkVersion    = '23.2.8568313'
$CmakeVersion  = '3.22.1'
$BuildTools    = '35.0.0'
$Platform      = 'android-35'
$PyVersion     = '3.10.11'      # last python.org Windows installer for 3.10
$CmdlineTools  = '11076708'

function Info($m){ Write-Host "==> $m" -ForegroundColor Cyan }
function Warn($m){ Write-Host "!! $m"  -ForegroundColor Yellow }
function Die($m){ Write-Host "ERROR: $m" -ForegroundColor Red; exit 1 }

# --- 1. JDK ---------------------------------------------------------
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { Die "no 'java' on PATH. Install JDK 21 (winget install EclipseAdoptium.Temurin.21.JDK)." }
$jv = (& java -version 2>&1)[0]
if ($jv -match 'version "(\d+)') { $major = [int]$Matches[1] } else { $major = 0 }
if ($major -lt 17) { Die "found Java $major; need JDK 17+ (21 recommended). winget install EclipseAdoptium.Temurin.21.JDK" }
Info "Java $major OK"

# --- 2. Android SDK ----------------------------------------------
$SdkDir = $env:ANDROID_HOME
if (-not $SdkDir) { $SdkDir = $env:ANDROID_SDK_ROOT }
if (-not $SdkDir -and (Test-Path local.properties)) {
    $line = (Get-Content local.properties | Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1)
    if ($line) { $SdkDir = $line.Substring(8) }
}
if (-not $SdkDir) { $SdkDir = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$SdkManager = @(
    "$SdkDir\cmdline-tools\latest\bin\sdkmanager.bat",
    "$SdkDir\cmdline-tools\bin\sdkmanager.bat",
    "$SdkDir\tools\bin\sdkmanager.bat"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $SdkManager) {
    Info "No Android SDK found — bootstrapping command-line tools into $SdkDir"
    New-Item -ItemType Directory -Force -Path "$SdkDir\cmdline-tools" | Out-Null
    $url = "https://dl.google.com/android/repository/commandlinetools-win-${CmdlineTools}_latest.zip"
    $tmp = Join-Path $env:TEMP "clt-$([guid]::NewGuid()).zip"
    Invoke-WebRequest -Uri $url -OutFile $tmp
    $ex = Join-Path $env:TEMP "clt-$([guid]::NewGuid())"
    Expand-Archive -Path $tmp -DestinationPath $ex -Force
    if (Test-Path "$SdkDir\cmdline-tools\latest") { Remove-Item -Recurse -Force "$SdkDir\cmdline-tools\latest" }
    Move-Item "$ex\cmdline-tools" "$SdkDir\cmdline-tools\latest"
    Remove-Item -Force $tmp; Remove-Item -Recurse -Force $ex
    $SdkManager = "$SdkDir\cmdline-tools\latest\bin\sdkmanager.bat"
}
Info "SDK dir: $SdkDir"
$env:ANDROID_HOME = $SdkDir; $env:ANDROID_SDK_ROOT = $SdkDir

Info "Accepting SDK licenses"
'y' * 100 -split '' | Out-String | & $SdkManager --licenses | Out-Null

Info "Installing SDK packages (platform-tools, $Platform, build-tools $BuildTools, ndk $NdkVersion, cmake $CmakeVersion)"
& $SdkManager --install "platform-tools" "platforms;$Platform" "build-tools;$BuildTools" "ndk;$NdkVersion" "cmake;$CmakeVersion"
if ($LASTEXITCODE -ne 0) { Die "sdkmanager failed" }

# --- 3. Python 3.10 for Chaquopy -------------------------------
function Find-Py310 {
    foreach ($cmd in @('py -3.10', 'python3.10', 'python')) {
        $parts = $cmd -split ' '
        $exe = Get-Command $parts[0] -ErrorAction SilentlyContinue
        if (-not $exe) { continue }
        $v = & $parts[0] $parts[1..($parts.Length-1)] -c "import sys;print('%d.%d'%sys.version_info[:2])" 2>$null
        if ($v -eq '3.10') { return ($exe.Source) }
    }
    return $null
}
$Py = Find-Py310
if (-not $Py) {
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Info "Installing Python 3.10 via winget"
        winget install --silent --accept-package-agreements --accept-source-agreements Python.Python.3.10
        $Py = Find-Py310
    }
}
if (-not $Py) { Die "no Python 3.10 found. Install from https://www.python.org/downloads/release/python-31011/ or 'winget install Python.Python.3.10', then re-run." }
Info "Python 3.10: $Py"

# --- 4. local.properties (Gradle wants forward slashes) -------
$SdkFwd = $SdkDir -replace '\\','/'
$PyFwd  = $Py -replace '\\','/'
"sdk.dir=$SdkFwd`nchaquopy.python=$PyFwd`n" | Set-Content -NoNewline -Path local.properties -Encoding ascii
Info "Wrote local.properties"

# --- 5. build (optional) -------------------------------------
if ($Build) {
    Info "Building arm64 debug APK"
    & .\gradlew.bat :app:assembleArm64Debug
    Info "APK: app\build\outputs\apk\arm64\debug\"
} else {
    Write-Host ""
    Info "Setup done. Build with:  .\gradlew.bat :app:assembleArm64Debug"
}
