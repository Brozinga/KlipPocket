# 断电续打 —— 断电后恢复打印

**语言：[English](../print-recovery.md) · [Português (BR)](../pt-br/print-recovery.md) · [简体中文](print-recovery.md)**

打印中如果打印机断电、USB 线松脱或 Klipper 关机，KlipPocket 可以让你**从中断处继续打印**。这是尽力而为的功能：模型仍牢固粘在热床上时效果最好。手机有电池，因此即使打印机断电，打印位置也不会丢失。

<p align="center"><img src="../images/powerless-recovery.png" alt="Fluidd 询问是否继续被中断的打印" width="720"></p>

## 工作方式

1. 打印时，每 **2 秒**（可配置）把 G-code 文件位置、喷嘴位置、温度、风扇、速度/流量、Z 偏移和热床网格保存到一个小文件。
2. 打印机恢复且 Klipper 就绪后，Fluidd 和 Mainsail 会弹出 **"Print interrupted"** 窗口询问是否继续。
3. 点击 **Resume print** 后，打印机会：
   1. 加热热床；
   2. 抬高喷嘴，**仅** X/Y 回零，移动到 **X 限位处**（远离模型）；
   3. 在那里加热喷嘴，避免耗材滴在模型上；
   4. 可选：挤出少量耗材（清洗）；
   5. 回到保存的位置，从文件保存的字节处继续打印。

**Discard** 放弃该打印，随后把喷嘴抬离被放弃的模型（默认 **50 mm**）并让 X、Y 回零，均可配置（`discard_lift_z`、`discard_home_x`、`discard_home_y`）。关闭窗口后可在控制台运行 `PRINT_RECOVERY_STATUS` 再次显示（有待恢复的打印时每分钟也会再次提示）。

## 启用

在 `printer.cfg` 中添加并重启：

```ini
[print_recovery]
```

以下选项均为可选，显示的是默认值。不需要修改的选项可以直接省略。没有值的行不能写出来（`park_x`、`park_y` 和 `state_file` 只在你真正设置时才添加）。

```ini
[print_recovery]
snapshot_interval: 2
park_enable_x: True
park_enable_y: True
park_speed: 100
lift_z: 10
discard_lift_z: 50
discard_home_x: True
discard_home_y: True
purge: True
purge_length: 20
purge_speed: 5
purge_retract: 2
min_extruded: 5
macro_variables: *
prompt: True
prompt_repeat: 60
```

## 逐行说明

数字为普通小数（`2`、`0.5`、`-6`）。`True`/`False` 也接受 `1`/`0`、`yes`/`no`、`on`/`off`。

| 选项 | 作用 | 可接受的值 |
|---|---|---|
| `snapshot_interval: 2` | 保存打印位置的间隔（秒）。越小恢复越精确，但写入越多。 | `0.5` 到 `300` 的数字 |
| `park_enable_x: True` | 恢复时 X 回零，并移动到 X 限位处，在远离模型的地方加热喷嘴。`False`：X **不会**回零或移动，假定仍在原位。 | `True` / `False` |
| `park_enable_y: True` | Y 同理。两者都为 `False` 时，喷嘴仅抬高 `lift_z` 并在模型上方原地加热，并跳过清洗（否则耗材会落在模型上）。仅在确定没有移动时使用。 | `True` / `False` |
| `park_x: -6` | X 回零后用于加热和清洗的 X 位置。**省略该行**则使用机器 X 最小值，即 X 限位处（例如某些机器上为 `-6`，热床左侧）。 | X 行程内的数字 |
| `park_y: 0` | Y 同理。**省略该行**则停在 Y 回零后的位置（通常是热床前端）。 | Y 行程内的数字 |
| `park_speed: 100` | 停靠移动速度（mm/s）。 | 大于 `0` 的数字 |
| `lift_z: 10` | X/Y 回零前抬高喷嘴的毫米数，以避开模型。 | `0` 或更大的数字 |
| `discard_lift_z: 50` | 点 **Discard** 后喷嘴抬高的毫米数，以远离被放弃的模型。 | `0` 或更大的数字（`0` = 不抬） |
| `discard_home_x: True` | **Discard** 后 X 回零。 | `True` / `False` |
| `discard_home_y: True` | **Discard** 后 Y 回零。 | `True` / `False` |
| `purge: True` | 回到打印前先清洗少量耗材。仅在 X 或 Y 已停靠时执行。 | `True` / `False` |
| `purge_length: 20` | 清洗耗材长度（mm）。 | `0` 或更大的数字 |
| `purge_speed: 5` | 清洗速度（mm/s）。 | 大于 `0` 的数字 |
| `purge_retract: 2` | 清洗后回抽的毫米数，避免拉丝。 | `0` 或更大的数字 |
| `min_extruded: 5` | 首次保存前需挤出的毫米数，避免为尚未真正开始的打印保存数据。 | `0` 或更大的数字 |
| `macro_variables: *` | 保存并恢复哪些宏的 `variable_xxx` 值。 | `*`（所有不以 `_` 开头的宏）、`none`（不保存）或列表，如 `PRINT_START, MY_MACRO` |
| `prompt: True` | 在 Fluidd/Mainsail 中显示"Print interrupted"窗口。`False`：只显示控制台消息，使用下面的命令。 | `True` / `False` |
| `prompt_repeat: 60` | 有待恢复的打印时，两次提醒之间的秒数。 | `0` 或更大的数字（`0` = 仅一次） |
| `state_file: ~/recovery.json` | 快照保存位置。**省略该行**则保存在 `printer.cfg` 同目录。 | 文件路径（接受 `~`） |

### 手动恢复时覆盖设置

`PRINT_RECOVERY_RESUME` 接受相同的设置作为参数，仅对这一次恢复有效：

```gcode
PRINT_RECOVERY_RESUME PARK_ENABLE_X=1 PARK_ENABLE_Y=0 PARK_X=-6 PURGE=0 PURGE_LENGTH=10 LIFT_Z=5
```

| 参数 | 对应选项 | 可接受的值 |
|---|---|---|
| `PARK_ENABLE_X` | `park_enable_x` | `1`/`0`、`True`/`False`、`yes`/`no`、`on`/`off` |
| `PARK_ENABLE_Y` | `park_enable_y` | 同上 |
| `PARK_X` | `park_x` | 数字 |
| `PARK_Y` | `park_y` | 数字 |
| `PURGE` | `purge` | 同上 |
| `PURGE_LENGTH` | `purge_length` | `0` 或更大的数字 |
| `LIFT_Z` | `lift_z` | `0` 或更大的数字 |

`PRINT_RECOVERY_DISCARD` 接受 `LIFT_Z`（`0` 或更大的数字）、`HOME_X` 和 `HOME_Y`（取值同上）。未指定的项使用 `printer.cfg` 中的值或默认值。

## 宏变量

如果你的宏用变量保存状态（宏段中的 `variable_xxx:`，例如起始宏或层计数器），最近一次快照的值会被保存，并在重新打开文件前写回。仅保留简单值（数字、文本、`True`/`False`、列表）。这不会撤销宏在变量之外所做的事。可用 `macro_variables` 只保存部分宏或全部不保存。

## 命令

| 命令 | 作用 |
|---|---|
| `PRINT_RECOVERY_STATUS` | 显示中断的打印并再次弹窗 |
| `PRINT_RECOVERY_RESUME` | 恢复 |
| `PRINT_RECOVERY_DISCARD` | 放弃 |

## 局限

- **假定 Z 未变化。** 断电后电机失去力矩，若龙门下沉或被移动，喷嘴可能碰到模型，因此移动前会先抬高喷嘴（`lift_z`）。
- **模型会冷却**，可能翘边或脱落，恢复处通常会留下一条纹路。
- 中断前几秒的内容会被重打（G-code 比运动提前读取），最多留下很小的凸起。
- 主板没有断电检测，无法在断电瞬间抬起或回抽。
- G-code 文件被删除或修改时会拒绝恢复。
- **不支持 Delta（三角洲）打印机**：它们无法单独让 X、Y 回零。
- **自定义 `G28`**：若配置覆盖了回零流程（例如 `homing_override` 同时让 Z 回零），恢复时的 X/Y 回零步骤可能出错。请确认 `G28 X Y` 只让 X 和 Y 回零。
- **多耗材和多工具**（MMU/Happy Hare、IDEX、换头）不受处理：只保存和恢复当前挤出机。
- **风扇**：只恢复模型冷却风扇（`[fan]`）。其他风扇（`fan_generic`、控制器或辅助风扇）恢复为配置或宏所设的状态。
- **热室（腔体）**：不保存也不恢复腔体加热器及其温度。
- 延时摄影会保留中断前的画面；Moonraker 会把续打记为新的历史任务。
