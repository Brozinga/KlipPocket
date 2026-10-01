# 使用 OctoEverywhere

**语言: [English](../octoeverywhere.md) · [Português (BR)](../pt-br/octoeverywhere.md) · [简体中文](octoeverywhere.md)**

[OctoEverywhere](https://octoeverywhere.com) 让你无需在路由器上开放端口，就能
通过 octoeverywhere.com 网站或手机应用远程访问打印机（摄像头、状态、控制）。
KlipPocket 运行的是真实的 OctoEverywhere Klipper/Moonraker 伴生程序，经过改造
直接在设备上运行，而不是它通常安装的 systemd 服务。

<p align="center"><img src="../images/camera-octoeverywhere-settings.png" alt="设置页面显示摄像头与远程访问区块" width="336"></p>

<p align="center"><img src="../images/octoeverywhere-view.jpg" alt="OctoEverywhere 快速视图，显示摄像头实时画面、状态和温度" width="480"></p>

## 启用步骤

1. 启动一个打印机配置（必须处于**运行中**状态——OctoEverywhere 会连接当前正在
   运行的那个配置）。
2. 进入**设置 → 远程访问**，打开 **Enable OctoEverywhere**。
3. 等几秒钟让它生成打印机 ID，然后点击**关联打印机**。会出现一个二维码——用手
   机摄像头扫描它（或者直接在本机打开链接），完成与你的 OctoEverywhere 账号的
   关联，和其他任何 OctoEverywhere 安装方式一样。
   - 如果显示 "Not ready yet"，说明伴生程序还没启动完成——稍等片刻再点一次
     **关联打印机**。
4. 关联完成后，你的打印机就会出现在 octoeverywhere.com 和 OctoEverywhere 手机
   应用中。
   - 伴生程序只在连接时检查一次是否已关联——对于已经打开的会话，不会收到实时
     更新。如果刚完成关联后，octoeverywhere.com 上的 "Go to Klipper" 仍然提示
     打印机未连接，把 **Enable OctoEverywhere** 关闭再打开一次，强制重新连接
     即可。

## 摄像头

OctoEverywhere 查找摄像头画面的方式和 Fluidd/Mainsail 一样：通过 Moonraker 自
己的摄像头列表，而不是 KlipPocket 自动注册的内容。启用摄像头服务器，并在
Fluidd 或 Mainsail 中添加一次（两者共享同一份配置），OctoEverywhere 就会自动
识别——完整图文指南见 [`webcam.md`](webcam.md)。

## 注意事项与限制

- 同一时间只能有**一个**打印机配置被关联，即使同时运行多个配置也是如此——
  OctoEverywhere 会跟随最先进入"运行中"状态的那个配置。切换配置后需要先关闭
  再打开开关，才能把关联转移过去。
- OctoEverywhere 自带的崩溃遥测（Sentry）默认已禁用；与 octoeverywhere.com 的
  实际远程访问连接不受影响。
- 这个功能尚未经过端到端的硬件测试（也就是用真实账号完成关联并通过
  octoeverywhere.com 实际使用）——伴生程序本身能正常运行并正确连接 Moonraker，
  但如果在关联或远程使用中遇到问题，请反馈。

## 故障排查

- **日志**：`octoeverywhere.log` 和该配置的 `klippy.log`/`moonraker.log` 放在
  同一个 `logs` 文件夹里——可以通过 KlipPocket 自身在系统中注册的存储入口
  （Android 文件管理器 App → "KlipPocket"）用文件管理器查看，就是你平时查看
  Klipper/Moonraker 日志的那个位置。
- **重新关联**：关闭 OctoEverywhere，删除该配置在应用私有存储中的
  `octoeverywhere` 文件夹（不是上面提到的公开文件夹——需要有 root 权限的文件
  管理器，或使用 `adb shell run-as`），然后重新打开开关以生成新的打印机 ID 并
  重新关联。你也可以直接在 octoeverywhere.com 的账号设置里解除/重新关联打印机。
