# Using Kalico

**Languages: [English](kalico.md) · [Português (BR)](pt-br/kalico.md) · [简体中文](zh-Hans/kalico.md)**

[Kalico](https://github.com/KalicoCrew/kalico) is a community fork of Klipper
that allows features and behaviors Klipper keeps out because they can be risky.
KlipPocket bundles **Kalico v2026.10.00** next to Klipper, so you can pick the
one that runs your printer.

## Switching the engine

1. Stop the printer profile if it is running.
2. Open **Settings** and tap **Firmware engine** to switch between Klipper and
   Kalico.
3. Start the profile again.

The bundled versions are listed under **Settings → Software versions**. Your
printer profiles and files are not touched when you switch.

## One change your `printer.cfg` may need

Kalico turns `[respond]` on by default, which gives it a built-in `M118`. If your
config defines its own `[gcode_macro M118]`, Kalico stops with
`gcode command M118 already registered`. Replace the macro with a `[respond]`
section, which is valid in both Klipper and Kalico:

```ini
[respond]
```

## What works

Checked on a real printer (a Neptune 3 Pro, over USB), with the app's Fluidd:

- Starting, connecting to the printer board and the web interface.
- Kalico's built-in `M118` and `RESPOND`, `BED_MESH_CHECK` and
  `RELOAD_GCODE_MACROS` (reload macros without restarting).
- Kalico's defaults being active: `[respond]`, `[force_move]` and
  `[exclude_object]` load without being in the config.
- A real config loading without errors: KAMP, Timelapse macros, Auto Speed,
  `bed_mesh`, `probe`, `safe_z_home`, `input_shaper`, `pause_resume`,
  `display_status` and the filament sensor.

Load check only (the module imports and starts; not run in a print):
LED Effect, TMC Autotune, Print Recovery, the beeper and camera extras, and
Z Calibration.

## What does not work, or is not tested

- **Z Calibration** needs a physical Z endstop, on Klipper too. With a probe as
  the Z endstop (`endstop_pin: probe:z_virtual_endstop`) it stops with
  *A virtual endstop for z is not supported*.
- **Configs with `[gcode_macro M118]`** do not start on Kalico until the macro is
  replaced, as shown above.
- **Board firmware built for Klipper** works, but Kalico warns that it is
  recommended to re-flash it for Kalico.
- **Optional boards** (`is_non_critical`) are not tested.
- Kalico changes some behaviors, such as `controller_fan` gaining a
  `standby_speed`. See the
  [Kalico documentation](https://docs.kalico.gg/Kalico_Additions.html).
