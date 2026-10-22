package ru.ytkab0bp.beamklipper.firmware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import java.io.IOException

/** How the board is written. Only the ones the app can really do are [supported]. */
enum class FlashMode(val supported: Boolean) { USB(true), UART(false), DFU(false), RP2040(true) }

enum class FlashStage { PREPARE, ENTER_BOOTSEL, WAIT_BOOTSEL, PERMISSION, ERASE, WRITE, VERIFY, REBOOT, WAIT_DEVICE }

enum class FlashFailure {
    NOT_SUPPORTED, WRONG_DEVICE, FILE_INVALID, NEEDS_RP2040_UF2, RP2350, PERMISSION_DENIED,
    BOOTSEL_TIMEOUT, USB_ERROR, VERIFY_MISMATCH, CANCELLED
}

sealed class FlashState {
    object Idle : FlashState()
    data class Running(val stage: FlashStage, val percent: Int) : FlashState()
    data class Done(val success: Boolean, val failure: FlashFailure?, val detail: String) : FlashState()
}

private class FlashAbort(val failure: FlashFailure, detail: String) : Exception(detail)

/**
 * Runs one firmware write at a time and logs everything in [FlashLog].
 * The state lives here (not in a composable) so it survives rotation and tab changes.
 */
object FlashCoordinator {
    private const val ACTION_PERMISSION = "${BuildConfig.APPLICATION_ID}.action.FLASH_USB_PERMISSION"
    const val NOTE_NO_RETURN = "no-return"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<FlashState>(FlashState.Idle)
    val state: StateFlow<FlashState> = _state

    val running: Boolean get() = _state.value is FlashState.Running

    fun reset() { if (!running) _state.value = FlashState.Idle }

    fun start(ctx: Context, entry: FirmwareStore.Entry, device: UsbDevice, mode: FlashMode) {
        if (running) return
        _state.value = FlashState.Running(FlashStage.PREPARE, 0)
        val app = ctx.applicationContext
        job = scope.launch {
            val label = "${hex(device.vendorId)}:${hex(device.productId)}"
            FlashLog.append("=== Firmware flash started ===")
            FlashLog.append("file=${entry.name} (${entry.size} bytes, ${entry.kind}) sha256=${FirmwareStore.sha256(entry.file)}")
            FlashLog.append("device=$label (${runCatching { device.productName }.getOrNull() ?: "?"}) mode=$mode")
            try {
                val missing = run(app, entry, device, mode)
                FlashLog.append("RESULT: SUCCESS" + if (missing) " (board did not come back on USB)" else "")
                _state.value = FlashState.Done(true, null, if (missing) NOTE_NO_RETURN else "")
            } catch (e: FlashAbort) {
                FlashLog.append("RESULT: FAILED (${e.failure}): ${e.message}")
                _state.value = FlashState.Done(false, e.failure, e.message.orEmpty())
            } catch (e: Throwable) {
                FlashLog.append("RESULT: FAILED (USB_ERROR): ${e.javaClass.simpleName}: ${e.message}")
                _state.value = FlashState.Done(false, FlashFailure.USB_ERROR, e.message.orEmpty())
            } finally {
                UsbSerialManager.connectAll()
            }
        }
    }

    /** Returns true when the data was written and verified but the board never reappeared on USB. */
    private suspend fun run(ctx: Context, entry: FirmwareStore.Entry, device: UsbDevice, mode: FlashMode): Boolean {
        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!mode.supported) {
            // Harmless on purpose: nothing is written, the reason is only reported.
            throw FlashAbort(FlashFailure.NOT_SUPPORTED, "mode $mode is not implemented in this version")
        }
        stage(FlashStage.PREPARE)
        val image = loadImage(entry)

        var target = device
        val isBoot = device.vendorId == Picoboot.VENDOR_RASPBERRY_PI && device.productId == Picoboot.PID_RP2350_BOOT
        if (isBoot) throw FlashAbort(FlashFailure.RP2350, "RP2350 (Pico 2) is not supported yet")
        when (mode) {
            FlashMode.RP2040 -> if (!Picoboot.isBootsel(device))
                throw FlashAbort(FlashFailure.WRONG_DEVICE, "the device is not an RP2040 in BOOTSEL mode (2e8a:0003)")
            FlashMode.USB -> if (!Picoboot.isBootsel(device)) {
                stage(FlashStage.ENTER_BOOTSEL)
                touch1200(usb, device)
                stage(FlashStage.WAIT_BOOTSEL)
                target = waitFor(usb, 15_000) { Picoboot.isBootsel(it) }
                    ?: throw FlashAbort(FlashFailure.BOOTSEL_TIMEOUT, "the board did not enter BOOTSEL after the 1200 bps request")
            }
            else -> {}
        }

        stage(FlashStage.PERMISSION)
        if (!usb.hasPermission(target) && !requestPermission(ctx, usb, target))
            throw FlashAbort(FlashFailure.PERMISSION_DENIED, "USB permission was not granted")

        val conn = usb.openDevice(target) ?: throw FlashAbort(FlashFailure.USB_ERROR, "could not open the USB device")
        Picoboot.open(target, conn).use { pb ->
            pb.reset()
            pb.exclusiveAccess(true)
            pb.exitXip()
            val data = image.data
            val sectors = data.size / Picoboot.SECTOR
            FlashLog.append("erase+write ${data.size} bytes at 0x${Integer.toHexString(image.address)} ($sectors sectors)")
            for (i in 0 until sectors) {
                pb.flashErase(image.address + i * Picoboot.SECTOR, Picoboot.SECTOR)
                progress(FlashStage.ERASE, (i + 1) * 100 / sectors)
            }
            for (i in 0 until sectors) {
                pb.write(image.address + i * Picoboot.SECTOR, data.copyOfRange(i * Picoboot.SECTOR, (i + 1) * Picoboot.SECTOR))
                progress(FlashStage.WRITE, (i + 1) * 100 / sectors)
            }
            for (i in 0 until sectors) {
                val back = pb.read(image.address + i * Picoboot.SECTOR, Picoboot.SECTOR)
                val off = i * Picoboot.SECTOR
                for (j in back.indices) if (back[j] != data[off + j])
                    throw FlashAbort(FlashFailure.VERIFY_MISMATCH, "flash differs from the file at 0x${Integer.toHexString(image.address + off + j)}")
                progress(FlashStage.VERIFY, (i + 1) * 100 / sectors)
            }
            FlashLog.append("verify OK")
            stage(FlashStage.REBOOT)
            pb.reboot(0, 0, 500)
        }
        stage(FlashStage.WAIT_DEVICE)
        val back = waitFor(usb, 20_000) { !Picoboot.isBootsel(it) && (it.vendorId == 0x1D50 || it.vendorId == Picoboot.VENDOR_RASPBERRY_PI) }
        if (back != null) { FlashLog.append("board is back: ${hex(back.vendorId)}:${hex(back.productId)}"); return false }
        FlashLog.append("board did not come back as a USB device within 20 s")
        return true
    }

    private fun loadImage(entry: FirmwareStore.Entry): FlashImage {
        val bytes = entry.file.readBytes()
        return try {
            when (entry.kind) {
                FirmwareKind.UF2 -> FirmwareImage.fromUf2(bytes)
                FirmwareKind.BIN -> FirmwareImage.fromBin(bytes)
                else -> throw FlashAbort(FlashFailure.NEEDS_RP2040_UF2, "the RP2040 needs a .uf2 or .bin file")
            }
        } catch (e: FirmwareException) {
            throw FlashAbort(if (e.message!!.contains("not for the RP2040")) FlashFailure.NEEDS_RP2040_UF2 else FlashFailure.FILE_INVALID, e.message!!)
        }
    }

    /** Arduino-style bootloader request: open the CDC port at 1200 bps (DTR low) and close it. */
    private fun touch1200(usb: UsbManager, dev: UsbDevice) {
        UsbSerialManager.close(UsbSerialManager.getUID(dev))
        val drv = UsbSerialProber(KlipperProbeTable.getInstance()).probeDevice(dev)
            ?: throw FlashAbort(FlashFailure.WRONG_DEVICE, "this device is not a USB serial (CDC) board")
        if (!usb.hasPermission(dev)) throw FlashAbort(FlashFailure.PERMISSION_DENIED, "USB permission was not granted")
        val port = drv.ports[0]
        try {
            port.open(usb.openDevice(dev) ?: throw IOException("could not open the USB device"))
            port.setParameters(1200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // Klipper checks "1200 baud and DTR low" when the control lines change, so drive them explicitly.
            port.dtr = true
            Thread.sleep(100)
            port.dtr = false
            Thread.sleep(300)
        } finally {
            try { port.close() } catch (_: Throwable) {}
        }
        FlashLog.append("1200 bps bootloader request sent")
    }

    private suspend fun waitFor(usb: UsbManager, ms: Long, match: (UsbDevice) -> Boolean): UsbDevice? {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            usb.deviceList.values.firstOrNull(match)?.let { return it }
            delay(250)
        }
        return null
    }

    private suspend fun requestPermission(ctx: Context, usb: UsbManager, dev: UsbDevice): Boolean {
        val answer = CompletableDeferred<Boolean>()
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                answer.complete(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        val filter = IntentFilter(ACTION_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(rx, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") ctx.registerReceiver(rx, filter)
        try {
            usb.requestPermission(dev, PendingIntent.getBroadcast(ctx, 1,
                Intent(ACTION_PERMISSION).setPackage(ctx.packageName), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return withTimeoutOrNull(60_000) { answer.await() } ?: false
        } finally {
            ctx.unregisterReceiver(rx)
        }
    }

    private fun stage(s: FlashStage) { FlashLog.append("stage: $s"); _state.value = FlashState.Running(s, 0) }
    private fun progress(s: FlashStage, p: Int) { _state.value = FlashState.Running(s, p) }
    private fun hex(v: Int) = "%04x".format(v)
}
