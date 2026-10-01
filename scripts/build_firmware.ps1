<#
  MCU-firmware builder for Windows.

    .\scripts\build_firmware.ps1 <board> [-KlipperTag v0.13.0]

  Native Klipper firmware builds need a Unix toolchain (make + gcc-arm-none-eabi),
  so on Windows this wraps the Docker image (firmware/docker-compose.yml), which
  produces byte-identical output to the Linux/macOS script.

  Requires Docker Desktop. Output lands in firmware\output\.

  If you prefer not to use Docker: run scripts/build_firmware.sh inside WSL or
  Git Bash (the toolchain download there uses the Linux tarball).
#>
param(
    [Parameter(Mandatory = $true)][string]$Board,
    [string]$KlipperTag = ''
)
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path "$PSScriptRoot\..").Path
Set-Location $RepoRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: Docker not found. Install Docker Desktop, or use WSL/Git Bash with scripts/build_firmware.sh." -ForegroundColor Red
    exit 1
}

$compose = 'firmware/docker-compose.yml'
New-Item -ItemType Directory -Force -Path 'firmware/output' | Out-Null

if ($KlipperTag) {
    Write-Host "==> Building image (KLIPPER_TAG=$KlipperTag)" -ForegroundColor Cyan
    & docker compose -f $compose build --build-arg KLIPPER_TAG=$KlipperTag
} else {
    & docker compose -f $compose build
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Building firmware for '$Board'" -ForegroundColor Cyan
& docker compose -f $compose run --rm fw $Board
exit $LASTEXITCODE
