# 使用 Obico

**语言: [English](../obico.md) · [Português (BR)](../pt-br/obico.md) · [简体中文](obico.md)**

[Obico](https://www.obico.io) 是一个社区构建的开源智能 3D 打印平台：远程监
控、打印失败检测和远程控制，可以连接 Obico Cloud，也可以连接你自己搭建的
Obico Server。KlipPocket 运行的是真实的 `moonraker-obico` 伴生程序（源码来自
[TheSpaghettiDetective/moonraker-obico](https://github.com/TheSpaghettiDetective/moonraker-obico)
并经过改造），直接在设备上运行，而不是它通常安装的 systemd 服务。

<p align="center"><img src="../images/obico-control.jpg" alt="Obico 控制页面，显示打印机、实时摄像头和温度" width="640"></p>

## 启用步骤

1. 启动一个打印机配置（必须处于**运行中**状态——Obico 会连接当前正在运行的
   那个配置）。
2. 进入**设置 → 远程访问 → Obico**，打开 **Enable Obico**。
3. 点击 **Obico 服务器**选择连接目标：
   - **Obico Cloud**（`app.obico.io`）——默认选项，不需要自己搭建服务器。
   - **自建服务器**——输入你自己的
     [Obico Server](https://www.obico.io/docs/server-guides/) 地址。
   - 切换服务器会清除现有的关联，因为关联只对签发它的服务器有效。
4. 点击**关联打印机**。KlipPocket 会自己生成一个验证码并直接显示出来（启用
   Obico 后，伴生程序启动并生成验证码可能需要几秒钟）。在 Obico 应用或网站
   上添加打印机，选择 **Klipper（自行安装）**，在提示时输入这个验证码——或
   者直接点击对话框中的**打开链接**，会打开 Obico 自己的关联页面，验证码已
   经预先填好。
   - 这与 Obico 自身安装脚本在真实 Klipper 安装上的做法完全一致
     （`./install.sh` → `python3 -m moonraker_obico.link`）：它每隔几秒钟
     向 Obico 服务器报告一台未关联的打印机和一个一次性验证码，直到你在
     Obico 那一侧输入这个验证码，或者 Obico 应用因为你登录的是同一个账号而
     自动找到它。整个过程不涉及任何局域网广播，所以无论是通过 WiFi、VPN 还
     是手机流量，效果都是一样的。
   - 反过来，已经有一个 Obico 给你的验证码了（例如通过 OctoPrint 方式手动
     设置得到的）？同一个对话框里也有一个输入框，可以直接输入这个验证码。

<p align="center"><img src="../images/obico-link-code.png" alt="关联打印机对话框，显示生成的验证码、打开链接按钮和手动验证码输入框" width="384"></p>

5. 关联完成后，你的打印机就会出现在 Obico 应用/网站中。要取消关联，再次打
   开**关联打印机**——已关联时对话框会显示**取消关联**选项。如果 Obico 应用
   /网站自动找到了这台打印机（同一账号），关联也会自动完成——KlipPocket 里
   不需要按任何按钮，对话框会在关联完成时自动关闭。

## 摄像头

Obico 获取摄像头画面的方式和 Fluidd/Mainsail 一样：通过 Moonraker 自己的摄
像头列表。启用摄像头服务器，并在 Fluidd 或 Mainsail 中添加一次（两者共享同
一份配置），Obico 就会自动识别——完整图文指南见 [`webcam.md`](webcam.md)。

Obico 通过内置 `janus-gateway` 进程和 `ffmpeg` 提供的实时 WebRTC 预览在这里
**不可用**——这两者在上游项目中都是为桌面 Linux/树莓派编译的原生二进制文
件，并非为 Android 构建，因此未被打包进来。这是一个文档化且受支持的配置项
（`disable_video_streaming`），不是对伴生程序代码的修改。无论这个选项如何设
置，Obico 仍会定期（约每 10 秒）获取一张最新快照，用于打印监控和失败检测。

## 注意事项与限制

- 同一时间只能有**一个**打印机配置被关联，即使同时运行多个配置也是如此——
  Obico 会跟随最先进入"运行中"状态的那个配置。
- Obico 自带的崩溃遥测（Sentry）默认已禁用；与所选服务器的实际连接不受影
  响。对于任何自建服务器，伴生程序本身也会自动禁用它，与这项设置无关。
- **已端到端验证在真实硬件上可用**：伴生程序能正确连接 Moonraker，并从真实
  的 Obico Cloud 服务器生成真实的一次性验证码，打印机也确实通过这个流程完成
  了关联。手动输入验证码的路径也已针对真实服务器验证过（故意输入错误验证码
  会正确返回"无效"）。

## 故障排查

- **日志**：`obico.log` 和该配置的 `klippy.log`/`moonraker.log`/
  `octoeverywhere.log` 放在同一个 `logs` 文件夹里——可以通过 KlipPocket 自身
  在系统中注册的存储入口（Android 文件管理器 App → "KlipPocket"）查看。
- **输入验证码后一直显示"未关联"**：确认验证码没有过期（Obico 的验证码有效
  期很短），并确认选择了正确的服务器（Cloud 还是自建）——一个服务器的验证码
  在另一个服务器上无效。
- **自建服务器无法访问**：关联对话框显示的错误信息就是实际的网络错误（例如
  "无法解析主机"）——先检查地址本身，包括 `http://` 还是 `https://`。
