# 固件标签页 —— 在应用内下载并安装 MCU 固件

**语言：[English](../firmware-tab.md) · [Português (BR)](../pt-br/firmware-tab.md) · [简体中文](firmware-tab.md)**

**固件**标签页（顶部栏的芯片图标）可以获取固件文件，并写入连接到手机/平板的主板，无需电脑。
它相当于 [PC 菜单](build-firmware.md)中选项 **6 Flash Firmware** 在设备上的版本：
固件本身仍需在**电脑上编译**（Android 无法编译），这里只负责获取和写入。

![固件标签页](../images/firmware-tab/tab-home.png)

## 支持与不支持

| 方式 | 主板 | 状态 |
|---|---|---|
| **RP2040** | 已处于 BOOTSEL 的树莓派 Pico / RP2040（`2e8a:0003`） | **可用**（已在 Pico 上测试） |
| **USB** | 通过 USB 运行 Klipper 的 Pico / RP2040：应用自动使其进入 BOOTSEL（1200 bps 请求）并写入 | **可用**（已在 Pico 上测试） |
| **DFU** | 处于 DFU 模式的 STM32（`0483:df11`） | 暂不可用：会运行并报告失败，**不会写入任何内容** |
| **UART** | 连接到串口引导程序的 USB 串口适配器 | 暂不可用：同 DFU |

另外**不**支持：Pico 2 / RP2350、通过 SD 卡加载固件的主板（例如 Neptune 3 Pro：请把 `.bin` 复制到 SD 卡）以及 CAN 主板。
文件必须是为 RP2040 构建的 `.uf2`（最安全）或原始 `.bin`；应用会拒绝其他芯片的 `.uf2`。

> **写入固件可能使主板无法使用。** 只写入为该主板构建的文件，保持线缆连接并保持界面打开，
> 并记住写入期间 Klipper 会失去与该主板的连接。

## 三个按钮

- **列出 USB 设备** —— 与 *设置 → USB → 列出 USB 设备* 相同的列表。

  ![USB 设备](../images/firmware-tab/list-usb.png)

- **下载固件** —— 粘贴链接，应用会保存文件。
- **安装固件** —— 下面的分步向导。

按钮下方的**固件文件**列出应用中保存的文件（名称、类型、大小），可**删除**；
**从设备导入**通过系统文件选择器复制你已有的文件（例如在电脑上编译的）。
接受 `.bin`、`.hex`、`.uf2` 和 `.zip`（会提取其中的固件文件）。

## 下载固件

点按**下载固件**，粘贴 **https** 链接并点按**下载**，例如
`https://github.com/utkabobr/klipper/releases/download/prebuilt-v0.12.0/stm32f401.bin`。
大小限制：16 MB。

| | |
|---|---|
| ![下载对话框](../images/firmware-tab/download-dialog.png) | ![文件已保存](../images/firmware-tab/download-saved.png) |

## 安装固件

点按**安装固件**。每一步都有**下一步** / **返回**；手机的返回键也会返回一步。

**1 —— 选择文件。** 选择已保存的文件之一。

![第 1 步](../images/firmware-tab/step1-file.png)

**2 —— 选择设备。** 列出手机看到的所有 USB 设备；**刷新**会更新列表（也会自动刷新）。
处于 BOOTSEL 的主板显示为 *RP2 Boot · 2e8a:0003*，运行 Klipper 的 Pico 显示为 *rp2040 · 1d50:614e*。
需要时 Android 会请求 USB 权限。

![第 2 步](../images/firmware-tab/step2-device.png)

**3 —— 选择写入方式。** 应用会根据设备给出建议（Klipper 主板用 *USB*，BOOTSEL 用 *RP2040*）。

![第 3 步](../images/firmware-tab/step3-mode.png)

**4 —— 确认。** 警告页显示文件、设备和方式。**取消/返回**不会改变任何内容；**确认**开始写入。

![第 4 步](../images/firmware-tab/step4-warning.png)

**进度。** 屏幕保持常亮并显示当前阶段：准备、使主板进入 BOOTSEL、等待、USB 权限、擦除、写入、校验（读回并比对）、重启、等待主板。

![进度](../images/firmware-tab/progress.png)

**结果。** 成功表示已写入、校验，并且（使用 USB 时）主板已作为 Klipper 设备重新出现在总线上。
如果数据已校验但主板没有重新出现在 USB 上，应用会提示。失败时会用你的语言说明原因。

| | |
|---|---|
| ![成功](../images/firmware-tab/success.png) | ![失败](../images/firmware-tab/failure.png) |

**查看日志**会打开日志标签页中新的**固件写入**来源。

## 日志

每次写入都会被记录（文件、sha256、设备、方式、各阶段、结果）：

![固件日志](../images/firmware-tab/logs.png)

## 主题与语言

该标签页遵循应用的主题和语言（English、Português、简体中文、繁體中文、Русский）。

| | |
|---|---|
| ![浅色主题](../images/firmware-tab/light-tab.png) | ![浅色主题，警告](../images/firmware-tab/light-warning.png) |
| ![葡萄牙语](../images/firmware-tab/pt-br-tab.png) | ![葡萄牙语，失败](../images/firmware-tab/pt-br-failure.png) |

## 出问题时

| 提示 | 处理方法 |
|---|---|
| 主板未进入 BOOTSEL 模式 | 插入主板时按住 **BOOTSEL**，并使用 *RP2040* 方式。 |
| 未授予 USB 权限 | 重试并接受 Android 的提示。 |
| 此 UF2 不适用于 RP2040 | 为 RP2040 构建固件（见 [build-firmware.md](build-firmware.md)）。 |
| 写入后闪存与文件不一致 | 不要拔线，重新运行。主板会停留在 BOOTSEL，随时可以重新写入。 |
| 该写入方式暂不可用 | DFU 和 UART 尚未实现；未写入任何内容。 |

Pico 始终保留其 BOOTSEL 引导程序，因此写入失败时按住 BOOTSEL 重新写入即可恢复。
