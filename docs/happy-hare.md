# Using Happy Hare (multi-filament / MMU)

**Languages: [English](happy-hare.md) · [Português (BR)](pt-br/happy-hare.md) · [简体中文](zh-Hans/happy-hare.md)**

[Happy Hare](https://github.com/moggieuk/Happy-Hare) is the Klipper driver for
multi-material units (MMU): ERCF, Tradrack, BoxTurtle, 3MS, 3D Chameleon, HTLF,
KMS, MMX and many others. KlipPocket bundles **Happy Hare v4.1.0** and its
Moonraker component, for both Klipper and [Kalico](kalico.md). Nothing is turned
on until your config asks for it, so printers without an MMU are not affected.

You set Happy Hare up **in the app** (or in the [dashboard](dashboard.md)): you
answer the same questions as Happy Hare's own install menu and KlipPocket writes
the files, so no computer is needed.

## What you need

- An MMU board flashed with Klipper **USB** firmware, connected to the
  phone/tablet (preferably through a powered hub). Motors and servos need their
  own power supply.
- A printer profile already created in KlipPocket.

## Set up in the app

1. Open **Settings → Happy Hare** and choose the printer (with a single printer
   it opens straight away). Tap **Set up Happy Hare**.

   <p align="center"><img src="images/happy-hare/app/01-settings.jpg" alt="Settings with the Happy Hare entry" width="240"> <img src="images/happy-hare/app/02-home.jpg" alt="Set up Happy Hare" width="240"></p>

2. Choose your MMU. Happy Hare fills in the defaults its designer recommends
   for it.

   <p align="center"><img src="images/happy-hare/app/03-mmu.jpg" alt="The list of MMUs" width="240"></p>

3. Go through the questions. They are Happy Hare's own menu, so they are shown
   in English. Open a section to see its questions; tap one to change it, and use
   **Restore the default** in its window to undo a change. A greyed switch is
   fixed by your other answers. Questions that depend on an answer appear right
   below it (for example the Turtle Neck version under the MMU type).

   <p align="center"><img src="images/happy-hare/app/04-questions.jpg" alt="The questions" width="240"></p>

4. Open **MCU connection** and set the device of the MMU board. Under
   **Enter serial device path**, pick the connected board from the list or type
   its path (see **Settings → USB → List USB devices**).

   <p align="center"><img src="images/happy-hare/app/05-mcu.jpg" alt="MCU connection" width="240"> <img src="images/happy-hare/app/06-device-path.jpg" alt="Serial device path" width="240"></p>

5. Back on the first page, tap **Review and create**. If **Answers still
   missing** is listed, go back and fill them in. Then choose:

   - **Add Happy Hare to printer.cfg and moonraker.conf**: on, KlipPocket adds
     the lines for you (a backup of each file is kept). Off, you add them
     yourself, see [Adding the lines yourself](#adding-the-lines-yourself).
   - **Also reset calibration and gate state**: on when you picked a different
     MMU. Leave it off when you only changed settings, to keep the calibration in
     `mmu/mmu_vars.cfg`.

   The `mmu` folder of the printer is deleted and created again, so changes made
   by hand inside it are lost. Tap **Create the configuration**.

   <p align="center"><img src="images/happy-hare/app/07-review.jpg" alt="Review and create" width="240"> <img src="images/happy-hare/app/08-done.jpg" alt="Configuration created" width="240"></p>

6. Restart the printer profile (or send **Firmware Restart**) so Klipper loads
   Happy Hare.

## Set up in the dashboard

The [dashboard](dashboard.md) has the same steps in its **Happy Hare** tab, next
to Settings: choose the printer, **Set up Happy Hare**, your MMU, the questions,
**Review and create**. In **MCU connection** a list offers the USB devices the
phone sees.

<p align="center"><img src="images/happy-hare/web/01-home.jpg" alt="Happy Hare tab" width="420"> <img src="images/happy-hare/web/02-mmu.jpg" alt="Choose the MMU" width="420"></p>
<p align="center"><img src="images/happy-hare/web/04-usb.jpg" alt="MCU connection" width="420"> <img src="images/happy-hare/web/05-review.jpg" alt="Review and create" width="420"></p>

## Adding the lines yourself

If you left **Add Happy Hare to printer.cfg and moonraker.conf** off, the last
screen shows the lines to add (or tap **Add them now** to let KlipPocket do it).

<p align="center"><img src="images/happy-hare/app/09-manual.jpg" alt="Lines to add" width="240"> <img src="images/happy-hare/web/07-manual.jpg" alt="Lines to add in the dashboard" width="420"></p>

Open the printer profile's config folder, the one that holds `printer.cfg`
(Fluidd/Mainsail's configuration file manager, or the KlipPocket entry in
Android's Files app), and:

1. Add the `[include …]` lines shown to the **top** of `printer.cfg`. They are
   usually:

   ```ini
   [include mmu/base/*.cfg]
   [include mmu/macros/*.cfg]
   ```

   plus `[include mmu/optional/client_macros.cfg]` (and `mmu_menu.cfg`) when
   those options are on; the screen shows exactly which.
2. Add this to the **end** of `moonraker.conf`:

   ```ini
   [mmu_server]
   enable_file_preprocessor: True
   enable_toolchange_next_pos: True
   update_spoolman_location: True
   ```

   Do not add an `[update_manager happy-hare]` section: the app updates Happy
   Hare together with the app.
3. Make sure `printer.cfg` has a `[pause_resume]` section and does not define
   its own `PAUSE`, `RESUME` or `CANCEL_PRINT` (Happy Hare provides them). A
   duplicate stops Klipper with `gcode command … already registered`.
4. Restart the printer profile.

## Change the MMU or the settings later

Open **Settings → Happy Hare** and the printer again.

<p align="center"><img src="images/happy-hare/app/10-installed.jpg" alt="A printer with Happy Hare" width="240"></p>

- **Edit the settings** opens the answers you saved; change what you need and
  create the configuration again.
- **Change the MMU (start over)** starts from the defaults of the MMU you pick.
  Creating the configuration deletes the old `mmu` folder and writes a new one
  (leave **Also reset calibration and gate state** on). The USB board you used
  before is offered again, so you do not have to pick it twice.

## See the MMU in the web interfaces

Once Klipper has loaded Happy Hare, the Fluidd and Mainsail that come with the
app show the MMU on their **Dashboard** page, with nothing to switch on: Fluidd
has an **MMU** card and Mainsail an **MMU** panel, with the gates, the selected
tool, the filament state and the buttons to preload, eject and recover. Move or
hide them with each interface's dashboard layout options.

In the console of Fluidd or Mainsail, `MMU_STATUS` prints the MMU summary and
`MMU_HELP` lists every command. Happy Hare's own log is written next to
`klippy.log`, as `mmu.log`.

[KlipperScreen for Happy Hare](https://github.com/moggieuk/KlipperScreen-Happy-Hare-Edition)
is a separate project for touchscreens.

## Quick troubleshooting

| Message | Likely cause |
|---|---|
| `MMU_STATUS` answers `Unknown command` | The `[include mmu/base/*.cfg]` line is missing from `printer.cfg`, or the `mmu` folder is not there. |
| `gcode command … already registered` | A macro in `printer.cfg` duplicates one Happy Hare provides (for example `PAUSE`, `RESUME` or `CANCEL_PRINT`). Remove yours. |
| `mcu 'unit0': Unable to connect` / `Unable to open serial port` | The board has no USB firmware, or its path changed after a re-flash: pick it again under **MCU connection** and create the configuration again. |
| `Happy Hare's Klipper modules failed to load` | Report it. The `install.sh` advice in that message does not apply to the app. |
