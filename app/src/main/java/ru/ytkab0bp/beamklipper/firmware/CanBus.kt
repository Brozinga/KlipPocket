package ru.ytkab0bp.beamklipper.firmware

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.io.IOException

class CanFrame(val id: Long, val data: ByteArray)

/** A CAN bus, so the bootloader conversation can run over a real adapter or a test double. */
interface CanBus {
    fun send(frame: CanFrame)
    /** The next frame seen on the bus; null when [timeoutMs] runs out. */
    fun receive(timeoutMs: Int): CanFrame?
}

/** Candlelight / gs_usb adapters (the Linux "gs_usb" driver), which includes Klipper's own USB-to-CAN bridge firmware. */
class GsUsbBus(
    private val conn: UsbDeviceConnection,
    private val intf: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint,
) : CanBus {
    class Timing(val propSeg: Int, val phaseSeg1: Int, val phaseSeg2: Int, val sjw: Int, val brp: Int)

    private fun control(out: Boolean, request: Int, data: ByteArray) {
        val r = conn.controlTransfer(if (out) 0x41 else 0xC1, request, 0, intf.id, data, data.size, 1000)
        if (r < 0) throw IOException("gs_usb request $request failed ($r)")
    }

    /** Resets the adapter, sets [bitrate] and starts it. */
    fun start(bitrate: Int, log: (String) -> Unit) {
        control(true, BREQ_HOST_FORMAT, le32(0x0000BEEF))
        val bt = ByteArray(40)
        control(false, BREQ_BT_CONST, bt)
        val feature = leInt(bt, 0)
        val clock = leInt(bt, 4)
        log("gs_usb adapter: ${clock / 1_000_000} MHz clock, features 0x${feature.toString(16)}")
        val timing = timing(bitrate, clock, IntArray(8) { leInt(bt, 8 + 4 * it).toInt() })
        control(true, BREQ_BITTIMING, le32(timing.propSeg.toLong()) + le32(timing.phaseSeg1.toLong()) + le32(timing.phaseSeg2.toLong()) +
                le32(timing.sjw.toLong()) + le32(timing.brp.toLong()))
        control(true, BREQ_MODE, le32(MODE_START) + le32(0))
    }

    fun stop() { try { control(true, BREQ_MODE, le32(MODE_RESET) + le32(0)) } catch (_: IOException) {} }

    override fun send(frame: CanFrame) {
        val b = ByteArray(FRAME)
        System.arraycopy(le32(0), 0, b, 0, 4)                                  // echo id
        System.arraycopy(le32(frame.id), 0, b, 4, 4)                           // 11-bit identifier
        b[8] = frame.data.size.toByte()
        System.arraycopy(frame.data, 0, b, 12, frame.data.size)
        val r = conn.bulkTransfer(output, b, b.size, 1000)
        if (r != b.size) throw IOException("gs_usb write failed ($r)")
    }

    override fun receive(timeoutMs: Int): CanFrame? {
        val end = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(maxOf(input.maxPacketSize, FRAME))
        while (true) {
            val left = (end - System.currentTimeMillis()).toInt()
            if (left <= 0) return null
            val r = conn.bulkTransfer(input, buf, buf.size, left)
            if (r < FRAME) continue
            if (leInt(buf, 0) != ECHO_NONE) continue                           // the echo of one of our own frames
            val len = minOf(buf[8].toInt() and 0x0F, 8)
            return CanFrame(leInt(buf, 4) and 0x1FFFFFFF, buf.copyOfRange(12, 12 + len))
        }
    }

    companion object {
        private const val BREQ_HOST_FORMAT = 0
        private const val BREQ_BITTIMING = 1
        private const val BREQ_MODE = 2
        private const val BREQ_BT_CONST = 4
        private const val MODE_RESET = 0L
        private const val MODE_START = 1L
        private const val FRAME = 20
        private const val ECHO_NONE = 0xFFFFFFFFL

        private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        private fun leInt(b: ByteArray, at: Int): Long =
            (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

        /**
         * Bit timing for [bitrate] on a controller clocked at [clock] Hz, aiming at an 87.5% sample point like the Linux CAN core.
         * [limits]: tseg1 min/max, tseg2 min/max, sjw max, brp min/max/increment, as reported by the adapter.
         */
        fun timing(bitrate: Int, clock: Long, limits: IntArray): Timing {
            val t1min = limits[0]; val t1max = limits[1]; val t2min = limits[2]; val t2max = limits[3]
            val sjwMax = limits[4]; val brpMin = limits[5]; val brpMax = limits[6]; val brpInc = limits[7]
            var best: Timing? = null
            var bestError = Double.MAX_VALUE
            var bestSample = Double.MAX_VALUE
            var brp = brpMin
            while (brp <= brpMax) {
                val tq = clock.toDouble() / brp / bitrate
                val total = Math.round(tq).toInt()
                if (Math.abs(tq - total) / tq < 0.001 && total >= 1 + t1min + t2min) {
                    // tseg1 + 1 (the sync segment) is the share of the bit before the sample point
                    var tseg1 = Math.round(total * 0.875).toInt() - 1
                    tseg1 = tseg1.coerceIn(t1min, t1max)
                    val tseg2 = total - 1 - tseg1
                    if (tseg2 in t2min..t2max) {
                        val sample = (1 + tseg1).toDouble() / total
                        val error = Math.abs(sample - 0.875)
                        if (error < bestError || (error == bestError && sample < bestSample)) {
                            bestError = error; bestSample = sample
                            best = Timing(tseg1 / 2, tseg1 - tseg1 / 2, tseg2, minOf(sjwMax, tseg2, 4).coerceAtLeast(1), brp)
                        }
                    }
                }
                brp += maxOf(brpInc, 1)
            }
            return best ?: throw FirmwareException("the CAN adapter cannot make ${bitrate} bit/s from its ${clock / 1_000_000} MHz clock")
        }

        /** Finds the vendor interface with a bulk IN and a bulk OUT endpoint, and claims it. */
        fun open(device: UsbDevice, conn: UsbDeviceConnection): GsUsbBus {
            for (i in 0 until device.interfaceCount) {
                val c = device.getInterface(i)
                if (c.interfaceClass != UsbConstants.USB_CLASS_VENDOR_SPEC) continue
                var inp: UsbEndpoint? = null
                var out: UsbEndpoint? = null
                for (e in 0 until c.endpointCount) {
                    val ep = c.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inp = ep else out = ep
                }
                if (inp == null || out == null) continue
                if (!conn.claimInterface(c, true)) throw IOException("could not claim the CAN adapter interface")
                return GsUsbBus(conn, c, inp, out)
            }
            throw IOException("this device is not a gs_usb CAN adapter")
        }
    }
}

/** One Katapult node on a [CanBus], as a byte stream: 8-byte frames to [txId], the answers come from [rxId]. */
class CanNodeLink(
    private val bus: CanBus,
    private val txId: Long,
    private val rxId: Long,
) : SerialLink {
    private val pending = java.util.ArrayDeque<Byte>()

    override fun write(data: ByteArray) {
        var i = 0
        while (i < data.size) {
            val n = minOf(8, data.size - i)
            bus.send(CanFrame(txId, data.copyOfRange(i, i + n)))
            i += n
        }
    }

    override fun read(n: Int, timeoutMs: Int): ByteArray {
        val end = System.currentTimeMillis() + timeoutMs
        while (pending.isEmpty()) {
            val left = (end - System.currentTimeMillis()).toInt()
            if (left <= 0) return ByteArray(0)
            val f = bus.receive(left) ?: return ByteArray(0)
            if (f.id == rxId) f.data.forEach { pending.add(it) }
        }
        val out = java.io.ByteArrayOutputStream()
        while (out.size() < n && pending.isNotEmpty()) out.write(pending.poll().toInt())
        return out.toByteArray()
    }

    override fun setLines(dtr: Boolean, rts: Boolean) {}
    override fun drain() {
        pending.clear()
        while (bus.receive(30) != null) { /* discard */ }
    }
}

/** Flashing a node over CAN through the Katapult bootloader, same sequence as Klipper's lib/canboot/flash_can.py. */
class CanKatapult(
    private val bus: CanBus,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val answerTimeoutMs: Int = 2000,
    private val queryMs: Int = 2000,
) {
    private fun admin(vararg data: Int) = bus.send(CanFrame(ADMIN_ID, ByteArray(data.size) { data[it].toByte() }))

    private fun uuidBytes(uuid: Long) = IntArray(6) { ((uuid shr ((5 - it) * 8)) and 0xFF).toInt() }

    /** UUIDs of the nodes that answered the "unassigned" query while sitting in Katapult. */
    private fun queryBootloaders(waitMs: Int, log: (String) -> Unit): List<Long> {
        admin(CMD_QUERY_UNASSIGNED)
        val found = ArrayList<Long>()
        val end = System.currentTimeMillis() + waitMs
        while (true) {
            val left = (end - System.currentTimeMillis()).toInt()
            if (left <= 0) break
            val f = bus.receive(left) ?: break
            if (f.id != ADMIN_RESP_ID || f.data.size != 8 || (f.data[0].toInt() and 0xFF) != RESP_NEED_NODEID) continue
            var uuid = 0L
            for (i in 1..6) uuid = (uuid shl 8) or (f.data[i].toLong() and 0xFF)
            val app = when (f.data[7].toInt() and 0xFF) { KLIPPER_SET_NODE -> "Klipper"; CMD_SET_NODEID -> "Katapult"; else -> "unknown" }
            log("detected UUID ${"%012x".format(uuid)}, application $app")
            if (app == "Katapult" && uuid !in found) found += uuid
        }
        return found
    }

    fun flash(uuid: Long, data: ByteArray, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        progress(FlashStage.ENTER_BOOTSEL, 0)
        // ask the Klipper node to reboot into Katapult; a node already there ignores it
        admin(KLIPPER_REBOOT_CMD, *uuidBytes(uuid))
        sleep(500)
        admin(CMD_CLEAR_NODEID)
        sleep(500)
        progress(FlashStage.WAIT_BOOTSEL, 0)
        val nodes = queryBootloaders(queryMs, log)
        if (uuid !in nodes) throw NoAnswerException("no Katapult node with the UUID ${"%012x".format(uuid)} answered on the CAN bus")
        val nodeId = 1 + NODEID_OFFSET
        admin(CMD_SET_NODEID, *uuidBytes(uuid), nodeId)
        val tx = nodeId * 2L + 0x100
        val link = CanNodeLink(bus, tx, tx + 1)
        sleep(500)
        KatapultFlasher(link, sleep, answerTimeoutMs).flash(data, progress, log, expectedUuid = uuid)
    }

    companion object {
        private const val ADMIN_ID = 0x3F0L
        private const val ADMIN_RESP_ID = 0x3F1L
        private const val KLIPPER_SET_NODE = 0x01
        private const val KLIPPER_REBOOT_CMD = 0x02
        private const val CMD_QUERY_UNASSIGNED = 0x00
        private const val CMD_SET_NODEID = 0x11
        private const val CMD_CLEAR_NODEID = 0x12
        private const val RESP_NEED_NODEID = 0x20
        private const val NODEID_OFFSET = 128

        /** "0123456789ab" -> the 48-bit CAN UUID Klipper reports (canbus_query.py); null when it is not 12 hex digits. */
        fun parseUuid(text: String): Long? {
            val t = text.trim().lowercase().removePrefix("0x")
            return if (t.length == 12 && t.all { it in '0'..'9' || it in 'a'..'f' }) t.toLong(16) else null
        }
    }
}
