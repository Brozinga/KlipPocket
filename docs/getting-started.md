# Getting started — install KlipPocket and set up your first printer

**Languages: [English](getting-started.md) · [Português (BR)](pt-br/getting-started.md) · [简体中文](zh-Hans/getting-started.md)**

A step-by-step guide for people who have never done this before. The steps
work for any printer that already runs Klipper firmware.

> The screenshots were taken on an Android phone and the
> Fluidd/Mainsail pages in Chrome. Your phone may look a little different
> (especially the Android install screens), but the order of the steps is the
> same. Fluidd shows its messages in your browser's language.

## What you need

- An Android phone or tablet (Android 5.0 or newer) that will stay plugged in
  next to the printer.
- A **USB OTG cable/adapter** to connect the printer to the phone.
- A printer that already runs **Klipper firmware** (if yours does not, see
  [build-firmware.md](build-firmware.md)).
- The phone and the computer you will use to open the web page must be on the
  **same Wi‑Fi network**.

## 1. Download the right APK

Download the `.apk` from the project's **Releases** page. There are three
versions — pick the one that matches your phone's processor:

| APK | Use it on |
|---|---|
| `arm64` | Most phones from about 2017 on (64‑bit) |
| `armv7` | Older phones and tablets (32‑bit) — **if you are not sure, choose this one**, it also runs on 64‑bit phones |
| `amd64` | Emulators and Intel/AMD Android devices |

## 2. Install the APK

1. Open your phone's **Files** (or **My Files**) app and go to **Downloads**.
2. Tap the APK file.

<p align="center"><img src="images/setup/01-apk-in-downloads.png" alt="The KlipPocket APK in the Downloads folder" width="288"></p>

3. Android asks if you want to install the app. Tap **Install**. (If it says
   installing from this source is blocked, tap **Settings** and allow it for
   your Files app, then go back.)

<p align="center"><img src="images/setup/02-install-prompt.png" alt="Install prompt" width="288"></p>

4. Google Play Protect may recommend a scan, because the app does not come
   from the Play Store. Tap **Scan app** and wait a few seconds — once the
   check says it is safe, the installation continues by itself. (Avoid
   **Install without scanning**: on some devices it leaves the installation
   stuck on "Installing…".)

<p align="center"><img src="images/setup/03-play-protect.png" alt="Play Protect: App scan recommended" width="288"></p>

5. Wait for **Installing…** to finish, then tap **Open**.

<p align="center"><img src="images/setup/05-installing.png" alt="Installing" width="288"></p>

> **"Installing…" never finishes?** On some devices the Google Play Protect
> check gets stuck and the install hangs for minutes (the app itself is
> fine). Tap **Cancel**, open the APK again and choose **Scan app**: once Play
> Protect approves it, the install finishes within seconds. Nothing needs to be
> turned off.

## 3. First launch: battery settings

The app has to keep running in the background while it prints. On the first
screen switch on both options and tap **Next**; when Android asks to stop
battery optimization for KlipPocket, tap **Allow**.

<p align="center"><img src="images/setup/07-battery-dialog.png" alt="Battery optimization prompt" width="288"> <img src="images/setup/08-battery-done.png" alt="Both battery options on" width="288"></p>

You land on the main screen. It is empty until you add a printer. (You can
change the app language any time in **Settings → App language**.)

<p align="center"><img src="images/setup/09-main-empty.png" alt="Empty main screen" width="288"></p>

## 4. Add your printer

1. Tap the big **+** button at the bottom.
2. **Name** — anything you like, for example `Printer 1`.
3. **Config** — tap it and pick the starting configuration for your printer
   model. The list is long and sorted by name; models start with `printer-`
   followed by the brand. Choose the one that matches your printer. If your model is not listed, pick
   `example-cartesian.cfg` and edit it later (see step 7).
4. **Autostart** — switch it on if you want this printer to start by itself
   whenever the app opens.
5. Tap **Create**.

<p align="center"><img src="images/setup/10-new-profile.png" alt="New profile screen" width="216"> <img src="images/setup/11-config-list.png" alt="Config list" width="216"> <img src="images/setup/12-pick-config.png" alt="Choosing the printer config" width="216"> <img src="images/setup/13-new-profile-filled.png" alt="Filled-in form" width="216"></p>

Your printer now appears under **Instances**, marked **Idle**.

<p align="center"><img src="images/setup/14-instance-created.png" alt="Printer created" width="288"></p>

## 5. Start it and allow USB access

1. Plug the printer into the phone with the OTG cable.
2. Tap the **▶ play** button (the big one at the bottom starts every printer;
   the small one on the card starts just that printer).
3. Android asks **"Allow KlipPocket to access USB Serial?"** — tick
   **Always open KlipPocket when USB Serial is connected** and tap **OK**.

<p align="center"><img src="images/setup/16-usb-permission.png" alt="USB permission prompt" width="288"></p>

> Missed the question, or tapped Cancel? Fully close the app and open it again
> — the question comes back. If the printer shows **MCU error during connect**
> in Fluidd/Mainsail, this is almost always the reason.

The card changes to **Running** and the address of the web page appears at
the top. Mainsail is `http://<phone-IP>:4409/` and Fluidd is
`http://<phone-IP>:4408/`.

<p align="center"><img src="images/setup/15-starting.png" alt="Printer running with the web address shown" width="288"></p>

## 6. Open Fluidd or Mainsail

On a computer or another phone on the same Wi‑Fi, open the address shown in
the app. To switch between the two interfaces go to **Settings → Web
frontend** and tap the tile.

<p align="center"><img src="images/setup/17-settings-frontend.png" alt="Settings: Web frontend tile" width="288"></p>

## 7. Fix the "missing configuration" warnings

The first time, both interfaces will complain that a few standard printer
features are missing from `printer.cfg` (pause/resume, cancel print, …). This
is normal — add them once and it is done.

**Mainsail** shows an orange **Missing configuration** box:

<p align="center"><img src="images/setup/20-mainsail-missing-config.png" alt="Mainsail missing configuration" width="720"></p>

**Fluidd** shows a **warnings** box with the same items:

<p align="center"><img src="images/setup/30-fluidd-missing-config.png" alt="Fluidd warnings" width="720"></p>

### The lines to add

Add this block to the top of `printer.cfg` (any place outside another
section works). **The two-space indentation on the lines under `gcode:` is
required.**

```ini
[pause_resume]

[display_status]

[gcode_macro CANCEL_PRINT]
description: Cancel the actual running print
rename_existing: CANCEL_PRINT_BASE
gcode:
  TURN_OFF_HEATERS
  CANCEL_PRINT_BASE

[gcode_macro PAUSE]
rename_existing: PAUSE_BASE
gcode:
  PAUSE_BASE

[gcode_macro RESUME]
rename_existing: RESUME_BASE
gcode:
  RESUME_BASE
```

### In Mainsail

1. Click **MACHINE** in the left menu.
2. In **Config Files**, click **printer.cfg**.

<p align="center"><img src="images/setup/21-mainsail-machine-page.png" alt="Mainsail Machine page" width="720"></p>

3. The editor opens. Click on an empty line near the top and paste the block.
4. Click **SAVE & RESTART** (top right).

<p align="center"><img src="images/setup/22-mainsail-printer-cfg-editor.png" alt="Mainsail editor before" width="720"></p>
<p align="center"><img src="images/setup/23-mainsail-cfg-added-lines.png" alt="Mainsail editor with the block pasted" width="720"></p>

After the restart the orange box is gone:

<p align="center"><img src="images/setup/24-mainsail-after-restart.png" alt="Mainsail dashboard without warnings" width="720"></p>

### In Fluidd

1. Click the **{…} Configuration** icon in the left menu.
2. In **Configuration files**, click **printer.cfg**.

<p align="center"><img src="images/setup/31-fluidd-configuration-page.png" alt="Fluidd configuration page" width="720"></p>

3. Click on an empty line near the top and paste the block. If you type it by
   hand instead of pasting, Fluidd's editor does **not** indent for you — the
   red lines in the editor mean the two spaces are missing.
4. Click **SAVE AND RESTART** in the top bar.

<p align="center"><img src="images/setup/32-fluidd-printer-cfg-editor.png" alt="Fluidd editor before" width="720"></p>
<p align="center"><img src="images/setup/33-fluidd-cfg-added-lines.png" alt="Fluidd editor with the block added" width="720"></p>

The warnings disappear and the **CANCEL_PRINT**, **PAUSE** and **RESUME**
buttons show up on the dashboard:

<p align="center"><img src="images/setup/34-fluidd-after-restart.png" alt="Fluidd dashboard after the fix" width="720"></p>

## 8. If Moonraker complains about `[virtual_sdcard]`

KlipPocket writes the `[virtual_sdcard]` section for you, with the correct
folder for each printer, near the bottom of `printer.cfg` (just above the
`#*# <--- SAVE_CONFIG --->` line). **Do not add your own.**

If a message like *"GCode path received from Klipper does not match expected
location"* still appears — usually after copying a `printer.cfg` from another
setup, or after recreating the printer — open `printer.cfg`, find
`[virtual_sdcard]` and set `path:` to **exactly** the path written in the
message (or delete your own copy of the section and restart, so the app writes
a fresh one). Then click **Save & Restart**.

## 9. Adjust the rest for your printer

The starting file is a generic preset. Check the `[mcu]`, pins, bed size
(`position_max`), `z_offset`, PID and mesh values against your printer, and
calibrate (PID, Z offset, bed mesh) before the first print. If your printer
does not run Klipper yet, see [build-firmware.md](build-firmware.md).

## Next steps

- Add a webcam and use **tap to focus** on the preview: [webcam.md](webcam.md).
- Remote access: [octoeverywhere.md](octoeverywhere.md) · [obico.md](obico.md).
- Optional add-ons: [mods/klipper-addons.md](mods/klipper-addons.md).
