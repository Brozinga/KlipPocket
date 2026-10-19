<#
  KlipPocket - interactive MCU firmware builder (Windows PowerShell 5.1+ / PowerShell 7).

    .\scripts\firmware_menu.ps1

  Pick Klipper or Kalico, then run the engine's real "Firmware Configuration"
  screen (make menuconfig), build with Docker, validate the result, flash the
  board (USB, UART, DFU or RP2040 mode) and verify what it runs. Needs Docker Desktop only: the compilers and both engines' sources live in the
  image (firmware\Dockerfile). Run it from Windows Terminal or a PowerShell
  window (menuconfig needs a real terminal).

  Files: configurations  -> firmware\configs\<profile>.config
         firmware images -> firmware\output\
#>
$ErrorActionPreference = 'Stop'
$Root    = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# Path inside the repository, with the separators of the running OS (works on Windows and Linux/macOS pwsh)
function Repo-Path([string]$Rel) {
    [IO.Path]::GetFullPath((Join-Path $Root ($Rel -replace '[\\/]', [string][IO.Path]::DirectorySeparatorChar)))
}
$Configs = Repo-Path 'firmware/configs'
$Output  = Repo-Path 'firmware/output'
$State   = Repo-Path 'firmware/.menu-state.json'
$Image   = 'klippocket-firmware'

$kalicoFile = Repo-Path 'app/src/main/kalico/kalico_version'
$kalicoDefault = if (Test-Path $kalicoFile) { (Get-Content $kalicoFile -Raw).Trim() } else { 'v2026.10.00' }
$S = [ordered]@{ Engine = 'klipper'; Profile = 'my-board'; KlipperTag = 'v0.13.0'; KalicoTag = $kalicoDefault }
if (Test-Path $State) {
    try { $saved = Get-Content $State -Raw | ConvertFrom-Json
          foreach ($k in @($S.Keys)) { if ($saved.$k) { $S[$k] = [string]$saved.$k } } } catch {}
}
function Save-State { $S | ConvertTo-Json | Set-Content -Encoding UTF8 $State }

# ---- requirements ----------------------------------------------------------
# Everything the menu runs lives in this folder and in the Docker image, so the
# only thing to install on the computer is Docker (no Python, no compilers).
# $script:DockerState: missing | stopped | denied | ready
$script:DockerState = 'missing'; $script:DockerVersion = ''; $script:PkgMissing = @()
$PackageFiles = @('firmware/Dockerfile', 'firmware/Dockerfile.dockerignore', 'firmware/container/fwtool.sh',
                  'firmware/container/fwcheck.py', 'scripts/build_firmware.sh')

function Check-Requirements {
    $script:PkgMissing = @($PackageFiles | Where-Object { -not (Test-Path (Repo-Path $_)) })
    $script:DockerVersion = ''
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { $script:DockerState = 'missing'; return }
    $out = & docker info 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        $script:DockerState = 'ready'
        $script:DockerVersion = (& docker version --format '{{.Server.Version}}' 2>$null | Out-String).Trim()
    } elseif ($out -match 'permission denied') { $script:DockerState = 'denied' }
    else { $script:DockerState = 'stopped' }
}
function Can-Build { return ($script:DockerState -eq 'ready' -and $script:PkgMissing.Count -eq 0) }

function Docker-Help {
    switch ($script:DockerState) {
        'missing' {
            Write-Host 'Docker is not installed. It is needed to configure, build, validate and flash firmware.' -ForegroundColor Red
            Write-Host '  Install Docker Desktop: https://www.docker.com/products/docker-desktop/'
            Write-Host '  Then run this menu again (or press c to check again).'
        }
        'stopped' {
            Write-Host 'Docker is installed but not running.' -ForegroundColor Yellow
            Write-Host '  Open Docker Desktop and wait until it says it is running, then press c to check again.'
        }
        'denied' {
            Write-Host 'Docker is installed, but your user may not use it.' -ForegroundColor Yellow
            Write-Host '  Add your user to the docker-users group (Windows) and sign in again, then press c to check again.'
        }
    }
}

# Every item that needs Docker (or the package) goes through here.
function Need-Docker {
    if (Can-Build) { return $true }
    Check-Requirements     # maybe it was installed/started meanwhile
    if (Can-Build) { return $true }
    Write-Host ''
    if ($script:DockerState -ne 'ready') { Docker-Help }
    if ($script:PkgMissing.Count -gt 0) {
        Write-Host ("This download is incomplete - missing: " + ($script:PkgMissing -join ', ')) -ForegroundColor Red
        Write-Host '  Download the repository again (all of firmware\ and scripts\ are needed).'
    }
    return $false
}

function Engine-Label { if ($S.Engine -eq 'kalico') { "Kalico $($S.KalicoTag)" } else { "Klipper $($S.KlipperTag)" } }
function Cfg-File { Join-Path $Configs "$($S.Profile).config" }
function Image-Ready {
    if ($script:DockerState -ne 'ready') { return $false }
    & docker image inspect $Image *> $null
    return ($LASTEXITCODE -eq 0)
}
function Pause-Menu { Write-Host ''; Read-Host 'Press Enter to return to the menu' | Out-Null }

# Native output must go straight to the console (menuconfig needs a real TTY), so
# these helpers return nothing: they leave the result in $script:Rc / $script:Ok
# and must be called as plain statements, never inside ( ) or a pipeline.
function Run-InImage([bool]$Tty, [string[]]$Cmd) {
    $a = @('run', '--rm', '-v', "${Configs}:/configs", '-v', "${Output}:/out")
    if ($Tty) { $a += @('-it', '-e', 'TERM=xterm-256color') }
    & docker @a $Image @Cmd
    $script:Rc = $LASTEXITCODE
}

# Same, but the container may see the host's USB devices (Linux/WSL2 engine with
# the device attached through usbipd-win; Docker Desktop alone cannot see USB).
function Run-Usb([bool]$Tty, [string[]]$Cmd) {
    $a = @('run', '--rm', '--privileged', '-v', '/dev:/dev', '-v', "${Configs}:/configs", '-v', "${Output}:/out")
    if ($Tty) { $a += @('-it', '-e', 'TERM=xterm-256color') }
    & docker @a $Image @Cmd
    $script:Rc = $LASTEXITCODE
}
function Test-Built { Test-Path (Join-Path $Output "$($S.Profile)-$($S.Engine).manifest.json") }

# Fills $script:Devs with @{Type; Value; Desc}: usb, uart, dfu, rp2040 or info devices.
function Scan-Devices {
    $script:Devs = @()
    $raw = & docker run --rm --privileged -v /dev:/dev -v "${Configs}:/configs" -v "${Output}:/out" $Image devices 2>$null
    foreach ($l in @($raw)) {
        $p = "$l".Split("`t")
        if ($p.Count -ge 2 -and $p[0]) {
            $script:Devs += @{ Type = $p[0]; Value = $p[1]; Desc = $(if ($p.Count -ge 3) { $p[2] } else { '' }) }
        }
    }
}

function Mode-Title([string]$Mode) {
    switch ($Mode) { 'usb' { 'USB' } 'uart' { 'UART' } 'dfu' { 'USB (DFU mode)' } 'rp2040' { 'USB RP2040 mode' } 'serial' { 'serial port' } }
}
function Mode-Types([string]$Mode) { if ($Mode -eq 'serial') { @('usb', 'uart') } else { @($Mode) } }
function Mode-Hint([string]$Mode) {
    switch ($Mode) {
        'usb'    { 'Boards running Klipper (or Katapult) over USB, or Arduino-style USB boards. They show up as /dev/serial/by-id/usb-... - plug the board in.' }
        'uart'   { "A USB-serial adapter (CH340, FTDI, CP210x) wired to the board's UART with its serial bootloader active (STM32: BOOT0 high, then reset). Plug the adapter in." }
        'dfu'    { 'An STM32 in DFU mode: hold/jumper BOOT0, reset, and it shows up as 0483:df11.' }
        'rp2040' { 'A Raspberry Pi Pico / RP2040 in BOOTSEL mode: hold the BOOTSEL button while plugging it in (shows as 2e8a:0003).' }
        'serial' { 'A board running Klipper over USB (ACM) or behind a USB-serial adapter.' }
    }
}

# Prints the connected devices of one kind; fills $script:Shown with their values.
function Show-Mode([string]$Mode) {
    Scan-Devices
    $types = Mode-Types $Mode
    $script:Shown = @()
    foreach ($d in $script:Devs) {
        if ($types -notcontains $d.Type) { continue }
        $script:Shown += $d.Value
        Write-Host ("   {0}) {1,-50} {2}" -f $script:Shown.Count, $d.Value, $d.Desc)
    }
    if ($script:Shown.Count -eq 0) {
        Write-Host '   (none found)' -ForegroundColor Yellow
        Write-Host ('   ' + (Mode-Hint $Mode)) -ForegroundColor DarkGray
    }
}

# Sets $script:Picked ('' = cancelled).
function Pick-Device([string]$Mode, [string]$Prompt) {
    $script:Picked = ''
    while ($true) {
        Write-Host ("  {0} devices connected:" -f (Mode-Title $Mode))
        Show-Mode $Mode
        Write-Host '   r) scan again    m) type a device by hand    Enter) cancel'
        $a = Read-Host $Prompt
        if (-not $a) { return }
        if ($a -match '^[rR]$') { continue }
        if ($a -match '^[mM]$') { $script:Picked = Read-Host 'Device (e.g. /dev/ttyUSB0, 0483:df11, 2e8a:0003)'; return }
        if ($a -match '^\d+$' -and [int]$a -ge 1 -and [int]$a -le $script:Shown.Count) { $script:Picked = $script:Shown[[int]$a - 1]; return }
        Write-Host 'Invalid choice.'
    }
}

function Build-Image {
    Write-Host "Building the Docker image (Klipper $($S.KlipperTag) + Kalico $($S.KalicoTag), ARM + AVR compilers)." -ForegroundColor Cyan
    Write-Host 'The first build downloads ~400 MB; later ones are cached.' -ForegroundColor DarkGray
    & docker build -t $Image -f (Repo-Path 'firmware/Dockerfile') `
        --build-arg "KLIPPER_TAG=$($S.KlipperTag)" --build-arg "KALICO_TAG=$($S.KalicoTag)" $Root
    $script:Ok = ($LASTEXITCODE -eq 0)
    if (-not $script:Ok) { Write-Host 'Image build failed.' -ForegroundColor Red }
}
function Ensure-Image {
    if (Image-Ready) { $script:Ok = $true; return }
    Write-Host "The Docker image $Image does not exist yet." -ForegroundColor Yellow
    $a = Read-Host 'Build it now? [Y/n]'
    if ($a -match '^[Nn]') { $script:Ok = $false; return }
    Build-Image
}

function Show-Header {
    Clear-Host
    Write-Host '  KlipPocket - MCU firmware builder' -ForegroundColor Cyan
    Write-Host '  ---------------------------------------------'
    Write-Host "  Engine  : $(Engine-Label)"
    $st = if ((Test-Path (Cfg-File)) -and (Get-Item (Cfg-File)).Length -gt 0) { 'saved' } else { 'new' }
    Write-Host "  Profile : $($S.Profile)  (firmware\configs\$($S.Profile).config - $st)"
    Write-Host ''
    Write-Host '  Requirements' -ForegroundColor White
    switch ($script:DockerState) {
        'ready' {
            $img = if (Image-Ready) { 'image ready' } else { 'image not built yet' }
            Write-Host ("   Docker  : OK - installed ({0}) and running - {1}" -f $script:DockerVersion, $img) -ForegroundColor Green
        }
        'stopped' { Write-Host '   Docker  : installed, but NOT RUNNING - configure/build/flash disabled' -ForegroundColor Yellow }
        'denied'  { Write-Host '   Docker  : installed, but you have NO PERMISSION to use it - configure/build/flash disabled' -ForegroundColor Yellow }
        default   { Write-Host '   Docker  : NOT INSTALLED - install it to configure/build/flash (see below)' -ForegroundColor Red }
    }
    if ($script:PkgMissing.Count -eq 0) {
        Write-Host '   Package : OK - complete (scripts, Dockerfile and configs are in this folder)' -ForegroundColor Green
    } else {
        Write-Host ('   Package : INCOMPLETE - missing files: ' + ($script:PkgMissing -join ', ')) -ForegroundColor Red
    }
    Write-Host ''
    if (Test-Path (Join-Path $Output "$($S.Profile)-$($S.Engine).manifest.json")) {
        Write-Host "  Output  : firmware\output\$($S.Profile)-$($S.Engine).*" -ForegroundColor Green
    }
    Write-Host ''
}

function Choose-Engine {
    Write-Host "  1) Klipper  ($($S.KlipperTag))"
    Write-Host "  2) Kalico   ($($S.KalicoTag))"
    switch (Read-Host 'Engine [1-2]') { '1' { $S.Engine = 'klipper' } '2' { $S.Engine = 'kalico' } default { return } }
    Save-State
}

function Choose-Profile {
    New-Item -ItemType Directory -Force -Path $Configs | Out-Null
    $files = @(Get-ChildItem $Configs -Filter '*.config' -ErrorAction SilentlyContinue | ForEach-Object { $_.BaseName })
    Write-Host '  Saved profiles:'
    for ($i = 0; $i -lt $files.Count; $i++) { Write-Host ("   {0}) {1}" -f ($i + 1), $files[$i]) }
    Write-Host '   n) new profile'
    $a = Read-Host "Choose [number / n / Enter = keep '$($S.Profile)']"
    if (-not $a) { return }
    if ($a -match '^[nN]$') {
        $nn = (Read-Host 'New profile name (letters, digits, - _)') -replace '[^A-Za-z0-9_-]', '-'
        $nn = $nn.Trim('-')
        if ($nn) { $S.Profile = $nn }
    } elseif ($a -match '^\d+$' -and [int]$a -ge 1 -and [int]$a -le $files.Count) {
        $S.Profile = $files[[int]$a - 1]
    } else { Write-Host 'Invalid choice.' }
    Save-State
}

function Do-Menuconfig {
    Ensure-Image
    if (-not $script:Ok) { return }
    New-Item -ItemType Directory -Force -Path $Configs, $Output | Out-Null
    Run-InImage $true @('menuconfig', $S.Engine, $S.Profile)
}

function Do-Build {
    Ensure-Image
    if (-not $script:Ok) { return }
    if (-not ((Test-Path (Cfg-File)) -and (Get-Item (Cfg-File)).Length -gt 0)) {
        Write-Host "No configuration saved for `"$($S.Profile)`" yet." -ForegroundColor Yellow
        $a = Read-Host 'Open Firmware Configuration now? [Y/n]'
        if ($a -match '^[Nn]') { return }
        Do-Menuconfig
        if (-not (Test-Path (Cfg-File))) { return }
    }
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
    Run-InImage $false @('build', $S.Engine, $S.Profile)
    if ($script:Rc -eq 0) {
        Write-Host ''; Write-Host '-- Validation --' -ForegroundColor White
        Run-InImage $false @('validate', $S.Engine, $S.Profile)
    } else {
        Write-Host ''; Write-Host 'Build failed - see the messages above.' -ForegroundColor Red
    }
}

function Do-Validate {
    Ensure-Image
    if (-not $script:Ok) { return }
    $a = Read-Host 'Also rebuild from scratch and compare hashes (slower)? [y/N]'
    $cmd = @('validate', $S.Engine, $S.Profile)
    if ($a -match '^[Yy]') { $cmd += '--deep' }
    Run-InImage $false $cmd
}

function List-Mode([string]$Mode) {
    Ensure-Image
    if (-not $script:Ok) { return }
    Write-Host ''
    Write-Host ("-- {0}: connected devices -- (nothing is written)" -f (Mode-Title $Mode)) -ForegroundColor White
    Show-Mode $Mode
}

function Do-FlashMode([string]$Mode) {
    Ensure-Image
    if (-not $script:Ok) { return }
    if (-not (Test-Built)) { Write-Host 'Build the firmware first (Make Firmware).' -ForegroundColor Yellow; return }
    Write-Host 'Docker only sees USB devices attached to WSL2 with usbipd-win (https://learn.microsoft.com/windows/wsl/connect-usb).' -ForegroundColor DarkGray
    Write-Host ''
    Write-Host ("Flash {0}-{1} via {2}" -f $S.Profile, $S.Engine, (Mode-Title $Mode)) -ForegroundColor White
    Write-Host 'Close any printer profile / program that is using the board first.'
    Pick-Device $Mode 'Flash which one?'
    if (-not $script:Picked) { return }
    Write-Host ''
    $a = Read-Host "Flash $($S.Profile)-$($S.Engine) to $($script:Picked)? This overwrites the firmware on the board. [y/N]"
    if ($a -notmatch '^[Yy]') { Write-Host 'Cancelled.'; return }
    Write-Host ''
    $lines = @()
    & docker run --rm --privileged -v /dev:/dev -v "${Configs}:/configs" -v "${Output}:/out" $Image flash $S.Engine $S.Profile $script:Picked $Mode 2>&1 |
        ForEach-Object { "$_" } | Tee-Object -Variable lines | Out-Host
    $rc = $LASTEXITCODE
    Write-Host ''
    if ($rc -eq 0) {
        Write-Host ("OK - Flash OK: everything went fine. {0}-{1} was written to {2}." -f $S.Profile, $S.Engine, $script:Picked) -ForegroundColor Green
        Write-Host ''
        $a = Read-Host 'Verify what the board is running now? [Y/n]'
        if ($a -notmatch '^[Nn]') { Write-Host ''; Run-Usb $false @('verify', $S.Engine, $S.Profile, 'auto', '30') }
    } else {
        Write-Host 'FAILED - Flash FAILED: the board may still hold its old firmware.' -ForegroundColor Red
        Write-Host 'Error:' -ForegroundColor Red
        @($lines) | Where-Object { $_ -match 'ERROR|rror|ailed|not found|timed out|\[FAIL\]' } | Select-Object -Last 6 | ForEach-Object { "   $_" }
    }
}

function Flash-Menu {
    while ($true) {
        Write-Host ''
        Write-Host ("  Flash Firmware - {0}-{1}" -f $S.Profile, $S.Engine) -ForegroundColor Cyan
        Write-Host '  Detect what is connected (writes nothing):' -ForegroundColor DarkGray
        Write-Host '    1) List USB devices'
        Write-Host '    2) List UART adapters'
        Write-Host '    3) List USB (DFU mode) devices'
        Write-Host '    4) List USB RP2040 mode devices'
        Write-Host '  Flash:' -ForegroundColor DarkGray
        Write-Host '    5) Flash via USB'
        Write-Host '    6) Flash via UART'
        Write-Host '    7) Flash via USB (DFU mode)'
        Write-Host '    8) Flash via USB RP2040 mode'
        Write-Host '    0) Back to the main menu'
        $opt = Read-Host 'Option'
        switch ($opt) {
            '1' { List-Mode 'usb' }
            '2' { List-Mode 'uart' }
            '3' { List-Mode 'dfu' }
            '4' { List-Mode 'rp2040' }
            '5' { Do-FlashMode 'usb' }
            '6' { Do-FlashMode 'uart' }
            '7' { Do-FlashMode 'dfu' }
            '8' { Do-FlashMode 'rp2040' }
            { $_ -in '0', 'q', 'Q' } { return }
            default { continue }
        }
        Pause-Menu
    }
}

function Do-Verify {
    Ensure-Image
    if (-not $script:Ok) { return }
    if (-not (Test-Built)) { Write-Host 'Build the firmware first (Make Firmware).' -ForegroundColor Yellow; return }
    Write-Host "Connects to the board and compares what it reports with this build."
    Write-Host "Close any app or profile that is using the board's port first."
    $port = ''
    while ($true) {
        Write-Host '  Serial ports connected:'
        Show-Mode 'serial'
        Write-Host '   a) automatic (try every port)    r) scan again    m) type a port by hand    Enter) cancel'
        $a = Read-Host 'Verify which one?'
        if (-not $a) { return }
        if ($a -match '^[rR]$') { continue }
        if ($a -match '^[aA]$') { $port = 'auto'; break }
        if ($a -match '^[mM]$') { $port = Read-Host 'Port (e.g. /dev/ttyACM0)'; break }
        if ($a -match '^\d+$' -and [int]$a -ge 1 -and [int]$a -le $script:Shown.Count) { $port = $script:Shown[[int]$a - 1]; break }
        Write-Host 'Invalid choice.'
    }
    if (-not $port) { return }
    Run-Usb $false @('verify', $S.Engine, $S.Profile, $port, '30')
}

function Do-Summary {
    $f = Cfg-File
    if (-not ((Test-Path $f) -and (Get-Item $f).Length -gt 0)) { Write-Host "No configuration saved for '$($S.Profile)'."; return }
    Write-Host "Key options in firmware\configs\$($S.Profile).config:"
    $re = '^CONFIG_(LOW_LEVEL_OPTIONS|MACH_\w+|MCU|CLOCK_FREQ|CLOCK_REF_FREQ|\w*FLASH_START_\w+|FLASH_APPLICATION_ADDRESS|\w*USB\w*|\w*SERIAL\w*|INITIAL_PINS|STM32_CLOCK_REF_\w+)='
    Get-Content $f | Where-Object { $_ -match $re } | ForEach-Object { "   $_" }
    Write-Host ''
    Get-ChildItem $Output -Filter "$($S.Profile)-$($S.Engine).*" -ErrorAction SilentlyContinue |
        ForEach-Object { "   {0} bytes  firmware\output\{1}" -f $_.Length, $_.Name }
}

function Do-Image {
    $a = Read-Host "  Klipper tag [$($S.KlipperTag)]"; if ($a) { $S.KlipperTag = $a }
    $a = Read-Host "  Kalico tag  [$($S.KalicoTag)]";  if ($a) { $S.KalicoTag = $a }
    Save-State
    Build-Image
}

Check-Requirements
while ($true) {
    Show-Header
    if ($script:DockerState -ne 'ready') { Docker-Help; Write-Host '' }
    $off = if (Can-Build) { 'White' } else { 'DarkGray' }
    $tag = if (Can-Build) { '' } else { ' [needs Docker]' }
    Write-Host '  1) Select engine (Klipper / Kalico)'
    Write-Host '  2) Select or create a configuration profile'
    Write-Host ("  3) Firmware Configuration (make menuconfig){0}" -f $tag) -ForegroundColor $off
    Write-Host ("  4) Make Firmware (Docker){0}" -f $tag) -ForegroundColor $off
    Write-Host ("  5) Validate Firmware{0}" -f $tag) -ForegroundColor $off
    Write-Host ("  6) Flash Firmware (USB, UART, DFU, RP2040){0}" -f $tag) -ForegroundColor $off
    Write-Host ("  7) Verify Flashed Firmware (compare with what the board runs){0}" -f $tag) -ForegroundColor $off
    Write-Host '  8) Show configuration summary and output files'
    Write-Host ("  9) Docker image: build / change engine versions{0}" -f $tag) -ForegroundColor $off
    Write-Host '  c) Check requirements again'
    Write-Host '  0) Quit'
    Write-Host ''
    $opt = Read-Host 'Option'
    Write-Host ''
    switch ($opt) {
        '1' { Choose-Engine }
        '2' { Choose-Profile }
        '3' { if (Need-Docker) { Do-Menuconfig } else { Pause-Menu } }
        '4' { if (Need-Docker) { Do-Build }; Pause-Menu }
        '5' { if (Need-Docker) { Do-Validate }; Pause-Menu }
        '6' { if (Need-Docker) { Flash-Menu } else { Pause-Menu } }
        '7' { if (Need-Docker) { Do-Verify }; Pause-Menu }
        '8' { Do-Summary; Pause-Menu }
        '9' { if (Need-Docker) { Do-Image }; Pause-Menu }
        { $_ -in 'c', 'C' } { Check-Requirements }
        { $_ -in '0', 'q', 'Q' } { exit 0 }
    }
}
