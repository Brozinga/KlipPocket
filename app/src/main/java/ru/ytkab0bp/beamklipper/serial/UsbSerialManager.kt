package ru.ytkab0bp.beamklipper.serial

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.beamklipper.utils.ViewUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object UsbSerialManager {
    const val ACTION_ON_DEVICE_CONNECTED = "${BuildConfig.APPLICATION_ID}.action.DEVICE_CONNECTED"
    const val FLAG_RESET_ARDUINO = 1
    private const val DEBUG = false
    private const val TAG = "beam_usb_serial"

    // A connected board: its USB port, the virtual serial port Klipper opens, and the reader.
    private class Entry(val uid: String, val port: UsbSerialPort, val nativePort: NativeSerialPort) {
        lateinit var thread: ReadThread
    }

    private val entries = PortSlots<Entry>()
    // Virtual serial ports kept open while their board is gone, so Klipper keeps the same
    // file and finds the board again after a firmware or Klipper restart
    private val parked = PortSlots<NativeSerialPort>()
    private var mUsbManager: UsbManager? = null

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED || intent.action == ACTION_ON_DEVICE_CONNECTED) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    if (mUsbManager?.hasPermission(device) == true) {
                        val open = Runnable {
                            val prober = UsbSerialProber(KlipperProbeTable.getInstance())
                            val drv = prober.probeDevice(device)
                            if (drv != null && drv.ports.isNotEmpty()) {
                                connect(drv)
                            }
                        }
                        // A board that just came up (printer powered on) is not ready right away
                        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                            ViewUtils.postOnMainThread(open, UsbReconnect.ATTACH_SETTLE_MS)
                        } else {
                            open.run()
                        }
                    } else {
                        if (intent.action == ACTION_ON_DEVICE_CONNECTED && !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (DEBUG) {
                                Log.d(TAG, "Failed to acquire usb permission")
                            }
                            return
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Failed to connect, no permission: ${getUID(device)}")
                        }
                    }
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    val uid = getUID(device)
                    // Ignore a late event about an older connection of the same board
                    val entry = entries.get(uid)
                    if (entry?.port?.device?.deviceName == device.deviceName && entries.removeIfCurrent(uid, entry)) {
                        release(entry, true)
                    }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun init(ctx: Application) {
        val f = File(KlipperApp.INSTANCE.filesDir, "serial")
        if (f.exists()) {
            for (c in f.listFiles() ?: emptyArray()) {
                if (c.delete() && DEBUG) {
                    Log.d(TAG, "Deleted old ${c.absolutePath}")
                }
            }
        } else f.mkdirs()

        mUsbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_ON_DEVICE_CONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }

        connectAll()
    }

    fun connectAll() {
        val prober = UsbSerialProber(KlipperProbeTable.getInstance())
        val manager = KlipperApp.INSTANCE.getSystemService(Context.USB_SERVICE) as UsbManager
        for (drv in prober.findAllDrivers(manager)) {
            if (drv.ports.isNotEmpty()) {
                connect(drv)
            }
        }
    }

    fun disconnectAll() {
        // close() removes the port from the slots: iterate over a copy
        for (uid in entries.keys() + parked.keys()) {
            close(uid)
        }
    }

    fun getUID(device: UsbDevice): String =
        UsbDeviceId.uid(Prefs.usbDeviceNaming, device.vendorId, device.productId, device.deviceName)

    fun connect(drv: UsbSerialDriver) = connect(drv, 0)

    fun connect(drv: UsbSerialDriver, flags: Int) = connect(drv, flags, 0)

    private fun connect(drv: UsbSerialDriver, flags: Int, attempt: Int) {
        if (mUsbManager?.hasPermission(drv.device) != true) {
            mUsbManager?.requestPermission(
                drv.device,
                PendingIntent.getBroadcast(
                    KlipperApp.INSTANCE,
                    0,
                    Intent(ACTION_ON_DEVICE_CONNECTED).setPackage(KlipperApp.INSTANCE.packageName),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return
        }

        val resetArduino = (flags and FLAG_RESET_ARDUINO) != 0
        val currentBaudRate = AtomicInteger(250000)
        val port = drv.ports[0]
        val uid = getUID(drv.device)

        // Drop a stale connection first, otherwise its release would delete the new port file
        entries.remove(uid)?.let { release(it, true) }

        try {
            val usbManager = mUsbManager ?: return
            port.open(usbManager.openDevice(drv.device))
            port.rts = true
            if (resetArduino) {
                port.dtr = true
            }
            port.setParameters(currentBaudRate.get(), UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open device ${drv.device}", e)
            try {
                port.close()
            } catch (ignored: Exception) {
            }
            retryConnect(drv.device, flags, attempt + 1)
            return
        }

        val nativePort = parked.remove(uid) ?: NativeSerialPort(uid)
        val entry = Entry(uid, port, nativePort)
        nativePort.setProxy { data ->
            try {
                port.write(data, 0)
                if (DEBUG) {
                    Log.d(TAG, "Write ${data.contentToString()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to USB serial", e)
                // This runs on the native port's own thread, which can't release itself
                ViewUtils.postOnMainThread { onPortFailed(uid, port) }
            }
        }
        val thread = ReadThread(uid, port, nativePort)
        entry.thread = thread
        ViewUtils.postOnMainThread { thread.start() }

        entries.put(uid, entry)
        if (DEBUG) {
            Log.d(TAG, "Connected ${nativePort.file}")
        }
    }

    // Opening can fail while the board is still starting up: try again a few times
    private fun retryConnect(device: UsbDevice, flags: Int, attempt: Int) {
        val delay = UsbReconnect.retryDelay(attempt - 1) ?: return
        ViewUtils.postOnMainThread({
            val manager = mUsbManager ?: return@postOnMainThread
            val current = manager.deviceList.values.firstOrNull { it.deviceName == device.deviceName }
                ?: return@postOnMainThread
            if (entries.get(getUID(current)) != null) return@postOnMainThread
            val drv = UsbSerialProber(KlipperProbeTable.getInstance()).probeDevice(current)
            if (drv != null && drv.ports.isNotEmpty()) {
                connect(drv, flags, attempt)
            }
        }, delay)
    }

    // The USB port broke under a reader or writer. Drop it and reconnect if the board is still there.
    private fun onPortFailed(uid: String, port: UsbSerialPort) {
        val entry = entries.get(uid)?.takeIf { it.port === port } ?: return
        if (!entries.removeIfCurrent(uid, entry)) return
        release(entry, true)
        retryConnect(port.device, 0, 1)
    }

    fun getDevice(uid: String): UsbDevice? = entries.get(uid)?.port?.device

    fun close(uid: String) {
        entries.remove(uid)?.let { release(it, false) }
        parked.remove(uid)?.release()
    }

    // [keepSerial] leaves the virtual serial port open for the board to come back to, when it comes back with the same name
    private fun release(entry: Entry, keepSerial: Boolean) {
        // Close USB first: a write still in flight on the serial port thread then fails fast
        try {
            entry.port.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close USB serial port", e)
        }
        if (keepSerial && UsbDeviceId.keepsNameOnReconnect(Prefs.usbDeviceNaming)) {
            entry.nativePort.setProxy { }
            parked.put(entry.uid, entry.nativePort)?.release()
        } else {
            entry.nativePort.release()
        }
        entry.thread.interrupt()
    }

    private class ReadThread(
        private val uid: String,
        private val port: UsbSerialPort,
        private val nativePort: NativeSerialPort
    ) : Thread() {
        private val buffer = ByteArray(16384)

        init {
            android.os.Process.setThreadPriority(-20)
        }

        override fun run() {
            while (!isInterrupted) {
                try {
                    val c = port.read(buffer, 0)
                    if (c > 0) {
                        nativePort.write(buffer, c)
                        if (DEBUG) {
                            Log.d(TAG, "Read ${buffer.contentToString()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading serial", e)
                    // Leave the loop: retrying a broken port only spins the CPU
                    onPortFailed(uid, port)
                    return
                }
            }
        }
    }
}
