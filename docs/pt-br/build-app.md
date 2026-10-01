# Compilando o APK

**Idiomas: [English](../build-app.md) · [Português (BR)](build-app.md) · [简体中文](../zh-Hans/build-app.md)**

O build precisa do Android SDK/NDK, de um JDK e de um interpretador **Python
3.10** (o Chaquopy compila o Python embutido contra 3.10). Nada que exponha
caminhos locais é commitado — `local.properties`, `.gradle/`, `build/`,
`firmware/toolchain/`, `firmware/.klipper-build/` e os artefatos baixados de
`fluidd`/`mainsail` são todos gitignored e regerados.

## Setup em um comando

| SO | Comando |
|---|---|
| Linux / macOS | `./scripts/setup.sh` |
| Windows (PowerShell) | `.\scripts\setup.ps1` |

O script:

1. verifica se há um JDK (17+, 21 recomendado);
2. baixa as command-line tools do Android se nenhum SDK estiver presente;
3. instala os pacotes fixados do SDK — `platform-tools`, `platforms;android-35`,
   `build-tools;35.0.0`, `ndk;23.2.8568313`, `cmake;3.22.1`;
4. garante que exista um interpretador Python 3.10 (via `pyenv` / `brew` /
   `winget`, ou mostra como instalar);
5. grava o `local.properties` (`sdk.dir` + `chaquopy.python`).

Adicione `--build` (bash) / `-Build` (PowerShell) para já compilar o APK arm64 debug.

## Compilar

```bash
./gradlew :app:assembleArm64Debug     # ou assembleArmv7Debug / assembleAmd64Debug
./gradlew :app:assembleRelease         # todos os ABIs, minificado (sem assinatura sem keystore)
```

Ou abra o projeto no Android Studio (Giraffe+) e clique em Run. Saída:
`app/build/outputs/apk/<abi>/<tipo>/KlipPocket_v<version>_<abi>.apk`.

O `preBuild` baixa os bundles do Fluidd/Mainsail do GitHub (precisa de rede no
primeiro build) e regera `app/src/main/assets/` a partir das fontes vendoradas em
`app/src/main/{klipper,kalico,moonraker,happyhare}`.

## Pré-requisitos manuais (se não usar o script)

- **JDK 21** (Temurin). AGP 8.10 / Gradle 8.12 precisam de 17+.
- **Android SDK** com: `platforms;android-35`, `build-tools;35.0.0`,
  `ndk;23.2.8568313`, `cmake;3.22.1`.
- **Python 3.10** — qualquer CPython 3.10.x; `pyenv install 3.10.14` funciona bem.
- `local.properties` na raiz do repositório:

  ```properties
  sdk.dir=/caminho/absoluto/para/Android/Sdk
  chaquopy.python=/caminho/absoluto/para/python3.10
  ```

## Testes

| O quê | Comando |
|---|---|
| Testes unitários em Kotlin (todas as regras de negócio do app: front ends, câmera, integração do add-on de recuperação de impressão, Prefs, o banco de perfis de impressora e os view models, configs do moonraker.conf/companions, edições do printer.cfg, roteamento/proxy do servidor web embutido, nomeação USB, o provedor de arquivos SAF, logs), sem aparelho | `./gradlew :app:testArm64DebugUnitTest` |
| Relatório de cobertura de linhas dos testes unitários | `./gradlew :app:jacocoUnitTestReport` (`app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`) |
| Testes do add-on do Klipper (`print_recovery`, `beam_beeper`/`beam_camera`), Python puro | `python3 -m unittest discover -s app/src/test/python -v` |
| Testes de interface (componentes Compose e o tile de front end), em aparelho ou emulador conectado | `./gradlew :app:connectedArm64DebugAndroidTest` |

Os testes de interface instalam um APK de teste ao lado do app, e o Gradle
desinstala os dois no fim — isso apaga os dados do app naquele aparelho, então
use um emulador ou um aparelho reserva.

Os testes unitários cobrem cada regra de negócio como um pequeno objeto Kotlin
testado de forma independente (portas, modelos de configuração, nomeação,
interpretação de texto, ...); a integração com Activity/Service, a E/S real de
Camera2/USB e a inicialização do processo Python são verificadas rodando o
próprio app, não por esses testes.

## Assinatura

Builds de debug usam a debug keystore. Para um release assinado, adicione ao
`local.properties` (já gitignored):

```properties
key.store=/caminho/absoluto/para/release.keystore
key.store.password=…
key.alias=…
key.key.password=…
```

Sem elas o release ainda é gerado, apenas sem assinatura.
