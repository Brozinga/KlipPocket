# 构建 MCU 固件

**语言: [English](../build-firmware.md) · [Português (BR)](../pt-br/build-firmware.md) · [简体中文](build-firmware.md)**

KlipPocket 在 Android 上运行 Klipper/Kalico 主机。打印机主板仍然需要 MCU 固件，在 PC 上构建并烧录一次（Android 不能编译）。

获取方式有三种：

| 方式 | 说明 |
|---|---|
| **预编译镜像** | Beam Klipper 项目为许多主板发布了固件：<https://github.com/utkabobr/klipper/releases>。更旧的镜像也能用于本应用；推荐 Klipper **0.13**。 |
| **Docker** | 一条命令。除 Docker 外无需安装任何东西。可复现。 |
| **本地脚本** | 一条命令。自动下载固定版本的工具链。 |

项目的两个构建器都会生成一个纯 `klipper.bin`，重命名为主板 bootloader 所期望的文件名。它们**不会**运行 `update_mks_robin.py`（那个 XOR 步骤只适用于 bootloader 会解密 SD 文件的 STM32F103 MKS Robin 主板；对按原样烧录的主板会损坏镜像）。它们还会拒绝生成会覆盖 bootloader 的镜像，并会把编译大小与 flash 应用区做对比检查。

Klipper 没有 MCU↔主机版本锁 —— MCU 在连接时把命令字典发给主机，主机自适应。固件不必与应用内置的 Klipper 完全一致。

---

## Docker

在仓库根目录运行：

```bash
# 构建一次镜像（固定 Klipper v0.13.0 + ARM gcc 10.3-2021.07）
docker compose -f firmware/docker-compose.yml build

# 为 firmware/configs/ 里有配置的主板构建固件
docker compose -f firmware/docker-compose.yml run --rm fw <主板>

# 列出可用的主板配置
docker compose -f firmware/docker-compose.yml run --rm fw
```

输出在 `firmware/output/`。在 `docker compose ... build` 上加 `--build-arg
KLIPPER_TAG=vX.Y.Z` 可换 Klipper 版本。

## 本地脚本

```bash
./scripts/build_firmware.sh <主板 | 路径/到/.config> [输出文件名] [klipper-tag]
```

`<主板>` 解析为 `firmware/configs/<主板>.config`，或传入用 `make menuconfig` 保存的任意 `.config` 的完整路径。首次运行会把 `gcc-arm-none-eabi 10.3-2021.07` 下载到 `firmware/toolchain/`，并把 Klipper 克隆到 `firmware/.klipper-build/`（两者都被 gitignore）。

可在 **Linux** 和 **macOS** 上运行（需要 `make`、`git`、`curl`、`tar`）。在 **Windows** 上使用 `scripts\build_firmware.ps1 <主板>`（封装 Docker 镜像），或在 WSL / Git Bash 中运行 `.sh`。

---

## 添加一块主板

### 1. 弄清主板芯片

光有打印机型号不够 —— 同一型号常常出过不同的主板（STM32F103 / F401 / GD32 / …），每块的内存布局都不同。用可能写入 bootloader 的方式烧错芯片的固件可能变砖。检查主板丝印、贴纸或 OEM 固件文件名。

### 2. 生成一个 `.config`

在有 Klipper 检出的 PC 上（或 `docker compose ... run --rm --entrypoint bash fw`）：

```bash
cd /opt/klipper        # 或你的 Klipper 克隆
make menuconfig
```

对 KlipPocket 重要的选项：

- **处理器型号** —— 与芯片完全一致。
- **Bootloader 偏移** —— 多数 OEM 主板有一个 8–32 KiB 的 bootloader 必须保留（例如 `CONFIG_FLASH_START_8000`）。这是主要的变砖风险。
- **通信接口** —— KlipPocket 通过设备的 OTG 口用 **USB 串口**。多数 OEM 主板上这是板载 USB-串口桥接芯片接到某个 UART，而非 MCU 的原生 USB。选 USB 口所接的那个 UART。
- **波特率** —— KlipPocket 只支持 **250000**。

社区里针对该主板的「known-good」配置是很好的起点：加载它，运行一次 `make menuconfig` 来调和，保存。

**在哪里找现成的主板 `.config`：**

| 来源 | 说明 |
|---|---|
| [`utkabobr/klipper` → `bin-templates/`](https://github.com/utkabobr/klipper/tree/master/bin-templates) | Beam Klipper 预编译固件所基于的 Kconfig `.config` 文件（约 90 块 OEM 主板）。与本应用最接近；旁边的 `.json` 文件标记 SD 卡（"robin"）主板。 |
| [`Klipper3d/klipper` → `config/`](https://github.com/Klipper3d/klipper/tree/master/config) | `printer-*.cfg` / `generic-*.cfg` —— 这些是 **printer.cfg** 模板，不是固件 `.config`，但其头部注释会说明 `make menuconfig` 需要的芯片、bootloader 偏移和通信接线。 |
| [Klipper 社区 Discourse](https://klipper.discourse.group/) | 按主板分享的固件配置。 |
| 主板厂商文档（BigTreeTech、MKS、Fysetc，STM32 见 TeamGloomy） | 按主板的 `make menuconfig` 选项。 |

### 3. 放进 `firmware/configs/`

保存为 `firmware/configs/<主板>.config`。一个可选的指令头让构建器命名输出并保护变砖关键行：

```ini
#! board_name:      <可读名称>
#! flash_filename:  <bootloader 查找的名字，例如 firmware.bin>
#! klipper_tag:     v0.13.0
#! assert: CONFIG_MCU="<芯片>"
#! assert: <任何必须在 `make olddefconfig` 后存活的 CONFIG_ 行>
#! assert: CONFIG_SERIAL_BAUD=250000
```

`#! assert:` 行会在 `make olddefconfig` 之后重新检查；只要有一行丢失，构建就停止。`#! expect_sha256: <hash>` 还会在输出不匹配时让构建失败 —— 适合固定一个已验证的镜像。

### 4. 烧录

- **SD 卡主板** —— 把输出复制到 FAT32 卡上，重命名为 bootloader 查找的名字，断电重启。bootloader 会重命名或删除该文件作为确认，且从不写入自身；最坏情况是「不开机」，用同样的方式烧回一个已知可用的镜像即可恢复。
- **DFU / `make flash` 主板** —— 在 PC 上按 Klipper 的常规流程操作。

### 5. 连接

把打印机插到设备上（OTG）。KlipPocket 会自动检测串口；`printer.cfg` 里的 `[mcu] serial:` 行会被忽略（为可移植性保留一行有效的）。如果固件重启后设备路径会变，使用 VID/PID 命名。

---

## 可复现性

一个干净的 Klipper tag + 固定的工具链 + 相同的 `.config` 会产生逐字节相同的二进制文件（Klipper 对干净树的构建会省略构建时间和主机名）。项目自带的示例配置使用 `#! expect_sha256`，因此结果漂移时构建会失败。

## 临时目录

`firmware/output/`、`firmware/toolchain/`、`firmware/.klipper-build/` 是构建临时目录，已被 gitignore。只提交经过验证的 `.bin` 到 `firmware/<主板-slug>/`。
