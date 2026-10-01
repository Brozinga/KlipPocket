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

**其他語言: [English](README.md) · [Português (BR)](README.pt-br.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)**

**KlipPocket 把一臺 Android 手機或平板變成 3D 印表機的“大腦”。**
透過 USB 連線印表機，它就能為你執行 [Klipper](https://github.com/KevinOConnor/klipper) 或 [Kalico](https://github.com/KalicoDTU/kalico) —— 不需要樹莓派，不需要 SSH，也不用維護 Linux。

許多“經典”印表機仍在使用緩慢且封閉的 Marlin 主機板。KlipPocket 讓它們用上現代的 Klipper 韌體，而沒有通常的複雜度：安裝應用，給印表機刷一次韌體，然後在瀏覽器裡透過網頁介面控制一切。所有內容都在你已有的裝置上本地執行，遠端訪問、攝像頭監控、故障檢測、斷電續打等額外工具，一鍵即可開啟。

> **只想安裝？** 到
> [Releases 頁面](https://github.com/Brozinga/KlipPocket/releases/latest)下載最新 APK —— 不確定選哪個就選 `armv7`（見下方
> [選擇正確的安裝包](#選擇正確的安裝包)）。

## 功能

- **支援 Klipper 0.13 及更早版本** —— 相容當前和較舊的 Klipper 韌體（同時支援 Kalico）
- **軟體保持最新** —— 應用內建較新的 Moonraker、Fluidd、Mainsail 和 Voyager UI
- **遠端連線** —— 透過 OctoEverywhere、Obico 等隨時隨地訪問印表機
- **AI 故障檢測** —— 藉助 Obico 及早發現失敗的列印
- **Print Recovery（斷電續打）** —— 斷電後繼續列印
- **攝像頭監控** —— 使用裝置攝像頭或 USB 攝像頭，支援實時預覽、縮放和延時攝影
- **網頁介面** —— Fluidd、Mainsail 和 Voyager UI，由裝置本身提供

## 選擇正確的安裝包

KlipPocket 提供三種 APK：

| 架構 | 包名 | 適用場景 |
|------|------|----------|
| arm64 | `KlipPocket_v*_arm64.apk` | 現代 64 位裝置 |
| armv7 | `KlipPocket_v*_armv7.apk` | 較舊的 32 位裝置 —— 大多數裝置都能用（不確定時推薦） |
| x86_64 | `KlipPocket_v*_amd64.apk` | x86_64 平板、Chromebook、Android 模擬器 |

**如何檢視裝置架構：**
- **設定 > 關於手機 > 架構** 或 **核心架構**
- 或安裝 "CPU-Z"、"AIDA64" 等 CPU 資訊應用
- 不確定時選 armv7 —— 它相容的裝置最多

## 快速開始

請按順序進行：

1. **生成並刷寫印表機韌體。** Klipper 需要在印表機主機板上執行自己的韌體。按照 [`docs/zh-Hans/build-firmware.md`](docs/zh-Hans/build-firmware.md) 為你的主機板生成並刷寫。
2. **檢查連線。** 印表機必須透過 **USB** 與裝置通訊，並且 Android 裝置必須支援 **OTG**（USB 主機）。
   - 部分裝置**在 USB 口用於資料傳輸時無法充電**。這種情況下長時間列印會耗盡電池，解決辦法是在內部焊接充電線路（直接焊到電池引腳）。
   - 購買集線器或線纜之前，**請確認你的裝置能透過計劃使用的介面同時充電和傳輸資料**（見[用哪種 USB 集線器?](#用哪種-usb-集線器)）。
3. **安裝並配置應用。** 按照帶圖的 [**入門指南**](docs/zh-Hans/getting-started.md) 安裝 APK、新增第一臺印表機並開啟網頁介面。

> **遇到問題？** 應用內的 **Logs** 標籤頁會顯示 Klipper、Moonraker 和應用日誌，無需電腦即可複製/分享。

## 文件

所有指南都在 [`docs/`](docs/zh-Hans/index.md)（另有 English 和 Português 版本）：

- [入門指南](docs/zh-Hans/getting-started.md) —— 一步步安裝應用並設定第一臺印表機
- [構建 MCU 韌體](docs/zh-Hans/build-firmware.md) —— 生成要刷入印表機主機板的韌體
- [斷電續打](docs/zh-Hans/print-recovery.md) —— 斷電後繼續列印
- [延時攝影](docs/zh-Hans/timelapse.md) —— 錄製延時攝影並找到影片
- [攝像頭 / 網路攝像頭](docs/zh-Hans/webcam.md) —— 裝置攝像頭、USB 攝像頭、預覽、縮放和點選對焦
- [OctoEverywhere](docs/zh-Hans/octoeverywhere.md) —— 使用 OctoEverywhere 遠端訪問
- [Obico](docs/zh-Hans/obico.md) —— 使用 Obico 遠端訪問和 AI 故障檢測
- [Klipper 附加元件](docs/zh-Hans/mods/klipper-addons.md) —— KAMP、LED Effect、Z Calibration、Auto Speed、TMC Autotune
- [無加速度計的 Input Shaper](docs/zh-Hans/mods/input-shaper-manual.md) —— 振鈴塔方法和宏
- [構建 APK](docs/zh-Hans/build-app.md) —— 自己編譯應用

## 網頁介面和攝像頭使用哪些埠？

只要有例項在執行，地址就會顯示在主介面上（`IP` 是裝置在你網路中的地址）。每個網頁介面都有自己的埠，跟隨應用中的前端切換：

| 服務 | 地址 |
|---|---|
| Fluidd | `http://IP:4408/` |
| Mainsail | `http://IP:4409/` |
| Voyager UI | `http://IP:4410/` |
| 攝像頭影片流 | `http://IP:8889/` |
| 攝像頭快照（單張 JPEG） | `http://IP:8889/snapshot` |

## 自動啟動

KlipPocket 可以在裝置開機時自動啟動，無需觸碰螢幕，印表機即可就緒：

1. 在每個需要自動啟動的印表機配置中開啟 **自動啟動**。
2. 將 KlipPocket 設為裝置的 **預設啟動器**（桌面應用）。
3. 如果裝置儲存已加密（多數裝置預設如此），請**移除鎖屏 PIN/密碼** —— 否則系統要等你輸入後才會放行應用。

## 後臺活動說明

部分廠商會限制後臺應用，可能中斷正在進行的列印。避免方法：

- 應用提示時，允許 KlipPocket 忽略電池最佳化（這是啟動時的第一個介面）。
- 在裝置設定中允許該應用的所有後臺活動。
- 在限制較嚴的系統上，將應用設為預設啟動器也有幫助。

## 支援 Android TV 嗎?

支援 —— 應該可以正常使用。注意，部分廉價電視盒子不允許在不先禁用系統啟動器的情況下將 KlipPocket 設為啟動器；請使用 ADB 或 root 禁用它。

## 用哪種 USB 集線器?

集線器是**受支援的**，但如果你的裝置能同時與印表機通訊並充電，就不是必需的。如果需要，推薦的集線器應**至少有一個 USB-A 口（接印表機）和一個供電/充電口**（例如支援 Power Delivery 透傳的 USB-C），並且前提是裝置支援在 OTG 模式下充電。在長時間列印前，請先確認這套組合在你的裝置上可用。

## 限制

- Web 伺服器無法使用預設埠，因為 Android/Linux 不允許使用者空間應用繫結 1024 以下的埠，而預設的 `http://IP` 需要 80 埠
- 部分裝置在韌體重啟後會重置裝置路徑，這種情況下請使用 VID/PID 命名
- 不支援 SSH（也因此無法在裝置上編譯韌體或執行額外的自啟服務）
- 部分裝置不支援同時 OTG 和充電，這種情況只能直接焊接到電池引腳（或者換一臺裝置，隨你）
- 僅支援 250000 波特率（不想把這個設定轉發到 Android USB 驅動，幾乎所有配置都用 250000 而已）

> **最常見的坑：** 如果手機沒法一邊充電一邊和印表機通訊，就是上面說的 OTG+充電
> 限制 —— 用一個自帶供電的 USB 集線器（見[用哪種 USB 集線器?](#用哪種-usb-集線器)）就能解決。

## 致謝

- **[ProtonKicker/Kocoa-Beam](https://github.com/ProtonKicker)** —— 將應用程式移植到 Kotlin 並重做了介面。
- **[Beam Klipper](https://github.com/utkabobr/BeamKlipper)** —— 本專案的原始來源。
- Klipper、Kalico、Moonraker、Fluidd、Mainsail 及其他內建元件歸各自作者所有（見[內建了什麼?](#內建了什麼)）。
- **[Voyager UI](https://github.com/ozancs/voyager-ui)**，作者 [ozancs](https://github.com/ozancs) —— 除 Fluidd 和 Mainsail 之外可選的第三個網頁前端。

## 貢獻

歡迎提交 Pull Request！
