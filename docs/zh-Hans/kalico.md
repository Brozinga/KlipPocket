# 使用 Kalico

**语言：[English](../kalico.md) · [Português (BR)](../pt-br/kalico.md) · [简体中文](kalico.md)**

[Kalico](https://github.com/KalicoCrew/kalico) 是 Klipper 的社区分支，允许使用
Klipper 因可能有风险而不提供的功能和行为。KlipPocket 在 Klipper 之外还内置了
**Kalico v2026.10.00**，由你选择用哪一个来运行打印机。

## 切换引擎

1. 如果打印机配置正在运行，先停止它。
2. 打开**设置**，点击**固件引擎**，在 Klipper 和 Kalico 之间切换。
3. 再次启动该配置。

内置版本显示在**设置 → 软件版本**中。切换不会改动你的打印机配置和文件。

## `printer.cfg` 可能需要的一处修改

Kalico 默认启用 `[respond]`，因此自带 `M118`。如果你的配置里定义了
`[gcode_macro M118]`，Kalico 会报
`gcode command M118 already registered` 并停止。请把这个宏换成 `[respond]`
段，它在 Klipper 和 Kalico 中都有效：

```ini
[respond]
```

## 可以正常使用的

在真实打印机（Neptune 3 Pro，通过 USB）上、使用应用内的 Fluidd 验证过：

- 启动、连接打印机主板以及网页界面。
- Kalico 内置的 `M118` 和 `RESPOND`、`BED_MESH_CHECK` 和
  `RELOAD_GCODE_MACROS`（无需重启即可重新加载宏）。
- Kalico 的默认设置生效：`[respond]`、`[force_move]` 和 `[exclude_object]`
  无需写在配置里就会加载。
- 一份真实配置无报错加载：KAMP、延时摄影宏、Auto Speed、`bed_mesh`、
  `probe`、`safe_z_home`、`input_shaper`、`pause_resume`、`display_status`
  以及断料传感器。

Happy Hare（多耗材）在 Kalico 上同样可以加载并响应 `MMU_*` 命令，见 [Happy Hare](happy-hare.md)。

仅验证了加载（模块能导入并启动，但没有在打印中运行）：LED Effect、
TMC Autotune、Print Recovery、蜂鸣器和摄像头扩展，以及 Z Calibration。

## 不能使用或未测试的

- **Z Calibration** 需要物理的 Z 限位开关，Klipper 上同样如此。如果用探针作为
  Z 限位（`endstop_pin: probe:z_virtual_endstop`），它会以
  *A virtual endstop for z is not supported* 停止。
- **含有 `[gcode_macro M118]` 的配置** 在 Kalico 上无法启动，需先按上文替换该宏。
- **为 Klipper 编译的主板固件** 可以工作，但 Kalico 会提示建议为 Kalico 重新刷写。
- **可选主板**（`is_non_critical`）未测试。
- Kalico 改变了一些行为，例如 `controller_fan` 新增了 `standby_speed`。请参阅
  [Kalico 文档](https://docs.kalico.gg/Kalico_Additions.html)。
