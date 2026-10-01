# 构建 APK

**语言: [English](../build-app.md) · [Português (BR)](../pt-br/build-app.md) · [简体中文](build-app.md)**

构建需要 Android SDK/NDK、一个 JDK，以及一个 **Python 3.10** 解释器（Chaquopy 会针对
3.10 编译内置的 Python）。不会提交任何泄露本地路径的东西 —— `local.properties`、
`.gradle/`、`build/`、`firmware/toolchain/`、`firmware/.klipper-build/` 以及
`fluidd`/`mainsail` 的下载产物都被 gitignore 并会重新生成。

## 一键设置

| 系统 | 命令 |
|---|---|
| Linux / macOS | `./scripts/setup.sh` |
| Windows（PowerShell） | `.\scripts\setup.ps1` |

脚本会：

1. 检查是否有 JDK（17+，推荐 21）；
2. 如果没有 SDK，则下载 Android 命令行工具；
3. 安装项目固定的 SDK 包 —— `platform-tools`、`platforms;android-35`、
   `build-tools;35.0.0`、`ndk;23.2.8568313`、`cmake;3.22.1`；
4. 确保存在 Python 3.10 解释器（通过 `pyenv` / `brew` / `winget`，或提示如何安装）；
5. 写入 `local.properties`（`sdk.dir` + `chaquopy.python`）。

加上 `--build`（bash）/ `-Build`（PowerShell）可顺便构建 arm64 debug APK。

## 构建

```bash
./gradlew :app:assembleArm64Debug     # 或 assembleArmv7Debug / assembleAmd64Debug
./gradlew :app:assembleRelease         # 所有 ABI，已 minify（无 keystore 时不签名）
```

或在 Android Studio（Giraffe+）中打开项目并 Run。输出：
`app/build/outputs/apk/<abi>/<type>/KlipPocket_v<version>_<abi>.apk`。

`preBuild` 会从 GitHub 下载 Fluidd/Mainsail 包（首次构建需要联网），并从
`app/src/main/{klipper,kalico,moonraker,happyhare}` 的 vendored 源重新生成
`app/src/main/assets/`。

## 手动前置条件（不用脚本时）

- **JDK 21**（Temurin）。AGP 8.10 / Gradle 8.12 需要 17+。
- **Android SDK**，包含：`platforms;android-35`、`build-tools;35.0.0`、
  `ndk;23.2.8568313`、`cmake;3.22.1`。
- **Python 3.10** —— 任意 CPython 3.10.x；`pyenv install 3.10.14` 效果很好。
- 仓库根目录的 `local.properties`：

  ```properties
  sdk.dir=/Android/Sdk 的绝对路径
  chaquopy.python=/python3.10 的绝对路径
  ```

## 测试

| 内容 | 命令 |
|---|---|
| Kotlin 单元测试（应用的全部业务规则：前端、摄像头、断电续打附加模块的接入、Prefs、打印机配置数据库与视图模型、moonraker.conf/伴生程序配置、printer.cfg 编辑、内置网页服务器的路由/代理、USB 命名、SAF 文件提供者、日志），无需设备 | `./gradlew :app:testArm64DebugUnitTest` |
| 单元测试的行覆盖率报告 | `./gradlew :app:jacocoUnitTestReport`（`app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`） |
| Klipper 附加模块测试（`print_recovery`、`beam_beeper`/`beam_camera`），纯 Python | `python3 -m unittest discover -s app/src/test/python -v` |
| 界面测试（Compose 组件和前端切换卡片），需连接设备或模拟器 | `./gradlew :app:connectedArm64DebugAndroidTest` |

界面测试会在应用旁安装一个测试 APK，结束后 Gradle 会把两者都卸载 —— 这会清除该设备上的应用数据，请使用模拟器或备用设备。

单元测试把每条业务规则都当作一个独立测试的小型 Kotlin 对象来覆盖（端口、配置模板、命名、解析……）；Activity/Service 的胶水代码、真实的 Camera2/USB I/O 以及 Python 进程的启动，则通过实际运行应用来验证，而不是这些测试。

## 签名

Debug 构建使用 debug keystore。要签名的 release，在 `local.properties`（已 gitignore）中加入：

```properties
key.store=/release.keystore 的绝对路径
key.store.password=…
key.alias=…
key.key.password=…
```

没有这些时 release 仍会生成，只是不签名。
