package ru.ytkab0bp.beamklipper.firmware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
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
import ru.ytkab0bp.beamklipper.serial.KlipperProbeTable
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import java.io.IOException

/** How the board is written. Only RP2040 (and USB with an RP2040) has conclusive hardware tests. */
enum class FlashMode { USB, UART, DFU, RP2040 }

enum class FlashStage { PREPARE, ENTER_BOOTSEL, WAIT_BOOTSEL, PERMISSION, HANDSHAKE, ERASE, WRITE, VERIFY, REBOOT, WAIT_DEVICE }

enum class FlashFailure {
    WRONG_DEVICE, FILE_INVALID, INCOMPATIBLE_FILE, WRONG_CHIP, PERMISSION_DENIED, NO_BOOTLOADER,
    NOT_SERIAL, NO_ANSWER, BAD_ADDRESS, USB_ERROR, VERIFY_MISMATCH, CANCELLED
}

/** [startAddress] is only used for raw .bin files written to STM32 boards (DFU / UART / USB). */
data class FlashOptions(val startAddress: Long = DEFAULT_STM32_START) {
    companion object { const val DEFAULT_STM32_START = 0x08000000L }
}

sealed class FlashState {
    object Idle : FlashState()
    /** [percent] is the progress inside [stage]; [overall] is 0..100 for the whole run. */
    data class Running(val stage: FlashStage, val percent: Int, val overall: Int) : FlashState()
    data class Done(val success: Boolean, val failure: FlashFailure?, val detail: String) : FlashState()
}

private class FlashAbort(val failure: FlashFailure, detail: String) : Exception(detail)

/** The share of the whole progress bar each stage owns. */
internal object FlashProgress {
    private val ranges = mapOf(
        FlashStage.PREPARE to (0 to 4), FlashStage.ENTER_BOOTSEL to (4 to 12), FlashStage.WAIT_BOOTSEL to (12 to 22),
        FlashStage.PERMISSION to (22 to 28), FlashStage.HANDSHAKE to (28 to 34), FlashStage.ERASE to (34 to 50),
        FlashStage.WRITE to (50 to 80), FlashStage.VERIFY to (80 to 96), FlashStage.REBOOT to (96 to 98),
        FlashStage.WAIT_DEVICE to (98 to 100),
    )

    fun overall(stage: FlashStage, percent: Int): Int {
        val (lo, hi) = ranges.getValue(stage)
        return lo + (hi - lo) * percent.coerceIn(0, 100) / 100
    }
}

/** Whether a mode lacks conclusive hardware tests: everything except RP2040 (and UF2 files through USB mode); the RP2350 is always experimental. */
fun isExperimental(mode: FlashMode, kind: FirmwareKind, chip: PicoChip? = null): Boolean =
    chip == PicoChip.RP2350 || !(mode == FlashMode.RP2040 || (mode == FlashMode.USB && kind == FirmwareKind.UF2))

/**
 * Runs one firmware write at a time and logs everything in [FlashLog].
 * The state lives here (not in a composable) so it survives rotation and tab changes.
 */
object FlashCoordinator {
    private const val ACTION_PERMISSION = "${BuildConfig.APPLICATION_ID}.action.FLASH_USB_PERMISSION"
    const val NOTE_NO_RETURN = "no-return"
    private const val VENDOR_ST = 0x0483
    private const val PRODUCT_ST_DFU = 0xDF11
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<FlashState>(FlashState.Idle)
    val state: StateFlow<FlashState> = _state

    val running: Boolean get() = _state.value is FlashState.Running

    fun reset() { if (!running) _state.value = FlashState.Idle }

    fun start(ctx: Context, entry: FirmwareStore.Entry, device: UsbDevice, mode: FlashMode, options: FlashOptions = FlashOptions()) {
        if (running) return
        _state.value = FlashState.Running(FlashStage.PREPARE, 0, 0)
        val app = ctx.applicationContext
        job = scope.launch {
            val label = "${hex(device.vendorId)}:${hex(device.productId)}"
            FlashLog.append("=== Firmware flash started ===")
            FlashLog.append("file=${entry.name} (${entry.size} bytes, ${entry.kind}) sha256=${FirmwareStore.sha256(entry.file)}")
            FlashLog.append("device=$label (${runCatching { device.productName }.getOrNull() ?: "?"}) mode=$mode" +
                    (if (isExperimental(mode, entry.kind, Picoboot.chipHint(device))) " [experimental]" else "") +
                    " start=0x${options.startAddress.toString(16)}")
            try {
                val missing = run(app, entry, device, mode, options)
                FlashLog.append("RESULT: SUCCESS" + if (missing) " (board did not come back on USB)" else "")
                _state.value = FlashState.Done(true, null, if (missing) NOTE_NO_RETURN else "")
            } catch (e: FlashAbort) {
                FlashLog.append("RESULT: FAILED (${e.failure}): ${e.message}")
                _state.value = FlashState.Done(false, e.failure, e.message.orEmpty())
            } catch (e: NoAnswerException) {
                FlashLog.append("RESULT: FAILED (NO_ANSWER): ${e.message}")
                _state.value = FlashState.Done(false, FlashFailure.NO_ANSWER, e.message.orEmpty())
            } catch (e: FirmwareException) {
                val f = if (e.message.orEmpty().startsWith("the flash differs")) FlashFailure.VERIFY_MISMATCH else FlashFailure.FILE_INVALID
                FlashLog.append("RESULT: FAILED ($f): ${e.message}")
                _state.value = FlashState.Done(false, f, e.message.orEmpty())
            } catch (e: Throwable) {
                FlashLog.append("RESULT: FAILED (USB_ERROR): ${e.javaClass.simpleName}: ${e.message}")
                _state.value = FlashState.Done(false, FlashFailure.USB_ERROR, e.message.orEmpty())
            } finally {
                UsbSerialManager.connectAll()
            }
        }
    }

    // --- routing ----------------------------------------------------------------

    /** Returns true when the data was written but the board never reappeared on USB. */
    private suspend fun run(ctx: Context, entry: FirmwareStore.Entry, device: UsbDevice, mode: FlashMode, opts: FlashOptions): Boolean {
        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        stage(FlashStage.PREPARE)

        var target = device
        when (mode) {
            FlashMode.RP2040 -> if (!Picoboot.isBootsel(device))
                throw FlashAbort(FlashFailure.WRONG_DEVICE, "the device is not an RP2040 / RP2350 in BOOTSEL mode (2e8a:0003 / 2e8a:000f)")
            FlashMode.DFU -> if (!isDfu(device))
                throw FlashAbort(FlashFailure.WRONG_DEVICE, "the device is not an STM32 in DFU mode (0483:df11)")
            FlashMode.UART -> {
                if (Picoboot.isBootsel(device) || isDfu(device))
                    throw FlashAbort(FlashFailure.WRONG_DEVICE, "this device is a USB bootloader, not a serial adapter: use RP2040 or DFU mode")
                if (serialDriver(device) == null)
                    throw FlashAbort(FlashFailure.NOT_SERIAL, "this device is not a USB-serial adapter or CDC serial port")
            }
            FlashMode.USB -> if (!Picoboot.isBootsel(device) && !isDfu(device)) {
                stage(FlashStage.ENTER_BOOTSEL)
                touch1200(usb, device)
                stage(FlashStage.WAIT_BOOTSEL)
                target = waitFor(usb, 15_000) {
                    Picoboot.isBootsel(it) || isDfu(it) ||
                            // Caterina (ATmega32U4) shows up as a different serial device
                            ((it.vendorId != device.vendorId || it.productId != device.productId) && serialDriver(it) != null)
                } ?: throw FlashAbort(FlashFailure.NO_BOOTLOADER,
                    "no supported bootloader appeared after the 1200 bps request (RP2040 BOOTSEL, STM32 DFU or AVR109)")
                FlashLog.append("bootloader appeared: ${hex(target.vendorId)}:${hex(target.productId)}")
            }
        }

        return when {
            Picoboot.isBootsel(target) -> flashPico(ctx, usb, target, entry)
            isDfu(target) -> flashDfu(ctx, usb, target, entry, opts)
            mode == FlashMode.UART -> flashUart(ctx, usb, target, entry, opts)
            else -> flashCaterina(ctx, usb, target, entry, opts)
        }
    }

    // --- RP2040 -----------------------------------------------------------------

    private suspend fun flashPico(ctx: Context, usb: UsbManager, target: UsbDevice, entry: FirmwareStore.Entry): Boolean {
        val chip = Picoboot.chipOf(target) ?: throw FlashAbort(FlashFailure.WRONG_DEVICE, "not a Raspberry Pi boot ROM device")
        val image = picoImage(entry, chip)
        if (chip == PicoChip.RP2350) FlashLog.append("RP2350 (experimental): PICOBOOT with REBOOT2")
        ensurePermission(ctx, usb, target)
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
            if (chip == PicoChip.RP2350) pb.reboot2(0, 500, 0, 0) else pb.reboot(0, 0, 500)
        }
        return waitForBoard(usb)
    }

    private fun picoImage(entry: FirmwareStore.Entry, chip: PicoChip): FlashImage {
        val bytes = entry.file.readBytes()
        return try {
            when (entry.kind) {
                FirmwareKind.UF2 -> FirmwareImage.fromUf2(bytes, chip)
                FirmwareKind.BIN -> FirmwareImage.fromBin(bytes, chip)
                else -> throw FlashAbort(FlashFailure.INCOMPATIBLE_FILE, "the $chip needs a .uf2 or .bin file")
            }
        } catch (e: WrongChipException) {
            throw FlashAbort(FlashFailure.WRONG_CHIP, e.message!!)
        } catch (e: FirmwareException) {
            throw FlashAbort(FlashFailure.FILE_INVALID, e.message!!)
        }
    }

    // --- STM32 DFU --------------------------------------------------------------

    private suspend fun flashDfu(ctx: Context, usb: UsbManager, target: UsbDevice, entry: FirmwareStore.Entry, opts: FlashOptions): Boolean {
        val image = rawImage(entry, opts)
        ensurePermission(ctx, usb, target)
        val conn = usb.openDevice(target) ?: throw FlashAbort(FlashFailure.USB_ERROR, "could not open the USB device")
        var intf: UsbInterface? = null
        try {
            for (i in 0 until target.interfaceCount) {
                val c = target.getInterface(i)
                if (c.interfaceClass == 0xFE && c.interfaceSubclass == 1 && c.alternateSetting == 0) { intf = c; break }
            }
            val dfu = intf ?: throw FlashAbort(FlashFailure.WRONG_DEVICE, "no DFU interface on this device")
            if (!conn.claimInterface(dfu, true)) throw FlashAbort(FlashFailure.USB_ERROR, "could not claim the DFU interface")
            conn.setInterface(dfu)
            val raw = conn.rawDescriptors ?: ByteArray(0)
            val transfer = dfuTransferSize(raw)
            val name = dfu.name ?: interfaceString(conn, raw, dfu.id)
                ?: throw FlashAbort(FlashFailure.USB_ERROR, "could not read the flash layout of the device")
            val layout = DfuseFlasher.parseLayout(name)
            if (layout.isEmpty()) throw FlashAbort(FlashFailure.USB_ERROR, "could not understand the flash layout \"$name\"")
            FlashLog.append("DFU: $name, transfer size $transfer, ${layout.size} sectors")
            FlashLog.append("write ${image.data.size} bytes at 0x${image.address.toString(16)}")
            DfuseFlasher(UsbDfuLink(conn, dfu.id), transfer, layout)
                .flash(image, verify = true, progress = this::progress, log = { FlashLog.append(it) })
        } finally {
            try { intf?.let { conn.releaseInterface(it) } } catch (_: Throwable) {}
            conn.close()
        }
        stage(FlashStage.WAIT_DEVICE)
        return waitForBoard(usb, announce = false)
    }

    private fun dfuTransferSize(raw: ByteArray): Int {
        var i = 0
        while (i + 2 <= raw.size) {
            val len = raw[i].toInt() and 0xFF
            if (len < 2) break
            if (raw[i + 1].toInt() == 0x21 && len >= 9 && i + 7 <= raw.size)
                return (((raw[i + 6].toInt() and 0xFF) shl 8) or (raw[i + 5].toInt() and 0xFF)).coerceIn(64, 4096)
            i += len
        }
        return 1024
    }

    /** Reads the iInterface string of interface [id], alternate setting 0, straight from the descriptors. */
    private fun interfaceString(conn: UsbDeviceConnection, raw: ByteArray, id: Int): String? {
        var i = 0
        while (i + 2 <= raw.size) {
            val len = raw[i].toInt() and 0xFF
            if (len < 2) break
            if (raw[i + 1].toInt() == 4 && len >= 9 && raw[i + 2].toInt() == id && raw[i + 3].toInt() == 0) {
                val index = raw[i + 8].toInt() and 0xFF
                if (index == 0) return null
                val buf = ByteArray(255)
                val r = conn.controlTransfer(0x80, 6, (3 shl 8) or index, 0x0409, buf, buf.size, 1000)
                return if (r > 2) String(buf, 2, r - 2, Charsets.UTF_16LE) else null
            }
            i += len
        }
        return null
    }

    // --- UART (STM32 AN3155, AVR STK500v1/v2) ----------------------------------

    private suspend fun flashUart(ctx: Context, usb: UsbManager, target: UsbDevice, entry: FirmwareStore.Entry, opts: FlashOptions): Boolean {
        val image = rawImage(entry, opts)
        UsbSerialManager.close(UsbSerialManager.getUID(target))
        ensurePermission(ctx, usb, target)
        val drv = serialDriver(target) ?: throw FlashAbort(FlashFailure.NOT_SERIAL, "this device is not a USB-serial adapter")
        val port = drv.ports[0]
        try {
            port.open(usb.openDevice(target) ?: throw FlashAbort(FlashFailure.USB_ERROR, "could not open the USB device"))
            val link = UsbSerialLink(port)
            val log = { m: String -> FlashLog.append(m) }
            if (image.address >= STM32_FLASH_MIN) {
                port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_EVEN)
                log("STM32 serial bootloader, mass erase, write at 0x${image.address.toString(16)}")
                Stm32SerialBootloader(link).flash(image, true, this::progress, log)
            } else {
                flashAvrSerial(port, link, image, log)
            }
        } finally {
            try { port.close() } catch (_: Throwable) {}
        }
        return false
    }

    /** Tries the AVR bootloaders Klipper uses over a serial line: arduino (STK500v1) and wiring (STK500v2). */
    private fun flashAvrSerial(port: UsbSerialPort, link: SerialLink, image: RawImage, log: (String) -> Unit) {
        val attempts = listOf<Triple<String, Int, () -> Unit>>(
            Triple("arduino (STK500v1) at 115200", 115200) { Stk500v1(link).flash(image, true, this::progress, log) },
            Triple("wiring (STK500v2) at 115200", 115200) { Stk500v2(link).flash(image, true, this::progress, log) },
            Triple("arduino (STK500v1) at 57600", 57600) { Stk500v1(link).flash(image, true, this::progress, log) },
        )
        var last: NoAnswerException? = null
        for ((name, baud, attempt) in attempts) {
            port.setParameters(baud, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            log("AVR bootloader: trying $name")
            try { attempt(); return } catch (e: NoAnswerException) { last = e }
        }
        throw last ?: NoAnswerException("no AVR bootloader answered")
    }

    // --- USB mode, AVR109 (Caterina) --------------------------------------------

    private suspend fun flashCaterina(ctx: Context, usb: UsbManager, target: UsbDevice, entry: FirmwareStore.Entry, opts: FlashOptions): Boolean {
        val image = rawImage(entry, opts)
        if (image.address >= STM32_FLASH_MIN)
            throw FlashAbort(FlashFailure.INCOMPATIBLE_FILE, "the AVR bootloader needs an AVR .hex file")
        ensurePermission(ctx, usb, target)
        val drv = serialDriver(target) ?: throw FlashAbort(FlashFailure.NOT_SERIAL, "the bootloader is not a serial device")
        val port = drv.ports[0]
        try {
            port.open(usb.openDevice(target) ?: throw FlashAbort(FlashFailure.USB_ERROR, "could not open the USB device"))
            port.setParameters(57600, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            Avr109(UsbSerialLink(port)).flash(image, true, this::progress) { FlashLog.append(it) }
        } finally {
            try { port.close() } catch (_: Throwable) {}
        }
        stage(FlashStage.WAIT_DEVICE)
        return waitForBoard(usb)
    }

    // --- helpers ----------------------------------------------------------------

    private const val STM32_FLASH_MIN = 0x08000000L
    private const val STM32_FLASH_MAX = 0x10000000L

    /** .bin (at the start address) or .hex (carries its own addresses) for STM32 and AVR. */
    private fun rawImage(entry: FirmwareStore.Entry, opts: FlashOptions): RawImage = try {
        when (entry.kind) {
            FirmwareKind.HEX -> IntelHex.parse(entry.file.readText())
            FirmwareKind.BIN -> {
                if (opts.startAddress < STM32_FLASH_MIN || opts.startAddress >= STM32_FLASH_MAX)
                    throw FlashAbort(FlashFailure.BAD_ADDRESS, "the flash start address 0x${opts.startAddress.toString(16)} is not valid for an STM32")
                IntelHex.fromBin(entry.file.readBytes(), opts.startAddress)
            }
            else -> throw FlashAbort(FlashFailure.INCOMPATIBLE_FILE, "this mode needs a .bin or .hex file")
        }
    } catch (e: FirmwareException) {
        throw FlashAbort(FlashFailure.FILE_INVALID, e.message!!)
    }

    private fun isDfu(d: UsbDevice) = d.vendorId == VENDOR_ST && d.productId == PRODUCT_ST_DFU

    private fun serialDriver(d: UsbDevice) = UsbSerialProber(KlipperProbeTable.getInstance()).probeDevice(d)

    /** Arduino-style bootloader request: open the CDC port at 1200 bps, DTR high then low, close it. */
    private fun touch1200(usb: UsbManager, dev: UsbDevice) {
        UsbSerialManager.close(UsbSerialManager.getUID(dev))
        val drv = serialDriver(dev)
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

    private suspend fun waitForBoard(usb: UsbManager, announce: Boolean = true): Boolean {
        if (announce) stage(FlashStage.WAIT_DEVICE)
        val back = waitFor(usb, 20_000) { !Picoboot.isBootsel(it) && !isDfu(it) && (it.vendorId == 0x1D50 || it.vendorId == Picoboot.VENDOR_RASPBERRY_PI) }
        if (back != null) { FlashLog.append("board is back: ${hex(back.vendorId)}:${hex(back.productId)}"); return false }
        FlashLog.append("board did not come back as a USB device within 20 s")
        return true
    }

    private suspend fun waitFor(usb: UsbManager, ms: Long, match: (UsbDevice) -> Boolean): UsbDevice? {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            usb.deviceList.values.firstOrNull(match)?.let { return it }
            delay(250)
        }
        return null
    }

    private suspend fun ensurePermission(ctx: Context, usb: UsbManager, dev: UsbDevice) {
        stage(FlashStage.PERMISSION)
        if (!usb.hasPermission(dev) && !requestPermission(ctx, usb, dev))
            throw FlashAbort(FlashFailure.PERMISSION_DENIED, "USB permission was not granted")
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

    private fun stage(s: FlashStage) { FlashLog.append("stage: $s"); progress(s, 0) }
    private fun progress(s: FlashStage, p: Int) { _state.value = FlashState.Running(s, p, FlashProgress.overall(s, p)) }
    private fun hex(v: Int) = "%04x".format(v)
}

/** [SerialLink] over a usb-serial-for-android port. */
class UsbSerialLink(private val port: UsbSerialPort) : SerialLink {
    override fun write(data: ByteArray) = port.write(data, 5000)

    override fun read(n: Int, timeoutMs: Int): ByteArray {
        val buf = ByteArray(n.coerceIn(1, 4096))
        val c = try {
            port.read(buf, timeoutMs.coerceAtLeast(1))
        } catch (e: IOException) {
            // After a silent timeout the library probes the device with GET_STATUS; some boards (Klipper CDC) stall
            // it. That is "no data yet", not a lost connection; a really unplugged device ends as "no answer".
            if (e.message.orEmpty().contains("get_status")) 0 else throw e
        }
        return buf.copyOf(maxOf(c, 0))
    }

    override fun setLines(dtr: Boolean, rts: Boolean) {
        try { port.dtr = dtr; port.rts = rts } catch (_: Exception) { /* the driver has no control lines */ }
    }

    override fun drain() {
        val b = ByteArray(4096)
        try { while (port.read(b, 20) > 0) { /* discard */ } } catch (_: Exception) {}
    }
}
