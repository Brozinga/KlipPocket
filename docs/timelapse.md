# Timelapse

**Languages: [English](timelapse.md) · [Português (BR)](pt-br/timelapse.md) · [简体中文](zh-Hans/timelapse.md)**

KlipPocket bundles the Moonraker-timelapse component. During a print it takes
a photo of the printer at every layer, and when the print ends it turns those
photos into a video — all on the phone, no computer or cloud needed. You
control it from the **Timelapse** page of Fluidd or Mainsail.

## What you need

- The **camera server on** (Settings → Camera → *Enable camera server*), with
  a camera aimed at the printer — the phone's own camera or a USB webcam. See
  [webcam.md](webcam.md). The timelapse photos come from the camera server's
  snapshot address `http://127.0.0.1:8889/snapshot`, so nothing else has to be
  configured for it. (If you registered a webcam in Fluidd/Mainsail, its
  *Snapshot URL* must work too; `http://<phone-IP>:8889/snapshot` does.)
- The printer already running in Fluidd/Mainsail — see
  [getting-started.md](getting-started.md).

## 1. Turn the timelapse macros on

The macro file `timelapse.cfg` is already in your printer's config folder, but
the printer does not load it until you include it.

1. Open `printer.cfg` in the Fluidd/Mainsail editor
   (Fluidd: **{…} Configuration**, Mainsail: **Machine**).
2. Add this line at the very top:

   ```ini
   [include timelapse.cfg]
   ```

3. If your `printer.cfg` already has a `[gcode_macro HYPERLAPSE]` stub (some printer templates include one — a placeholder that only prints
   "timelapse not configured"), **delete that macro** so it cannot clash with
   the real one from `timelapse.cfg`.
4. Click **Save & Restart**.

After the restart, `TIMELAPSE_TAKE_FRAME`, `TIMELAPSE_RENDER` and
`HYPERLAPSE` exist, and the **Timelapse** page shows *Enabled* and
*Auto Render* switches.

<p align="center"><img src="images/timelapse/fluidd-timelapse-page.png" alt="Fluidd Timelapse page with a rendered video, the status panel and the settings" width="720"></p>

## 2. Make each layer take a photo

The default mode is **layermacro**: the slicer calls `TIMELAPSE_TAKE_FRAME`
once per layer. Put that command in your slicer's *layer change* G-code:

| Slicer | Where |
|---|---|
| PrusaSlicer / SuperSlicer | Printer Settings → Custom G-code → **Before layer change G-code** |
| OrcaSlicer | Printer settings → Machine G-code → **Before layer change G-code** (or the *Time lapse G-code* field) |
| Cura | Extensions → Post Processing → Modify G-Code → **Insert at layer change** |

Value to insert: `TIMELAPSE_TAKE_FRAME`. (Menu names change a little between
versions.)

**Prefer not to touch the slicer?** Switch the mode to **hyperlapse** in the
timelapse settings: a photo is taken every 30 seconds (configurable) for the
whole print, whatever the slicer does.

## 3. Print

Nothing else to do. Frames are collected while printing. When the print
finishes, **Auto Render** builds the video by itself; you can also press
**Render** on the Timelapse page at any time, or **Save Frames** to keep the
raw photos as a zip.

## Where the video is

Rendered videos appear on the **Timelapse** page of Fluidd (left menu,
*Timelapse*) and Mainsail (**TIMELAPSE**), next to a preview picture. Open a
file there to watch it, or use the file's menu to download it to your
computer.

Physically the files live in the app's private storage, in a folder per
printer:

| What | Path (inside the app's storage) |
|---|---|
| Finished videos (`timelapse_<file>_<date>.mp4` + `.jpg` preview) | `files/instance/<printer-id>/public/timelapses/` |
| Photos being collected for the next video | `files/instance/<printer-id>/timelapse_frames/` |

On the phone that is `/data/data/com.brozinga.klippocket/files/…`. Android
does not let file managers or the gallery open that folder, so **use the web
page to get your videos out**. You can also download one directly from a
browser on the same network:

```
http://<phone-IP>:4408/server/files/timelapse/<video-name>.mp4     (Fluidd)
http://<phone-IP>:4409/server/files/timelapse/<video-name>.mp4     (Mainsail)
```

> ⚠️ Videos are stored together with the printer profile. **Deleting the
> printer in the app, or uninstalling the app, deletes its videos.** Download
> the ones you want to keep first.

## About the video

- It is encoded on the phone with Android's hardware H.264 encoder, so it needs
  no extra software and is quick. The picture is scaled to fit within
  **1280×720**.
- The output frame rate, quality and rotation/flip can be changed under
  **Render Settings** on the Timelapse page.
- The photos are removed automatically when the next print starts.

## Trying it without printing

You can check the whole chain on the bench, with the printer idle:

1. In the console, run `_TIMELAPSE_NEW_FRAME HYPERLAPSE=FALSE` five or more
   times (one photo each, no head movement).
2. On the Timelapse page, press **Render**.
3. A new `.mp4` appears in the list within a few seconds.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Unknown command: TIMELAPSE_TAKE_FRAME` | `[include timelapse.cfg]` is missing, or Klipper was not restarted. |
| Klipper reports a problem with `HYPERLAPSE` | Delete the old `[gcode_macro HYPERLAPSE]` stub (step 1.3). |
| Frame counter stays at 0 | The camera server is off, or the snapshot address is not reachable. Open `http://<phone-IP>:8889/snapshot` in a browser: it should show a picture. |
| Render fails right away | Use an app version that includes the timelapse fix — earlier builds could not render at all. |
| Nothing in the list after the print | Turn on **Auto Render**, or press **Render**; check the layer-change G-code really contains `TIMELAPSE_TAKE_FRAME`. |
