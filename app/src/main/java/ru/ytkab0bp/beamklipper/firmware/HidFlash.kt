package ru.ytkab0bp.beamklipper.firmware

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.io.IOException

/** The 64-byte HID reports of the STM32 HID bootloader, so the protocol can be tested without a USB stack. */
interface HidLink {
    /** Sends one 64-byte output report (no report id). */
    fun write(report: ByteArray)
    /** Waits for one input report; null when [timeoutMs] runs out. */
    fun read(timeoutMs: Int): ByteArray?
}

class UsbHidLink(
    private val conn: UsbDeviceConnection,
    private val intf: UsbInterface,
    private val out: UsbEndpoint?,
    private val input: UsbEndpoint?,
) : HidLink {
    override fun write(report: ByteArray) {
        val r = if (out != null) conn.bulkTransfer(out, report, report.size, 1000)
        // SET_REPORT (output report, id 0) when the interface has no interrupt OUT endpoint
        else conn.controlTransfer(0x21, 0x09, 0x0200, intf.id, report, report.size, 1000)
        if (r < 0) throw IOException("USB HID write failed ($r)")
    }

    override fun read(timeoutMs: Int): ByteArray? {
        if (input == null) return null
        val buf = ByteArray(maxOf(input.maxPacketSize, 8))
        val r = conn.bulkTransfer(input, buf, buf.size, timeoutMs)
        return if (r > 0) buf.copyOf(r) else null
    }

    companion object {
        /** Finds the HID interface and its interrupt endpoints. */
        fun open(device: UsbDevice, conn: UsbDeviceConnection): UsbHidLink {
            for (i in 0 until device.interfaceCount) {
                val c = device.getInterface(i)
                if (c.interfaceClass != UsbConstants.USB_CLASS_HID) continue
                var out: UsbEndpoint? = null
                var inp: UsbEndpoint? = null
                for (e in 0 until c.endpointCount) {
                    val ep = c.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_INT) continue
                    if (ep.direction == UsbConstants.USB_DIR_OUT) out = ep else inp = ep
                }
                if (!conn.claimInterface(c, true)) throw IOException("could not claim the HID interface")
                return UsbHidLink(conn, c, out, inp)
            }
            throw IOException("no HID interface on this device")
        }
    }
}

/** STM32F1/F4 "HID bootloader" (1209:beba), same sequence as Klipper's lib/hidflash/hid-flash: 1 KiB sectors in 64-byte reports. */
class HidFlasher(
    private val link: HidLink,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val ackTimeoutMs: Int = 10_000,
) {
    private fun command(code: Int): ByteArray = ByteArray(REPORT).also {
        "BTLDCMD".forEachIndexed { i, ch -> it[i] = ch.code.toByte() }
        it[7] = code.toByte()
    }

    private fun send(report: ByteArray) {
        var failure: IOException? = null
        // "Flash is unavailable when writing to it, so the USB transfer may fail": retry, as hid-flash does
        for (attempt in 0 until 20) {
            try { link.write(report); return } catch (e: IOException) { failure = e; sleep(100) }
        }
        throw failure ?: IOException("USB HID write failed")
    }

    /** Waits for the "sector accepted" report: byte 7 is 0x02 (page written) or 0x03 (half of a 2 KiB page buffered). */
    private fun ack(): Int {
        val end = System.currentTimeMillis() + ackTimeoutMs
        while (System.currentTimeMillis() < end) {
            val r = link.read(500)
            if (r != null && r.size > 7 && (r[7].toInt() and 0xFE) == 0x02) return r[7].toInt() and 0xFF
        }
        throw NoAnswerException("the HID bootloader did not acknowledge the sector")
    }

    fun flash(data: ByteArray, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        if (data.isEmpty()) throw FirmwareException("the file is empty")
        progress(FlashStage.HANDSHAKE, 0)
        send(command(CMD_RESET_PAGES))
        progress(FlashStage.HANDSHAKE, 100)
        var sent = 0
        var offset = 0
        var last = 0
        while (true) {
            val sector = ByteArray(SECTOR)
            if (offset < data.size) System.arraycopy(data, offset, sector, 0, minOf(SECTOR, data.size - offset))
            offset += SECTOR
            for (i in 0 until SECTOR step REPORT) {
                send(sector.copyOfRange(i, i + REPORT))
                sent += REPORT
                sleep(1)
            }
            last = ack()
            progress(FlashStage.WRITE, minOf(100, minOf(offset, data.size) * 100 / data.size))
            // 2 KiB-page chips answer 0x03 after the first half: send zeros until the page is complete
            if (offset >= data.size && (last != 0x03 || sent % 2048 == 0)) break
        }
        log("sent $sent bytes in ${sent / SECTOR} sectors, last acknowledgement 0x${last.toString(16)}")
        progress(FlashStage.REBOOT, 0)
        try { send(command(CMD_REBOOT)) } catch (e: IOException) { log("the reboot command failed: ${e.message}") }
    }

    companion object {
        const val VENDOR = 0x1209
        const val PRODUCT = 0xBEBA
        private const val REPORT = 64
        private const val SECTOR = 1024
        private const val CMD_RESET_PAGES = 0x00
        private const val CMD_REBOOT = 0x01
    }
}
