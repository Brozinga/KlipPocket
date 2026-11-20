# Complementos (mods) de Klipper incluidos en KlipPocket

**Idiomas: [English](../../mods/klipper-addons.md) · [Português (BR)](../../pt-br/mods/klipper-addons.md) · [Español](klipper-addons.md) · [简体中文](../../zh-Hans/mods/klipper-addons.md)**

Estos complementos están incluidos pero inactivos — Klipper carga uno solo
cuando la `[sección]` correspondiente está presente en `printer.cfg`, así que
una configuración que no los referencia no se ve afectada. Se seleccionaron para
funcionar bajo Android / Chaquopy (Python 3.10, sin compilador en el
dispositivo, sin `pip` a nivel de usuario).

| Mod | Sección para activarlo | Necesita |
|---|---|---|
| KAMP (malla adaptativa + purga) | `[include KAMP/KAMP_Settings.cfg]` | `[exclude_object]` (núcleo), `[bed_mesh]` |
| LED Effect | `[led_effect NOMBRE]` | un `[neopixel]` / `[dotstar]` direccionable |
| Z Calibration | `[z_calibration]` | sonda + un punto de referencia metálico fijo |
| Auto Speed | `[auto_speed]` | nada (mide los pasos perdidos) |
| TMC Autotune | `[autotune_tmc stepper_x]` … | `[tmc2209]`/`[tmc5160]`/… ya configurado |
| Sonda de corrientes de Foucault | `[probe_eddy_current]` + `[ldc1612]` | BTT Eddy / sensor LDC1612 (**núcleo** de Klipper, ya presente) |

> Input shaper sin acelerómetro: consulta
> [input-shaper-manual.md](input-shaper-manual.md).

---

## KAMP — Klipper Adaptive Meshing & Purging

Se copia automáticamente en `<instancia>/config/KAMP/` en el primer arranque de
Klipper (5 archivos, no se sobrescriben si ya existen).

**Activarlo:** añade a `printer.cfg` (cerca del principio, después de
`[exclude_object]`):

```ini
[include KAMP/KAMP_Settings.cfg]
```

Después abre `KAMP/KAMP_Settings.cfg` en el editor de configuración de
Fluidd/Mainsail y descomenta las partes que quieras:

```ini
[include Adaptive_Meshing.cfg]   # sondea solo el área que ocupa la impresión
[include Line_Purge.cfg]         # línea de purga delante de la impresión real
[include Smart_Park.cfg]         # aparca junto a la impresión para el calentamiento final
#[include Voron_Purge.cfg]       # purga en gota sobre el logo de Voron (necesita la macro del logo)
```

**Intégralo en tu secuencia de inicio** — en `PRINT_START`, sustituye las
llamadas simples de malla / purga:

```ini
BED_MESH_CALIBRATE ADAPTIVE=1     # en lugar de un BED_MESH_CALIBRATE simple
SMART_PARK                        # justo antes del calentamiento final de la boquilla
LINE_PURGE                        # en lugar de una línea de purga escrita a mano
```

El slicer debe emitir `EXCLUDE_OBJECT_DEFINE` (OrcaSlicer / Creality Print:
«Label objects» activado; PrusaSlicer/SuperSlicer: activa «Label objects»; Cura:
plugin *Exclude Objects*) — eso es lo que le dice a KAMP dónde está realmente la
impresión.

Los ajustes viven en la macro `_KAMP_Settings` (cantidad de purga, flujo,
márgenes, altura del smart-park).

---

## LED Effect (`julianschill/klipper-led_effect`)

Efectos animados para una tira de LED direccionable (barra de progreso,
degradado de temperatura, iluminación de la cámara, estados «imprimiendo /
calentando / terminado»).

**Activarlo:** primero necesitas un LED direccionable, p. ej.:

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

Comandos: `SET_LED_EFFECT EFFECT=panel_progress`, `STOP_LED_EFFECTS`. Sintaxis
completa de las capas:
<https://github.com/julianschill/klipper-led_effect/blob/master/docs/LED_Effect.md>

Nota: Happy Hare v4 incluye su **propio** `[mmu_led_effect]` para los LED de
estado de la MMU — es independiente de este `[led_effect]`.

---

## Z Calibration (`protoloft/klipper_z_calibration`)

Calcula el Z offset en vivo a partir de tres sondeos — boquilla sobre un punto
metálico, sonda sobre el mismo punto, sonda sobre la cama — de modo que se
mantiene correcto al cambiar de boquilla y con la temperatura. Útil sobre todo
con una sonda de interruptor/klicky/inductiva y una lengüeta metálica fija cerca
de la cama.

**Activarlo:**

```ini
[z_calibration]
nozzle_xy_position:   <x>,<y>     # boquilla sobre el punto metálico de referencia
switch_xy_position:   <x>,<y>     # sonda sobre el mismo punto
bed_xy_position:      <x>,<y>     # un punto seguro de la cama (el centro de la malla vale)
switch_offset:        0.5         # distancia entre el disparo y el contacto de tu sonda, empieza en ~0.5
start_gcode:          <macro que despliega la sonda, si es acoplable>
```

Ejecuta `CALIBRATE_Z` tras el homing + QGL/malla. Documentación:
<https://github.com/protoloft/klipper_z_calibration>

---

## Auto Speed (`Anonoei/klipper_auto_speed`)

Encuentra tu aceleración y velocidad máximas reales moviendo el cabezal y
detectando pasos perdidos (compara la posición ordenada con la medida de los
motores tras un home). Sin acelerómetro.

**Activarlo:**

```ini
[auto_speed]
margin: 20            # margen de seguridad respecto a los límites de los ejes
```

`z` no es una opción de configuración — es un parámetro del propio comando
G-code `AUTO_SPEED` (`AUTO_SPEED Z=50`), si quieres que primero se mueva a una
altura Z dada.

Ejecuta `AUTO_SPEED` (barrido completo, ~varios minutos) o
`AUTO_SPEED_VELOCITY` / `AUTO_SPEED_ACCEL`. Imprime los `max_velocity` /
`max_accel` recomendados. El gráfico opcional de varianza necesita `matplotlib`
(no instalado) — el resultado numérico no. Documentación:
<https://github.com/Anonoei/klipper_auto_speed>

---

## TMC Autotune (`andrewmcgr/klipper_tmc_autotune`)

Calcula buenos valores de registro de los drivers TMC (control de corriente,
`PWM`, `CoolStep`, umbrales de `StealthChop`/`SpreadCycle`) a partir de la hoja
de datos del motor en lugar de ajustarlos a mano. Actualizado al `main` original
(2026-08).

**Activarlo** — una sección por cada driver que ya tengas configurado:

```ini
[autotune_tmc stepper_x]
motor: ldo-42sth40-1004ah        # busca tu motor en extras/motor_database.cfg
[autotune_tmc stepper_y]
motor: ldo-42sth40-1004ah
[autotune_tmc extruder]
motor: ldo-36sth20-1004ahg
```

Si tu motor no está en `motor_database.cfg`, elige el más parecido o añádelo.
Documentación: <https://github.com/andrewmcgr/klipper_tmc_autotune>

---

## Sonda de corrientes de Foucault (BTT Eddy, LDC1612 genérico) — núcleo de Klipper

No hace falta ningún mod — `[probe_eddy_current]` y `[ldc1612]` forman parte del
Klipper 0.13 incluido. Conéctala según la documentación de Klipper
(<https://www.klipper3d.org/Eddy_Probe.html>). Es la **única** opción de sonda de
escaneo segura en Android (consulta la siguiente sección).

### Por qué no Beacon / Cartographer

Tanto `beacon.py` como `cartographer.py` crean un `multiprocessing.Process` para
transmitir muestras, y el ajuste de modelo de Cartographer quiere `scipy`.
`multiprocessing` no es fiable bajo Chaquopy en Android (no hay un `fork()`
seguro del proceso de la app) y `scipy` no está instalado. **No** se incluyen. Si
tienes ese hardware, la vía compatible es un `[probe_eddy_current]` al estilo
BTT Eddy.
