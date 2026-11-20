# Primeros pasos — instala KlipPocket y configura tu primera impresora

**Idiomas: [English](../getting-started.md) · [Português (BR)](../pt-br/getting-started.md) · [Español](getting-started.md) · [简体中文](../zh-Hans/getting-started.md)**

Una guía paso a paso para quienes nunca han hecho esto antes. Los pasos
funcionan con cualquier impresora que ya ejecute firmware de Klipper.

> Las capturas se tomaron en un teléfono Android y en las páginas de
> Fluidd/Mainsail en Chrome. Tu teléfono puede verse un poco distinto
> (sobre todo las pantallas de instalación de Android), pero el orden de los
> pasos es el mismo. Fluidd muestra sus mensajes en el idioma de tu navegador.

## Qué necesitas

- Un teléfono o tablet Android (Android 5.0 o posterior) que se quede
  enchufado junto a la impresora.
- Un **cable/adaptador USB OTG** para conectar la impresora al teléfono.
- Una impresora que ya ejecute **firmware de Klipper** (si la tuya no lo hace,
  consulta [build-firmware.md](build-firmware.md)).
- El teléfono y el ordenador con el que abrirás la página web deben estar en la
  **misma red Wi‑Fi**.

## 1. Descarga el APK correcto

Descarga el `.apk` desde la página de **Releases** del proyecto. Hay tres
versiones — elige la que corresponda al procesador de tu teléfono:

| APK | Úsalo en |
|---|---|
| `arm64` | La mayoría de los teléfonos de 2017 en adelante (64 bits) |
| `armv7` | Teléfonos y tablets más antiguos (32 bits) — **si no estás seguro, elige este**, también funciona en teléfonos de 64 bits |
| `amd64` | Emuladores y dispositivos Android Intel/AMD |

## 2. Instala el APK

1. Abre la app **Archivos** (o **Mis archivos**) de tu teléfono y ve a
   **Descargas**.
2. Toca el archivo APK.

<p align="center"><img src="../images/setup/01-apk-in-downloads.png" alt="El APK de KlipPocket en la carpeta Descargas" width="288"></p>

3. Android pregunta si quieres instalar la app. Toca **Instalar**. (Si dice que
   instalar desde este origen está bloqueado, toca **Ajustes**, permítelo para
   tu app de Archivos y vuelve.)

<p align="center"><img src="../images/setup/02-install-prompt.png" alt="Pregunta de instalación" width="288"></p>

4. Google Play Protect puede recomendar un análisis, porque la app no viene de
   Play Store. Toca **Analizar app** y espera unos segundos — cuando la
   comprobación diga que es segura, la instalación continúa sola. (Evita
   **Instalar sin analizar**: en algunos dispositivos deja la instalación
   atascada en «Instalando…».)

<p align="center"><img src="../images/setup/03-play-protect.png" alt="Play Protect: análisis de la app recomendado" width="288"></p>

5. Espera a que termine **Instalando…** y toca **Abrir**.

<p align="center"><img src="../images/setup/05-installing.png" alt="Instalando" width="288"></p>

> **¿«Instalando…» no termina nunca?** En algunos dispositivos la comprobación
> de Google Play Protect se atasca y la instalación se queda colgada durante
> minutos (la app en sí está bien). Toca **Cancelar**, abre el APK otra vez y
> elige **Analizar app**: cuando Play Protect lo apruebe, la instalación termina
> en segundos. No hace falta desactivar nada.

## 3. Primer arranque: ajustes de batería

La app tiene que seguir funcionando en segundo plano mientras imprime. En la
primera pantalla activa las dos opciones y toca **Siguiente**; cuando Android
pida dejar de optimizar la batería para KlipPocket, toca **Permitir**.

<p align="center"><img src="../images/setup/07-battery-dialog.png" alt="Petición de optimización de batería" width="288"> <img src="../images/setup/08-battery-done.png" alt="Las dos opciones de batería activadas" width="288"></p>

Llegas a la pantalla principal. Está vacía hasta que añadas una impresora.
(Puedes cambiar el idioma de la app en cualquier momento en **Ajustes → Idioma
de la app**.)

<p align="center"><img src="../images/setup/09-main-empty.png" alt="Pantalla principal vacía" width="288"></p>

## 4. Añade tu impresora

1. Toca el botón grande **+** de la parte inferior.
2. **Nombre** — lo que quieras, por ejemplo `Impresora 1`.
3. **Configuración** — tócala y elige la configuración inicial para el modelo de
   tu impresora. La lista es larga y está ordenada por nombre; los modelos
   empiezan por `printer-` seguido de la marca. Elige el que coincida con tu
   impresora. Si tu modelo no aparece, elige `example-cartesian.cfg` y edítalo
   después (consulta el paso 7).
4. **Inicio automático** — actívalo si quieres que esta impresora arranque sola
   cada vez que se abra la app.
5. Toca **Crear**.

<p align="center"><img src="../images/setup/10-new-profile.png" alt="Pantalla de nuevo perfil" width="216"> <img src="../images/setup/11-config-list.png" alt="Lista de configuraciones" width="216"> <img src="../images/setup/12-pick-config.png" alt="Elegir la configuración de la impresora" width="216"> <img src="../images/setup/13-new-profile-filled.png" alt="Formulario rellenado" width="216"></p>

Tu impresora aparece ahora bajo **Perfiles**, marcada como **Inactivo**.

<p align="center"><img src="../images/setup/14-instance-created.png" alt="Impresora creada" width="288"></p>

## 5. Inicia la impresora y permite el acceso USB

1. Conecta la impresora al teléfono con el cable OTG.
2. Toca el botón **▶ reproducir** (el grande de abajo inicia todas las
   impresoras; el pequeño de la tarjeta inicia solo esa impresora).
3. Android pregunta **«¿Permitir que KlipPocket acceda a USB Serial?»** — marca
   **Abrir siempre KlipPocket cuando se conecte USB Serial** y toca **Aceptar**.

<p align="center"><img src="../images/setup/16-usb-permission.png" alt="Petición de permiso USB" width="288"></p>

> ¿Te perdiste la pregunta, o tocaste Cancelar? Cierra la app por completo y
> ábrela de nuevo — la pregunta vuelve. Si la impresora muestra **MCU error
> during connect** en Fluidd/Mainsail, casi siempre es por esto.

La tarjeta pasa a **En ejecución** y arriba aparece la dirección de la página
web. Mainsail es `http://<IP-del-teléfono>:4409/` y Fluidd es
`http://<IP-del-teléfono>:4408/`.

<p align="center"><img src="../images/setup/15-starting.png" alt="Impresora en ejecución con la dirección web visible" width="288"></p>

## 6. Abre Fluidd o Mainsail

En un ordenador u otro teléfono de la misma red Wi‑Fi, abre la dirección que
muestra la app. Para alternar entre las dos interfaces ve a **Ajustes →
Interfaz web** y toca el mosaico.

<p align="center"><img src="../images/setup/17-settings-frontend.png" alt="Ajustes: mosaico de interfaz web" width="288"></p>

## 7. Corrige los avisos de «configuración faltante»

La primera vez, ambas interfaces se quejarán de que faltan en `printer.cfg`
algunas funciones estándar de la impresora (pausa/reanudar, cancelar impresión,
…). Es normal — añádelas una vez y listo.

**Mainsail** muestra un cuadro naranja **Missing configuration**:

<p align="center"><img src="../images/setup/20-mainsail-missing-config.png" alt="Configuración faltante en Mainsail" width="720"></p>

**Fluidd** muestra un cuadro de **warnings** con los mismos elementos:

<p align="center"><img src="../images/setup/30-fluidd-missing-config.png" alt="Avisos de Fluidd" width="720"></p>

### Las líneas que hay que añadir

Añade este bloque al principio de `printer.cfg` (vale cualquier lugar fuera de
otra sección). **La sangría de dos espacios en las líneas bajo `gcode:` es
obligatoria.**

```ini
[pause_resume]

[display_status]

[gcode_macro CANCEL_PRINT]
description: Cancel the actual running print
rename_existing: CANCEL_PRINT_BASE
gcode:
  TURN_OFF_HEATERS
  CANCEL_PRINT_BASE

[gcode_macro PAUSE]
rename_existing: PAUSE_BASE
gcode:
  PAUSE_BASE

[gcode_macro RESUME]
rename_existing: RESUME_BASE
gcode:
  RESUME_BASE
```

### En Mainsail

1. Haz clic en **MACHINE** en el menú de la izquierda.
2. En **Config Files**, haz clic en **printer.cfg**.

<p align="center"><img src="../images/setup/21-mainsail-machine-page.png" alt="Página Machine de Mainsail" width="720"></p>

3. Se abre el editor. Haz clic en una línea vacía cerca del principio y pega el
   bloque.
4. Haz clic en **SAVE & RESTART** (arriba a la derecha).

<p align="center"><img src="../images/setup/22-mainsail-printer-cfg-editor.png" alt="Editor de Mainsail antes" width="720"></p>
<p align="center"><img src="../images/setup/23-mainsail-cfg-added-lines.png" alt="Editor de Mainsail con el bloque pegado" width="720"></p>

Tras el reinicio el cuadro naranja desaparece:

<p align="center"><img src="../images/setup/24-mainsail-after-restart.png" alt="Panel de Mainsail sin avisos" width="720"></p>

### En Fluidd

1. Haz clic en el icono **{…} Configuration** en el menú de la izquierda.
2. En **Configuration files**, haz clic en **printer.cfg**.

<p align="center"><img src="../images/setup/31-fluidd-configuration-page.png" alt="Página de configuración de Fluidd" width="720"></p>

3. Haz clic en una línea vacía cerca del principio y pega el bloque. Si lo
   escribes a mano en lugar de pegarlo, el editor de Fluidd **no** aplica la
   sangría por ti — las líneas rojas en el editor significan que faltan los dos
   espacios.
4. Haz clic en **SAVE AND RESTART** en la barra superior.

<p align="center"><img src="../images/setup/32-fluidd-printer-cfg-editor.png" alt="Editor de Fluidd antes" width="720"></p>
<p align="center"><img src="../images/setup/33-fluidd-cfg-added-lines.png" alt="Editor de Fluidd con el bloque añadido" width="720"></p>

Los avisos desaparecen y los botones **CANCEL_PRINT**, **PAUSE** y **RESUME**
aparecen en el panel:

<p align="center"><img src="../images/setup/34-fluidd-after-restart.png" alt="Panel de Fluidd tras la corrección" width="720"></p>

## 8. Si Moonraker se queja de `[virtual_sdcard]`

KlipPocket escribe por ti la sección `[virtual_sdcard]`, con la carpeta correcta
para cada impresora, cerca del final de `printer.cfg` (justo encima de la línea
`#*# <--- SAVE_CONFIG --->`). **No añadas la tuya.**

Si aun así aparece un mensaje como *«GCode path received from Klipper does not
match expected location»* — normalmente tras copiar un `printer.cfg` de otra
instalación, o tras volver a crear la impresora — abre `printer.cfg`, busca
`[virtual_sdcard]` y pon en `path:` **exactamente** la ruta escrita en el
mensaje (o elimina tu propia copia de la sección y reinicia, para que la app
escriba una nueva). Después haz clic en **Save & Restart**.

## 9. Ajusta el resto para tu impresora

El archivo inicial es un preajuste genérico. Revisa los valores de `[mcu]`, los
pines, el tamaño de la cama (`position_max`), `z_offset`, PID y malla según tu
impresora, y calibra (PID, Z offset, malla de la cama) antes de la primera
impresión. Si tu impresora aún no ejecuta Klipper, consulta
[build-firmware.md](build-firmware.md).

## Siguientes pasos

- Añade una webcam y usa **tocar para enfocar** en la vista previa:
  [webcam.md](webcam.md).
- Acceso remoto: [octoeverywhere.md](octoeverywhere.md) · [obico.md](obico.md).
- Complementos opcionales: [mods/klipper-addons.md](mods/klipper-addons.md).
