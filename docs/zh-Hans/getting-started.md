# 快速上手 — 安装 KlipPocket 并设置第一台打印机

**语言: [English](../getting-started.md) · [Português (BR)](../pt-br/getting-started.md) · [简体中文](getting-started.md)**

写给第一次接触的用户的分步指南。只要打印机已经运行
Klipper 固件，步骤都是一样的。

> 截图来自一部 Android 手机，Fluidd/Mainsail 页面在 Chrome 中打开。你的手机
> 界面（尤其是 Android 安装界面）可能略有不同，但步骤顺序相同。Fluidd 会使用浏览器的语言显示消息。

## 你需要准备

- 一台 Android 手机或平板（Android 5.0 及以上），并保持充电、放在打印机旁边。
- 一根 **USB OTG 数据线/转接头**，用来连接打印机和手机。
- 已经运行 **Klipper 固件** 的打印机（如果没有，请看 [build-firmware.md](build-firmware.md)）。
- 手机与用来打开网页的电脑必须在**同一个 Wi‑Fi 网络**中。

## 1. 下载正确的 APK

在项目的 **Releases** 页面下载 `.apk`。共有三个版本，请按手机处理器选择：

| APK | 适用于 |
|---|---|
| `arm64` | 2017 年以后的大多数手机（64 位） |
| `armv7` | 较旧的手机和平板（32 位）——**不确定时选它**，它也能在 64 位手机上运行 |
| `amd64` | 模拟器和 Intel/AMD 的 Android 设备 |

## 2. 安装 APK

1. 打开手机的**文件**（或**我的文件**）应用，进入 **Downloads（下载）**。
2. 点击 APK 文件。

<p align="center"><img src="../images/setup/01-apk-in-downloads.png" alt="下载文件夹中的 KlipPocket APK" width="288"></p>

3. 系统会询问是否安装，点击**安装**。（如果提示该来源被禁止安装，点击**设置**，为你的文件应用开启权限后返回。）

<p align="center"><img src="../images/setup/02-install-prompt.png" alt="安装提示" width="288"></p>

4. Google Play Protect 可能建议扫描该应用（因为它不是来自 Play 商店）。点击**扫描应用**并等待几秒——检查通过后安装会自动继续。（避免点**仍要安装（不扫描）**：在部分设备上它会让安装卡在“正在安装…”。）

<p align="center"><img src="../images/setup/03-play-protect.png" alt="Play Protect：建议扫描应用" width="288"></p>

5. 等待**正在安装…**完成，然后点击**打开**。

<p align="center"><img src="../images/setup/05-installing.png" alt="正在安装" width="288"></p>

> **“正在安装…”一直不结束？** 部分设备上，Google Play Protect 的检查会卡住，
> 导致安装停滞数分钟（应用本身没有问题）。点击**取消**，再次打开 APK 并选择**扫描应用**：
> Play Protect 批准后几秒内即可完成安装。无需关闭任何设置。

## 3. 首次启动：电池设置

打印期间应用必须在后台持续运行。在第一个界面中打开两个选项并点击**下一步**；当系统询问是否停止对 KlipPocket 的电池优化时，点击**允许**。

<p align="center"><img src="../images/setup/07-battery-dialog.png" alt="电池优化提示" width="288"> <img src="../images/setup/08-battery-done.png" alt="两个电池选项均已开启" width="288"></p>

随后进入主界面，添加打印机之前它是空的。（应用语言可随时在**设置 → 应用语言**中更改。）

<p align="center"><img src="../images/setup/09-main-empty.png" alt="空的主界面" width="288"></p>

## 4. 添加打印机

1. 点击底部的大号 **+** 按钮。
2. **名称**——随意填写，例如 `Printer 1`。
3. **配置**——点击并选择你的打印机型号对应的初始配置。列表较长且按名称排序，型号以 `printer-` 加品牌开头。请选择与你的打印机对应的那个。如果没有你的型号，选择 `example-cartesian.cfg`，之后再修改（见第 7 步）。
4. **自动启动**——如果希望应用打开时这台打印机自动启动，请打开。
5. 点击**创建**。

<p align="center"><img src="../images/setup/10-new-profile.png" alt="新建配置界面" width="216"> <img src="../images/setup/11-config-list.png" alt="配置列表" width="216"> <img src="../images/setup/12-pick-config.png" alt="选择打印机配置" width="216"> <img src="../images/setup/13-new-profile-filled.png" alt="填写完成的表单" width="216"></p>

打印机会出现在**实例**下，状态为 **Idle（空闲）**。

<p align="center"><img src="../images/setup/14-instance-created.png" alt="打印机已创建" width="288"></p>

## 5. 启动并允许 USB 访问

1. 用 OTG 数据线把打印机连接到手机。
2. 点击 **▶ 播放**按钮（底部的大按钮启动所有打印机；卡片上的小按钮只启动该打印机）。
3. 系统询问**“允许 KlipPocket 访问 USB Serial？”**——勾选**连接 USB Serial 时始终打开 KlipPocket**，然后点击**确定**。

<p align="center"><img src="../images/setup/16-usb-permission.png" alt="USB 权限提示" width="288"></p>

> 错过了提示，或点了取消？完全关闭应用后重新打开，提示会再次出现。如果 Fluidd/Mainsail 显示 **MCU error during connect**，几乎都是这个原因。

卡片会变为 **Running（运行中）**，顶部显示网页地址。Mainsail 是 `http://<手机IP>:4409/`，Fluidd 是 `http://<手机IP>:4408/`。

<p align="center"><img src="../images/setup/15-starting.png" alt="打印机运行中并显示网页地址" width="288"></p>

## 6. 打开 Fluidd 或 Mainsail

在同一 Wi‑Fi 下的电脑或另一部手机上，打开应用中显示的地址。要在两个界面之间切换，进入**设置 → Web 前端**并点击该图块。

<p align="center"><img src="../images/setup/17-settings-frontend.png" alt="设置：Web 前端图块" width="288"></p>

## 7. 处理“缺少配置”警告

第一次使用时，两个界面都会提示 `printer.cfg` 中缺少一些标准打印机功能（暂停/继续、取消打印等）。这是正常的——添加一次即可。

**Mainsail** 会显示橙色的 **Missing configuration** 框：

<p align="center"><img src="../images/setup/20-mainsail-missing-config.png" alt="Mainsail 缺少配置" width="720"></p>

**Fluidd** 会显示包含相同项目的**警告**框：

<p align="center"><img src="../images/setup/30-fluidd-missing-config.png" alt="Fluidd 警告" width="720"></p>

### 需要添加的内容

把下面的内容添加到 `printer.cfg` 顶部（任何不在其他配置段内的位置都可以）。**`gcode:` 下面各行的两个空格缩进是必须的。**

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

### 在 Mainsail 中

1. 点击左侧菜单的 **MACHINE**。
2. 在 **Config Files** 中点击 **printer.cfg**。

<p align="center"><img src="../images/setup/21-mainsail-machine-page.png" alt="Mainsail Machine 页面" width="720"></p>

3. 编辑器打开后，点击靠近顶部的空行并粘贴上面的内容。
4. 点击右上角的 **SAVE & RESTART**。

<p align="center"><img src="../images/setup/22-mainsail-printer-cfg-editor.png" alt="Mainsail 编辑器（修改前）" width="720"></p>
<p align="center"><img src="../images/setup/23-mainsail-cfg-added-lines.png" alt="已粘贴内容的 Mainsail 编辑器" width="720"></p>

重启后橙色框消失：

<p align="center"><img src="../images/setup/24-mainsail-after-restart.png" alt="没有警告的 Mainsail 仪表盘" width="720"></p>

### 在 Fluidd 中

1. 点击左侧菜单的 **{…} 配置** 图标。
2. 在配置文件列表中点击 **printer.cfg**。

<p align="center"><img src="../images/setup/31-fluidd-configuration-page.png" alt="Fluidd 配置页面" width="720"></p>

3. 点击靠近顶部的空行并粘贴内容。如果是手动输入而不是粘贴，Fluidd 的编辑器**不会**自动缩进——编辑器中出现红色行表示缺少那两个空格。
4. 点击顶部栏的**保存并重启**。

<p align="center"><img src="../images/setup/32-fluidd-printer-cfg-editor.png" alt="Fluidd 编辑器（修改前）" width="720"></p>
<p align="center"><img src="../images/setup/33-fluidd-cfg-added-lines.png" alt="已添加内容的 Fluidd 编辑器" width="720"></p>

警告消失，仪表盘上出现 **CANCEL_PRINT**、**PAUSE** 和 **RESUME** 按钮：

<p align="center"><img src="../images/setup/34-fluidd-after-restart.png" alt="修复后的 Fluidd 仪表盘" width="720"></p>

## 8. 如果 Moonraker 提示 `[virtual_sdcard]` 问题

KlipPocket 会自动为每台打印机写入带有正确文件夹的 `[virtual_sdcard]` 配置段，位于 `printer.cfg` 靠近末尾处（正好在 `#*# <--- SAVE_CONFIG --->` 行上方）。**不要自己再添加。**

如果仍出现类似 *“GCode path received from Klipper does not match expected location”* 的消息（通常是从其他环境复制了 `printer.cfg`，或重新创建了打印机之后），请打开 `printer.cfg`，找到 `[virtual_sdcard]`，把 `path:` 设置为消息中写出的**完全一致**的路径（或删除你自己的那一段并重启，让应用重新写入）。然后点击 **Save & Restart**。

## 9. 按你的打印机调整其余配置

初始文件是通用预设。请对照你的打印机检查 `[mcu]`、引脚、热床尺寸（`position_max`）、`z_offset`、PID 和网床（mesh）数值，并在首次打印前完成校准（PID、Z 偏移、热床网格）。如果你的打印机还没有运行 Klipper，请看 [build-firmware.md](build-firmware.md)。

## 下一步

- 添加摄像头并在预览中使用**点击对焦**：[webcam.md](webcam.md)。
- 远程访问：[octoeverywhere.md](octoeverywhere.md) · [obico.md](obico.md)。
- 可选附加组件：[mods/klipper-addons.md](mods/klipper-addons.md)。
