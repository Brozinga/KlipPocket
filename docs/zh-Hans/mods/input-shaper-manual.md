# 无加速度计调 input shaper（ringing tower 方法）

**语言: [English](../../mods/input-shaper-manual.md) · [Português (BR)](../../pt-br/mods/input-shaper-manual.md) · [简体中文](input-shaper-manual.md)**

Klipper 的自动共振测试（`TEST_RESONANCES`、`SHAPER_CALIBRATE`）需要打印头上有一个 ADXL345 / LIS2DW / MPU-9250 加速度计。许多打印机没有，而在 KlipPocket 上加装一个需要把 SPI/I²C 传感器接到打印机 MCU（Android 设备帮不上忙）。

**ringing tower 测试**用眼睛 + 卡尺就能给你同样的两个数字：

1. X 和 Y 的**共振频率** → `shaper_freq_x` / `shaper_freq_y`
2. 附带一个安全的**最大加速度**

这就是 Klipper 文档自己针对无加速度计情况所描述的方法：<https://www.klipper3d.org/Resonance_Compensation.html>

---

## 1. 一次性准备

在 `printer.cfg` 里加一个**空的** input shaper section（这样测试可以把它关掉，也让你有地方写结果）：

```ini
[input_shaper]
```

把测试宏（本文件末尾）加到 `printer.cfg` 或一个 `[include]` 的文件里。

下载 Klipper 的 ringing 测试模型 **`ringing_tower.stl`**：<https://github.com/Klipper3d/klipper/raw/master/docs/prints/ringing_tower.stl>

---

## 2. 切片（这很关键 —— 切错了数字就是垃圾）

| 设置 | 值 |
|---|---|
| 层高 | 0.2–0.25 mm |
| 墙 / 外壁 | 2 |
| 顶 / 底层 | **0** |
| 填充 | **0 %** |
| 外壁速度 | **100 mm/s**，固定 —— 把这个数字记下来作为 `V` |
| 短层降速 / 最小层时间 | **关**（0 s） |
| 动态 / 自动速度、「外壁速度」渐变 | 关 |
| 切片软件里的 input shaper /「振动」/「ringing」选项 | 关 |
| 把塔打印在热床**中心附近** | 是 |

塔必须以**恒定**的外壁速度打印。如果你的切片软件坚持给前几层降速，把最小层时间提到 0，并只对这次打印禁用「冷却」速度限制。

---

## 3. 运行测试

在 Fluidd/Mainsail 控制台里，热床 + 喷嘴到温、打印机已 home 之后：

```
RINGING_TOWER
```

这个宏会：
- 禁用 input shaping 和 pressure advance，
- 把 square-corner-velocity 设低、cruise-ratio 设为 0（这样真正驱动拐角的是加速度），
- 布好一个 `TUNING_TOWER`，从 1500 mm/s² 开始，**每 5 mm 的 Z 把加速度提高 300 mm/s²**。

现在从文件列表启动 `ringing_tower.stl` 的打印。看着它；如果打印头开始严重打滑 / 丢步，就停掉 —— 那个高度以下的部分仍然有效。

---

## 4. 读塔

你会得到**两个**结果。

### 4a. 最大加速度

找出 ringing（每个凹口之后的鬼影回波）变得可接受地小的那个 Z 高度。高度 `Z` 处的加速度是：

```
accel = START + FACTOR * floor(Z_mm / BAND)
      = 1500  + 300    * floor(Z_mm / 5)
```

例子：在 Z = 40 mm 处干净 → `1500 + 300 * 8 = 3900 mm/s²`。实际打印用其 ~**80 %** 作为 `max_accel`。

### 4b. 共振频率（重要的那个）

塔在 **X 面**和 **Y 面**上都有凹口。ringing 表现为每个凹口*之后*一段衰减的波，出现在**与运动轴垂直**的那个面上：

- **朝 X** 的墙上的 ringing → **X** 共振
- **朝 Y** 的墙上的 ringing → **Y** 共振

用卡尺，测量**你能覆盖的尽量多个完整振荡**的距离（比如 4 个），然后相除：

```
D = (测得的总距离) / (振荡数量)      [每个振荡多少 mm]
f = V / D                            [Hz]
```

其中 `V` 是你在切片软件里设的外壁速度（100 mm/s）。

例子：4 个振荡跨越 8.0 mm → D = 2.0 mm → `f = 100 / 2.0 = 50 Hz`。

X 和 Y 分别做 —— 它们通常不同。在 bed-slinger 上，移动热床的那个轴（Y）通常是两者中较低的。

---

## 5. 应用结果

```ini
[input_shaper]
shaper_type_x: mzv
shaper_freq_x: 50          # 你测得的 X 频率
shaper_type_y: mzv
shaper_freq_y: 38          # 你测得的 Y 频率
```

`shaper_type` 参考：

| 情况 | 类型 |
|---|---|
| 默认，全面均衡 | `mzv` |
| 频率偏低（< 25 Hz）或仍然看到 ringing | `ei` |
| 非常低 / 非常嘈杂 | `2hump_ei` |
| 想要最少的平滑、且频率高又干净 | `zv` |

然后把 input shaper **打开**再跑一次塔（把宏的 `_DISABLE_SHAPER` 变量改成 0，或者在 `RINGING_TOWER` 里干脆不停）来确认在你的目标加速度下 ringing 已经消失。

另外要给加速度设上限，让 shaper 的「平滑」保持合理 —— 经验法则是让 `max_accel` 满足 `max_accel / min(shaper_freq_x, shaper_freq_y)` 大致 ≤ 100（超过这个数 Klipper 会警告）。

---

## 6. 宏

把下面这些加到 `printer.cfg`（或一个 `[include]` 的文件）：

```ini
[gcode_macro RINGING_TOWER]
description: Arma o teste da torre de ringing (input shaper sem acelerometro)
gcode:
    # ---- parametros (todos opcionais) ----
    {% set start_accel = params.START|default(1500)|int %}   # accel da 1a banda (mm/s^2)
    {% set factor      = params.FACTOR|default(300)|int %}    # incremento de accel por banda
    {% set band        = params.BAND|default(5)|int %}        # altura de cada banda (mm)
    {% set v           = params.SPEED|default(100)|int %}     # veloc. do perimetro externo no slicer (mm/s)
    {% set keep_shaper = params.KEEP_SHAPER|default(0)|int %} # 1 = nao desligar o input_shaper

    {% if not keep_shaper and printer.configfile.settings.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X=0 SHAPER_FREQ_Y=0
    {% endif %}
    {% if printer.extruder is defined %}
        SET_PRESSURE_ADVANCE ADVANCE=0
    {% endif %}

    SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=1 MINIMUM_CRUISE_RATIO=0 ACCEL={start_accel} ACCEL_TO_DECEL={start_accel}

    TUNING_TOWER COMMAND=SET_VELOCITY_LIMIT PARAMETER=ACCEL START={start_accel} FACTOR={factor} BAND={band}

    M118 Ringing tower ARMADO.
    M118 accel(Z) = {start_accel} + {factor} * floor(Z/{band})
    M118 Imprima ringing_tower.stl (2 perimetros, 0 top/bottom, 0% infill, perimetro externo a {v} mm/s constante).
    M118 Depois: meca D entre as oscilacoes; frequencia f = {v} / D (Hz). Faca X e Y separado.

[gcode_macro RINGING_TOWER_RESET]
description: Restaura limites normais apos o teste da torre de ringing
gcode:
    {% set cfg = printer.configfile.settings %}
    SET_VELOCITY_LIMIT VELOCITY={cfg.printer.max_velocity} ACCEL={cfg.printer.max_accel} SQUARE_CORNER_VELOCITY={cfg.printer.square_corner_velocity|default(5)} MINIMUM_CRUISE_RATIO={cfg.printer.minimum_cruise_ratio|default(0.5)}
    {% if cfg.input_shaper is defined %}
        SET_INPUT_SHAPER SHAPER_FREQ_X={cfg.input_shaper.shaper_freq_x|default(0)} SHAPER_FREQ_Y={cfg.input_shaper.shaper_freq_y|default(0)}
    {% endif %}
    M118 Limites restaurados do printer.cfg.
```

> 宏内的注释保留原文（葡萄牙语）以便与英文/葡文版本逐字一致；命令本身与语言无关。

用法：

```
RINGING_TOWER                 # 默认: START=1500 FACTOR=300 BAND=5 SPEED=100
RINGING_TOWER START=1000 FACTOR=200 SPEED=80
# ... 打印 ringing_tower.stl，测量，计算 f = SPEED / D ...
RINGING_TOWER_RESET
```
