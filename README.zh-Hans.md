# KlipPocket - Klipper brain for your 3D printer

<p align="center">
  <img src="docs/images/logo.png" alt="KlipPocket" width="260">
</p>

<p align="center">
  <a href="https://github.com/Brozinga/KlipPocket/releases/latest"><img src="https://img.shields.io/github/v/release/Brozinga/KlipPocket?label=latest%20release&color=49CBEB" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/platform-Android%205.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 5.0+">
  <img src="https://img.shields.io/badge/license-GPL--3.0-4B8BBE" alt="License: GPL-3.0">
  <img src="https://img.shields.io/badge/kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin / Jetpack Compose">
</p>

**其他语言: [English](README.md) · [Português (BR)](README.pt-br.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

**KlipPocket 把一台 Android 手机或平板变成 3D 打印机的“大脑”。**
通过 USB 连接打印机，它就能为你运行 [Klipper](https://github.com/KevinOConnor/klipper) 或 [Kalico](https://github.com/KalicoDTU/kalico) —— 不需要树莓派，不需要 SSH，也不用维护 Linux。

许多“经典”打印机仍在使用缓慢且封闭的 Marlin 主板。KlipPocket 让它们用上现代的 Klipper 固件，而没有通常的复杂度：安装应用，给打印机刷一次固件，然后在浏览器里通过网页界面控制一切。所有内容都在你已有的设备上本地运行，远程访问、摄像头监控、故障检测、断电续打等额外工具，一键即可开启。

> **只想安装？** 到
> [Releases 页面](https://github.com/Brozinga/KlipPocket/releases/latest)下载最新 APK —— 不确定选哪个就选 `armv7`（见下方
> [选择正确的安装包](#选择正确的安装包)）。

## 功能

- **支持 Klipper 0.13 及更早版本** —— 兼容当前和较旧的 Klipper 固件（同时支持 Kalico）
- **软件保持最新** —— 应用内置较新的 Moonraker、Fluidd、Mainsail 和 Voyager UI
- **远程连接** —— 通过 OctoEverywhere、Obico 等随时随地访问打印机
- **AI 故障检测** —— 借助 Obico 及早发现失败的打印
- **Print Recovery（断电续打）** —— 断电后继续打印
- **摄像头监控** —— 使用设备摄像头或 USB 摄像头，支持实时预览、缩放和延时摄影
- **网页界面** —— Fluidd、Mainsail 和 Voyager UI，由设备本身提供

## 选择正确的安装包

KlipPocket 提供三种 APK：

| 架构 | 包名 | 适用场景 |
|------|------|----------|
| arm64 | `KlipPocket_v*_arm64.apk` | 现代 64 位设备 |
| armv7 | `KlipPocket_v*_armv7.apk` | 较旧的 32 位设备 —— 大多数设备都能用（不确定时推荐） |
| x86_64 | `KlipPocket_v*_amd64.apk` | x86_64 平板、Chromebook、Android 模拟器 |

**如何查看设备架构：**
- **设置 > 关于手机 > 架构** 或 **内核架构**
- 或安装 "CPU-Z"、"AIDA64" 等 CPU 信息应用
- 不确定时选 armv7 —— 它兼容的设备最多

## 快速开始

请按顺序进行：

1. **生成并刷写打印机固件。** Klipper 需要在打印机主板上运行自己的固件。按照 [`docs/zh-Hans/build-firmware.md`](docs/zh-Hans/build-firmware.md) 为你的主板生成并刷写。
2. **检查连接。** 打印机必须通过 **USB** 与设备通信，并且 Android 设备必须支持 **OTG**（USB 主机）。
   - 部分设备**在 USB 口用于数据传输时无法充电**。这种情况下长时间打印会耗尽电池，解决办法是在内部焊接充电线路（直接焊到电池引脚）。
   - 购买集线器或线缆之前，**请确认你的设备能通过计划使用的接口同时充电和传输数据**（见[用哪种 USB 集线器?](#用哪种-usb-集线器)）。
3. **安装并配置应用。** 按照带图的 [**入门指南**](docs/zh-Hans/getting-started.md) 安装 APK、添加第一台打印机并打开网页界面。

> **遇到问题？** 应用内的 **Logs** 标签页会显示 Klipper、Moonraker 和应用日志，无需电脑即可复制/分享。

## 文档

所有指南都在 [`docs/`](docs/zh-Hans/index.md)（另有 English 和 Português 版本）：

- [入门指南](docs/zh-Hans/getting-started.md) —— 一步步安装应用并设置第一台打印机
- [构建 MCU 固件](docs/zh-Hans/build-firmware.md) —— 生成要刷入打印机主板的固件
- [断电续打](docs/zh-Hans/print-recovery.md) —— 断电后继续打印
- [延时摄影](docs/zh-Hans/timelapse.md) —— 录制延时摄影并找到视频
- [摄像头 / 网络摄像头](docs/zh-Hans/webcam.md) —— 设备摄像头、USB 摄像头、预览、缩放和点击对焦
- [OctoEverywhere](docs/zh-Hans/octoeverywhere.md) —— 使用 OctoEverywhere 远程访问
- [Obico](docs/zh-Hans/obico.md) —— 使用 Obico 远程访问和 AI 故障检测
- [Klipper 附加组件](docs/zh-Hans/mods/klipper-addons.md) —— KAMP、LED Effect、Z Calibration、Auto Speed、TMC Autotune
- [无加速度计的 Input Shaper](docs/zh-Hans/mods/input-shaper-manual.md) —— 振铃塔方法和宏
- [构建 APK](docs/zh-Hans/build-app.md) —— 自己编译应用

## 网页界面和摄像头使用哪些端口？

只要有实例在运行，地址就会显示在主界面上（`IP` 是设备在你网络中的地址）。每个网页界面都有自己的端口，跟随应用中的前端切换：

| 服务 | 地址 |
|---|---|
| Fluidd | `http://IP:4408/` |
| Mainsail | `http://IP:4409/` |
| Voyager UI | `http://IP:4410/` |
| 摄像头视频流 | `http://IP:8889/` |
| 摄像头快照（单张 JPEG） | `http://IP:8889/snapshot` |
| 摄像头视频流（webcam 风格） | `http://IP:8889/webcam/?action=stream` |
| 摄像头快照（webcam 风格） | `http://IP:8889/webcam/?action=snapshot` |

## 自动启动

KlipPocket 可以在设备开机时自动启动，无需触碰屏幕，打印机即可就绪：

1. 在每个需要自动启动的打印机配置中开启 **自动启动**。
2. 将 KlipPocket 设为设备的 **默认启动器**（桌面应用）。
3. 如果设备存储已加密（多数设备默认如此），请**移除锁屏 PIN/密码** —— 否则系统要等你输入后才会放行应用。

## 后台活动说明

部分厂商会限制后台应用，可能中断正在进行的打印。避免方法：

- 应用提示时，允许 KlipPocket 忽略电池优化（这是启动时的第一个界面）。
- 在设备设置中允许该应用的所有后台活动。
- 在限制较严的系统上，将应用设为默认启动器也有帮助。

## 支持 Android TV 吗?

支持 —— 应该可以正常使用。注意，部分廉价电视盒子不允许在不先禁用系统启动器的情况下将 KlipPocket 设为启动器；请使用 ADB 或 root 禁用它。

## 用哪种 USB 集线器?

集线器是**受支持的**，但如果你的设备能同时与打印机通信并充电，就不是必需的。如果需要，推荐的集线器应**至少有一个 USB-A 口（接打印机）和一个供电/充电口**（例如支持 Power Delivery 透传的 USB-C），并且前提是设备支持在 OTG 模式下充电。在长时间打印前，请先确认这套组合在你的设备上可用。

## 限制

- Web 服务器无法使用默认端口，因为 Android/Linux 不允许用户空间应用绑定 1024 以下的端口，而默认的 `http://IP` 需要 80 端口
- 部分设备在固件重启后会重置设备路径，这种情况下请使用 VID/PID 命名
- 不支持 SSH（也因此无法在设备上编译固件或运行额外的自启服务）
- 部分设备不支持同时 OTG 和充电，这种情况只能直接焊接到电池引脚（或者换一台设备，随你）
- 仅支持 250000 波特率（不想把这个设置转发到 Android USB 驱动，几乎所有配置都用 250000 而已）

> **最常见的坑：** 如果手机没法一边充电一边和打印机通信，就是上面说的 OTG+充电
> 限制 —— 用一个自带供电的 USB 集线器（见[用哪种 USB 集线器?](#用哪种-usb-集线器)）就能解决。

## 致谢

- **[ProtonKicker/Kocoa-Beam](https://github.com/ProtonKicker)** —— 将应用移植到 Kotlin 并重做了界面。
- **[Beam Klipper](https://github.com/utkabobr/BeamKlipper)** —— 本项目的原始来源。
- Klipper、Kalico、Moonraker、Fluidd、Mainsail 及其他内置组件归各自作者所有（见[内置了什么?](#内置了什么)）。
- **[Voyager UI](https://github.com/ozancs/voyager-ui)**，作者 [ozancs](https://github.com/ozancs) —— 除 Fluidd 和 Mainsail 之外可选的第三个网页前端。

## 贡献

欢迎提交 Pull Request！
