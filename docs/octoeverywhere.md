# Using OctoEverywhere

**Languages: [English](octoeverywhere.md) · [Português (BR)](pt-br/octoeverywhere.md) · [简体中文](zh-Hans/octoeverywhere.md)**

[OctoEverywhere](https://octoeverywhere.com) gives you remote access to your
printer (webcam, status, control) from octoeverywhere.com or its mobile app,
without opening ports on your router. KlipPocket runs the real OctoEverywhere
Klipper/Moonraker companion, adapted to run on-device instead of the
systemd service it normally installs as.

<p align="center"><img src="images/camera-octoeverywhere-settings.png" alt="Settings screen showing the Camera and Remote access sections" width="336"></p>

<p align="center"><img src="images/octoeverywhere-view.jpg" alt="OctoEverywhere quick view showing the printer webcam live, status and temperatures" width="480"></p>

## Enabling it

1. Start a printer profile (it has to be **running** — OctoEverywhere links
   to whichever profile is currently active).
2. Go to **Settings → Remote access** and turn on **Enable OctoEverywhere**.
3. Wait a few seconds for it to generate a printer ID, then tap **Link
   printer**. A QR code appears — scan it with your phone's camera (or open
   the link on the device itself) to finish linking it to your
   OctoEverywhere account, same as any other OctoEverywhere install.
   - If you see "Not ready yet", it just means the companion hasn't finished
     starting — wait a moment and tap **Link printer** again.
4. Once linked, your printer shows up at octoeverywhere.com and in the
   OctoEverywhere mobile app.
   - The companion only checks whether it's linked once, at connect time —
     it doesn't get a live update for a session that's already open. If
     "Go to Klipper" on octoeverywhere.com still says the printer isn't
     connected right after you finish linking, turn **Enable
     OctoEverywhere** off and back on once to force a reconnect.

## Webcam

OctoEverywhere finds its webcam feed the same way Fluidd/Mainsail do: through
Moonraker's own webcam list, not through anything KlipPocket registers
automatically. Enable the camera server and add it to Fluidd or Mainsail once
(config is shared between them) and OctoEverywhere picks it up automatically —
full walkthrough with screenshots: [`webcam.md`](webcam.md).

## Notes and limitations

- Only **one** printer profile can be linked at a time, even if you run
  several concurrently — OctoEverywhere follows whichever profile reached
  "running" first. Disable and re-enable after switching profiles to move it.
- OctoEverywhere's own crash telemetry (Sentry) is disabled by default; the
  actual remote-access connection to octoeverywhere.com is unaffected.
- This has not been hardware-tested end-to-end (i.e. an actual account
  linked and driven from octoeverywhere.com) — the companion runs and talks
  to Moonraker correctly, but please report any issues you hit while linking
  or using it remotely.

## Troubleshooting

- **Logs**: `octoeverywhere.log` lives alongside `klippy.log`/`moonraker.log`
  in that profile's `logs` folder — browse to it with a file manager app
  through KlipPocket's own storage entry (Android's Files app → "KlipPocket"),
  the same place you'd already look for Klipper/Moonraker logs.
- **Re-linking from scratch**: turn OctoEverywhere off, delete that profile's
  `octoeverywhere` folder from the app's private storage (not the public one
  above — a file manager with root, or `adb shell run-as`, is needed for
  this), then turn it back on to generate a fresh printer ID and link again.
  You can also just unlink/re-link the printer directly from your
  octoeverywhere.com account settings instead.
