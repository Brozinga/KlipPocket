# Firmware tab — download and install MCU firmware from the app

**Languages: [English](firmware-tab.md) · [Português (BR)](pt-br/firmware-tab.md) · [简体中文](zh-Hans/firmware-tab.md)**

The **Firmware** tab (the chip icon in the top bar) lets you fetch a firmware
file and write it to a board plugged into the phone/tablet, without a PC. It is
the on-device counterpart of option **6 Flash Firmware** of the
[PC menu](build-firmware.md#flashing-the-board): the firmware itself is still
**built on a PC** (Android cannot compile), here you only get it and flash it.

![The Firmware tab](images/firmware-tab/tab-home.png)

## What works and what does not

| Mode | Board | Status |
|---|---|---|
| **RP2040** | Raspberry Pi Pico / RP2040 already in BOOTSEL (`2e8a:0003`) | **Works** (tested on a Pico) |
| **USB** | Raspberry Pi Pico / RP2040 running Klipper over USB: the app puts it in BOOTSEL by itself (1200 bps request) and flashes it | **Works** (tested on a Pico) |
| **DFU** | STM32 in DFU mode (`0483:df11`) | Not available yet: it runs and reports a failure, **nothing is written** |
| **UART** | USB-serial adapter wired to a serial bootloader | Not available yet: same as DFU |

Also **not** covered: Pico 2 / RP2350, boards that load their firmware from an
SD card (for example the Neptune 3 Pro: copy the `.bin` to the card instead),
and CAN boards. The file must be a `.uf2` built for the RP2040 (the safe choice)
or a raw `.bin`; the app refuses a `.uf2` for another chip.

> **Writing firmware can leave a board unusable.** Flash only a file built for
> that exact board, keep the cable connected and the screen open, and
> remember Klipper loses its connection to the board while it is written.

## The three buttons

- **List USB devices** — the same list as *Settings → USB → List USB devices*.

  ![USB devices](images/firmware-tab/list-usb.png)

- **Download firmware** — paste a link and the app saves the file.
- **Install firmware** — the step-by-step assistant below.

Below the buttons, **Firmware files** lists what is stored in the app (name,
type, size) with **Delete**, and **Import from device** copies a file you
already have (for example one you built on a PC) using the system file picker.
Accepted files: `.bin`, `.hex`, `.uf2` and `.zip` (the firmware files inside are extracted).

## Download a firmware

Tap **Download firmware**, paste an **https** link and tap **Download**, for
example `https://github.com/utkabobr/klipper/releases/download/prebuilt-v0.12.0/stm32f401.bin`.
Limit: 16 MB.

| | |
|---|---|
| ![Download dialog](images/firmware-tab/download-dialog.png) | ![File saved](images/firmware-tab/download-saved.png) |

## Install a firmware

Tap **Install firmware**. Each step has **Next** / **Back**; the back button of
the phone also goes back one step.

**1 — Choose the file.** Pick one of the stored files.

![Step 1](images/firmware-tab/step1-file.png)

**2 — Choose the device.** Every USB device the phone sees is listed;
**Refresh** updates the list (it also refreshes by itself). A board in BOOTSEL
shows as *RP2 Boot · 2e8a:0003*, a Pico running Klipper as *rp2040 · 1d50:614e*.
Android asks for USB permission when needed.

![Step 2](images/firmware-tab/step2-device.png)

**3 — Choose the write mode.** The app suggests one from the device
(*USB* for a Klipper board, *RP2040* for BOOTSEL).

![Step 3](images/firmware-tab/step3-mode.png)

**4 — Confirm.** A warning shows the file, device and mode. **Cancel/Back**
changes nothing; **Confirm** starts.

![Step 4](images/firmware-tab/step4-warning.png)

**Progress.** The screen stays on and shows the stage: preparing, putting the
board in BOOTSEL, waiting, USB permission, erasing, writing, verifying
(read back and compared), restarting and waiting for the board.

![Progress](images/firmware-tab/progress.png)

**Result.** On success the board is flashed, verified, and (when it uses USB)
back on the bus as a Klipper device. If the data was verified but the board did
not reappear on USB, the app says so. On failure it tells why in your language.

| | |
|---|---|
| ![Success](images/firmware-tab/success.png) | ![Failure](images/firmware-tab/failure.png) |

**View log** opens the Logs tab on the new **Firmware flashing** source.

## The log

Every flash is recorded (file, sha256, device, mode, each stage, result):

![Firmware log](images/firmware-tab/logs.png)

## Theme and language

The tab follows the app theme and language (English, Português, 简体中文, 繁體中文, Русский).

| | |
|---|---|
| ![Light theme](images/firmware-tab/light-tab.png) | ![Light theme, warning](images/firmware-tab/light-warning.png) |
| ![Portuguese](images/firmware-tab/pt-br-tab.png) | ![Portuguese, failure](images/firmware-tab/pt-br-failure.png) |

## If something goes wrong

| Message | What to do |
|---|---|
| The board did not enter BOOTSEL | Hold **BOOTSEL** while plugging the board and use *RP2040* mode. |
| USB permission was not granted | Try again and accept the Android prompt. |
| This UF2 is not for the RP2040 | Build the firmware for the RP2040 (see [build-firmware.md](build-firmware.md)). |
| The flash does not match the file | Do not unplug; run it again. The board stays in BOOTSEL and can always be re-flashed. |
| This write mode is not available yet | DFU and UART are not implemented; nothing was written. |

A Pico always keeps its BOOTSEL loader, so a bad flash can be fixed by holding
BOOTSEL and flashing again.
