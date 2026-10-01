# Input shaper without an accelerometer (ringing-tower method)

**Languages: [English](input-shaper-manual.md) · [Português (BR)](../pt-br/mods/input-shaper-manual.md) · [简体中文](../zh-Hans/mods/input-shaper-manual.md)**

Klipper's automatic resonance testing (`TEST_RESONANCES`, `SHAPER_CALIBRATE`)
needs an ADXL345 / LIS2DW / MPU-9250 accelerometer on the toolhead. Many printers
do not have one, and adding one on KlipPocket requires an SPI/I²C sensor wired to
the printer MCU (the Android device cannot help).

The **ringing-tower test** produces the same two numbers by eye and calipers:

1. the **ringing frequency** for X and for Y → `shaper_freq_x` / `shaper_freq_y`
2. a safe **max acceleration** as a bonus

It is the method the Klipper docs themselves describe for the no-accelerometer
case: <https://www.klipper3d.org/Resonance_Compensation.html>

---

## 1. One-time setup

Add an **empty** input shaper section to `printer.cfg` (so the test can toggle it
off, and so you have somewhere to write the result):

```ini
[input_shaper]
```

Add the test macro (bottom of this file) to `printer.cfg` or a `[include]`d file.

Download Klipper's ringing test model **`ringing_tower.stl`**:
<https://github.com/Klipper3d/klipper/raw/master/docs/prints/ringing_tower.stl>

---

## 2. Slice it (this matters — get it wrong and the numbers are garbage)

| Setting | Value |
|---|---|
| Layer height | 0.2–0.25 mm |
| Walls / perimeters | 2 |
| Top / bottom layers | **0** |
| Infill | **0 %** |
| External perimeter speed | **100 mm/s**, fixed — write this number down as `V` |
| Slowdown for short layers / min layer time | **off** (0 s) |
| Dynamic / auto speed, "outer wall speed" ramps | off |
| Input shaper / "vibration"/"ringing" slicer options | off |
| Print the tower **near the centre** of the bed | yes |

The tower must be printed with a **constant** outer-wall speed. If your slicer
insists on slowing the first few layers, raise the minimum layer time to 0 and
disable "cooling" speed limits for this print only.

---

## 3. Run the test

In the Fluidd/Mainsail console, after the bed + nozzle are at temp and the
printer is homed:

```
RINGING_TOWER
```

That macro:
- disables input shaping and pressure advance,
- sets square-corner-velocity low and cruise-ratio to 0 (so acceleration is what
  actually drives the corners),
- arms a `TUNING_TOWER` that **raises acceleration by 300 mm/s² every 5 mm of Z**,
  starting at 1500 mm/s².

Now start the print of `ringing_tower.stl` from the file list. Watch it; if the
head starts skidding / skipping badly, stop it — everything below that height is
still valid.

---

## 4. Read the tower

You get **two** results.

### 4a. Max acceleration

Find the Z height where the ringing (the ghost echoes after each notch) becomes
acceptably small. Acceleration at height `Z` is:

```
accel = START + FACTOR * floor(Z_mm / BAND)
      = 1500  + 300    * floor(Z_mm / 5)
```

Example: clean by Z = 40 mm → `1500 + 300 * 8 = 3900 mm/s²`. Use ~**80 %** of
that as `max_accel` for real prints.

### 4b. Ringing frequency (the important one)

The tower has notches on the **X faces** and the **Y faces**. Ringing shows up as
a decaying wave *after* each notch, on the face **perpendicular to the axis that
moved**:

- ringing on the **X-facing** wall → **X** resonance
- ringing on the **Y-facing** wall → **Y** resonance

With calipers, measure the distance covering **as many complete oscillations as
you can** (say 4), then divide:

```
D = (total measured distance) / (number of oscillations)      [mm per oscillation]
f = V / D                                                     [Hz]
```

where `V` is the external-perimeter speed you set in the slicer (100 mm/s).

Example: 4 oscillations span 8.0 mm → D = 2.0 mm → `f = 100 / 2.0 = 50 Hz`.

Do this for X and for Y separately — they are usually different. On a bed-slinger
the moving-bed axis (Y) is typically the lower of the two.

---

## 5. Apply the result

```ini
[input_shaper]
shaper_type_x: mzv
shaper_freq_x: 50          # your measured X frequency
shaper_type_y: mzv
shaper_freq_y: 38          # your measured Y frequency
```

`shaper_type` guidance:

| Situation | Type |
|---|---|
| Default, good all-round | `mzv` |
| Frequency is low (< 25 Hz) or you still see ringing | `ei` |
| Very low / very noisy | `2hump_ei` |
| You want minimum smoothing and freq is high & clean | `zv` |

Then re-run the tower once with input shaper **on** (edit the macro's
`_DISABLE_SHAPER` variable to 0, or just don't stop it in `RINGING_TOWER`) to
confirm the ringing is gone at your target acceleration.

Also cap acceleration so the shaper's "smoothing" stays sane — as a rule keep
`max_accel` such that `max_accel / min(shaper_freq_x, shaper_freq_y)` is roughly
≤ 100 (Klipper warns above that).

---

## 6. The macro

Add this to `printer.cfg` (or an `[include]`d file):

```ini
[gcode_macro RINGING_TOWER]
description: Arma o teste da torre de ringing (input shaper sem acelerometro)
gcode:
    # ---- parametros (todos opcionais) ----
    {% set start_accel = params.START|default(1500)|int %}   # accel da 1a banda (mm/s^2)
    {% set factor      = params.FACTOR|default(300)|int %}    # incremento de accel por banda
    {% set band        = params.BAND|default(5)|int %}        # altura de cada banda (mm)
    {% set v           = params.SPEED|default(100)|int %}     # veloc. do perimetro externo no slicer (mm/s)
    {% set keep_shaper = params.KEEP_SHAPER|default(0)|int %} # 1 = nao desligar o input_shaper

    {% if not keep_shaper and printer.configfile.settings.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X=0 SHAPER_FREQ_Y=0
    {% endif %}
    {% if printer.extruder is defined %}
        SET_PRESSURE_ADVANCE ADVANCE=0
    {% endif %}

    SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=1 MINIMUM_CRUISE_RATIO=0 ACCEL={start_accel} ACCEL_TO_DECEL={start_accel}

    TUNING_TOWER COMMAND=SET_VELOCITY_LIMIT PARAMETER=ACCEL START={start_accel} FACTOR={factor} BAND={band}

    M118 Ringing tower ARMADO.
    M118 accel(Z) = {start_accel} + {factor} * floor(Z/{band})
    M118 Imprima ringing_tower.stl (2 perimetros, 0 top/bottom, 0% infill, perimetro externo a {v} mm/s constante).
    M118 Depois: meca D entre as oscilacoes; frequencia f = {v} / D (Hz). Faca X e Y separado.

[gcode_macro RINGING_TOWER_RESET]
description: Restaura limites normais apos o teste da torre de ringing
gcode:
    {% set cfg = printer.configfile.settings %}
    SET_VELOCITY_LIMIT VELOCITY={cfg.printer.max_velocity} ACCEL={cfg.printer.max_accel} SQUARE_CORNER_VELOCITY={cfg.printer.square_corner_velocity|default(5)} MINIMUM_CRUISE_RATIO={cfg.printer.minimum_cruise_ratio|default(0.5)}
    {% if cfg.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X={cfg.input_shaper.shaper_freq_x|default(0)} SHAPER_FREQ_Y={cfg.input_shaper.shaper_freq_y|default(0)}
    {% endif %}
    M118 Limites restaurados do printer.cfg.
```

Usage:

```
RINGING_TOWER                 # defaults: START=1500 FACTOR=300 BAND=5 SPEED=100
RINGING_TOWER START=1000 FACTOR=200 SPEED=80
# ... print ringing_tower.stl, measure, compute f = SPEED / D ...
RINGING_TOWER_RESET
```
