# 使用 Happy Hare（多耗材 / MMU）

**语言：[English](../happy-hare.md) · [Português (BR)](../pt-br/happy-hare.md) · [简体中文](happy-hare.md)**

[Happy Hare](https://github.com/moggieuk/Happy-Hare) 是 Klipper 上用于多材料单元（MMU）的驱动：
ERCF、Tradrack、BoxTurtle、3MS、3D Chameleon、HTLF、KMS、MMX 等。KlipPocket 内置
**Happy Hare v4.1.0** 及其 Moonraker 组件，Klipper 和 [Kalico](kalico.md) 都能使用。
只有你的配置要求时才会启用，所以没有 MMU 的打印机不受影响。

> **尚未用实体 MMU 测试。** 内置的 Happy Hare 已通过两项检查：在应用所用的 Python 版本上运行它自带的测试套件；
> 在模拟的主板上用生成的 Tradrack 配置启动 Klipper 和 Kalico（`mmu` 对象成功加载，`MMU_*` 命令有响应）。
> 如果你在真实硬件上试过，欢迎反馈结果。

## 你需要什么

- 一块刷入 Klipper **USB** 固件的 MMU 主板。只支持 **CAN**（例如 CAN 模式的 MMB）或只支持 **UART** 的板，
  应用无法连接，参见 [多 MCU](multi-mcu.md#哪些可用哪些不可用)。
- 主板能通过 USB 连接到手机/平板，最好经过带独立供电的 USB 集线器。电机和舵机需要自己的电源。
- 一台电脑，用几分钟生成 Happy Hare 配置（第 1 步）。手机上不需要安装任何东西。

## 第 1 步 —— 在电脑上生成 Happy Hare 配置

Happy Hare 通过引导式菜单写出配置：你回答关于 MMU 的问题（类型、通道数、主板、传感器），而不用手动改引脚。
在 Linux 或 macOS 电脑上：

```sh
git clone --branch v4.1.0 https://github.com/moggieuk/Happy-Hare.git
cd Happy-Hare
./install.sh -i -t
```

`-t` 是 Happy Hare 的测试模式：把结果写到 `/tmp/mmu_test`，不需要安装 Klipper 或 Moonraker。
完成后，在 `/tmp/mmu_test/printer_data/config` 中可以找到：

- **`mmu`** 文件夹（`base`、`macros`、`optional` 和 `mmu_vars.cfg`）；
- Happy Hare 修改过的 `printer.cfg` 和 `moonraker.conf`，其中能看到它添加的行（第 2、4 步会用到）。

请使用与应用相同的 Happy Hare 版本（v4.1.0）：配置格式会随版本变化，Happy Hare 会拒绝较旧版本生成的配置。

## 第 2 步 —— 把配置复制到应用中

1. 打开该打印机配置的配置文件夹，也就是存放 `printer.cfg` 的位置。有两种方式：
   Fluidd/Mainsail 的配置文件管理器（新建文件夹并上传文件），或 Android「文件」应用里的
   KlipPocket 条目，它会显示每个配置的 `config` 文件夹。
2. 把整个 **`mmu`** 文件夹复制进去，得到 `mmu/base/…`、`mmu/macros/…` 和 `mmu/mmu_vars.cfg`。
3. 在 `printer.cfg` 靠前的位置加入这两行（Happy Hare 会在测试模式的输出里加上它们）：

   ```ini
   [include mmu/macros/*.cfg]
   [include mmu/base/*.cfg]
   ```

   如果你在安装程序里选了可选菜单或客户端宏，也要加上它写出的对应 `[include mmu/optional/…]` 行。
4. 确认 `printer.cfg` 里仍有 `[pause_resume]` 段，并且你自己的 `PAUSE`、`RESUME`、`CANCEL_PRINT`
   宏没有重复定义（Happy Hare 已提供）。重复会让 Klipper 因
   `gcode command … already registered` 而停止。

生成的 `mmu/base/mmu_macro_vars.cfg` 里有一个 `[save_variables]` 块，它的 `filename` 是电脑上的路径
（例如 `/tmp/mmu_test/printer_data/config/mmu/mmu_vars.cfg`）。**你不需要修改它：**
当手机上不存在该文件夹时，应用会改用打印机配置自己的配置文件夹下的相同路径，
Happy Hare 因此把状态保存在 `mmu/mmu_vars.cfg`，与你的其他文件放在一起。备份或迁移配置时请保留这个文件。

## 第 3 步 —— 让 Happy Hare 指向主板

Happy Hare 的 `mmu/base/mmu_hardware.cfg` 把 MMU 主板声明为一个额外的 MCU：

```ini
[mcu unit0]
serial: -enter-device-path-
```

接上主板，打开 **设置 → USB → List USB devices**，把占位符替换成列表中显示的路径。
[多 MCU](multi-mcu.md) 介绍了设备列表和两种命名方式。重新刷固件后路径变化的话，请再次运行
**List USB devices**。

## 第 4 步 —— 启用 Moonraker 组件

Happy Hare 的 Moonraker 组件会对上传的 G-code 做换色预处理，并与 Spoolman 保持同步。
把下面内容加到该打印机配置的 `moonraker.conf`：

```ini
[mmu_server]
enable_file_preprocessor: True
enable_toolchange_next_pos: True
update_spoolman_location: True
```

**不要**复制安装程序 `moonraker.conf` 里的 `[update_manager happy-hare]` 段：
Happy Hare 随应用一起更新，手机上没有 git 仓库供更新管理器使用。

重启该打印机配置。在 Fluidd/Mainsail 控制台里，`MMU_STATUS` 应显示 MMU 概况，
`MMU_HELP` 列出所有命令。MMU 自己的日志会写在 `klippy.log` 旁边，文件名为 `mmu.log`。

## 可以正常使用的

用生成的 Tradrack 配置在 Klipper 和 Kalico 上检查过：

- Happy Hare 启动（`Happy Hare v4.1.0 controlling 1 units`），应用的 `[save_variables]` 处理生效。
- `MMU_*` 命令有响应，例如 `MMU_STATUS`、`MMU_HELP`、`MMU_TEST_CONFIG`、`MMU_GATE_MAP`、
  `MMU_TTG_MAP`、`MMU_ENDLESS_SPOOL`、`MMU_SENSORS`、`MMU_TEST_MOVE` 和 `MMU_STATS`。
- Moonraker 加载 `[mmu_server]` 并完成启动。
- Happy Hare 的测试套件（2,543 个测试）在应用自带的 Python 版本上全部通过。

## 不可用或未测试的

- **真实硬件：** 通道、选择器、舵机、编码器、传感器以及完整的换色流程都没有在实体 MMU 上运行过。
- **CAN 和 UART 主板** 无法连接（见「你需要什么」）。
- **断电续打** 只保存和恢复当前挤出机，所以断电中断的打印无法在正确的通道上继续。
  见 [断电续打](print-recovery.md)。
- **NFC/RFID 读卡器和 TD-1 扫描仪** 可以加载，但没有用设备试过。解密 Bambu/Creality NFC 标签需要
  `pycryptodome` 库，应用没有内置；Happy Hare 会因此关闭这一功能。
- **Happy Hare 面向 Mainsail 和 KlipperScreen 的专用界面** 是独立项目，通过 Moonraker 通信。
  应用里的 Fluidd/Mainsail 可以在控制台或宏里发送所有 `MMU_*` 命令。
- **在已有配置中途更改 MMU**（增加单元）需要用之前的答案重新运行安装程序；
  把新的 `mmu` 文件夹覆盖到旧的上面，但保留你的 `mmu/mmu_vars.cfg`。

## 常见问题速查

| 提示 | 可能原因 |
|---|---|
| `MMU_STATUS` 返回 `Unknown command` | 缺少 `[include mmu/base/*.cfg]` 行或 `mmu` 文件夹，Happy Hare 没有加载。 |
| `Looks like Happy Hare is not installed correctly - cannot find happy_hare_version` | 配置不是由 Happy Hare 安装程序生成的。请用 v4.1.0 生成（第 1 步）。 |
| `Looks like you upgraded (vX -> v4.1.0)?` | 配置由较旧的 Happy Hare 生成。请用 v4.1.0 重新生成。 |
| `gcode command … already registered` | `printer.cfg` 里的某个宏与 Happy Hare 提供的重复（例如 `PAUSE`、`RESUME` 或 `CANCEL_PRINT`）。删除你自己的。 |
| `mcu 'unit0': Unable to connect` / `Unable to open serial port` | `[mcu unit0]` 的 `serial:` 仍是占位符、主板没有 USB 固件，或路径已变化。 |
| `Happy Hare's Klipper modules failed to load` | 请反馈。该提示里关于 `install.sh` 的建议不适用于应用。 |
