# Print recovery — resume a print after a power loss

**Languages: [English](print-recovery.md) · [Português (BR)](pt-br/print-recovery.md) · [简体中文](zh-Hans/print-recovery.md)**

If the printer loses power, the USB cable comes loose or Klipper shuts down in
the middle of a print, KlipPocket can offer to **continue that print from where
it stopped**. It is a best-effort feature: it works best on parts that are still
firmly stuck to the bed.

The phone keeps running (it has a battery), so the print position is safe even
when the printer is off.

<p align="center"><img src="images/powerless-recovery.png" alt="Fluidd asking whether to resume an interrupted print" width="720"></p>

## How it works

1. While printing, the position in the G-code file, the nozzle position, the
   temperatures, fan, speed/flow, Z offset and bed mesh are saved to a small
   file every **2 seconds** (configurable).
2. After the printer is back and Klipper is ready, Fluidd and Mainsail show a
   **"Print interrupted"** window asking whether to resume.
3. If you tap **Resume print**, the printer:
   1. heats the bed;
   2. lifts the nozzle, homes **X and Y only**, and moves to the **X stop**
      (away from the part) — optional, see `park_enable_x` / `park_enable_y`;
   3. heats the nozzle there, so nothing drips on the print;
   4. optionally purges a little filament — see `purge`;
   5. goes back to the saved position and keeps printing from the saved byte
      of the file.

**Discard** forgets the print, then lifts the nozzle away from the abandoned
part (**50 mm** by default) and homes X and Y — both configurable, see
`discard_lift_z`, `discard_home_x` and `discard_home_y`. If you closed the window, run
`PRINT_RECOVERY_STATUS` in the console to see it again (it also reappears
every minute while a print is waiting).

## Turn it on

Add this to `printer.cfg` (any place outside another section) and restart:

```ini
[print_recovery]
```

Everything below is optional; the values shown are the defaults. A setting
you do not need to change can simply be left out. A line with no value must not
be written at all (`park_x`, `park_y` and `state_file` are only added when you
really set them).

```ini
[print_recovery]
snapshot_interval: 2
park_enable_x: True
park_enable_y: True
park_speed: 100
lift_z: 10
discard_lift_z: 50
discard_home_x: True
discard_home_y: True
purge: True
purge_length: 20
purge_speed: 5
purge_retract: 2
min_extruded: 5
macro_variables: *
prompt: True
prompt_repeat: 60
```

## Settings, line by line

Numbers are plain decimals (`2`, `0.5`, `-6`). `True`/`False` also accept
`1`/`0`, `yes`/`no` and `on`/`off`.

| Setting | What it does | Accepted values |
|---|---|---|
| `snapshot_interval: 2` | Seconds between saves of the print position. Lower = a more exact resume, but more writes. | Number from `0.5` to `300` |
| `park_enable_x: True` | On resume, home X and move to the X stop to heat the nozzle away from the part. `False`: X is **not** homed or moved and is assumed to be exactly where it was. | `True` / `False` |
| `park_enable_y: True` | Same for Y. With both set to `False` the nozzle only lifts by `lift_z` and heats in place above the part, and the purge is skipped (it would drop filament on the print). Use it only if you are sure nothing moved. | `True` / `False` |
| `park_x: -6` | X position used to heat and purge after X is homed. **Leave the line out** to use the machine's X minimum, i.e. the X stop (e.g. `-6`, left of the bed, on some machines). | Number inside the X range |
| `park_y: 0` | Same for Y. **Leave the line out** to stay where Y homing left the nozzle (usually the front of the bed). | Number inside the Y range |
| `park_speed: 100` | Travel speed while parking, in mm/s. | Number greater than `0` |
| `lift_z: 10` | Millimetres the nozzle is lifted before X/Y are homed, so it clears the part. | Number `0` or more |
| `discard_lift_z: 50` | Millimetres the nozzle rises when you press **Discard**, to clear the abandoned part. | Number `0` or more (`0` = do not lift) |
| `discard_home_x: True` | Home X after **Discard**. | `True` / `False` |
| `discard_home_y: True` | Home Y after **Discard**. | `True` / `False` |
| `purge: True` | Purge a little filament before going back to the print. Only happens when X or Y is parked. | `True` / `False` |
| `purge_length: 20` | Millimetres of filament to purge. | Number `0` or more |
| `purge_speed: 5` | Purge speed in mm/s. | Number greater than `0` |
| `purge_retract: 2` | Millimetres retracted after purging, to avoid a string of filament. | Number `0` or more |
| `min_extruded: 5` | Millimetres that must be extruded before the first save, so nothing is saved for a print that has not really started. | Number `0` or more |
| `macro_variables: *` | Which macros have their `variable_xxx` values saved and restored. | `*` (all macros not starting with `_`), `none` (no macro), or a list such as `PRINT_START, MY_MACRO` |
| `prompt: True` | Show the "Print interrupted" window in Fluidd/Mainsail. `False`: only the console message and the commands below. | `True` / `False` |
| `prompt_repeat: 60` | Seconds between reminders while a print is waiting. | Number `0` or more (`0` = show once) |
| `state_file: ~/recovery.json` | Where the snapshot is kept. **Leave the line out** to keep it next to `printer.cfg`. | A file path (`~` is accepted) |

### Overriding values when resuming by hand

`PRINT_RECOVERY_RESUME` accepts the same settings as parameters, for that one
resume only:

```gcode
PRINT_RECOVERY_RESUME PARK_ENABLE_X=1 PARK_ENABLE_Y=0 PARK_X=-6 PURGE=0 PURGE_LENGTH=10 LIFT_Z=5
```

| Parameter | Same as | Accepted values |
|---|---|---|
| `PARK_ENABLE_X` | `park_enable_x` | `1`/`0`, `True`/`False`, `yes`/`no`, `on`/`off` |
| `PARK_ENABLE_Y` | `park_enable_y` | same as above |
| `PARK_X` | `park_x` | number |
| `PARK_Y` | `park_y` | number |
| `PURGE` | `purge` | same as above |
| `PURGE_LENGTH` | `purge_length` | number `0` or more |
| `LIFT_Z` | `lift_z` | number `0` or more |

`PRINT_RECOVERY_DISCARD` accepts `LIFT_Z` (number `0` or more), `HOME_X` and
`HOME_Y` (same on/off values as above). Anything you leave out uses the value
from `printer.cfg`, or the default.

## Macro variables

If your macros keep state in variables (`variable_xxx:` in their section, for
example a start macro or a layer counter), the values at the last snapshot are
saved and put back before the file is reopened. Only plain values (numbers,
text, `True`/`False`, lists) are kept. This does not undo what a macro did
outside its variables. Use `macro_variables` to save only some macros or none.

## Commands

| Command | What it does |
|---|---|
| `PRINT_RECOVERY_STATUS` | Shows the interrupted print and the window again |
| `PRINT_RECOVERY_RESUME` | Resumes it |
| `PRINT_RECOVERY_DISCARD` | Forgets it |

## After the printer comes back

- If Klipper shows an error such as *"Lost communication with MCU"*, press
  **Firmware Restart**. The window appears once Klipper is ready.
- Do **not** move the axes by hand: the resume assumes Z did not change.

## Limits — please read

- **Z is assumed unchanged.** Without power the motors let go. If the gantry
  dropped or was moved, the nozzle can touch the part. That is why the nozzle
  is lifted (`lift_z`) before anything moves.
- **The part cools down.** It can lift or warp, and a visible line usually
  remains at the resume height. Resume soon and only if the part is still stuck.
- A few seconds of printing before the interruption are printed again (the
  G-code is read ahead of the motion); this leaves a tiny bump at most.
- There is no power-loss detector on the printer board, so the nozzle cannot
  park or retract at the moment of the power cut.
- If the G-code file is deleted or changed, resuming is refused.
- **Delta printers are not supported:** they cannot home X and Y on their own.
- **Custom `G28`:** if your config overrides homing (for example a
  `homing_override` that also homes Z), the X/Y homing step of the resume can
  misbehave. Check that `G28 X Y` only homes X and Y.
- **Multi-filament and multi-tool setups** (MMU/Happy Hare, IDEX, tool
  changers) are not handled: only the active extruder is saved and restored.
- **Fans:** only the part-cooling fan (`[fan]`) is restored. Other fans
  (`fan_generic`, controller or auxiliary fans) go back to what your config or
  macros set.
- **Chamber:** chamber heaters and their temperatures are not saved or
  restored.
- The timelapse keeps the frames taken before the interruption. Moonraker
  records the resume as a new job in the history.
