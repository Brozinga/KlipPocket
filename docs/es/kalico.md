# Usar Kalico

**Idiomas: [English](../kalico.md) · [Português (BR)](../pt-br/kalico.md) · [Español](kalico.md) · [简体中文](../zh-Hans/kalico.md)**

[Kalico](https://github.com/KalicoCrew/kalico) es un fork comunitario de Klipper
que permite funciones y comportamientos que Klipper deja fuera por ser
potencialmente arriesgados. KlipPocket incluye **Kalico v2026.10.00** junto a
Klipper, para que elijas cuál mueve tu impresora.

## Cambiar el motor

1. Detén el perfil de impresora si está en ejecución.
2. Abre **Ajustes** y toca **Motor de firmware** para alternar entre Klipper y
   Kalico.
3. Inicia el perfil de nuevo.

Las versiones incluidas aparecen en **Ajustes → Versiones del software**. Tus
perfiles de impresora y tus archivos no se tocan al cambiar.

## Un cambio que tu `printer.cfg` puede necesitar

Kalico activa `[respond]` por defecto, lo que le da un `M118` integrado. Si tu
configuración define su propio `[gcode_macro M118]`, Kalico se detiene con
`gcode command M118 already registered`. Sustituye la macro por una sección
`[respond]`, válida tanto en Klipper como en Kalico:

```ini
[respond]
```

## Qué funciona

Comprobado en una impresora real (una Neptune 3 Pro, por USB), con el Fluidd de
la app:

- El arranque, la conexión con la placa de la impresora y la interfaz web.
- El `M118` integrado de Kalico y `RESPOND`, `BED_MESH_CHECK` y
  `RELOAD_GCODE_MACROS` (recargar macros sin reiniciar).
- Los valores por defecto de Kalico activos: `[respond]`, `[force_move]` y
  `[exclude_object]` se cargan sin estar en la configuración.
- Una configuración real cargando sin errores: KAMP, macros de Timelapse, Auto
  Speed, `bed_mesh`, `probe`, `safe_z_home`, `input_shaper`, `pause_resume`,
  `display_status` y el sensor de filamento.

Happy Hare (multifilamento) también se carga y responde a sus comandos `MMU_*`
en Kalico; consulta [Happy Hare](happy-hare.md).

Solo comprobada la carga (el módulo se importa y arranca; no se ha usado en una
impresión): LED Effect, TMC Autotune, Print Recovery, el zumbador y los extras
de cámara, y Z Calibration.

## Qué no funciona o no se ha probado

- **Z Calibration** necesita un endstop de Z físico, también en Klipper. Con una
  sonda como endstop de Z (`endstop_pin: probe:z_virtual_endstop`) se detiene
  con *A virtual endstop for z is not supported*.
- **Las configuraciones con `[gcode_macro M118]`** no arrancan en Kalico hasta
  sustituir la macro, como se indica arriba.
- **El firmware de placa compilado para Klipper** funciona, pero Kalico avisa de
  que se recomienda volver a flashearlo para Kalico.
- **Las placas opcionales** (`is_non_critical`) no se han probado.
- Kalico cambia algunos comportamientos, como que `controller_fan` gane un
  `standby_speed`. Consulta la
  [documentación de Kalico](https://docs.kalico.gg/Kalico_Additions.html).
