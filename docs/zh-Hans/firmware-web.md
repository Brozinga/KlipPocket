# 固件网页 —— 用浏览器写入固件（端口 4400）

**Languages: [English](../firmware-web.md) · [Português (BR)](../pt-br/firmware-web.md) · [简体中文](firmware-web.md)**

应用会在 **4400 端口**提供一个小网页，功能与[固件标签页](firmware-tab.md)相同，但可以在同一网络中
电脑或另一部手机的浏览器里使用：上传固件文件、粘贴下载链接、查看连接到手机的 USB 设备并写入主板。
网页只与应用通信：上传、下载和写入都由与标签页相同的代码完成，因此两者始终一致。

只要应用已打开，网页**始终可用**（无需运行打印机），应用在后台时一条简短通知会让它保持可访问。

## 打开网页

在应用中打开**固件**标签页，滚动到**网页**：会显示地址（`http://<手机 IP>:4400`）和六位数的 **PIN**。

![应用中的地址和 PIN](../images/firmware-web/app-card.png)

在同一网络的任意浏览器中打开该地址并输入 PIN。应用中的**新 PIN** 会替换它并关闭所有已打开的网页。

![PIN 页面](../images/firmware-web/login.jpg)

> **为什么需要 PIN？** 能访问网页的人就能写入连接在手机上的主板。除网页自身的文件外，一切都需要 PIN；
> 输错五次后该地址会被锁定一分钟。网页使用普通 HTTP，与应用的其他网页一样，请在可信网络中使用。

## 主页

![主页](../images/firmware-web/home.jpg)

- **上传文件** —— 选择文件或拖到虚线区域。支持 `.bin`、`.hex`、`.uf2`，最大 16 MB。文件保存在应用的
  固件文件夹中（与标签页同一个列表）。加载界面会显示进度。

  ![上传](../images/firmware-web/upload.jpg)

- **粘贴链接** —— 粘贴固件文件的链接（仅 https），例如
  `https://github.com/utkabobr/klipper/releases/download/prebuilt-v0.12.0/stm32f401.bin`，然后点**下载**。
  由应用自己下载，因此手机需要联网。

  ![粘贴链接](../images/firmware-web/paste-link.jpg)

- **固件文件** —— 已保存的文件，可**删除**或**全部删除**。
- **USB 设备** —— 手机看到的所有设备，包括名称、`VID/PID`、总线路径，以及串口主板在 `printer.cfg` 中使用的端口：
  与应用中*列出 USB 设备*相同。插拔主板后点**刷新**重新读取（也会自动刷新）。
- **写入固件** —— 下面的向导。

## 写入固件

**写入固件**会先提示该功能为**实验性**，可能造成问题；点**继续**开始四个步骤。

![实验性功能提示](../images/firmware-web/experimental.jpg)

| 1 —— 文件 | 2 —— 设备 |
|---|---|
| ![第 1 步](../images/firmware-web/step1-file.jpg) | ![第 2 步](../images/firmware-web/step2-device.jpg) |

**1** 选择文件；**2** 选择设备（可**刷新**）；**3** 选择方式。

![第 3 步](../images/firmware-web/step3-mode.jpg)

方式有 **USB**、**UART**、**DFU** 和 **RP2040 / RP2350**，规则、说明和*实验性*标记与[标签页](firmware-tab.md)相同。
对于 STM32 上的原始 `.bin` 会出现**起始地址**字段。（应用的 Katapult、HID-Flash、SAM-BA 和 CAN 方式暂未
放到网页中：请使用标签页。）

**4** 提示此过程可能损坏主板：点**确认**开始，**取消**不做任何更改。

![第 4 步](../images/firmware-web/step4-warning.jpg)

随后加载界面显示阶段和百分比（Android 可能在**手机上**请求 USB 权限：请在手机上允许）。结束时网页会告知成功与否，
失败时说明**原因**：

| | |
|---|---|
| ![进度](../images/firmware-web/progress.jpg) | ![成功](../images/firmware-web/success.jpg) |
| ![失败及原因](../images/firmware-web/failure.jpg) | |

**日志**显示应用*固件写入*日志的末尾；**完成**关闭结果。

### 刷新或关闭网页时

写入在应用内运行，而不是在网页里。刷新网页、关闭标签页、在另一台设备上打开：都会直接回到进度界面
（若写入已结束且无人关闭结果，则显示结果）。你所在的步骤（文件、设备、方式）在确认之前会保存在浏览器中。

## 语言和主题

网页提供应用的五种语言，首次打开时跟随应用的语言和浅色/深色主题；在网页上的选择会保存在浏览器中。

![中文，浅色主题](../images/firmware-web/home-zh-light.jpg)

## 出问题时

| 现象 | 处理方法 |
|---|---|
| 网页打不开 | 手机和电脑必须在同一 Wi-Fi 且未开启*客户端隔离*；使用应用中显示的地址。若 4400 端口被其他应用占用，固件标签页中的卡片会提示网页未运行。 |
| *尝试次数过多* | 等待提示的秒数，或在应用中使用**新 PIN**。 |
| *与应用的连接已断开* | 应用被关闭或手机休眠：重新打开应用。只要应用还在，进行中的写入不会丢失。 |
| 写入失败 | 提示信息与[标签页](firmware-tab.md)相同。 |

## 开发者

`/api` 下的一切都需要 PIN 登录的会话 cookie，且在有修改时需要请求头 `X-KlipPocket: 1`。主要路由：
`GET /api/state`、`PUT /api/upload?name=…`、`POST /api/download`、`GET /api/devices`、`POST /api/flash`、
`GET /api/flash`、`POST /api/flash/reset`。代码位于 `app/src/main/java/ru/ytkab0bp/beamklipper/firmwareweb/`，
网页位于 `app/src/main/assets/firmware_web/`；其文本由 `scripts/gen_firmware_web_i18n.py` 从应用字符串生成。
