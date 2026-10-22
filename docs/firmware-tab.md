# Firmware tab — download and install MCU firmware from the app

**Languages: [English](firmware-tab.md) · [Português (BR)](pt-br/firmware-tab.md) · [简体中文](zh-Hans/firmware-tab.md)**

The **Firmware** tab (the chip icon in the top bar) lets you fetch a firmware
file and write it to a board plugged into the phone/tablet, without a PC. It is
the on-device counterpart of option **6 Flash Firmware** of the
[PC menu](build-firmware.md#flashing-the-board): the firmware itself is still
**built on a PC** (Android cannot compile), here you only get it and flash it.

![The Firmware tab](images/firmware-tab/tab-home.png)

## Modes: what works and what is experimental

| Mode | Board | Status |
|---|---|---|
| **RP2040 / RP2350** | Raspberry Pi Pico / Pico 2 already in BOOTSEL (`2e8a:0003` / `2e8a:000f`) | **Tested** on a Pico (RP2040). The **RP2350 is experimental** (no Pico 2 to test with) |
| **USB** | A board running Klipper over USB. The app sends the 1200 bps bootloader request (as Klipper's `make flash` does) and flashes through the bootloader that appears: RP2040 BOOTSEL, STM32 DFU or AVR109 (ATmega32U4 / Caterina) | **Tested** for the RP2040 with a `.uf2`; the other bootloaders (and the RP2350) are **experimental** |
| **DFU** | STM32 in DFU mode (`0483:df11`), DfuSe protocol: erase sectors, write, read back, leave | **Experimental** |
| **UART** | USB-serial adapter wired to the board's serial bootloader: STM32 (ST AN3155, the whole flash is erased) or AVR (STK500v1 "arduino", STK500v2 "wiring") | **Experimental** |
| **Katapult** | Klipper's [Katapult](https://github.com/Arksine/katapult) bootloader (`1d50:6177`) over USB, or over a USB-serial adapter wired to its UART (250000 baud). A Klipper board over USB is sent to Katapult first (1200 bps). Raw `.bin` | **Experimental** |
| **HID-Flash** | STM32F1/F4 with the HID bootloader (`1209:beba`), the protocol of Klipper's `hid-flash`. A Klipper board over USB is sent to it first (1200 bps). Raw `.bin` | **Experimental** |
| **SAM-BA** | Atmel SAM3 / SAM4 / SAME70 (Arduino Due, SAM4E boards…) in the ROM bootloader (`03eb:6124`): erases the whole flash, writes, reads back, boots from flash and resets, like `bossac`. A Klipper board over USB is sent to it first (1200 bps). Raw `.bin` | **Experimental** |
| **CAN** | Katapult over a CAN bus, through a gs_usb / candlelight adapter or Klipper's USB-to-CAN bridge (`1d50:606f`). You give the node's **UUID** (`canbus_query.py`) and the bus speed; the app sends the node to Katapult, gives it an id and flashes it, like `flash_can.py`. Raw `.bin` | **Experimental** |

> **Experimental modes** are fully implemented, but they have **no conclusive
> tests on real boards** (only unit tests against simulated bootloaders). The
> app marks them in the mode list and warns again before writing. **Use them at
> your own risk.**

Also **not** covered: boards that load their firmware from an
SD card (for example the Neptune 3 Pro: copy the `.bin` to the card instead),
the SAMD / SAME5x boards of the SAM-BA family (another flash controller) and
bootloaders other than the ones above. The app refuses a file or a device that does not fit the mode: a
`.uf2` must be built for the chip, a raw `.bin` for a Pico must start with a valid
RP2040 boot2 block (or an RP2350 IMAGE_DEF block), so an STM32 `.bin` is never
written to a Pico, DFU / UART need a `.bin` or `.hex`, and Katapult / HID-Flash / SAM-BA / CAN need a `.bin`. Klipper writes the
same UF2 family id for the RP2040 and the RP2350, so the app tells them apart by
looking inside the image; a file for the other chip is refused before anything is written.

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

### Delete files

Tap a file in the list to select it: a **trash icon** appears next to *Firmware
files* and deletes that file. **Delete all** removes every stored firmware. Both ask for confirmation first.

![Delete buttons](images/firmware-tab/delete-files.png)

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
(*USB* for a Klipper board, *RP2040* for BOOTSEL, *DFU* for `0483:df11`). Modes
without conclusive tests show an **Experimental** note.

![Step 3](images/firmware-tab/step3-mode.png)

For a raw **`.bin`** written to an **STM32** (DFU, UART or USB mode) a **Flash
start address** field appears: where the file goes (`0x08000000` unless a
bootloader of yours sits below; Klipper's *Bootloader offset* is that size).
`.hex` files carry their own addresses and `.uf2` files too.

![Start address](images/firmware-tab/start-address.png)

In **CAN** mode two fields appear instead: the **CAN UUID** of the node (12 hex
digits, as `canbus_query.py` prints it) and the **bus speed** in bit/s
(1000000 by default, the same `canbus_frequency` as in the Klipper config). In
step 2 pick the adapter itself (the gs_usb device), not the board behind it.

**4 — Confirm.** A warning shows the file, device and mode, and, for experimental
modes, a second warning that you proceed at your own risk. **Cancel/Back**
changes nothing; **Confirm** starts.

![Step 4](images/firmware-tab/step4-warning.png)

**Progress.** The screen stays on. One bar covers the whole run (stage by
stage, never backwards) and fills smoothly with a light sweeping over it while
the app works; below it, the current stage and the overall percentage:
preparing, putting the board in BOOTSEL, waiting, USB permission, talking to the
bootloader, erasing, writing, verifying (read back and compared), restarting
and waiting for the board. If something fails the bar disappears and the
error is shown.

![Progress](images/firmware-tab/progress.png)

**Result.** On success (the bar is full) the board is flashed, verified, and (when it uses USB)
back on the bus as a Klipper device. If the data was verified but the board did
not reappear on USB, the app says so. On failure it tells why in your language.

| | |
|---|---|
| ![Success](images/firmware-tab/success.png) | ![Failure: bootloader did not answer](images/firmware-tab/failure.png) |
| ![Wrong device for the mode](images/firmware-tab/failure-wrong-device.png) | |

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
| No supported bootloader appeared | The board did not enter RP2040 BOOTSEL, STM32 DFU, Katapult, HID, SAM-BA or AVR109 after the 1200 bps request. Put it in the bootloader by hand (BOOTSEL / BOOT0 / the bootloader button) and use the mode of that bootloader. |
| The chosen device does not match the mode | Pick the right device for the mode (*DFU* needs `0483:df11`, *RP2040* needs `2e8a:0003`). Nothing was written. |
| The bootloader did not answer | UART: check BOOT0 and reset (STM32) or the reset line (AVR), the wiring and the baud rate. Nothing was written. |
| The device is not a USB-serial adapter | UART mode needs a CDC / CH34x / CP210x / FTDI adapter. |
| This file type does not fit the chosen mode | RP2040: `.uf2` or `.bin`; STM32 / AVR: `.bin` or `.hex`; Katapult, HID-Flash, SAM-BA and CAN: `.bin`. |
| The CAN UUID is missing or not valid | 12 hex digits, exactly as `canbus_query.py` shows them. |
| The device is not a CAN adapter | In CAN mode choose the gs_usb / candlelight adapter (or the Klipper USB-to-CAN bridge) in step 2. |
| The flash start address is not valid | STM32 `.bin` files go between `0x08000000` and `0x0FFFFFFF`. |
| USB permission was not granted | Try again and accept the Android prompt. |
| The file is not for this chip | The RP2040 and the RP2350 each need their own firmware: build it for the chip you have (see [build-firmware.md](build-firmware.md)). |
| The flash does not match the file | Do not unplug; run it again. An RP2040 stays in BOOTSEL and can always be re-flashed. |

## How the experimental modes were tested without a board

The author has only a Raspberry Pi Pico, so every other mode was checked against
**simulated boards that are themselves checked against the real tools**:

- The simulators are in [`app/src/test/python/fake_bootloaders.py`](../app/src/test/python/fake_bootloaders.py):
  a Katapult node (USB/UART and CAN bus), an STM32 HID bootloader (1 KiB and 2 KiB
  pages), and a SAM3X8E / SAM4E8E in its SAM-BA ROM with the EEFC flash controller.
- `test_bootloader_fakes.py` runs Klipper's **own** tools against them: `flash_can.py`
  (serial and CAN paths), `hid-flash` and `bossac` (compiled from the sources in the
  tree). If the real tool finishes with a good flash, the simulator talks like the board.
- The Kotlin tests (`OtherBootloadersTest`) then run the app's code against the
  same simulators, including wrong answers, silence, bad writes and a failed
  read-back. A mutation check (skipping the SAM-BA erase) makes them fail, as it should.
- What this **cannot** prove: USB timing, the electrical side, a bootloader version
  that differs from the reference one, and the `gs_usb` framing of a real CAN
  adapter (it follows the Linux `gs_usb` driver but has not seen one). That is why
  these modes stay marked **experimental**.

A Pico always keeps its BOOTSEL loader, so a bad flash can be fixed by holding
BOOTSEL and flashing again.
