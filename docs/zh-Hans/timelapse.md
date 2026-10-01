# 延时摄影（Timelapse）

**语言: [English](../timelapse.md) · [Português (BR)](../pt-br/timelapse.md) · [简体中文](timelapse.md)**

KlipPocket 内置了 Moonraker-timelapse 组件。打印过程中它会在每一层拍一张打印机的照片，打印结束后再把这些照片合成为视频——全部在手机上完成，不需要电脑或云端。你可以在 Fluidd 或 Mainsail 的 **Timelapse** 页面进行控制。

## 你需要准备

- **开启摄像头服务器**（设置 → 摄像头 → *启用摄像头服务器*），并让摄像头对准打印机——可以是手机自带摄像头或 USB 摄像头。参见 [webcam.md](webcam.md)。延时摄影的照片来自摄像头服务器的快照地址 `http://127.0.0.1:8889/snapshot`，因此无需再做其他配置。（如果你在 Fluidd/Mainsail 中添加了摄像头，它的 *Snapshot URL* 也必须可用；`http://<手机IP>:8889/snapshot` 可用。）
- 打印机已经在 Fluidd/Mainsail 中正常运行——参见 [getting-started.md](getting-started.md)。

## 1. 启用延时摄影宏

宏文件 `timelapse.cfg` 已经在你的打印机配置文件夹中，但除非你把它包含进来，否则打印机不会加载它。

1. 在 Fluidd/Mainsail 的编辑器中打开 `printer.cfg`（Fluidd：**{…} 配置**，Mainsail：**Machine**）。
2. 在最顶部添加这一行：

   ```ini
   [include timelapse.cfg]
   ```

3. 如果你的 `printer.cfg` 里已经有占位用的 `[gcode_macro HYPERLAPSE]`（部分打印机模板里有，它只会输出“timelapse 未配置”），请**删除该宏**，以免与 `timelapse.cfg` 中真正的宏冲突。
4. 点击**保存并重启**。

重启后就会有 `TIMELAPSE_TAKE_FRAME`、`TIMELAPSE_RENDER` 和 `HYPERLAPSE`，**Timelapse** 页面会显示 *Enabled* 和 *Auto Render* 开关。

<p align="center"><img src="../images/timelapse/fluidd-timelapse-page.png" alt="Fluidd 的 Timelapse 页面：已渲染的视频、状态面板和设置" width="720"></p>

## 2. 让每一层拍一张照片

默认模式是 **layermacro**：切片软件在每一层调用一次 `TIMELAPSE_TAKE_FRAME`。请把这条命令放到切片软件的*换层* G-code 中：

| 切片软件 | 位置 |
|---|---|
| PrusaSlicer / SuperSlicer | 打印机设置 → 自定义 G-code → **换层前 G-code** |
| OrcaSlicer | 打印机设置 → 机器 G-code → **换层前 G-code**（或 *延时摄影 G-code* 字段） |
| Cura | 扩展 → 后期处理 → 修改 G-Code → **在换层时插入** |

要插入的内容：`TIMELAPSE_TAKE_FRAME`。（菜单名称在不同版本间略有差异。）

**不想改动切片软件？** 在延时摄影设置中把模式切换为 **hyperlapse**：整个打印过程中每 30 秒（可配置）拍一张照片，与切片软件无关。

## 3. 开始打印

其余无需操作。打印时会不断收集照片，打印结束后 **Auto Render** 会自动合成视频；你也可以随时在 Timelapse 页面点击 **Render**，或点击 **Save Frames** 把原始照片保存为 zip。

## 视频存放在哪里

渲染好的视频会显示在 Fluidd（左侧菜单 *Timelapse*）和 Mainsail（**TIMELAPSE**）的 Timelapse 页面中，旁边带有预览图。在那里打开文件即可观看，或通过文件菜单下载到电脑。

这些文件实际存放在应用的私有存储中，每台打印机一个文件夹：

| 内容 | 路径（应用存储内） |
|---|---|
| 生成的视频（`timelapse_<文件>_<日期>.mp4` + `.jpg` 预览图） | `files/instance/<打印机ID>/public/timelapses/` |
| 为下一个视频正在收集的照片 | `files/instance/<打印机ID>/timelapse_frames/` |

在手机上就是 `/data/data/com.brozinga.klippocket/files/…`。Android 不允许文件管理器或相册打开该文件夹，因此**请通过网页把视频取出来**。你也可以在同一网络的浏览器中直接下载：

```
http://<手机IP>:4408/server/files/timelapse/<视频名>.mp4     (Fluidd)
http://<手机IP>:4409/server/files/timelapse/<视频名>.mp4     (Mainsail)
```

> ⚠️ 视频与打印机配置存放在一起。**在应用中删除打印机，或卸载应用，会同时删除它的视频。**请先下载想保留的视频。

## 关于视频

- 视频由手机使用 Android 的硬件 H.264 编码器生成，无需额外软件且速度很快。画面会被缩放到不超过 **1280×720**。
- 输出帧率、画质以及旋转/翻转可在 Timelapse 页面的 **Render Settings** 中修改。
- 下一次打印开始时，照片会被自动清理。

## 不打印也能测试

打印机空闲时也可以在工作台上验证整个流程：

1. 在控制台中运行五次或更多次 `_TIMELAPSE_NEW_FRAME HYPERLAPSE=FALSE`（每次一张照片，喷头不会移动）。
2. 在 Timelapse 页面点击 **Render**。
3. 几秒钟后列表中会出现新的 `.mp4`。

## 故障排查

| 现象 | 可能原因 |
|---|---|
| `Unknown command: TIMELAPSE_TAKE_FRAME` | 缺少 `[include timelapse.cfg]`，或 Klipper 没有重启。 |
| Klipper 提示 `HYPERLAPSE` 有问题 | 删除旧的 `[gcode_macro HYPERLAPSE]` 占位宏（第 1 步第 3 点）。 |
| 帧计数一直为 0 | 摄像头服务器未开启，或快照地址无法访问。请在浏览器中打开 `http://<手机IP>:8889/snapshot`：应能看到一张图片。 |
| 渲染立刻失败 | 请使用包含延时摄影修复的应用版本——更早的版本完全无法渲染。 |
| 打印结束后列表里什么都没有 | 开启 **Auto Render** 或点击 **Render**；确认换层 G-code 中确实包含 `TIMELAPSE_TAKE_FRAME`。 |
