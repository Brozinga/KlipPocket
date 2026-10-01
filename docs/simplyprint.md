# Using SimplyPrint

**Languages: [English](simplyprint.md) · [Português (BR)](pt-br/simplyprint.md) · [简体中文](zh-Hans/simplyprint.md)**

[SimplyPrint](https://simplyprint.io) is a 3D printing platform with remote
monitoring, print management and AI failure detection. The Moonraker bundled
in KlipPocket already includes the SimplyPrint connection, so there is nothing
to install: you only turn it on in Moonraker's configuration and link the
printer to your SimplyPrint account.

<p align="center"><img src="images/simply-print-view.png" alt="SimplyPrint dashboard showing the printer, live webcam and temperatures" width="640"></p>

## What you need

- A printer profile **running** in KlipPocket, with Fluidd or Mainsail open in
  your browser — see [`getting-started.md`](getting-started.md).
- A free account on [simplyprint.io](https://simplyprint.io).

## 1. Turn it on in `moonraker.conf`

1. Open `moonraker.conf` in the Fluidd/Mainsail editor
   (Fluidd: **{…} Configuration**, Mainsail: **Machine**).
2. Add this line at the very bottom, if it is not there yet:

   ```ini
   [simplyprint]
   ```

   > Check first that the file does not already have a `[simplyprint]`
   > section. A duplicated section makes Moonraker refuse to start.

3. Click **Save & Restart**.

## 2. Get the setup code

1. Wait a few seconds for Moonraker to come back, then click the **bell**
   icon in the top bar of Fluidd/Mainsail.
2. Open the **SimplyPrint Setup Request** notification and copy the setup
   code it shows.

## 3. Link the printer

1. Go to [simplyprint.io](https://simplyprint.io) and log in.
2. Choose **Add Printer** and paste the setup code.
3. Confirm the connection. The printer now shows up in your SimplyPrint
   dashboard.

If the printer already appears under **Pending Printers** on SimplyPrint, you
can add it from there without typing the code.

## Webcam

SimplyPrint gets the camera the same way Fluidd/Mainsail do: through
Moonraker's own webcam list. Enable the camera server and add it to Fluidd or
Mainsail once (the configuration is shared between them) and SimplyPrint picks
it up — full walkthrough with screenshots: [`webcam.md`](webcam.md).

## Notes

- Each printer profile has its own `moonraker.conf`, so repeat the steps for
  every profile you want in SimplyPrint.
- SimplyPrint needs Moonraker v0.8.0 or newer; the one bundled here is newer.

## Official guide

SimplyPrint's own instructions for Klipper printers:
<https://simplyprint.io/setup-guide/methods/klipper-powered>
