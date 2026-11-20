# Compilar el APK

**Idiomas: [English](../build-app.md) · [Português (BR)](../pt-br/build-app.md) · [Español](build-app.md) · [简体中文](../zh-Hans/build-app.md)**

La compilación necesita el SDK/NDK de Android, un JDK y un intérprete de
**Python 3.10** (Chaquopy compila el Python incluido contra la 3.10). No se
sube nada que filtre rutas locales — `local.properties`, `.gradle/`, `build/`,
`firmware/toolchain/`, `firmware/.klipper-build/` y los artefactos de descarga
de `fluidd`/`mainsail` están todos en git-ignore y se regeneran.

## Configuración en un solo paso

| SO | Comando |
|---|---|
| Linux / macOS | `./scripts/setup.sh` |
| Windows (PowerShell) | `.\scripts\setup.ps1` |

El script:

1. comprueba que haya un JDK (17+, se recomienda 21);
2. prepara las herramientas de línea de comandos de Android si no hay SDK;
3. instala los paquetes del SDK fijados — `platform-tools`,
   `platforms;android-35`, `build-tools;35.0.0`, `ndk;23.2.8568313`,
   `cmake;3.22.1`;
4. se asegura de que exista un intérprete de Python 3.10 (con `pyenv` / `brew` /
   `winget`, o te indica cómo instalarlo);
5. escribe `local.properties` (`sdk.dir` + `chaquopy.python`).

Añade `--build` (bash) / `-Build` (PowerShell) para compilar además el APK debug
arm64.

## Compilar

```bash
./gradlew :app:assembleArm64Debug     # o assembleArmv7Debug / assembleAmd64Debug
./gradlew :app:assembleRelease         # todas las ABI, minificado (sin firmar si no hay keystore)
```

O abre el proyecto en Android Studio (Giraffe+) y pulsa Run. Salida:
`app/build/outputs/apk/<abi>/<type>/KlipPocket_v<version>_<abi>.apk`.

`preBuild` descarga los paquetes de Fluidd/Mainsail desde GitHub (hace falta red
en la primera compilación) y regenera `app/src/main/assets/` a partir de las
fuentes incluidas en `app/src/main/{klipper,kalico,moonraker,happyhare}`.

## Requisitos manuales (si no usas el script)

- **JDK 21** (Temurin). AGP 8.10 / Gradle 8.12 necesitan 17+.
- **SDK de Android** con: `platforms;android-35`, `build-tools;35.0.0`,
  `ndk;23.2.8568313`, `cmake;3.22.1`.
- **Python 3.10** — cualquier CPython 3.10.x; `pyenv install 3.10.14`
  funciona bien.
- `local.properties` en la raíz del repositorio:

  ```properties
  sdk.dir=/ruta/absoluta/a/Android/Sdk
  chaquopy.python=/ruta/absoluta/a/python3.10
  ```

## Pruebas

| Qué | Comando |
|---|---|
| Pruebas unitarias de Kotlin (todas las reglas de negocio de la app: interfaces web, cámara, integración del complemento de recuperación de impresión, Prefs, la base de datos de perfiles de impresora y los view models, las configuraciones de moonraker.conf/complementos, las ediciones de printer.cfg, el enrutamiento/proxy del servidor web integrado, la nomenclatura USB, el proveedor de archivos SAF, los registros), sin necesidad de dispositivo | `./gradlew :app:testArm64DebugUnitTest` |
| Informe de cobertura de líneas de las pruebas unitarias | `./gradlew :app:jacocoUnitTestReport` (`app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`) |
| Pruebas de los complementos de Klipper (`print_recovery`, `beam_beeper`/`beam_camera`) y de los bootloaders simulados de la pestaña Firmware (contrastados con `flash_can.py`, `hid-flash` y `bossac` de Klipper; se omiten si faltan `g++`/`gcc`/`pyserial`), Python simple | `python3 -m unittest discover -s app/src/test/python -v` |
| Pruebas de interfaz (componentes de Compose y el mosaico de la interfaz web), en un dispositivo conectado o un emulador | `./gradlew :app:connectedArm64DebugAndroidTest` |

Las pruebas de interfaz instalan un APK de pruebas junto a la app, y Gradle
desinstala ambos al terminar — eso borra los datos de la app en ese
dispositivo, así que usa un emulador o un dispositivo de repuesto.

Las pruebas unitarias cubren cada regla de negocio como un pequeño objeto Kotlin
probado de forma independiente (puertos, plantillas de configuración,
nomenclatura, análisis, ...); la infraestructura de Activity/Service, la E/S
real de Camera2/USB y el arranque del proceso de Python se ejercitan ejecutando
la propia app, no con estas pruebas.

## Firma

Las compilaciones debug usan el keystore de depuración. Para una versión
release firmada, añade estas claves a `local.properties` (ya está en
git-ignore):

```properties
key.store=/ruta/absoluta/a/release.keystore
key.store.password=…
key.alias=…
key.key.password=…
```

Sin ellas se genera igualmente una compilación release, solo que sin firmar.
