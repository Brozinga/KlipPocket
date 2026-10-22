# Building MCU firmware

**Languages: [English](build-firmware.md) · [Português (BR)](pt-br/build-firmware.md) · [简体中文](zh-Hans/build-firmware.md)**

KlipPocket runs the Klipper/Kalico host on Android. The printer mainboard still
needs MCU firmware, built and flashed once on a PC (Android cannot compile).

**The recommended way is the interactive menu** described on this page. It asks
for Klipper or Kalico, shows that engine's own *Firmware Configuration* screen,
builds in Docker, validates the result, flashes the board and checks that the
new firmware is really the one running. The other ways (pre-built images, the
Docker/Local scripts) are at the [end of the page](#other-ways-to-get-the-firmware).

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Step by step: a complete example](#step-by-step-a-complete-example)
- [Flashing the board](#flashing-the-board)
- [What the validation checks](#what-the-validation-checks)
- [Other ways to get the firmware](#other-ways-to-get-the-firmware)

---

## Requirements

| What | Why |
|---|---|
| **The repository**, downloaded or cloned (`git clone https://github.com/Brozinga/KlipPocket.git`, or *Code → Download ZIP* on GitHub) | The menu, the Dockerfile and every helper script live in `scripts/` and `firmware/`. Nothing else has to be fetched by hand. |
| **Docker** — Docker Desktop on Windows and macOS, Docker Engine on Linux | Everything runs inside it: the compilers (ARM, AVR), the Klipper and Kalico sources and the flashing tools. |
| **A shell to start the menu** — **Bash** on Linux and macOS; **PowerShell** (5.1 or 7) or **Git Bash** on Windows | `scripts/firmware_menu.sh` (Bash / Git Bash) or `scripts/firmware_menu.ps1` (PowerShell). |
| **Internet, the first time** | Docker builds its image once (about 400 MB: compilers and the sources of both engines); after that it is cached. |

## Quick start

From the repository folder:

```bash
./scripts/firmware_menu.sh        # Linux, macOS, WSL, Git Bash
```
```powershell
.\scripts\firmware_menu.ps1       # Windows PowerShell / Windows Terminal
```

![Main menu](images/firmware-menu/menu-main.png)

The top shows the engine and the profile in use and the **requirements**
(Docker installed and running, download complete). The options:

| Option | What it does |
|---|---|
| **1 Select engine** | Klipper (v0.13.0) or Kalico (v2026.10.00). |
| **2 Select or create a profile** | A profile is one saved configuration: `firmware/configs/<profile>.config`. |
| **3 Firmware Configuration** | The engine's real `make menuconfig`. |
| **4 Make Firmware** | Compiles in Docker and validates the result. |
| **5 Validate Firmware** | Re-checks the output; an optional deep check rebuilds from scratch and compares hashes. |
| **6 Flash Firmware** | Lists what is connected and writes the board (USB, UART, DFU or RP2040 mode). |
| **7 Verify Flashed Firmware** | Connects to the board and checks that it runs exactly the firmware you built. |
| **8 Show summary** | The key options of the profile and the files of the last build. |
| **9 Docker image** | Builds the image, or changes the Klipper/Kalico versions inside it. |
| **c** | Checks the requirements again (after installing or starting Docker). |

Results go to `firmware/output/` as `<profile>-<engine>.*`: the image (`.bin`,
`.hex` for AVR, `.uf2` for RP2040), the `.dict` the host reads, the configuration
that was really used (`.config.used`) and a `.manifest.json` with sizes and
SHA-256 hashes.

---

## Step by step: a complete example

This builds firmware for an STM32F401 board with a 32 KiB bootloader that talks
to the host over its serial port (UART) — the kind of board most OEM printers use.

### Where do I find the settings for my printer?

The right answers for `make menuconfig` (chip, bootloader offset, how the board
talks to the host) depend on your mainboard. The Klipper project keeps a
`printer-*.cfg` for many printers here:

**<https://github.com/Klipper3d/klipper/tree/master/config>**

Open the one for your printer: **the comments at the top say how to generate its
firmware.** For the board used in this example it reads:

```ini
# To use this config, during "make menuconfig" select the STM32F401 with a
# "32KiB bootloader" and serial (on USART1 PA10/PA9) communication.

# Note that the "make flash" command does not work with this board.
# After running "make", rename the out/klipper.bin file to out/<file name>.bin
# Copy the file out/<file name>.bin to an SD card formatted to FAT32
# and then restart the printer with the SD card inserted.
```

That tells you three things: **what to select in the menu** (STM32F401, 32 KiB
bootloader, serial on USART1 PA10/PA9), **how the firmware gets onto the board**
(here: SD card, not USB) and **what the file must be called** for that
bootloader. Keep those notes at hand for the steps below.

> Not sure which chip your board has? See [Identify the mainboard chip](#1-identify-the-mainboard-chip).

### 1. Open the menu

Run the script as in the [quick start](#quick-start). If the top shows Docker as
installed and running and the package as complete, you are ready.

### 2. Choose the engine (option 1)

![Choosing the engine](images/firmware-menu/menu-engine.png)

Type `1` for Klipper or `2` for Kalico. Kalico builds exactly like Klipper and
shows the same configuration screens.

### 3. Create a profile (option 2)

![Creating a profile](images/firmware-menu/menu-profile.png)

Choose `n` and give the profile a name (letters, digits, `-` and `_`). It is saved
as `firmware/configs/<name>.config`, so you can keep one profile per board and
come back to it later.

### 4. Firmware Configuration (option 3)

This opens the engine's own configuration screen, inside Docker. Move with the
**arrow keys**, press **Space/Enter** to toggle or open an item, **Esc** to go
back, **Q** to quit (it asks to save: press **Y**).

![menuconfig, first screen](images/firmware-menu/menuconfig-1-start.png)

The first screen only shows the defaults for an Atmega2560. Tick **Enable extra
low-level configuration options** (Space): the options of your chip appear.

![Enabling the low-level options](images/firmware-menu/menuconfig-2-low-level.png)

**Micro-controller Architecture** — pick the family of the chip, here
*STMicroelectronics STM32*:

![Architecture list](images/firmware-menu/menuconfig-3-architecture.png)

**Processor model** — the exact chip, here *STM32F401*:

![Processor list](images/firmware-menu/menuconfig-4-processor.png)

**Bootloader offset** — how much of the flash the board's own bootloader uses
and that must not be overwritten, here *32KiB bootloader*. This is the most
important setting to get right:

![Bootloader offset](images/firmware-menu/menuconfig-5-bootloader.png)

**Clock Reference** keeps the default (*8 MHz crystal*). **Communication
interface** is how the board talks to the host, here *Serial (on USART1
PA10/PA9)*; the baud rate stays at 250000, the only one KlipPocket supports:

![Communication interface](images/firmware-menu/menuconfig-6-communication.png)

The finished screen:

![The finished configuration](images/firmware-menu/menuconfig-7-summary.png)

Press **Q**, then **Y** to save the profile:

![Saving the configuration](images/firmware-menu/menuconfig-8-save.png)

Boards with USB on the MCU itself (for example many STM32 and RP2040 boards) use
*USB* as the communication interface instead of serial; the other screens work
the same way.

### 5. Make Firmware (option 4)

The menu compiles inside Docker and validates what came out:

![Make Firmware](images/firmware-menu/menu-make.png)

`All checks passed` means the image is sane: right chip, right clock, vector
table at the bootloader offset, baud rate 250000 (see
[what the validation checks](#what-the-validation-checks)). Option **8** lists the
key options and the files:

![Summary](images/firmware-menu/menu-summary.png)

The image is `firmware/output/<profile>-<engine>.bin`.

### 6. Put it on the board

**This example board loads its firmware from an SD card**, so it is not flashed
from the menu: rename the `.bin` to the name the header asked for, copy it to a
FAT32 SD card, insert the card and power-cycle the board (details in
[Flashing the board](#flashing-the-board)).

Boards that can be flashed over USB use option **6 Flash Firmware**, shown next
with a Raspberry Pi Pico.

---

## Flashing the board

> **Away from the PC?** The [Firmware tab](firmware-tab.md) of the app downloads a firmware and flashes a Raspberry Pi Pico / RP2040 straight from the phone.

*Flash Firmware* (option **6**) has its own menu. The first half **lists what is
connected** without writing anything, so you can check that the board is
recognised before you flash; the second half flashes. Both come in four modes:

![Flash menu](images/firmware-menu/flash-menu.png)

| Mode | For | Lists |
|---|---|---|
| **USB** | A board running Klipper (or Katapult) over USB, or an Arduino-style USB board | `/dev/serial/by-id/usb-…` ports (CDC/ACM) |
| **UART** | A USB-serial adapter (CH340, FTDI, CP210x) wired to the board's UART bootloader; STM32 boards use `stm32flash`, AVR boards `avrdude` | USB-serial adapters |
| **USB (DFU mode)** | An STM32 in DFU mode (BOOT0, then reset: `0483:df11`) | DFU devices |
| **USB RP2040 mode** | A Raspberry Pi Pico / RP2040 held in BOOTSEL (`2e8a:0003`) | BOOTSEL devices |

### Example: a Raspberry Pi Pico

Build the Pico profile as above (architecture *Raspberry Pi RP2040/RP235x*,
communication *USB*). Then hold **BOOTSEL** while plugging the Pico in.

**1. List first.** Options 1–4 only look; they write nothing. With the Pico in
BOOTSEL, *USB RP2040 mode* finds it, while *USB* has nothing to show yet (the
hint says what a board of that kind looks like):

![Nothing found in USB mode](images/firmware-menu/flash-list-none.png)
![The Pico found in RP2040 mode](images/firmware-menu/flash-list-rp2040.png)

**2. Flash.** Option 8 (*Flash via USB RP2040 mode*) shows the devices of that
kind, asks which one and asks for confirmation:

![Choosing the device](images/firmware-menu/flash-pick-device.png)

Before writing, the menu validates the build again, rebuilds it and checks that it
is **byte-identical** to the validated image, then runs Klipper's flash rule. On
success it says so and offers the check of what the board is running:

![Flash OK](images/firmware-menu/flash-ok.png)

**3. Verify.** *Verify Flashed Firmware* (offered right after the flash, or
option **7** of the main menu) opens the board's serial port, asks for its data
dictionary the way Klipper does on connect and compares it with your build:

![Verify](images/firmware-menu/flash-verify.png)

`the board runs exactly the firmware that was built` is the real proof; a
different version or chip is reported as a failure, which also catches a flash
that did not take effect. The Pico now runs Klipper over USB, so *USB* lists it:

![The Pico found in USB mode](images/firmware-menu/flash-list-usb.png)

(In USB mode you can flash it again without touching BOOTSEL: the menu asks the
running firmware to restart in its bootloader.)

### When something goes wrong

If the flash fails the menu says so and shows the error, then waits for Enter.
For instance, a firmware for an RP2040 offered to a board in STM32 DFU mode is
refused before anything is written:

![A refused flash](images/firmware-menu/flash-error.png)

Safety checks before anything is written:

1. The build is validated again (hashes, format, configuration).
2. The mode must fit the firmware: DFU needs an STM32 build, RP2040 mode an
   RP2040/RP2350 build (a wrong image can brick a board).
3. The firmware is rebuilt and must be **byte-identical** to the validated image.
4. The device must exist; the write times out after four minutes instead of hanging.

The flash tools verify while they write where they can (`avrdude` and
`stm32flash -v` read back). It uses Klipper's own flash rule (`make flash`) with
the tools already in the image (`dfu-util`, `stm32flash`, `avrdude`, the RP2040
and SAM flashers).

### Limits

- **Linux** works out of the box (the container is started with access to `/dev`).
  On **Windows**, attach the board to WSL2 with
  [usbipd-win](https://learn.microsoft.com/windows/wsl/connect-usb); Docker
  Desktop on **macOS** cannot access USB, so flash `firmware/output/` with the
  tools of your system.
- Close the app or any profile that is using the board.
- Boards whose bootloader reads the firmware from an **SD card** are not flashed
  here: copy the image from `firmware/output/` to the card (renamed as the
  board's notes say). *Verify Flashed Firmware* still works afterwards.
- Checked on a Raspberry Pi Pico (BOOTSEL mode and Klipper-over-USB mode). The
  other modes use the same flash rules but have not been run on hardware.

---

## What the validation checks

Run by *Make Firmware* and by option **5**:

- The files exist, are not empty and match the hashes recorded at build time.
- The image format: Intel HEX checksums (AVR), UF2 blocks (RP2040), and for ARM
  `.bin` files a sane vector table linked at the configured bootloader offset.
- The image was built for the MCU and clock in the configuration, and its
  version is the engine's version.
- The baud rate is 250000 (the only one KlipPocket supports) or the board uses USB.
- The bootloader offset leaves room and the image fits the application area.
- The saved profile was not changed after the build.

The image carries the ARM (pinned gcc 10.3), AVR and host compilers, so ARM
Cortex-M chips (STM32, RP2040, LPC176x, SAM, HC32), AVR and the Linux process
build. The AR100 and PRU targets need other compilers and are not included.

---

## Troubleshooting

### Docker is missing

If Docker is not installed, not running, or your user may not use it, the menu
says so at the top and disables what depends on it. Choosing the engine/profile
and the summary still work. Install or start Docker and press `c`.

![Docker not installed](images/firmware-menu/menu-no-docker.png)

### PowerShell

The PowerShell menu is the same; only the symbols are plainer.

![PowerShell menu](images/firmware-menu/menu-powershell.png)

### Other questions

- *Which chip does my board have?* See [Identify the mainboard chip](#1-identify-the-mainboard-chip).
- *Can I use a pre-made configuration?* Copy a `.config` into `firmware/configs/`:
  it shows up in option 2. See [Adding a board](#adding-a-board).

---

## Other ways to get the firmware

The menu above is the way to go. These alternatives remain for special cases.

Both project builders emit a plain `klipper.bin`, renamed to the filename the
board's bootloader expects. They do **not** run `update_mks_robin.py` (that
XOR step is only for STM32F103 MKS Robin boards whose bootloader decrypts the SD
file; it corrupts the image for boards that flash it verbatim). They also refuse
to emit an image that would overlap the bootloader, and check the compiled size
against the flash application area.

Klipper has no MCU↔host version lock — the MCU sends its command dictionary to
the host on connect and the host adapts. The firmware does not have to match the
app's bundled Klipper exactly.

| Method | Notes |
|---|---|
| **Pre-built image** | The Beam Klipper project publishes firmware for many boards: <https://github.com/utkabobr/klipper/releases>. Older images work with this app; Klipper **0.13** is recommended. |
| **Docker (command line)** | One command per board that has a config in `firmware/configs/`. |
| **Local script** | One command. Downloads its own pinned toolchain. |

### Docker (command line)

Run from the repository root:

```bash
# build the image once (pins Klipper v0.13.0 + Kalico v2026.10.00 + ARM gcc 10.3-2021.07)
docker compose -f firmware/docker-compose.yml build

# build firmware for a board that has a config in firmware/configs/
docker compose -f firmware/docker-compose.yml run --rm fw <board>

# list the available board configs
docker compose -f firmware/docker-compose.yml run --rm fw
```

Output goes to `firmware/output/`. Use a different Klipper version with
`--build-arg KLIPPER_TAG=vX.Y.Z` on `docker compose ... build`.

### Local script

```bash
./scripts/build_firmware.sh <board | path/to/.config> [output-name] [klipper-tag]
```

`<board>` resolves to `firmware/configs/<board>.config`, or pass a full path to
any `.config` saved with `make menuconfig`. The first run downloads
`gcc-arm-none-eabi 10.3-2021.07` into `firmware/toolchain/` and clones Klipper
into `firmware/.klipper-build/` (both git-ignored).

Runs on **Linux** and **macOS** (needs `make`, `git`, `curl`, `tar`). On
**Windows** use `scripts\build_firmware.ps1 <board>` (wraps the Docker image), or
run the `.sh` inside WSL / Git Bash.

### Adding a board

#### 1. Identify the mainboard chip

The printer model is not enough — the same model often shipped with different
mainboards (STM32F103 / F401 / GD32 / …), each with a different memory map.
Flashing the wrong chip's firmware with a method that can write the bootloader
can brick the board. Check the board silkscreen, the sticker, or the OEM firmware
filename.

#### 2. Produce a `.config`

Use the menu (option 3), or on a PC with a Klipper checkout
(or `docker compose ... run --rm --entrypoint bash fw`):

```bash
cd /opt/klipper        # or your Klipper clone
make menuconfig
```

Settings that matter for KlipPocket:

- **Processor model** — match the chip exactly.
- **Bootloader offset** — most OEM boards have an 8–32 KiB bootloader that must be
  preserved (e.g. `CONFIG_FLASH_START_8000`). This is the main brick risk.
- **Communication interface** — KlipPocket uses **USB serial** through the
  device's OTG port. On most OEM boards this is an onboard USB-serial bridge
  wired to a UART, not the MCU's native USB. Select the UART the USB port is
  wired to.
- **Baud** — KlipPocket only supports **250000**.

A community "known-good" config for the board is a good starting point: load it,
run `make menuconfig` once to reconcile it, and save.

**Where to find ready-made board `.config` files:**

| Source | Notes |
|---|---|
| [`utkabobr/klipper` → `bin-templates/`](https://github.com/utkabobr/klipper/tree/master/bin-templates) | The Kconfig `.config` files the Beam Klipper pre-built firmware is generated from (~90 OEM boards). Closest match to this app; a `.json` sidecar marks SD-card ("robin") boards. |
| [`Klipper3d/klipper` → `config/`](https://github.com/Klipper3d/klipper/tree/master/config) | `printer-*.cfg` / `generic-*.cfg` — these are **printer.cfg** templates, not firmware `.config`, but their header comments state the chip, bootloader offset and comms wiring you need for `make menuconfig`. |
| [Klipper Community Discourse](https://klipper.discourse.group/) | Shared firmware configs by board. |
| Board vendor docs (BigTreeTech, MKS, Fysetc, TeamGloomy for STM32) | Per-board `make menuconfig` answers. |

#### 3. Add it to `firmware/configs/`

Save it as `firmware/configs/<board>.config`. An optional directive header lets
the builder name the output and guard the brick-critical lines:

```ini
#! board_name:      <human-readable name>
#! flash_filename:  <name the bootloader looks for, e.g. firmware.bin>
#! klipper_tag:     v0.13.0
#! assert: CONFIG_MCU="<chip>"
#! assert: <any CONFIG_ line that must survive `make olddefconfig`>
#! assert: CONFIG_SERIAL_BAUD=250000
```

`#! assert:` lines are re-checked after `make olddefconfig`; the build stops if
any is missing. `#! expect_sha256: <hash>` additionally fails the build unless the
output matches — useful for pinning a verified Klipper image (it is skipped for
Kalico builds). `#! flash_filename:` names the output exactly as the bootloader
of an SD-card board expects, so there is nothing to rename.

#### 4. Flash

- **SD-card boards** — copy the output to a FAT32 card, renamed to what the
  bootloader looks for, and power-cycle. The bootloader renames or deletes the
  file as confirmation and never writes itself; the worst case is "does not boot",
  recovered by flashing a known-good image the same way.
- **DFU / `make flash` boards** — use the menu's *Flash Firmware*, or follow
  Klipper's normal procedure on the PC.

#### 5. Connect

Plug the printer into the device (OTG). KlipPocket auto-detects the serial port;
the `[mcu] serial:` line in `printer.cfg` is ignored (keep a valid one for
portability). If the device path changes when the firmware restarts, use VID/PID
naming.

### Reproducibility

A clean Klipper tag + the pinned toolchain + the same `.config` produce a
byte-for-byte identical binary (Klipper omits build time and hostname for
clean-tree builds). The shipped example config uses `#! expect_sha256` so the
build fails if the result drifts. The menu's deep check (option 5) rebuilds from
scratch and compares the hashes the same way.

### Scratch directories

`firmware/output/`, `firmware/toolchain/` and `firmware/.klipper-build/` are
git-ignored build scratch. Commit only a verified `.bin` under
`firmware/<board-slug>/`.
