# Klipper add-on mods bundled in KlipPocket

**Languages: [English](klipper-addons.md) · [Português (BR)](../pt-br/mods/klipper-addons.md) · [简体中文](../zh-Hans/mods/klipper-addons.md)**

These add-ons are bundled but inactive — Klipper loads one only when the matching
`[section]` is present in `printer.cfg`, so a configuration that does not
reference them is unaffected. They were selected to run under Android / Chaquopy
(Python 3.10, no on-device compiler, no user-level `pip`).

| Mod | Section to enable | Needs |
|---|---|---|
| KAMP (adaptive mesh + purge) | `[include KAMP/KAMP_Settings.cfg]` | `[exclude_object]` (core), `[bed_mesh]` |
| LED Effect | `[led_effect NAME]` | an addressable `[neopixel]` / `[dotstar]` |
| Z Calibration | `[z_calibration]` | probe + a fixed metal reference point |
| Auto Speed | `[auto_speed]` | nothing (measures skipped steps) |
| TMC Autotune | `[autotune_tmc stepper_x]` … | `[tmc2209]`/`[tmc5160]`/… already configured |
| Eddy-current probe | `[probe_eddy_current]` + `[ldc1612]` | BTT Eddy / LDC1612 sensor (Klipper **core**, already present) |

> Input shaper without an accelerometer: see
> [input-shaper-manual.md](input-shaper-manual.md).

---

## KAMP — Klipper Adaptive Meshing & Purging

Seeded automatically into `<instance>/config/KAMP/` on first Klipper start
(5 files, not overwritten if present).

**Enable:** add to `printer.cfg` (near the top, after `[exclude_object]`):

```ini
[include KAMP/KAMP_Settings.cfg]
```

Then open `KAMP/KAMP_Settings.cfg` in the Fluidd/Mainsail config editor and
uncomment the parts you want:

```ini
[include Adaptive_Meshing.cfg]   # probe only the area the print occupies
[include Line_Purge.cfg]         # purge line in front of the actual print
[include Smart_Park.cfg]         # park next to the print for final heat-soak
#[include Voron_Purge.cfg]       # blob purge on the Voron logo (needs the logo macro)
```

**Wire it into your start sequence** — in `PRINT_START`, replace the plain mesh /
purge calls:

```ini
BED_MESH_CALIBRATE ADAPTIVE=1     # instead of a plain BED_MESH_CALIBRATE
SMART_PARK                        # just before the final nozzle heat
LINE_PURGE                        # instead of a hand-written purge line
```

The slicer must emit `EXCLUDE_OBJECT_DEFINE` (OrcaSlicer / Creality Print: "Label
objects" on; PrusaSlicer/SuperSlicer: enable "Label objects"; Cura: *Exclude
Objects* plugin) — that's what tells KAMP where the print actually is.

Settings live in the `_KAMP_Settings` macro (purge amount, flow, margins,
smart-park height).

---

## LED Effect (`julianschill/klipper-led_effect`)

Animated effects for an addressable LED strip (progress bar, temperature
gradient, chamber lighting, "printing / heating / done" states).

**Enable:** you need an addressable LED first, e.g.:

```ini
[neopixel chamber]
pin: PA8
chain_count: 20
color_order: GRB

[led_effect panel_progress]
leds:
    neopixel:chamber
autostart: false
frame_rate: 24
layers:
    progress  10 0 add  (0,0,1),(0,1,0)
```

Commands: `SET_LED_EFFECT EFFECT=panel_progress`, `STOP_LED_EFFECTS`. Full layer
syntax: <https://github.com/julianschill/klipper-led_effect/blob/master/docs/LED_Effect.md>

Note: Happy Hare v4 ships its **own** `[mmu_led_effect]` for MMU status LEDs —
that's separate and independent from this `[led_effect]`.

---

## Z Calibration (`protoloft/klipper_z_calibration`)

Computes the live Z offset from three probes — nozzle on a metal point, probe on
the same point, probe on the bed — so it stays correct across nozzle changes and
temperature. Mainly useful with a switch/klicky/inductive probe and a fixed metal
tab near the bed.

**Enable:**

```ini
[z_calibration]
nozzle_xy_position:   <x>,<y>     # nozzle over the metal reference point
switch_xy_position:   <x>,<y>     # probe over the same point
bed_xy_position:      <x>,<y>     # a safe spot on the bed (mesh center is fine)
switch_offset:        0.5         # trigger-to-touch gap of your probe, start ~0.5
start_gcode:          <deploy probe macro, if dockable>
```

Run `CALIBRATE_Z` after homing + QGL/mesh. Docs:
<https://github.com/protoloft/klipper_z_calibration>

---

## Auto Speed (`Anonoei/klipper_auto_speed`)

Finds your real maximum acceleration and velocity by driving the toolhead and
detecting missed steps (compares commanded vs. measured stepper position after a
home). No accelerometer.

**Enable:**

```ini
[auto_speed]
margin: 20            # keep-out margin from the axis limits
```

`z` is not a config option — it's a param on the `AUTO_SPEED` gcode command
itself (`AUTO_SPEED Z=50`), if you want it to move to a given Z height first.

Run `AUTO_SPEED` (full sweep, ~several minutes) or `AUTO_SPEED_VELOCITY` /
`AUTO_SPEED_ACCEL`. It prints recommended `max_velocity` / `max_accel`. The
optional variance graph needs `matplotlib` (not installed) — the numeric result
does not. Docs: <https://github.com/Anonoei/klipper_auto_speed>

---

## TMC Autotune (`andrewmcgr/klipper_tmc_autotune`)

Computes good TMC driver register values (current control, `PWM`, `CoolStep`,
`StealthChop`/`SpreadCycle` thresholds) from the motor's datasheet instead of
hand-tuning. Refreshed to upstream `main` (2026-08).

**Enable** — one section per driver you already have configured:

```ini
[autotune_tmc stepper_x]
motor: ldo-42sth40-1004ah        # look up your motor in extras/motor_database.cfg
[autotune_tmc stepper_y]
motor: ldo-42sth40-1004ah
[autotune_tmc extruder]
motor: ldo-36sth20-1004ahg
```

If your motor isn't in `motor_database.cfg`, pick the closest or add it. Docs:
<https://github.com/andrewmcgr/klipper_tmc_autotune>

---

## Eddy-current probe (BTT Eddy, generic LDC1612) — Klipper core

No mod needed — `[probe_eddy_current]` and `[ldc1612]` are part of the vendored
Klipper 0.13. Wire it up per the Klipper docs
(<https://www.klipper3d.org/Eddy_Probe.html>). This is the **only** Android-safe
scanning-probe option (see next section).

### Why not Beacon / Cartographer

Both `beacon.py` and `cartographer.py` spin up a `multiprocessing.Process` to
stream samples, and Cartographer's model fitting wants `scipy`. `multiprocessing`
is not reliable under Chaquopy on Android (no safe `fork()` of the app process),
and `scipy` isn't installed. They are **not bundled**. If you have that hardware,
BTT-Eddy-style `[probe_eddy_current]` is the supported route.
