# Building the APK

**Languages: [English](build-app.md) · [Português (BR)](pt-br/build-app.md) · [简体中文](zh-Hans/build-app.md)**

The build needs the Android SDK/NDK, a JDK, and a **Python 3.10** interpreter
(Chaquopy compiles the bundled Python against 3.10). Nothing is committed that
would leak local paths — `local.properties`, `.gradle/`, `build/`,
`firmware/toolchain/`, `firmware/.klipper-build/` and the `fluidd`/`mainsail`
download artifacts are all git-ignored and regenerated.

## One-shot setup

| OS | Command |
|---|---|
| Linux / macOS | `./scripts/setup.sh` |
| Windows (PowerShell) | `.\scripts\setup.ps1` |

The script:

1. checks for a JDK (17+, 21 recommended);
2. bootstraps the Android command-line tools if no SDK is present;
3. installs the pinned SDK packages — `platform-tools`, `platforms;android-35`,
   `build-tools;35.0.0`, `ndk;23.2.8568313`, `cmake;3.22.1`;
4. ensures a Python 3.10 interpreter exists (via `pyenv` / `brew` / `winget`, or
   prints how to install one);
5. writes `local.properties` (`sdk.dir` + `chaquopy.python`).

Add `--build` (bash) / `-Build` (PowerShell) to also build the arm64 debug APK.

## Build

```bash
./gradlew :app:assembleArm64Debug     # or assembleArmv7Debug / assembleAmd64Debug
./gradlew :app:assembleRelease         # all ABIs, minified (unsigned without a keystore)
```

Or open the project in Android Studio (Giraffe+) and Run. Output:
`app/build/outputs/apk/<abi>/<type>/KlipPocket_v<version>_<abi>.apk`.

`preBuild` downloads the Fluidd/Mainsail bundles from GitHub (network required on
the first build) and regenerates `app/src/main/assets/` from the vendored
`app/src/main/{klipper,kalico,moonraker,happyhare}` sources.

## Manual prerequisites (if not using the script)

- **JDK 21** (Temurin). AGP 8.10 / Gradle 8.12 need 17+.
- **Android SDK** with: `platforms;android-35`, `build-tools;35.0.0`,
  `ndk;23.2.8568313`, `cmake;3.22.1`.
- **Python 3.10** — any CPython 3.10.x; `pyenv install 3.10.14` works well.
- `local.properties` at the repo root:

  ```properties
  sdk.dir=/absolute/path/to/Android/Sdk
  chaquopy.python=/absolute/path/to/python3.10
  ```

## Tests

| What | Command |
|---|---|
| Kotlin unit tests (all the app's business rules: front ends, camera, print recovery add-on wiring, Prefs, the printer profile database and view models, moonraker.conf/companion configs, printer.cfg edits, the embedded web server's routing/proxy, USB naming, the SAF file provider, logs), no device needed | `./gradlew :app:testArm64DebugUnitTest` |
| Line coverage report of the unit tests | `./gradlew :app:jacocoUnitTestReport` (`app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`) |
| Klipper add-on tests (`print_recovery`, `beam_beeper`/`beam_camera`), plain Python | `python3 -m unittest discover -s app/src/test/python -v` |
| UI tests (Compose components and the front end tile), on a connected device or emulator | `./gradlew :app:connectedArm64DebugAndroidTest` |

The UI tests install a test APK next to the app, and Gradle uninstalls both when
they finish — that erases the app's data on that device, so use an emulator or a
spare device.

The unit tests cover every business rule as a small, independently tested
Kotlin object (ports, config templates, naming, parsing, ...); Activity/Service
plumbing, real Camera2/USB I/O and the Python process bootstrap are exercised
by running the app itself, not by these tests.

## Signing

Debug builds use the debug keystore. For a signed release, add these keys to
`local.properties` (already git-ignored):

```properties
key.store=/absolute/path/to/release.keystore
key.store.password=…
key.alias=…
key.key.password=…
```

Without them a release build is still produced, just unsigned.
