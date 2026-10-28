# Using Happy Hare (multi-filament / MMU)

**Languages: [English](happy-hare.md) · [Português (BR)](pt-br/happy-hare.md) · [简体中文](zh-Hans/happy-hare.md)**

[Happy Hare](https://github.com/moggieuk/Happy-Hare) is the Klipper driver for
multi-material units (MMU): ERCF, Tradrack, BoxTurtle, 3MS, 3D Chameleon, HTLF,
KMS, MMX and many others. KlipPocket bundles **Happy Hare v4.1.0** and its
Moonraker component, for both Klipper and [Kalico](kalico.md). Nothing is turned
on until your config asks for it, so printers without an MMU are not affected.

> **Not tested with a physical MMU yet.** The bundled Happy Hare was checked by
> running its own test suite on the app's Python version and by starting Klipper
> and Kalico with a generated Tradrack config on simulated boards (the
> `mmu` objects load and the `MMU_*` commands answer). If you try it on real
> hardware, please report how it went.

## What you need

- An MMU board flashed with Klipper **USB** firmware. A board that only speaks
  **CAN** (for example an MMB in CAN mode) or only **UART** cannot be reached by
  the app; see [Multi-MCU](multi-mcu.md#what-works-and-what-does-not).
- The board reachable over USB from the phone/tablet, preferably through a
  powered hub. Motors and servos need their own power supply.
- A computer for a few minutes, to generate the Happy Hare config (see Step 1).
  Nothing has to be installed on the phone.

## Step 1 — Generate the Happy Hare config on a computer

Happy Hare writes its config from a guided menu, so you answer questions about
your MMU (type, number of gates, board, sensors) instead of editing pins by hand.
On a Linux or macOS computer:

```sh
git clone --branch v4.1.0 https://github.com/moggieuk/Happy-Hare.git
cd Happy-Hare
./install.sh -i -t
```

`-t` is Happy Hare's test mode: it writes the result to `/tmp/mmu_test` and does
not need Klipper or Moonraker installed. When it finishes, you will find in
`/tmp/mmu_test/printer_data/config`:

- the **`mmu`** folder (`base`, `macros`, `optional` and `mmu_vars.cfg`);
- the `printer.cfg` and `moonraker.conf` Happy Hare edits, showing the lines it
  adds (Steps 2 and 4 use them).

Use the same Happy Hare version as the app (v4.1.0): its config format changes
between versions, and Happy Hare refuses a config made by an older one.

## Step 2 — Copy the config into the app

1. Open the printer profile's config folder, the one that holds `printer.cfg`.
   Two ways to reach it: Fluidd/Mainsail's configuration file manager (create
   the folders and upload the files), or the KlipPocket entry in Android's Files
   app, which shows each profile's `config` folder.
2. Copy the whole **`mmu`** folder into it, so you get `mmu/base/…`,
   `mmu/macros/…` and `mmu/mmu_vars.cfg`.
3. Add these two lines near the top of `printer.cfg` (Happy Hare adds them in the
   test output):

   ```ini
   [include mmu/macros/*.cfg]
   [include mmu/base/*.cfg]
   ```

   If you chose the optional menu or client macros in the installer, also add
   the matching `[include mmu/optional/…]` line it wrote.
4. Make sure `printer.cfg` still has a `[pause_resume]` section, and that your own
   `PAUSE`, `RESUME` and `CANCEL_PRINT` macros are not defined twice (Happy Hare
   provides them). A duplicate stops Klipper with
   `gcode command … already registered`.

The generated `mmu/base/mmu_macro_vars.cfg` contains a `[save_variables]` block
whose `filename` is a path on the computer (for example
`/tmp/mmu_test/printer_data/config/mmu/mmu_vars.cfg`). **You do not need to
change it:** when that folder does not exist on the phone, the app uses the same
path under the printer profile's own config folder, so Happy Hare keeps its
state in `mmu/mmu_vars.cfg` next to your other files. Keep that file when you
back up or move the profile.

## Step 3 — Point Happy Hare at the board

Happy Hare's `mmu/base/mmu_hardware.cfg` declares the MMU board as an extra MCU:

```ini
[mcu unit0]
serial: -enter-device-path-
```

Plug the board in, open **Settings → USB → List USB devices**, and replace the
placeholder with the path shown for it. [Multi-MCU](multi-mcu.md) explains the
device list and the two naming modes. Re-run **List USB devices** if the path
changes after a re-flash.

## Step 4 — Turn on the Moonraker component

Happy Hare's Moonraker component pre-processes uploaded G-code for tool
changes and keeps Spoolman in sync. Add this to the printer profile's
`moonraker.conf`:

```ini
[mmu_server]
enable_file_preprocessor: True
enable_toolchange_next_pos: True
update_spoolman_location: True
```

Do **not** copy the `[update_manager happy-hare]` section from the installer's
`moonraker.conf`: the app updates Happy Hare with the app itself, and there is no
git checkout on the phone for the update manager to use.

Restart the printer profile. In Fluidd/Mainsail's console, `MMU_STATUS` should
print the MMU summary and `MMU_HELP` lists every command. The MMU's own log is
written next to `klippy.log`, as `mmu.log`.

## What works

Checked with a generated Tradrack config, on Klipper and on Kalico:

- Happy Hare starts (`Happy Hare v4.1.0 controlling 1 units`) with the app's
  `[save_variables]` handling.
- The `MMU_*` commands answer, such as `MMU_STATUS`, `MMU_HELP`,
  `MMU_TEST_CONFIG`, `MMU_GATE_MAP`, `MMU_TTG_MAP`, `MMU_ENDLESS_SPOOL`,
  `MMU_SENSORS`, `MMU_TEST_MOVE` and `MMU_STATS`.
- Moonraker loads `[mmu_server]` and finishes its start-up.
- Happy Hare's test suite (2,543 tests) passes on the Python version the app
  ships.

## What does not work, or is not tested

- **Real hardware:** gates, selector, servo, encoder, sensors and a full tool
  change were not run on a physical MMU.
- **CAN and UART boards** are not reachable (see "What you need").
- **Print Recovery** only saves and restores the active extruder, so a print
  interrupted by a power loss cannot be resumed on the right gate.
  See [Print recovery](print-recovery.md).
- **NFC/RFID readers and TD-1 scanners** load, but were not tried with a
  device. Decrypting Bambu/Creality NFC tags needs a library (`pycryptodome`)
  that the app does not bundle; Happy Hare then leaves that feature off.
- **Happy Hare's own UI pieces** for Mainsail and KlipperScreen are separate
  projects that talk to Moonraker. Fluidd/Mainsail in the app can send every
  `MMU_*` command from the console or macros.
- **Changing the MMU in the middle of a profile** (adding units) means running
  the installer again with your previous answers; copy the new `mmu` folder over
  the old one, but keep your `mmu/mmu_vars.cfg`.

## Quick troubleshooting

| Message | Likely cause |
|---|---|
| `MMU_STATUS` answers `Unknown command` | The `[include mmu/base/*.cfg]` line or the `mmu` folder is missing, so Happy Hare never loaded. |
| `Looks like Happy Hare is not installed correctly - cannot find happy_hare_version` | The config was not made by Happy Hare's installer. Generate it with v4.1.0 (Step 1). |
| `Looks like you upgraded (vX -> v4.1.0)?` | The config was generated by an older Happy Hare. Generate it again with v4.1.0. |
| `gcode command … already registered` | A macro in `printer.cfg` duplicates one Happy Hare provides (for example `PAUSE`, `RESUME` or `CANCEL_PRINT`). Remove yours. |
| `mcu 'unit0': Unable to connect` / `Unable to open serial port` | The `serial:` of `[mcu unit0]` is still the placeholder, the board has no USB firmware, or its path changed. |
| `Happy Hare's Klipper modules failed to load` | Report it. The `install.sh` advice in that message does not apply to the app. |
