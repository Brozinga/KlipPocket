# KlipPocket - Klipper brain for your 3D printer

<p align="center">
  <img src="docs/images/logo.png" alt="KlipPocket" width="260">
</p>

<p align="center">
  <a href="https://github.com/Brozinga/KlipPocket/releases/latest"><img src="https://img.shields.io/github/v/release/Brozinga/KlipPocket?label=latest%20release&color=49CBEB" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/platform-Android%205.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/license-GPL--3.0-4B8BBE" alt="License: GPL-3.0">
  <img src="https://img.shields.io/badge/kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin / Jetpack Compose">
</p>

**Read this in other languages: [English](README.md) · [Português (BR)](README.pt-br.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

**KlipPocket turns an Android phone or tablet into the brain of your 3D printer.**
Plug it into the printer over USB and it runs [Klipper](https://github.com/KevinOConnor/klipper) or [Kalico](https://github.com/KalicoDTU/kalico) for you — no Raspberry Pi, no SSH, no Linux to maintain.

Many "classic" printers still run slow, closed Marlin boards. KlipPocket brings them modern Klipper firmware without the usual complexity: install the app, flash the printer once, and control everything from the web interfaces in your browser. Everything runs locally on the device you already own, and the extra tools — remote access, camera monitoring, failure detection, print recovery — are a switch away.

> **Just want to install it?** Grab the latest APK from the
> [Releases page](https://github.com/Brozinga/KlipPocket/releases/latest) — if you're not sure which one, pick `armv7` (see
> [Choosing the Right Package](#choosing-the-right-package) below).

## Features

- **Klipper 0.13 and earlier** — compatible with current and older Klipper firmware (Kalico also supported)
- **Up-to-date software** — recent Moonraker, Fluidd, Mainsail and Voyager UI bundled in the app
- **Remote connection** — reach your printer from anywhere with OctoEverywhere, Obico and others
- **AI failure detection** — spot failed prints early through Obico
- **SimplyPrint** — remote monitoring and AI failure detection, already supported by the bundled Moonraker
- **Print Recovery** — resume a print after a power loss
- **Camera monitoring** — use the device camera or a USB webcam, with live preview, zoom and timelapse
- **Web interfaces** — Fluidd, Mainsail and Voyager UI, served by the device itself

## Choosing the Right Package

KlipPocket provides three APK variants:

| Architecture | Package Name | Use Case |
|-------------|--------------|----------|
| arm64 | `KlipPocket_v*_arm64.apk` | Modern 64-bit devices |
| armv7 | `KlipPocket_v*_armv7.apk` | Older 32-bit devices — works on most devices (recommended if unsure) |
| x86_64 | `KlipPocket_v*_amd64.apk` | x86_64 tablets, Chromebooks, Android emulators |

**How to check your device architecture:**
- **Settings > About Phone > Architecture** or **Kernel Architecture**
- Or install a CPU info app like "CPU-Z" or "AIDA64"
- If unsure, pick armv7 — it is the package that works on the widest range of devices

## Quick Start

Follow these steps in order:

1. **Generate and flash the firmware on the printer.** Klipper needs its own firmware on the printer's mainboard. Build it for your board and flash it by following [`docs/build-firmware.md`](docs/build-firmware.md).
2. **Check the connection.** The printer must talk to the device over **USB**, and the Android device must support **OTG** (USB host).
   - Some devices **do not charge while the USB port is used for data**. In that case the battery drains during long prints, and the fix is an internal soldered charging connection (direct to the battery pins).
   - Before buying a hub or cable, **check that your device can charge and transfer data at the same time** through the connector you plan to use (see [What USB Hub to Use?](#what-usb-hub-to-use)).
3. **Install the app and set it up.** Install the APK, add your first printer and open the web interface by following the illustrated guide in [**Getting Started**](docs/getting-started.md).

> **Stuck?** The in-app **Logs** tab shows the Klipper, Moonraker and app logs, and lets you copy/share them without a PC.

## Documentation

All guides live in [`docs/`](docs/index.md) (also available in Português and 简体中文):

- [Getting Started](docs/getting-started.md) — install the app and set up your first printer, step by step
- [Building the MCU firmware](docs/build-firmware.md) — generate the firmware to flash on your printer board
- [Print Recovery](docs/print-recovery.md) — resume a print after a power loss
- [Timelapse](docs/timelapse.md) — record a timelapse and find the video
- [Camera / Webcam](docs/webcam.md) — device camera, USB webcam, preview, zoom and tap to focus
- [OctoEverywhere](docs/octoeverywhere.md) — remote access with OctoEverywhere
- [Obico](docs/obico.md) — remote access and AI failure detection with Obico
- [SimplyPrint](docs/simplyprint.md) — remote monitoring and AI failure detection with SimplyPrint
- [Klipper add-ons](docs/mods/klipper-addons.md) — KAMP, LED Effect, Z Calibration, Auto Speed, TMC Autotune
- [Input shaper without an accelerometer](docs/mods/input-shaper-manual.md) — ringing-tower method and macros
- [Building the APK](docs/build-app.md) — compile the app yourself

## Which ports do the web interfaces and the camera use?

The address is shown on the main screen whenever an instance is running (`IP` is the device's address on your network). Each web interface has its own port, following the front-end toggle in the app:

| Service | Address |
|---|---|
| Fluidd | `http://IP:4408/` |
| Mainsail | `http://IP:4409/` |
| Voyager UI | `http://IP:4410/` |
| Camera stream | `http://IP:8889/` |
| Camera snapshot (single JPEG) | `http://IP:8889/snapshot` |
| Camera stream (webcam style) | `http://IP:8889/webcam/?action=stream` |
| Camera snapshot (webcam style) | `http://IP:8889/webcam/?action=snapshot` |

## Autostart

KlipPocket can start by itself when the device boots, so the printer is ready without touching the screen:

1. Enable **Autostart** on every printer profile you want to start automatically.
2. Set KlipPocket as the device's **default launcher** (home app).
3. If your device storage is encrypted (the default on most devices), **remove the lockscreen PIN/password** — the system only unlocks the app after you type it.

## Background Activity Notice

Some manufacturers restrict apps that run in the background, which can stop a print that is already running. To avoid it:

- Allow KlipPocket to ignore battery optimization when the app asks (it is the first screen at launch).
- Allow all background activity for the app in your device's settings.
- Setting the app as the default launcher also helps on stricter systems.

## Android TV Support?

Yes — it should work just fine. Note that some cheap TV boxes don't allow setting KlipPocket as the launcher without disabling the system one first; use ADB or root to disable it.

## What USB Hub to Use?

A hub is **supported**, but not required if your device can talk to the printer and charge at the same time. If you need one, the recommended hub has **at least one USB-A port (for the printer) and one power/charging port** (such as USB-C Power Delivery pass-through), and only if your device supports charging while in OTG mode. Check that the combination works with your device before relying on it for long prints.

## Restrictions

- Web server can't run on default port because Android/linux doesn't allow user-space apps to bind to ports less than 1024 and we want 80 for default `http://IP`
- Some devices may reset device path on firmware restart, you should use VID/PID naming in that case
- No SSH (You won't be able to build firmware or run additional autorun services anyway)
- Some devices doesn't support OTG and charging at the same time, you must solder directly to the battery pins in that case (Or use different device, it's up to you)
- Only 250000 baud rate is supported (I don't want to forward this setting into Android USB driver, almost all configurations use 250000 anyway)

> **Most common snag:** if your phone can't charge and talk to the printer at
> the same time over the same cable, that's the OTG+charging restriction above
> — a powered USB hub (see [What USB Hub to Use?](#what-usb-hub-to-use)) fixes it.

## Credits

- **[ProtonKicker/Kocoa-Beam](https://github.com/ProtonKicker)** — ported the application to Kotlin and redesigned its look.
- **[Beam Klipper](https://github.com/utkabobr/BeamKlipper)** — the original project this one comes from.
- **[Voyager UI](https://github.com/ozancs/voyager-ui)** by [ozancs](https://github.com/ozancs) — the third web front end you can pick alongside Fluidd and Mainsail.
- Klipper, Kalico, Moonraker, Fluidd, Mainsail and the other bundled components belong to their respective authors (see [What's inside?](#whats-inside)).

## Contributing

Pull requests are welcome!
