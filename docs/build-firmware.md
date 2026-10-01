# Building MCU firmware

**Languages: [English](build-firmware.md) · [Português (BR)](pt-br/build-firmware.md) · [简体中文](zh-Hans/build-firmware.md)**

KlipPocket runs the Klipper/Kalico host on Android. The printer mainboard still
needs MCU firmware, built and flashed once on a PC (Android cannot compile).

There are three ways to obtain it:

| Method | Notes |
|---|---|
| **Pre-built image** | The Beam Klipper project publishes firmware for many boards: <https://github.com/utkabobr/klipper/releases>. Older images work with this app; Klipper **0.13** is recommended. |
| **Docker** | One command. Nothing to install except Docker. Reproducible. |
| **Local script** | One command. Downloads its own pinned toolchain. |

Both project builders emit a plain `klipper.bin`, renamed to the filename the
board's bootloader expects. They do **not** run `update_mks_robin.py` (that
XOR step is only for STM32F103 MKS Robin boards whose bootloader decrypts the SD
file; it corrupts the image for boards that flash it verbatim). They also refuse
to emit an image that would overlap the bootloader, and check the compiled size
against the flash application area.

Klipper has no MCU↔host version lock — the MCU sends its command dictionary to
the host on connect and the host adapts. The firmware does not have to match the
app's bundled Klipper exactly.

---

## Docker

Run from the repository root:

```bash
# build the image once (pins Klipper v0.13.0 + ARM gcc 10.3-2021.07)
docker compose -f firmware/docker-compose.yml build

# build firmware for a board that has a config in firmware/configs/
docker compose -f firmware/docker-compose.yml run --rm fw <board>

# list the available board configs
docker compose -f firmware/docker-compose.yml run --rm fw
```

Output goes to `firmware/output/`. Use a different Klipper version with
`--build-arg KLIPPER_TAG=vX.Y.Z` on `docker compose ... build`.

## Local script

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

---

## Adding a board

### 1. Identify the mainboard chip

The printer model is not enough — the same model often shipped with different
mainboards (STM32F103 / F401 / GD32 / …), each with a different memory map.
Flashing the wrong chip's firmware with a method that can write the bootloader
can brick the board. Check the board silkscreen, the sticker, or the OEM firmware
filename.

### 2. Produce a `.config`

On a PC with a Klipper checkout (or `docker compose ... run --rm --entrypoint bash fw`):

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

### 3. Add it to `firmware/configs/`

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
output matches — useful for pinning a verified image.

### 4. Flash

- **SD-card boards** — copy the output to a FAT32 card, renamed to what the
  bootloader looks for, and power-cycle. The bootloader renames or deletes the
  file as confirmation and never writes itself; the worst case is "does not boot",
  recovered by flashing a known-good image the same way.
- **DFU / `make flash` boards** — follow Klipper's normal procedure, on the PC.

### 5. Connect

Plug the printer into the device (OTG). KlipPocket auto-detects the serial port;
the `[mcu] serial:` line in `printer.cfg` is ignored (keep a valid one for
portability). If the device path changes when the firmware restarts, use VID/PID
naming.

---

## Reproducibility

A clean Klipper tag + the pinned toolchain + the same `.config` produce a
byte-for-byte identical binary (Klipper omits build time and hostname for
clean-tree builds). The shipped example config uses `#! expect_sha256` so the
build fails if the result drifts.

## Scratch directories

`firmware/output/`, `firmware/toolchain/` and `firmware/.klipper-build/` are
git-ignored build scratch. Commit only a verified `.bin` under
`firmware/<board-slug>/`.
