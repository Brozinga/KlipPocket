package ru.ytkab0bp.beamklipper.firmware

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PICOBOOT, the USB protocol of the RP2040 / RP2350 boot ROM (BOOTSEL mode, 2e8a:0003 / 2e8a:000f).
 * Port of the commands used by Klipper's lib/rp2040_flash/picoboot_connection.c.
 */
class Picoboot private constructor(
    private val conn: UsbDeviceConnection,
    private val intf: UsbInterface,
    private val out: UsbEndpoint,
    private val inp: UsbEndpoint
) : AutoCloseable {
    private var token = 1

    fun reset() {
        // Vendor request, interface recipient: PICOBOOT_IF_RESET
        val r = conn.controlTransfer(0x41, IF_RESET, 0, intf.id, null, 0, 1000)
        if (r < 0) throw IOException("interface reset failed ($r)")
    }

    fun exclusiveAccess(exclusive: Boolean) = command(CMD_EXCLUSIVE_ACCESS, 1, 0, byteArrayOf(if (exclusive) 1 else 0))

    fun exitXip() = command(CMD_EXIT_XIP, 0, 0, ByteArray(0))

    fun flashErase(addr: Int, size: Int) = command(CMD_FLASH_ERASE, 8, 0, le(addr, size))

    fun write(addr: Int, data: ByteArray) {
        command(CMD_WRITE, 8, data.size, le(addr, data.size), data)
    }

    fun read(addr: Int, size: Int): ByteArray {
        val buf = ByteArray(size)
        command(CMD_READ, 8, size, le(addr, size), buf)
        return buf
    }

    fun reboot(pc: Int, sp: Int, delayMs: Int) = command(CMD_REBOOT, 12, 0, le(pc, sp, delayMs))

    /** RP2350 reboot (the RP2040 command is not available there): flags 0 = normal boot from flash. */
    fun reboot2(flags: Int, delayMs: Int, param0: Int, param1: Int) = command(CMD_REBOOT2, 16, 0, reboot2Args(flags, delayMs, param0, param1))

    private fun command(id: Int, argSize: Int, transfer: Int, args: ByteArray, data: ByteArray? = null) {
        bulk(out, buildCommand(token++, id, argSize, transfer, args), 32, TIMEOUT_MS, "send command")
        val isIn = (id and 0x80) != 0
        if (transfer > 0 && data != null) {
            if (isIn) bulk(inp, data, transfer, TIMEOUT_MS, "receive data")
            else bulk(out, data, transfer, TIMEOUT_MS, "send data")
        }
        // The acknowledgement travels in the opposite direction, zero length.
        val ack = ByteArray(64)
        val ackTimeout = if (transfer == 0) ERASE_TIMEOUT_MS else TIMEOUT_MS
        val r = conn.bulkTransfer(if (isIn) out else inp, ack, 1, ackTimeout)
        if (r < 0) throw IOException("no acknowledgement for command 0x${id.toString(16)}")
    }

    private fun bulk(ep: UsbEndpoint, buf: ByteArray, len: Int, timeout: Int, what: String) {
        var done = 0
        while (done < len) {
            val r = conn.bulkTransfer(ep, buf, done, minOf(len - done, 16384), timeout)
            if (r <= 0) throw IOException("USB $what failed ($r)")
            done += r
        }
    }

    override fun close() {
        try { conn.releaseInterface(intf) } catch (_: Throwable) {}
        conn.close()
    }

    companion object {
        const val VENDOR_RASPBERRY_PI = 0x2E8A
        const val PID_RP2040_BOOT = 0x0003
        const val PID_RP2350_BOOT = 0x000F
        const val FLASH_START = 0x10000000
        const val FLASH_MAX = 2 * 1024 * 1024
        const val SECTOR = 4096
        const val PAGE = 256

        private const val MAGIC = 0x431fd10b
        private const val IF_RESET = 0x41
        private const val CMD_EXCLUSIVE_ACCESS = 0x01
        private const val CMD_REBOOT = 0x02
        private const val CMD_REBOOT2 = 0x0A
        private const val CMD_FLASH_ERASE = 0x03
        private const val CMD_READ = 0x84
        private const val CMD_WRITE = 0x05
        private const val CMD_EXIT_XIP = 0x06
        private const val TIMEOUT_MS = 10_000
        private const val ERASE_TIMEOUT_MS = 20_000

        /** Which chip a boot ROM device (BOOTSEL) belongs to, or null when [dev] is not one. */
        fun chipOf(dev: UsbDevice): PicoChip? =
            if (dev.vendorId == VENDOR_RASPBERRY_PI) PicoChip.values().firstOrNull { it.bootPid == dev.productId } else null

        fun isBootsel(dev: UsbDevice) = chipOf(dev) != null

        /** [chipOf], or for a board running Klipper over USB the chip named in its product string ("rp2350"). */
        fun chipHint(dev: UsbDevice): PicoChip? = chipOf(dev)
            ?: if ((try { dev.productName } catch (_: SecurityException) { null })?.contains("rp2350", ignoreCase = true) == true) PicoChip.RP2350 else null

        internal const val CMD_REBOOT2_ID = 0x0A // same value as CMD_REBOOT2, visible to the tests

        /** picoboot_reboot2_cmd: dFlags, dDelayMS, dParam0, dParam1 (little endian). */
        internal fun reboot2Args(flags: Int, delayMs: Int, p0: Int, p1: Int) = le(flags, delayMs, p0, p1)

        /** The 32-byte PICOBOOT command packet. */
        internal fun buildCommand(token: Int, id: Int, argSize: Int, transfer: Int, args: ByteArray): ByteArray {
            val cmd = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            cmd.putInt(MAGIC).putInt(token).put(id.toByte()).put(argSize.toByte()).putShort(0).putInt(transfer)
            cmd.put(args)
            return cmd.array()
        }

        /** Claims the vendor interface of a BOOTSEL device. [conn] comes from UsbManager.openDevice. */
        fun open(dev: UsbDevice, conn: UsbDeviceConnection): Picoboot {
            for (i in 0 until dev.interfaceCount) {
                val intf = dev.getInterface(i)
                if (intf.interfaceClass != UsbConstants.USB_CLASS_VENDOR_SPEC || intf.endpointCount < 2) continue
                var o: UsbEndpoint? = null
                var n: UsbEndpoint? = null
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_OUT) o = ep else n = ep
                }
                if (o == null || n == null) continue
                if (!conn.claimInterface(intf, true)) throw IOException("could not claim the PICOBOOT interface")
                return Picoboot(conn, intf, o, n)
            }
            conn.close()
            throw IOException("PICOBOOT interface not found")
        }

        private fun le(vararg v: Int): ByteArray {
            val b = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            v.forEach { b.putInt(it) }
            return b.array()
        }
    }
}
