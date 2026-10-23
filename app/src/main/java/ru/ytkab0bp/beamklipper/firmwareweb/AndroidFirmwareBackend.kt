package ru.ytkab0bp.beamklipper.firmwareweb

import android.content.Context
import android.content.res.Configuration
import android.hardware.usb.UsbManager
import android.os.Build
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.firmware.FirmwareDownloader
import ru.ytkab0bp.beamklipper.firmware.FirmwareException
import ru.ytkab0bp.beamklipper.firmware.FirmwareKind
import ru.ytkab0bp.beamklipper.firmware.FirmwareStore
import ru.ytkab0bp.beamklipper.firmware.FlashCoordinator
import ru.ytkab0bp.beamklipper.firmware.FlashLog
import ru.ytkab0bp.beamklipper.firmware.FlashMode
import ru.ytkab0bp.beamklipper.firmware.FlashOptions
import ru.ytkab0bp.beamklipper.firmware.FlashState
import ru.ytkab0bp.beamklipper.firmware.Picoboot
import ru.ytkab0bp.beamklipper.firmware.isExperimental
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbDeviceLabel
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import org.json.JSONArray
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.utils.BeamLogs
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** The page's view of the app: the same Firmware tab objects ([FirmwareStore], [FlashCoordinator]...), nothing is written twice. */
class AndroidFirmwareBackend(private val ctx: Context) : FirmwareBackend {
    private val usb get() = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    private val downloadState = AtomicReference(WebDownload("idle", 0, "", emptyList()))
    private val lastRun = AtomicReference(Triple("", "", ""))

    override fun language() = Prefs.appLanguage

    override fun dark(): Boolean =
        if (Build.VERSION.SDK_INT >= 29) ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else true

    override fun files() = FirmwareStore.list().map { WebFile(it.name, it.kind.name, it.size) }

    override fun deleteFile(name: String): Boolean = FirmwareStore.list().firstOrNull { it.name == name }?.let { FirmwareStore.delete(it) } ?: false

    override fun deleteAll() = FirmwareStore.deleteAll()

    override fun saveUpload(name: String, source: File): WebFile {
        val safe = FirmwareStore.safeName(name)
        require(FirmwareStore.isAllowedName(safe)) { "type" }
        require(source.length() in 1..FirmwareStore.MAX_BYTES) { if (source.length() == 0L) "empty" else "size" }
        val dest = FirmwareStore.target(safe)
        source.copyTo(dest, overwrite = false)
        return WebFile(dest.name, FirmwareKind.of(dest.name).name, dest.length())
    }

    override fun startDownload(url: String): String? {
        if (downloadState.get().state == "running") return "busy"
        try { FirmwareDownloader.fileNameFor(url) } catch (e: FirmwareException) { return e.message }
        downloadState.set(WebDownload("running", 0, "", emptyList()))
        Thread({
            try {
                val saved = FirmwareDownloader.download(url, { p -> downloadState.set(WebDownload("running", p.coerceAtLeast(0), "", emptyList())) })
                downloadState.set(WebDownload("done", 100, "", saved.map { it.name }))
            } catch (e: Exception) {
                downloadState.set(WebDownload("error", 0, e.message ?: e.javaClass.simpleName, emptyList()))
            }
        }, "firmware-web-download").start()
        return null
    }

    override fun download() = downloadState.get()

    override fun resetDownload() { if (downloadState.get().state != "running") downloadState.set(WebDownload("idle", 0, "", emptyList())) }

    private fun sortedDevices() = usb.deviceList.values.sortedBy { it.deviceName }

    override fun devices(): List<WebDevice> {
        val list = sortedDevices()
        val names = UsbDeviceLabel.numbered(list.map { UsbDeviceLabel.baseName(try { it.productName } catch (e: SecurityException) { null }) })
        return list.mapIndexed { i, d ->
            val drv = KlipperProbeTable.getInstance().findDriver(d)
            WebDevice(
                d.deviceName, names[i], Integer.toHexString(d.vendorId), Integer.toHexString(d.productId), d.deviceName,
                drv?.name, if (drv != null) File(KlipperApp.INSTANCE.filesDir, "serial/" + UsbSerialManager.getUID(d)).absolutePath else null,
            )
        }
    }

    override fun experimental(file: String, device: String, mode: String): Boolean {
        val f = FirmwareStore.list().firstOrNull { it.name == file } ?: return true
        val m = runCatching { FlashMode.valueOf(mode) }.getOrNull() ?: return true
        val chip = sortedDevices().firstOrNull { it.deviceName == device }?.let { Picoboot.chipHint(it) }
        return isExperimental(m, f.kind, chip)
    }

    override fun startFlash(file: String, device: String, mode: String, startAddress: Long?): String? {
        if (FlashCoordinator.running) return "busy"
        val entry = FirmwareStore.list().firstOrNull { it.name == file } ?: return "file"
        val dev = sortedDevices().firstOrNull { it.deviceName == device } ?: return "device"
        val m = runCatching { FlashMode.valueOf(mode) }.getOrNull()?.takeIf { it.name in WebRequests.MODES } ?: return "mode"
        if (startAddress != null && startAddress !in 0x08000000L until 0x10000000L) return "address"
        lastRun.set(Triple(file, device, mode))
        FlashCoordinator.reset()
        FlashCoordinator.start(ctx, entry, dev, m, FlashOptions(startAddress ?: FlashOptions.DEFAULT_STM32_START))
        return null
    }

    override fun flash(): WebFlash {
        val (file, device, mode) = lastRun.get()
        return when (val s = FlashCoordinator.state.value) {
            is FlashState.Idle -> WebFlash("idle", "", 0, 0, false, null, "", false, "", "", "")
            is FlashState.Running -> WebFlash("running", s.stage.name, s.percent, s.overall, false, null, "", false, file, device, mode)
            is FlashState.Done -> WebFlash("done", "", 100, if (s.success) 100 else 0, s.success, s.failure?.name, s.detail,
                s.detail == FlashCoordinator.NOTE_NO_RETURN, file, device, mode)
        }
    }

    override fun resetFlash() { FlashCoordinator.reset() }

    override fun log() = FlashLog.read().lines().takeLast(60).joinToString("\n")

    private val collector = DashboardCollector(ctx)

    override fun dashboard(): JSONObject {
        val usb = JSONArray()
        devices().forEach {
            usb.put(JSONObject().put("label", it.label).put("vendor", it.vendor).put("product", it.product)
                .put("path", it.path).put("driver", it.driver ?: JSONObject.NULL).put("serialPath", it.serialPath ?: JSONObject.NULL))
        }
        return collector.info(usb)
    }

    override fun metrics(): JSONObject = collector.metrics()

    // BeamLogs lists the sources at the moment of the call (instances come and go), so each call asks again
    override fun logSources() = BeamLogs.sources(ctx).map { WebLogSource(it.id, it.label) }

    override fun readLog(id: String): String? = BeamLogs.sources(ctx).firstOrNull { it.id == id }?.let { runCatching(it.load).getOrElse { e -> "<${e.message}>" } }

    override fun clearLogs(): Boolean = BeamLogs.clear(ctx)
}
