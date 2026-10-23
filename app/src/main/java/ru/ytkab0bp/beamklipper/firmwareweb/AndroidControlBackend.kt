package ru.ytkab0bp.beamklipper.firmwareweb

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.InstanceActions
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.service.CameraService
import ru.ytkab0bp.beamklipper.NewInstance
import ru.ytkab0bp.beamklipper.ui.state.InstanceEditorViewModel
import ru.ytkab0bp.beamklipper.ui.state.MainViewModel
import ru.ytkab0bp.beamklipper.ui.state.SettingsViewModel
import ru.ytkab0bp.beamklipper.utils.CameraFocus
import ru.ytkab0bp.beamklipper.utils.CameraZoom
import ru.ytkab0bp.beamklipper.utils.Engines
import ru.ytkab0bp.beamklipper.utils.Frontends
import ru.ytkab0bp.beamklipper.utils.Languages
import ru.ytkab0bp.beamklipper.utils.ObicoLink
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.UsbNaming
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/** The page's controls run the same code as the app's own screens ([SettingsViewModel], [MainViewModel]): nothing is written twice. */
class AndroidControlBackend(private val ctx: Context) : ControlBackend {
    private val app = ctx.applicationContext as Application
    private val settings by lazy { SettingsViewModel(app) }
    private val main by lazy { MainViewModel(app) }

    private fun option(id: Any?, label: String) = JSONObject().put("id", id ?: "").put("label", label)
    private fun <T> options(items: List<T>, id: (T) -> Any?, label: (T) -> String) = JSONArray().also { a -> items.forEach { a.put(option(id(it), label(it))) } }

    override fun settings(): JSONObject {
        val engines = listOf(Prefs.ENGINE_KLIPPER, Prefs.ENGINE_KALICO).filter { Engines.isInstalled(app.filesDir, it) }
        val zoom = settings.cameraZoomOptions()
        val octoUrl = if (Prefs.isOctoEverywhereEnabled) settings.octoEverywhereLinkUrl() else null
        val obicoStatus = if (Prefs.isObicoEnabled && !Prefs.isObicoLinked) settings.syncObicoLinkStatus() else null
        return JSONObject()
            .put("engine", JSONObject().put("value", Prefs.engine).put("options", options(engines, { it }, { settings.engineTitle(it) })))
            .put("frontend", JSONObject().put("value", Prefs.webFrontend).put("options", options(Frontends.ALL, { it }, { settings.frontendTitle(it) })))
            .put("usbNaming", JSONObject().put("value", Prefs.usbDeviceNaming).put("options",
                options(listOf(Prefs.USB_DEVICE_NAMING_BY_PATH, Prefs.USB_DEVICE_NAMING_BY_VID_PID), { it }, { settings.usbNamingTitle(it) })))
            .put("language", JSONObject().put("value", Prefs.appLanguage).put("options", options(Languages.ALL, { it }, { settings.languageTitle(it) })))
            .put("camera", JSONObject()
                .put("enabled", Prefs.isCameraEnabled)
                .put("permission", hasCameraPermission())
                .put("source", JSONObject().put("value", Prefs.cameraId ?: "").put("options", options(settings.cameraSourceOptions(), { it.id }, { it.label })))
                .put("rotation", JSONObject().put("value", Prefs.cameraRotation).put("options", options(listOf(0, 90, 180, 270), { it }, { "$it°" })))
                .put("resolution", JSONObject().put("value", Prefs.cameraResolution).put("options",
                    options(listOf(Prefs.CAMERA_RESOLUTION_LOW, Prefs.CAMERA_RESOLUTION_MEDIUM, Prefs.CAMERA_RESOLUTION_HIGH), { it }, { settings.cameraResolutionTitle(it) })))
                .put("zoom", JSONObject().put("value", settings.effectiveCameraZoom(Prefs.cameraZoom).toDouble()).put("options", options(zoom, { it.toDouble() }, { CameraZoom.label(it) }))))
            .put("octoEverywhere", JSONObject().put("enabled", Prefs.isOctoEverywhereEnabled).put("linkUrl", octoUrl ?: JSONObject.NULL))
            .put("obico", JSONObject()
                .put("enabled", Prefs.isObicoEnabled)
                .put("cloud", settings.isObicoCloud(Prefs.obicoServerUrl))
                .put("serverUrl", Prefs.obicoServerUrl)
                .put("linked", Prefs.isObicoLinked)
                .put("passcode", obicoStatus?.passcode?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("passlink", obicoStatus?.passlink?.takeIf { it.isNotBlank() } ?: JSONObject.NULL))
    }

    private fun hasCameraPermission() = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun applySetting(key: String, body: JSONObject): String? {
        val value = body.opt("value")
        return when (key) {
            "engine" -> { val v = value as? String ?: return "value"; if (v !in listOf(Prefs.ENGINE_KLIPPER, Prefs.ENGINE_KALICO)) "value" else if (!Engines.isInstalled(app.filesDir, v)) "unavailable" else { settings.setEngine(v); null } }
            "frontend" -> { val v = value as? String ?: return "value"; if (v !in Frontends.ALL) "value" else { settings.setFrontend(v); null } }
            "usbNaming" -> { val v = (value as? Number)?.toInt() ?: return "value"; if (v != Prefs.USB_DEVICE_NAMING_BY_PATH && v != Prefs.USB_DEVICE_NAMING_BY_VID_PID) "value" else { Prefs.usbDeviceNaming = v; null } }
            "language" -> { val v = value as? String ?: return "value"; if (v !in Languages.ALL) "value" else { Prefs.appLanguage = v; null } }
            "cameraEnabled" -> {
                val on = value as? Boolean ?: return "value"
                if (on && !hasCameraPermission()) "permission" else { settings.setCameraEnabled(on); null }
            }
            "cameraSource" -> { val v = (value as? String ?: return "value").ifEmpty { null }; if (v != null && settings.cameraSourceOptions().none { it.id == v }) "value" else { settings.setCameraSource(v); null } }
            "cameraRotation" -> { val v = (value as? Number)?.toInt() ?: return "value"; if (v !in listOf(0, 90, 180, 270)) "value" else { Prefs.cameraRotation = v; null } }
            "cameraResolution" -> { val v = (value as? Number)?.toInt() ?: return "value"; if (v !in 0..2) "value" else { Prefs.cameraResolution = v; null } }
            "cameraZoom" -> { val v = (value as? Number)?.toFloat() ?: return "value"; if (settings.cameraZoomOptions().none { Math.abs(it - v) < 0.01f }) "value" else { Prefs.cameraZoom = v; null } }
            "octoEverywhere" -> { settings.setOctoEverywhereEnabled(value as? Boolean ?: return "value"); null }
            "obico" -> { settings.setObicoEnabled(value as? Boolean ?: return "value"); null }
            "obicoServer" -> {
                val cloud = body.optBoolean("cloud", true)
                val url = body.optString("url", "")
                if (!cloud && !(url.startsWith("http://") || url.startsWith("https://"))) "value" else { settings.setObicoServer(cloud, url); null }
            }
            "obicoLink" -> {
                val code = value as? String ?: return "value"
                when (runBlocking { settings.linkObico(code) }) {
                    ObicoLink.Result.Success -> null
                    ObicoLink.Result.InvalidCode -> "link"
                    is ObicoLink.Result.NetworkError -> "network"
                }
            }
            "obicoUnlink" -> { settings.unlinkObico(); null }
            else -> "key"
        }
    }

    override fun control(id: String?, action: String): String? {
        if (action != "start" && action != "stop" && action != "toggle") return "action"
        if (id == null) {
            // the app's own "start / stop all" button: stops everything when anything runs, else starts everything
            val anyActive = KlipperInstance.getInstances().any { InstanceActions.isActive(it.getState()) }
            if ((action == "start" && anyActive) || (action == "stop" && !anyActive)) return null
            main.runStopAll()
            return null
        }
        val inst = KlipperInstance.getInstance(id) ?: return "id"
        val active = InstanceActions.isActive(inst.getState())
        if ((action == "start" && active) || (action == "stop" && !active)) return null
        if (!active && !InstanceActions.canStart(inst.getState(), KlipperInstance.hasFreeSlots())) return "slots"
        main.toggle(inst)
        return null
    }

    /** The app's own editor logic (names, starter config, files), loaded: the profile bundle may still be unpacking on a first start. */
    private fun loadedEditor(): InstanceEditorViewModel {
        val vm = InstanceEditorViewModel(app)
        vm.loadForCreate()
        val deadline = System.currentTimeMillis() + 20_000
        while (vm.filesList.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        return vm
    }

    private fun profileCount() = runCatching { KlipperApp.DATABASE.getInstances().size }.getOrDefault(0)

    override fun newPrinterInfo(): JSONObject {
        val vm = loadedEditor()
        val files = vm.filesList.value
        return JSONObject()
            .put("templates", JSONArray(files))
            .put("default", vm.configFile.value ?: JSONObject.NULL)
            .put("defaultName", vm.defaultName.value ?: "")
            .put("canCreate", NewInstance.canCreate(profileCount(), KlipperInstance.SLOTS_COUNT, vm.configFile.value))
    }

    override fun addPrinter(name: String, config: String, autostart: Boolean): String? {
        val vm = loadedEditor()
        if (config !in vm.filesList.value) return "config"
        if (!NewInstance.canCreate(profileCount(), KlipperInstance.SLOTS_COUNT, config)) return "slots"
        vm.selectConfig(config)
        val done = java.util.concurrent.CountDownLatch(1)
        vm.save(name, autostart) { done.countDown() }
        return if (done.await(30, java.util.concurrent.TimeUnit.SECONDS)) null else "server"
    }

    override fun camera(): JSONObject = JSONObject()
        .put("enabled", Prefs.isCameraEnabled)
        .put("permission", hasCameraPermission())
        .put("focusSupported", try { CameraFocus.isSupportedForSelectedCamera(ctx) } catch (_: Throwable) { false })

    override fun openCameraStream(): CameraFeed? {
        if (!Prefs.isCameraEnabled) return null
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress("127.0.0.1", CAMERA_PORT), 1500)
            socket.getOutputStream().apply { write("GET /webcam/?action=stream HTTP/1.0\r\n\r\n".toByteArray()); flush() }
            val input = socket.getInputStream()
            // skip the status line and headers of the camera service's answer
            var contentType = "multipart/x-mixed-replace; boundary=camera-frame"
            while (true) {
                val line = readLine(input) ?: throw java.io.IOException("closed")
                if (line.isEmpty()) break
                if (line.startsWith("Content-Type:", ignoreCase = true)) contentType = line.substringAfter(':').trim()
            }
            CameraFeed(contentType, object : FilterInputStream(input) {
                override fun close() { try { socket.close() } catch (_: Exception) {} }
            })
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
            null
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    override fun focusAt(x: Float, y: Float) {
        KlipperApp.INSTANCE.sendBroadcast(
            Intent(CameraService.ACTION_TAP_FOCUS).putExtra(CameraService.KEY_TAP_X, x).putExtra(CameraService.KEY_TAP_Y, y),
            KlipperApp.PERMISSION
        )
    }

    override fun resetFocus() {
        KlipperApp.INSTANCE.sendBroadcast(Intent(CameraService.ACTION_RESET_FOCUS), KlipperApp.PERMISSION)
    }

    private companion object { const val CAMERA_PORT = 8889 }
}
