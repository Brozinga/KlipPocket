# KlipPocket 内置的 Klipper 附加模块

**语言: [English](../../mods/klipper-addons.md) · [Português (BR)](../../pt-br/mods/klipper-addons.md) · [简体中文](klipper-addons.md)**

这些附加模块已随包附带但处于非激活状态 —— 只有当 `printer.cfg` 中存在对应的 `[section]` 时 Klipper 才会加载其中之一，因此不引用它们的配置不受影响。它们的选择标准是能在 Android / Chaquopy 下运行（Python 3.10，设备上没有编译器，用户没有 `pip`）。

| 模块 | 启用的 section | 需要 |
|---|---|---|
| KAMP（自适应网床 + 清料） | `[include KAMP/KAMP_Settings.cfg]` | `[exclude_object]`（core）、`[bed_mesh]` |
| LED Effect | `[led_effect NAME]` | 一条可寻址的 `[neopixel]` / `[dotstar]` |
| Z Calibration | `[z_calibration]` | 探针 + 一个固定的金属参考点 |
| Auto Speed | `[auto_speed]` | 无（检测丢步） |
| TMC Autotune | `[autotune_tmc stepper_x]` … | 已配置好的 `[tmc2209]`/`[tmc5160]`/… |
| 电涡流探针 | `[probe_eddy_current]` + `[ldc1612]` | BTT Eddy / LDC1612 传感器（是 Klipper **core**，已存在） |

> 无加速度计调 input shaper：见 [input-shaper-manual.md](input-shaper-manual.md)。

---

## KAMP —— Klipper Adaptive Meshing & Purging

Klipper 首次启动时自动写入 `<实例>/config/KAMP/`（5 个文件，已存在则不覆盖）。

**启用：** 加到 `printer.cfg`（靠近顶部，在 `[exclude_object]` 之后）：

```ini
[include KAMP/KAMP_Settings.cfg]
```

然后在 Fluidd/Mainsail 的配置编辑器里打开 `KAMP/KAMP_Settings.cfg`，取消注释你想要的部分：

```ini
[include Adaptive_Meshing.cfg]   # 只探测打印件占用的区域
[include Line_Purge.cfg]         # 在实际打印件前面的清料线
[include Smart_Park.cfg]         # 停靠在打印件旁边做最终保温
#[include Voron_Purge.cfg]       # 在 Voron logo 上的团状清料（需要 logo 宏）
```

**接入你的起始序列** —— 在 `PRINT_START` 里，替换掉普通的 mesh / 清料调用：

```ini
BED_MESH_CALIBRATE ADAPTIVE=1     # 代替普通的 BED_MESH_CALIBRATE
SMART_PARK                        # 就在喷嘴最终加热之前
LINE_PURGE                        # 代替手写的清料线
```

切片软件必须输出 `EXCLUDE_OBJECT_DEFINE`（OrcaSlicer / Creality Print：开启「Label objects」；PrusaSlicer/SuperSlicer：启用「Label objects」；Cura：*Exclude Objects* 插件）—— 这是告诉 KAMP 打印件实际在哪里的东西。

各项设置在 `_KAMP_Settings` 宏里（清料量、流量、边距、smart-park 高度）。

---

## LED Effect（`julianschill/klipper-led_effect`）

可寻址 LED 灯带的动画效果（进度条、温度渐变、箱体照明、「打印中 / 加热中 / 完成」状态）。

**启用：** 你需要先有一条可寻址 LED，例如：

```ini
[neopixel chamber]
pin: PA8
chain_count: 20
color_order: GRB

[led_effect panel_progress]
leds:
    neopixel:chamber
autostart: false
frame_rate: 24
layers:
    progress  10 0 add  (0,0,1),(0,1,0)
```

命令：`SET_LED_EFFECT EFFECT=panel_progress`、`STOP_LED_EFFECTS`。完整的 layer 语法：<https://github.com/julianschill/klipper-led_effect/blob/master/docs/LED_Effect.md>

注意：Happy Hare v4 自带**自己的** `[mmu_led_effect]` 用于 MMU 状态灯 —— 那与这个 `[led_effect]` 是分开、独立的。

---

## Z Calibration（`protoloft/klipper_z_calibration`）

由三次测量计算实时 Z-offset —— 喷嘴在一个金属点上、探针在同一个点上、探针在热床上 —— 这样在换喷嘴和温度变化时它都保持正确。主要在配合 switch/klicky/电感探针以及热床旁一个固定金属片时有用。

**启用：**

```ini
[z_calibration]
nozzle_xy_position:   <x>,<y>     # 喷嘴位于金属参考点上方
switch_xy_position:   <x>,<y>     # 探针位于同一个点上方
bed_xy_position:      <x>,<y>     # 热床上一个安全的点（网床中心即可）
switch_offset:        0.5         # 你探针的触发-到-接触间隙，从 ~0.5 开始
start_gcode:          <部署探针的宏，如果是可停靠式>
```

在 homing + QGL/mesh 之后运行 `CALIBRATE_Z`。文档：<https://github.com/protoloft/klipper_z_calibration>

---

## Auto Speed（`Anonoei/klipper_auto_speed`）

通过驱动打印头并检测丢步（home 之后对比命令位置与实测步进位置），找出你真正的最大加速度和速度。无需加速度计。

**启用：**

```ini
[auto_speed]
margin: 20            # 距轴限位的安全边距
```

`z` 不是配置项，而是 `AUTO_SPEED` 命令本身的参数（`AUTO_SPEED Z=50`），
如果你想先移动到指定 Z 高度再测试可以这样用。

运行 `AUTO_SPEED`（完整扫描，约几分钟）或 `AUTO_SPEED_VELOCITY` / `AUTO_SPEED_ACCEL`。它会打印推荐的 `max_velocity` / `max_accel`。可选的方差图需要 `matplotlib`（未安装）—— 数值结果不需要。文档：<https://github.com/Anonoei/klipper_auto_speed>

---

## TMC Autotune（`andrewmcgr/klipper_tmc_autotune`）

根据电机数据手册计算合适的 TMC 驱动寄存器值（电流控制、`PWM`、`CoolStep`、`StealthChop`/`SpreadCycle` 阈值），而不是手动调。已刷新到上游 `main`（2026-08）。

**启用** —— 你已经配置好的每个驱动一个 section：

```ini
[autotune_tmc stepper_x]
motor: ldo-42sth40-1004ah        # 在 extras/motor_database.cfg 里查你的电机
[autotune_tmc stepper_y]
motor: ldo-42sth40-1004ah
[autotune_tmc extruder]
motor: ldo-36sth20-1004ahg
```

如果你的电机不在 `motor_database.cfg` 里，选最接近的或自己加。文档：<https://github.com/andrewmcgr/klipper_tmc_autotune>

---

## 电涡流探针（BTT Eddy、通用 LDC1612）—— Klipper core

不需要任何模块 —— `[probe_eddy_current]` 和 `[ldc1612]` 是内置 Klipper 0.13 的一部分。按 Klipper 文档接线（<https://www.klipper3d.org/Eddy_Probe.html>）。这是**唯一**在 Android 上安全的扫描探针方案（见下一节）。

### 为什么不用 Beacon / Cartographer

`beacon.py` 和 `cartographer.py` 都会起一个 `multiprocessing.Process` 来做采样流式传输，而 Cartographer 的模型拟合还想要 `scipy`。`multiprocessing` 在 Android 的 Chaquopy 下不可靠（无法安全 `fork()` 应用进程），而 `scipy` 也没安装。它们**没有内置**。如果你有这类硬件，BTT-Eddy 式的 `[probe_eddy_current]` 是受支持的路线。
