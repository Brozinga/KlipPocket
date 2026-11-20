# KlipPocket - Klipper brain for your 3D printer

<p align="center">
  <img src="docs/images/logo.png" alt="KlipPocket" width="260">
</p>

<p align="center">
  <a href="https://github.com/Brozinga/KlipPocket/releases/latest"><img src="https://img.shields.io/github/v/release/Brozinga/KlipPocket?label=%C3%BAltima%20versi%C3%B3n&color=49CBEB" alt="Última versión"></a>
  <img src="https://img.shields.io/badge/platform-Android%205.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/license-GPL--3.0-4B8BBE" alt="License: GPL-3.0">
  <img src="https://img.shields.io/badge/kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin / Jetpack Compose">
</p>

**Lee esto en otros idiomas: [English](README.md) · [Português (BR)](README.pt-br.md) · [Español](README.es.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

**KlipPocket convierte un teléfono o tablet Android en el cerebro de tu impresora 3D.**
Conéctalo a la impresora por USB y ejecuta [Klipper](https://github.com/KevinOConnor/klipper) o [Kalico](https://github.com/KalicoCrew/kalico) por ti — sin Raspberry Pi, sin SSH, sin un Linux que mantener.

Muchas impresoras «clásicas» siguen usando placas Marlin lentas y cerradas. KlipPocket les lleva el Klipper moderno sin la complejidad de siempre: instala la app, flashea la impresora una vez y controla todo desde las interfaces web en tu navegador. Todo se ejecuta localmente en el dispositivo que ya tienes, y las herramientas extra — acceso remoto, supervisión por cámara, detección de fallos, recuperación de impresión — están a un interruptor de distancia.

> **¿Solo quieres instalarla?** Descarga el último APK desde la
> [página de Releases](https://github.com/Brozinga/KlipPocket/releases/latest) — si no estás seguro de cuál, elige `armv7` (consulta
> [Elegir el paquete correcto](#elegir-el-paquete-correcto) más abajo).

## Funcionalidades

- **Klipper 0.13 y anteriores** — compatible con el firmware actual y antiguo de Klipper (también se admite Kalico v2026.10.00)
- **Software actualizado** — Moonraker, Fluidd, Mainsail y Voyager UI recientes incluidos en la app
- **Conexión remota** — accede a tu impresora desde cualquier lugar con OctoEverywhere, Obico y otros
- **Detección de fallos con IA** — detecta a tiempo las impresiones fallidas mediante Obico
- **SimplyPrint** — supervisión remota y detección de fallos con IA, ya compatible con el Moonraker incluido
- **Print Recovery** — reanuda una impresión tras un corte de energía
- **Supervisión por cámara** — usa la cámara del dispositivo o una webcam USB, con vista previa en vivo, zoom y timelapse
- **Interfaces web** — Fluidd, Mainsail y Voyager UI, servidas por el propio dispositivo
- **KlipPocket Dashboard** — tarjetas y gráficos en vivo de CPU, memoria, temperatura y red, información de los MCU, registros, ajustes, iniciar/detener y añadir impresoras, la cámara y el flasheo de firmware, desde cualquier navegador (apto para teléfono)

## Elegir el paquete correcto

KlipPocket ofrece tres variantes de APK:

| Arquitectura | Nombre del paquete | Caso de uso |
|-------------|--------------|----------|
| arm64 | `KlipPocket_v*_arm64.apk` | Dispositivos modernos de 64 bits |
| armv7 | `KlipPocket_v*_armv7.apk` | Dispositivos más antiguos de 32 bits — funciona en la mayoría de los dispositivos (recomendado si no estás seguro) |
| x86_64 | `KlipPocket_v*_amd64.apk` | Tablets x86_64, Chromebooks, emuladores de Android |

**Cómo comprobar la arquitectura de tu dispositivo:**
- **Ajustes > Acerca del teléfono > Arquitectura** o **Arquitectura del kernel**
- O instala una app de información de CPU como «CPU-Z» o «AIDA64»
- Si no estás seguro, elige armv7 — es el paquete que funciona en la gama más amplia de dispositivos

## Inicio rápido

Sigue estos pasos en orden:

1. **Genera y flashea el firmware en la impresora.** Klipper necesita su propio firmware en la placa base de la impresora. Compílalo para tu placa y flashéalo siguiendo [`docs/es/build-firmware.md`](docs/es/build-firmware.md).
2. **Comprueba la conexión.** La impresora debe comunicarse con el dispositivo por **USB**, y el dispositivo Android debe admitir **OTG** (host USB).
   - Algunos dispositivos **no cargan mientras el puerto USB se usa para datos**. En ese caso la batería se agota durante las impresiones largas, y la solución es una conexión de carga interna soldada (directa a los pines de la batería).
   - Antes de comprar un hub o un cable, **comprueba que tu dispositivo puede cargar y transferir datos a la vez** a través del conector que piensas usar (consulta [¿Qué hub USB usar?](#qué-hub-usb-usar)).
3. **Instala la app y configúrala.** Instala el APK, añade tu primera impresora y abre la interfaz web siguiendo la guía ilustrada [**Primeros pasos**](docs/es/getting-started.md).

> **¿Atascado?** La pestaña **Registros** de la app muestra los registros de Klipper, Moonraker y de la app, y te permite copiarlos/compartirlos sin un PC.

## Documentación

Todas las guías están en [`docs/`](docs/es/index.md) (también disponibles en English, Português y 简体中文):

- [Primeros pasos](docs/es/getting-started.md) — instala la app y configura tu primera impresora, paso a paso
- [Compilar el firmware del MCU](docs/es/build-firmware.md) — menú interactivo para el firmware de Klipper o Kalico (Docker): compilar, validar, flashear (USB, UART, DFU, RP2040) y verificar
- [Recuperación de impresión](docs/es/print-recovery.md) — reanuda una impresión tras un corte de energía
- [Timelapse](docs/es/timelapse.md) — graba un timelapse y encuentra el vídeo
- [Cámara / Webcam](docs/es/webcam.md) — cámara del dispositivo, webcam USB, vista previa, zoom y tocar para enfocar
- [OctoEverywhere](docs/es/octoeverywhere.md) — acceso remoto con OctoEverywhere
- [Obico](docs/es/obico.md) — acceso remoto y detección de fallos con IA con Obico
- [SimplyPrint](docs/es/simplyprint.md) — supervisión remota y detección de fallos con IA con SimplyPrint
- [Kalico](docs/es/kalico.md) — cambiar el motor a Kalico, qué funciona y qué no
- [Multi-MCU](docs/es/multi-mcu.md) — usar placas USB extra (cabezal, sensores, puertos extra) con ejemplos de `printer.cfg`
- [Happy Hare](docs/es/happy-hare.md) — compatibilidad con multifilamento (MMU): elige tu MMU y responde a las preguntas de configuración de Happy Hare en la app o en el KlipPocket Dashboard
- [Complementos de Klipper](docs/es/mods/klipper-addons.md) — KAMP, LED Effect, Z Calibration, Auto Speed, TMC Autotune
- [Input shaper sin acelerómetro](docs/es/mods/input-shaper-manual.md) — método de la torre de ringing y macros
- [KlipPocket Dashboard](docs/es/dashboard.md) — gráficos en vivo de CPU, memoria, temperatura y red, información del anfitrión y de los MCU, registros, ajustes, control y alta de impresoras, cámara y flasheo de firmware desde un navegador
- [Compilar el APK](docs/es/build-app.md) — compila la app tú mismo

## ¿Qué puertos usan las interfaces web y la cámara?

La dirección se muestra en la pantalla principal siempre que hay una instancia en ejecución (`IP` es la dirección del dispositivo en tu red). Cada interfaz web tiene su propio puerto, según el selector de interfaz de la app:

| Servicio | Dirección |
|---|---|
| Fluidd | `http://IP:4408/` |
| Mainsail | `http://IP:4409/` |
| Voyager UI | `http://IP:4410/` |
| KlipPocket Dashboard (dispositivo, MCU, registros, firmware) | `http://IP:4400/` |
| Transmisión de la cámara | `http://IP:8889/` |
| Instantánea de la cámara (un solo JPEG) | `http://IP:8889/snapshot` |
| Transmisión de la cámara (estilo webcam) | `http://IP:8889/webcam/?action=stream` |
| Instantánea de la cámara (estilo webcam) | `http://IP:8889/webcam/?action=snapshot` |

## Inicio automático

KlipPocket puede arrancar solo cuando el dispositivo se enciende, para que la impresora esté lista sin tocar la pantalla:

1. Activa **Inicio automático** en cada perfil de impresora que quieras que arranque automáticamente.
2. Establece KlipPocket como el **launcher predeterminado** (app de inicio) del dispositivo.
3. Si el almacenamiento de tu dispositivo está cifrado (lo habitual en la mayoría de los dispositivos), **quita el PIN/contraseña de la pantalla de bloqueo** — el sistema solo desbloquea la app después de que lo escribas.

## Aviso sobre la actividad en segundo plano

Algunos fabricantes restringen las apps que se ejecutan en segundo plano, lo que puede detener una impresión que ya está en marcha. Para evitarlo:

- Permite que KlipPocket ignore la optimización de batería cuando la app lo pida (es la primera pantalla al iniciar).
- Permite toda la actividad en segundo plano para la app en los ajustes de tu dispositivo.
- Establecer la app como launcher predeterminado también ayuda en sistemas más estrictos.

## ¿Compatible con Android TV?

Sí — debería funcionar sin problemas. Ten en cuenta que algunas cajas de TV baratas no permiten establecer KlipPocket como launcher sin desactivar antes el del sistema; usa ADB o root para desactivarlo.

## ¿Qué hub USB usar?

Un hub está **admitido**, pero no es necesario si tu dispositivo puede hablar con la impresora y cargar al mismo tiempo. Si lo necesitas, el hub recomendado tiene **al menos un puerto USB-A (para la impresora) y un puerto de alimentación/carga** (como un paso de USB-C Power Delivery), y solo si tu dispositivo admite carga en modo OTG. Comprueba que la combinación funciona con tu dispositivo antes de depender de ella en impresiones largas.

## Restricciones

- El servidor web no puede ejecutarse en el puerto por defecto porque Android/Linux no permite a las apps de espacio de usuario enlazarse a puertos inferiores a 1024 y queremos el 80 para el `http://IP` por defecto
- Algunos dispositivos pueden restablecer la ruta del dispositivo al reiniciar el firmware; en ese caso debes usar el nombrado por VID/PID
- Sin SSH (de todos modos no podrías compilar firmware ni ejecutar servicios adicionales de inicio automático)
- Algunos dispositivos no admiten OTG y carga al mismo tiempo; en ese caso debes soldar directamente a los pines de la batería (o usar otro dispositivo, tú decides)
- **La velocidad serie (baud rate) es un ajuste avanzado, elegido por placa.** Cada impresora/MCU puede usar una velocidad distinta, así que comprueba la que usa el firmware de tu placa (`CONFIG_SERIAL_BAUD`). En la mayoría de los casos el valor por defecto, 250000, ya funciona bien: cámbialo solo si KlipPocket no consigue conectarse al MCU. Elígela en **Ajustes → USB → Listar dispositivos USB** o en la lista USB del Dashboard; se guarda por placa solo cuando la cambias y se aplica al instante. Para más información, consulta [Cambiar la velocidad en baudios del USB](docs/es/usb-baud-rate.md).

> **El tropiezo más común:** si tu teléfono no puede cargar y hablar con la
> impresora a la vez por el mismo cable, es la restricción de OTG+carga de
> arriba — un hub USB con alimentación (consulta [¿Qué hub USB usar?](#qué-hub-usb-usar)) lo soluciona.

## Créditos

- **[ProtonKicker/Kocoa-Beam](https://github.com/ProtonKicker)** — portó la aplicación a Kotlin y rediseñó su aspecto.
- **[Beam Klipper](https://github.com/utkabobr/BeamKlipper)** — el proyecto original del que procede este.
- **[Voyager UI](https://github.com/ozancs/voyager-ui)** de [ozancs](https://github.com/ozancs) — la tercera interfaz web que puedes elegir junto a Fluidd y Mainsail.
- **[ronaldmirabal](https://github.com/ronaldmirabal)** — la traducción al español de la app.
- Klipper, Kalico, Moonraker, Fluidd, Mainsail y los demás componentes incluidos pertenecen a sus respectivos autores.

## Contribuir

¡Los pull requests son bienvenidos!
