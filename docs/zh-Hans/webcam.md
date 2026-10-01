# 使用摄像头 / USB 摄像头

**语言: [English](../webcam.md) · [Português (BR)](../pt-br/webcam.md) · [简体中文](webcam.md)**

KlipPocket 可以推送实时摄像头画面用于监控打印——可以是设备自带的摄像头，也可以
是通过 OTG/集线器连接的 USB UVC 摄像头。

<p align="center"><img src="../images/camera-octoeverywhere-settings.png" alt="设置页面显示摄像头区块" width="336"></p>

## 启用

1. **设置 → 摄像头 → 启用摄像头服务器。**
2. **摄像头来源**可以选择使用哪个摄像头：
   - **自动**（默认）——优先使用插入的 USB 摄像头而非内置摄像头，服务器运行时
     插拔摄像头会实时切换。
   - 或者固定某一个摄像头。对于每个方向有多个镜头的设备（后置的
     主/超广角/长焦，有时前置也有两个），每个镜头会单独列出，并标注 35mm 等效
     焦距——例如 "后置摄像头 1（26mm）" 对比 "后置摄像头 2（14mm）"——和手机宣传
     用的是同一个数字，所以真的能区分开。
3. **摄像头旋转**在 0°/90°/180°/270° 之间循环，适用于横放或倒置安装的手机。

画面会通过 `http://<设备-ip>:8889/`（推流）和
`http://<设备-ip>:8889/snapshot`（单张 JPEG）提供，与 Fluidd/Mainsail 自己使用
的端口无关。

### 实时预览标签页

启用摄像头服务器后，顶部栏 Logs 旁会出现**摄像头标签页**，显示实时画面 ——
与 Fluidd/Mainsail 收到的完全一致（含旋转、分辨率和缩放），无需打开浏览器即可确认取景。
首次使用时应用会请求**摄像头权限**（启用服务器时也会请求）。离开标签页即断开预览，不用时没有任何开销。
摄像头服务器取决于**启用摄像头服务器**开关而非打印机：即使没有任何打印机在运行，预览也能使用。

<p align="center"><img src="../images/camera-preview-tab.png" alt="实时预览标签页（1×）与 2× 缩放下的同一画面" width="288"> <img src="../images/camera-preview-zoom.png" alt="实时预览标签页（1×）与 2× 缩放下的同一画面" width="288"></p>

### 缩放

**设置 → 摄像头 → 摄像头缩放**在各缩放档位间循环（1×、1.5×、2×、3× … 最高 10×）。
只提供**所选摄像头真正支持**的档位 —— 手机的超广角、长焦和 USB 摄像头的限制各不相同 ——
切换摄像头来源后列表会随之更新。若摄像头不支持缩放，该行显示"此摄像头不支持"。修改缩放会短暂重启摄像头服务器。

<p align="center"><img src="../images/camera-settings-zoom.png" alt="摄像头设置中新增的“摄像头缩放”一行" width="288"></p>

### 点击对焦

在**摄像头预览标签页**中，点击画面上的任意位置即可对该处对焦。黄色方框会标出
请求对焦的位置，向该点收缩后淡出。镜头会对该区域对焦一次并保持，Fluidd/Mainsail
接收的是同一路画面，因此也会看到新的对焦效果。

<p align="center"><img src="../images/camera-tap-focus.png" alt="点击后带有黄色对焦方框的摄像头预览" width="288"></p>

- 适用于硬件支持指定区域对焦的摄像头（大多数手机摄像头）。如果所选摄像头不支持
  （许多 USB 摄像头只有固定或连续对焦），预览下方的提示会显示“此摄像头不支持
  点击对焦。”，点击不会有任何效果，也不会出错。
- 即使设置了**旋转**和**缩放**，点击的位置依然准确。
- **对焦会被记住。** 镜头对焦完成后，应用会保存该对焦，因此重启应用或手机都不会丢失。如果对焦已经合适，无需任何操作。
- 切换到**另一个摄像头**会忘记已保存的对焦（需要重新对焦）。点击预览下方的**重置对焦**可手动忘记，例如移动摄像头之后。
- 通过手动对焦设置更改对焦也会替换已保存的对焦。
- 点击后该摄像头会切换为单次自动对焦；应用设置中的对焦模式会在下次摄像头重启时重新生效。
- 旧设备（Android 5.0+）也可使用：仅使用标准 Camera2 对焦控制，并会先检查摄像头上报的能力再启用。

### USB 摄像头支持

这依赖设备通过 Android 标准 Camera2 API 将 USB 摄像头暴露为外部摄像头
（`LENS_FACING_EXTERNAL`），大多数基于 AOSP 的设备自 Android 9 起支持此特性，
但部分厂商定制的相机框架不会暴露它。在相机 HAL 没有实现外部摄像头 provider 的设备上，USB UVC 摄像头可能在系统/USB 层面
被识别，但不会通过 Camera2 暴露。这种情况下应用会正常回退到内置摄像头；更接近原生
AOSP 的设备（部分 Android 电视盒/平板）更可能暴露该摄像头。

## 添加到 Fluidd 或 Mainsail

Fluidd 和 Mainsail 的摄像头列表来自同一个地方——该打印机配置在 Moonraker 中的
摄像头设置——所以你只需要在其中**任意一个**里添加一次，两边都会显示。

**在 Fluidd 中：** 齿轮图标（设置）→ **Cameras** → **+ Add Camera**：

<p align="center"><img src="../images/fluidd-cameras-settings.png" alt="Fluidd 的摄像头设置区块" width="1280"></p>

| 字段 | 值 |
|---|---|
| 名称 | 任意，例如 "USB 摄像头" |
| Service | `MJPEG-Streamer` |
| Stream URL | `http://<设备-ip>:8889/` |
| Snapshot URL | `http://<设备-ip>:8889/snapshot` |

只有在手机本机浏览器中查看 Fluidd 时才用 `127.0.0.1`；从其他设备访问时，使用
手机的局域网 IP（就是你 Fluidd/Mainsail 网址里已经用的那个）。

<p align="center"><img src="../images/fluidd-dashboard-webcam.png" alt="带实时摄像头的 Fluidd 仪表盘" width="1280"></p>

**在 Mainsail 中：** 对应的添加摄像头表单在 **Machine → Webcams** 下，使用
service 类型 `UV4L-MJPEG`，配合上面同样的 stream/snapshot 地址。由于配置是共
享的，只在 Fluidd 里添加一次就够了——下面是在 Fluidd 添加一次后，同一个摄像头
已经在 Mainsail 仪表盘上实时显示的效果：

<p align="center"><img src="../images/mainsail-dashboard-webcam.png" alt="带实时摄像头的 Mainsail 仪表盘" width="1280"></p>

一旦在这里配置好，OctoEverywhere 也会自动使用这同一个摄像头——参见
[`octoeverywhere.md`](octoeverywhere.md)。

## 故障排查

- **Fluidd/Mainsail 拒绝该摄像头/显示错误，但流地址在普通浏览器标签页或 `curl`
  中都正常：** 这曾是一个真实的 bug（已修复），`/snapshot` 发送了错误的
  `Content-Type`。确认你使用的版本已包含修复——响应应为
  `Content-Type: image/jpeg`，而不是 `multipart/x-mixed-replace`。
- **只有通过 OctoEverywhere 查看时画面卡顿/缓慢（本地正常）：** 默认的
  分辨率/画质/帧率已经针对此场景做过调整（实测 640x480、约 14fps 时约为
  117KB/s，而旧的 720p 默认值约为 1.25MB/s）——经云端中继的连接带宽比局域网
  受限得多。如果对你的上行带宽来说仍然太慢，或者你想要更高画质，FPS 上限和
  分辨率是可以调整的（源码中的 `CameraService`/`Prefs.cameraWidth`/
  `cameraHeight`）——目前还没有对应的界面控件。
