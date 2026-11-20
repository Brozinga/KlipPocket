# Input shaper sin acelerómetro (método de la torre de ringing)

**Idiomas: [English](../../mods/input-shaper-manual.md) · [Português (BR)](../../pt-br/mods/input-shaper-manual.md) · [Español](input-shaper-manual.md) · [简体中文](../../zh-Hans/mods/input-shaper-manual.md)**

Las pruebas automáticas de resonancia de Klipper (`TEST_RESONANCES`,
`SHAPER_CALIBRATE`) necesitan un acelerómetro ADXL345 / LIS2DW / MPU-9250 en el
cabezal. Muchas impresoras no lo tienen, y añadir uno en KlipPocket exige un
sensor SPI/I²C conectado al MCU de la impresora (el dispositivo Android no puede
ayudar).

La **prueba de la torre de ringing** produce los mismos dos números a ojo y con
un calibre:

1. la **frecuencia de ringing** de X y de Y → `shaper_freq_x` / `shaper_freq_y`
2. como extra, una **aceleración máxima** segura

Es el método que describe la propia documentación de Klipper para el caso sin
acelerómetro: <https://www.klipper3d.org/Resonance_Compensation.html>

---

## 1. Configuración inicial (una sola vez)

Añade una sección de input shaper **vacía** a `printer.cfg` (para que la prueba
pueda desactivarlo y para que tengas dónde escribir el resultado):

```ini
[input_shaper]
```

Añade la macro de prueba (al final de este archivo) a `printer.cfg` o a un
archivo incluido con `[include]`.

Descarga el modelo de prueba de ringing de Klipper **`ringing_tower.stl`**:
<https://github.com/Klipper3d/klipper/raw/master/docs/prints/ringing_tower.stl>

---

## 2. Lamínalo (esto importa — si lo haces mal los números no valen nada)

| Ajuste | Valor |
|---|---|
| Altura de capa | 0.2–0.25 mm |
| Paredes / perímetros | 2 |
| Capas superiores / inferiores | **0** |
| Relleno | **0 %** |
| Velocidad del perímetro externo | **100 mm/s**, fija — anota este número como `V` |
| Ralentización en capas cortas / tiempo mínimo de capa | **desactivado** (0 s) |
| Velocidad dinámica / automática, rampas de «velocidad de pared exterior» | desactivado |
| Input shaper / opciones de «vibración»/«ringing» del slicer | desactivado |
| Imprime la torre **cerca del centro** de la cama | sí |

La torre debe imprimirse con una velocidad de pared exterior **constante**. Si
tu slicer insiste en ralentizar las primeras capas, sube el tiempo mínimo de
capa a 0 y desactiva los límites de velocidad de «refrigeración» solo para esta
impresión.

---

## 3. Ejecuta la prueba

En la consola de Fluidd/Mainsail, con la cama y la boquilla a temperatura y la
impresora con home hecho:

```
RINGING_TOWER
```

Esa macro:
- desactiva el input shaping y el pressure advance,
- pone la square-corner-velocity baja y el cruise-ratio a 0 (para que la
  aceleración sea lo que realmente mueve las esquinas),
- arma un `TUNING_TOWER` que **sube la aceleración 300 mm/s² cada 5 mm de Z**,
  empezando en 1500 mm/s².

Ahora inicia la impresión de `ringing_tower.stl` desde la lista de archivos.
Obsérvala; si el cabezal empieza a patinar / saltar mucho, páralo — todo lo que
está por debajo de esa altura sigue siendo válido.

---

## 4. Lee la torre

Obtienes **dos** resultados.

### 4a. Aceleración máxima

Busca la altura Z a la que el ringing (los ecos fantasma tras cada muesca) se
vuelve aceptablemente pequeño. La aceleración a la altura `Z` es:

```
accel = START + FACTOR * floor(Z_mm / BAND)
      = 1500  + 300    * floor(Z_mm / 5)
```

Ejemplo: limpio a partir de Z = 40 mm → `1500 + 300 * 8 = 3900 mm/s²`. Usa
~**80 %** de eso como `max_accel` en las impresiones reales.

### 4b. Frecuencia de ringing (la importante)

La torre tiene muescas en las **caras X** y en las **caras Y**. El ringing se
ve como una onda que se amortigua *después* de cada muesca, en la cara
**perpendicular al eje que se movió**:

- ringing en la pared **orientada a X** → resonancia de **X**
- ringing en la pared **orientada a Y** → resonancia de **Y**

Con un calibre, mide la distancia que abarca **tantas oscilaciones completas
como puedas** (digamos 4) y divide:

```
D = (distancia total medida) / (número de oscilaciones)       [mm por oscilación]
f = V / D                                                     [Hz]
```

donde `V` es la velocidad del perímetro externo que pusiste en el slicer (100
mm/s).

Ejemplo: 4 oscilaciones abarcan 8.0 mm → D = 2.0 mm → `f = 100 / 2.0 = 50 Hz`.

Haz esto para X y para Y por separado — normalmente son distintas. En una
impresora de cama móvil el eje de la cama móvil (Y) suele ser el más bajo de los
dos.

---

## 5. Aplica el resultado

```ini
[input_shaper]
shaper_type_x: mzv
shaper_freq_x: 50          # tu frecuencia X medida
shaper_type_y: mzv
shaper_freq_y: 38          # tu frecuencia Y medida
```

Guía de `shaper_type`:

| Situación | Tipo |
|---|---|
| Por defecto, bueno en general | `mzv` |
| La frecuencia es baja (< 25 Hz) o aún ves ringing | `ei` |
| Muy baja / muy ruidosa | `2hump_ei` |
| Quieres el mínimo suavizado y la frecuencia es alta y limpia | `zv` |

Después vuelve a ejecutar la torre una vez con el input shaper **activado**
(edita la variable `_DISABLE_SHAPER` de la macro a 0, o simplemente no lo
detengas en `RINGING_TOWER`) para confirmar que el ringing ha desaparecido a la
aceleración objetivo.

Limita también la aceleración para que el «suavizado» del shaper siga siendo
razonable — como regla, mantén `max_accel` de modo que
`max_accel / min(shaper_freq_x, shaper_freq_y)` sea aproximadamente ≤ 100
(Klipper avisa por encima de eso).

---

## 6. La macro

Añade esto a `printer.cfg` (o a un archivo incluido con `[include]`):

```ini
[gcode_macro RINGING_TOWER]
description: Arma la prueba de la torre de ringing (input shaper sin acelerometro)
gcode:
    # ---- parametros (todos opcionales) ----
    {% set start_accel = params.START|default(1500)|int %}   # accel de la 1a banda (mm/s^2)
    {% set factor      = params.FACTOR|default(300)|int %}    # incremento de accel por banda
    {% set band        = params.BAND|default(5)|int %}        # altura de cada banda (mm)
    {% set v           = params.SPEED|default(100)|int %}     # veloc. del perimetro externo en el slicer (mm/s)
    {% set keep_shaper = params.KEEP_SHAPER|default(0)|int %} # 1 = no desactivar el input_shaper

    {% if not keep_shaper and printer.configfile.settings.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X=0 SHAPER_FREQ_Y=0
    {% endif %}
    {% if printer.extruder is defined %}
        SET_PRESSURE_ADVANCE ADVANCE=0
    {% endif %}

    SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=1 MINIMUM_CRUISE_RATIO=0 ACCEL={start_accel} ACCEL_TO_DECEL={start_accel}

    TUNING_TOWER COMMAND=SET_VELOCITY_LIMIT PARAMETER=ACCEL START={start_accel} FACTOR={factor} BAND={band}

    M118 Torre de ringing ARMADA.
    M118 accel(Z) = {start_accel} + {factor} * floor(Z/{band})
    M118 Imprime ringing_tower.stl (2 perimetros, 0 top/bottom, 0% relleno, perimetro externo a {v} mm/s constante).
    M118 Despues: mide D entre las oscilaciones; frecuencia f = {v} / D (Hz). Haz X e Y por separado.

[gcode_macro RINGING_TOWER_RESET]
description: Restaura los limites normales tras la prueba de la torre de ringing
gcode:
    {% set cfg = printer.configfile.settings %}
    SET_VELOCITY_LIMIT VELOCITY={cfg.printer.max_velocity} ACCEL={cfg.printer.max_accel} SQUARE_CORNER_VELOCITY={cfg.printer.square_corner_velocity|default(5)} MINIMUM_CRUISE_RATIO={cfg.printer.minimum_cruise_ratio|default(0.5)}
    {% if cfg.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X={cfg.input_shaper.shaper_freq_x|default(0)} SHAPER_FREQ_Y={cfg.input_shaper.shaper_freq_y|default(0)}
    {% endif %}
    M118 Limites restaurados desde printer.cfg.
```

Uso:

```
RINGING_TOWER                 # por defecto: START=1500 FACTOR=300 BAND=5 SPEED=100
RINGING_TOWER START=1000 FACTOR=200 SPEED=80
# ... imprime ringing_tower.stl, mide, calcula f = SPEED / D ...
RINGING_TOWER_RESET
```
