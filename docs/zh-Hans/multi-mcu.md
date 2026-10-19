# 使用多个 MCU（multi-MCU）

**语言：[English](../multi-mcu.md) · [Português (BR)](../pt-br/multi-mcu.md) · [简体中文](multi-mcu.md)**

有些打印机在主板之外还会再接一块板：工具头板、用来扩展接口（驱动、限位、风扇）的板，
或只负责传感器（加速度计、涡流探头）的小板。在 Klipper 里，每一块都是一个额外的
`[mcu 名称]` 配置段。

KlipPocket **没有专门的多 MCU 界面**，但可以手动使用：应用会为每块 USB 板提供一个
独立的虚拟串口，你把这个串口写进 `printer.cfg` 即可。本页介绍做法。

> **尚未用两块板测试。** 下面的步骤依据应用目前处理 USB 设备的方式编写。
> 如果你试过，欢迎反馈结果。

## 哪些可用，哪些不可用

| 方案 | 在应用中 |
|---|---|
| 刷入 **USB** 固件的额外板（Klipper USB / CDC） | 可行，见下文 |
| 走 **CAN 总线** 的额外板（包括 USB 转 CAN 适配器） | **不支持** —— Android 不向应用提供 CAN 接口 |
| 只有 **UART** 引脚的板 | 不支持 —— 应用只使用 USB |

因此额外的板必须在[固件构建](build-firmware.md)中选择 **USB** 接口并刷入。
很多板出厂是 CAN 固件，或两种都能刷，请先确认你的板是哪一种。

## 你需要

- 所有板都能通过 USB 连到手机/平板：强烈建议使用**带独立供电的 USB 集线器**，
  因为设备无法同时给多块板供电。
- 额外板上的电机和加热器仍需要自己的电源：USB 只给板子的逻辑部分供电。
- 每块板都刷入 Klipper 固件（USB 接口）。

## 步骤 1 —— 选择设备命名方式

打开 **设置 → USB**。**Device naming**（设备命名）决定每个虚拟串口的名称。
点按它会在两种模式之间切换：

| By path | By VID/PID |
|---|---|
| ![Device naming 为 By path](../images/multi-mcu/usb-naming-by-path.png) | ![Device naming 为 By VID/PID](../images/multi-mcu/usb-naming-by-vid-pid.png) |

- **By VID/PID** —— 以 USB 厂商/产品 ID 命名，例如 `1a86_7523`。把线插到别的接口
  名称也不变。当各板 ID **不同**时使用（例如主板是 CH340，另一块板是别的芯片）。
- **By path** —— 以 USB 总线路径命名，例如 `_dev_bus_usb_001_003`。当两块板 ID
  **相同**时使用。所有运行 Klipper 自带 USB 固件的板都显示同一个 ID（`1d50:614e`），
  所以两块板在 *By VID/PID* 下会共用一个名称。缺点是重新插拔或换集线器接口后路径可能改变，
  这时需要更新 `printer.cfg`。

更改命名方式会改变路径，所以请在编辑 `printer.cfg` 之前决定，并在更改后重启配置。

## 步骤 2 —— 找到每块板的路径

1. 把所有板（通过集线器）连上，并为每块板允许 USB 权限提示。
2. 打开 **设置 → USB → List USB devices**。

每个被识别的板会显示 `vid/pid`、设备名、驱动，下一行是**要写进 `printer.cfg` 的串口路径**。
真实示例：

第一项是主板（CH340 芯片，`1a86/7523`），串口路径在下一行。第二项（`1908/226`）是摄像头：它不是 Klipper 板，所以没有路径那一行。同一块板在不同命名方式下路径不同：

| By path | By VID/PID |
|---|---|
| ![USB 设备列表，By path](../images/multi-mcu/usb-list-by-path.png) | ![USB 设备列表，By VID/PID](../images/multi-mcu/usb-list-by-vid-pid.png) |

请复制每块 Klipper 板的完整路径（以 `/data/user/0/…` 开头的那一行）。

## 步骤 3 —— 写进 `printer.cfg`

`[mcu]` 使用主板的路径，每块额外的板再加一个 `[mcu 名称]` 配置段。
照常通过 Fluidd/Mainsail 编辑 `printer.cfg`。

```ini
[mcu]
serial: /data/user/0/com.brozinga.klippocket/files/serial/1a86_7523

[mcu toolhead]
serial: /data/user/0/com.brozinga.klippocket/files/serial/1d50_614e
```

额外板上的引脚要加名称前缀，`名称:引脚`：

```ini
# 额外板上的一个风扇和一个限位
[fan_generic part_fan]
pin: toolhead:PA0

[stepper_z]
endstop_pin: toolhead:PB6

# 仅传感器板上的加速度计
[adxl345]
cs_pin: toolhead:gpio9
spi_bus: spi1a

[resonance_tester]
accel_chip: adxl345
probe_points: 100, 100, 20
```

引脚名称取决于具体的板，请查该板自己的文档。像 `[mcu toolhead]` 这样的配置段只需要
`serial:`；没有 `baud:` 需要设置（应用会自行处理连接）。

保存并重启。在 Fluidd/Mainsail 的 **System** 面板中会列出两个 MCU，`Klipper` 应进入 **Ready**。

## 注意事项

- **启动前先接好额外板。** 如果已配置的 MCU 缺失，Klipper 无法完成启动，
  并提示 *Unable to connect*。
- **重新刷写或重新插拔**在 *By path* 下可能改变路径。如果 Klipper 报
  *Unable to open serial port*，请再次运行 **List USB devices** 并更新路径。
- **同芯片同 ID：** 两块板 ID 相同又使用 *By VID/PID* 时会抢同一个名称，请改用 *By path*。
- 两个引擎都适用，[Klipper 和 Kalico](kalico.md)。
- 依赖额外软件的板（例如 Happy Hare）是另一回事：这些模块在应用中不可用。

## 快速排查

| 提示 | 可能原因 |
|---|---|
| `Unable to open serial port` | `printer.cfg` 中的路径已不存在。重新列出设备。 |
| `mcu 'toolhead': Unable to connect` | 板未连接、没有 USB 固件，或没有供电。 |
| **List USB devices** 中没有该板 | 未允许权限、线缆只供电，或集线器没有独立供电。 |
