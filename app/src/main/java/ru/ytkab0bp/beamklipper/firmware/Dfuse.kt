package ru.ytkab0bp.beamklipper.firmware

import android.hardware.usb.UsbDeviceConnection
import java.io.IOException

/** The two DFU control-transfer directions, so the protocol can be tested without a USB stack. */
interface DfuLink {
    fun out(request: Int, value: Int, data: ByteArray?)
    fun inn(request: Int, value: Int, length: Int): ByteArray
}

class UsbDfuLink(private val conn: UsbDeviceConnection, private val interfaceId: Int) : DfuLink {
    override fun out(request: Int, value: Int, data: ByteArray?) {
        val r = conn.controlTransfer(0x21, request, value, interfaceId, data, data?.size ?: 0, 5000)
        if (r < 0) throw IOException("DFU request $request failed ($r)")
    }

    override fun inn(request: Int, value: Int, length: Int): ByteArray {
        val buf = ByteArray(length)
        val r = conn.controlTransfer(0xA1, request, value, interfaceId, buf, length, 5000)
        if (r < 0) throw IOException("DFU request $request failed ($r)")
        return buf.copyOf(r)
    }
}

class DfuseSector(val start: Long, val size: Int, val erasable: Boolean)

/** ST's DfuSe extension of USB DFU (STM32 system bootloader, 0483:df11). Same sequence as dfu-util. */
class DfuseFlasher(
    private val link: DfuLink,
    private val transferSize: Int,
    private val sectors: List<DfuseSector>,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private class Status(val status: Int, val pollMs: Long, val state: Int)

    private fun status(): Status {
        val b = link.inn(GETSTATUS, 0, 6)
        if (b.size < 6) throw IOException("short DFU status")
        return Status(b[0].toInt() and 0xFF,
            (b[1].toLong() and 0xFF) or ((b[2].toLong() and 0xFF) shl 8) or ((b[3].toLong() and 0xFF) shl 16),
            b[4].toInt() and 0xFF)
    }

    private fun toIdle() {
        var s = status()
        if (s.state == STATE_ERROR) { link.out(CLRSTATUS, 0, null); s = status() }
        if (s.state != STATE_IDLE) { link.out(ABORT, 0, null); s = status() }
        if (s.state != STATE_IDLE) throw IOException("the DFU device is not idle (state ${s.state})")
    }

    /** DNLOAD followed by the two status polls the DFU state machine needs. */
    private fun download(block: Int, data: ByteArray) {
        link.out(DNLOAD, block, data)
        val busy = status()
        sleep(busy.pollMs)
        val done = status()
        if (done.status != 0 || done.state == STATE_ERROR) throw IOException("DFU error status ${done.status}")
    }

    private fun special(cmd: Int, addr: Long) = download(0, byteArrayOf(cmd.toByte(),
        addr.toByte(), (addr shr 8).toByte(), (addr shr 16).toByte(), (addr shr 24).toByte()))

    /** [progress] gets (stage, 0..100). */
    fun flash(image: RawImage, verify: Boolean, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        val start = image.address
        val end = start + image.data.size
        val toErase = sectors.filter { it.erasable && it.start < end && it.start + it.size > start }
        if (toErase.isEmpty()) throw FirmwareException("the address range is not erasable flash of this device")
        if (sectors.none { it.start <= start && start < it.start + it.size })
            throw FirmwareException("0x${start.toString(16)} is outside the flash of this device")
        toIdle()
        progress(FlashStage.HANDSHAKE, 100)
        toErase.forEachIndexed { i, s ->
            special(CMD_ERASE, s.start)
            progress(FlashStage.ERASE, (i + 1) * 100 / toErase.size)
        }
        special(CMD_SET_ADDRESS, start)
        val blocks = (image.data.size + transferSize - 1) / transferSize
        for (i in 0 until blocks) {
            val from = i * transferSize
            download(2 + i, image.data.copyOfRange(from, minOf(image.data.size, from + transferSize)))
            progress(FlashStage.WRITE, (i + 1) * 100 / blocks)
        }
        if (verify) {
            try {
                special(CMD_SET_ADDRESS, start)
                toIdle()
                for (i in 0 until blocks) {
                    val from = i * transferSize
                    val want = image.data.copyOfRange(from, minOf(image.data.size, from + transferSize))
                    val got = link.inn(UPLOAD, 2 + i, want.size)
                    if (got.size < want.size || !got.copyOf(want.size).contentEquals(want))
                        throw FirmwareException("the flash differs from the file near 0x${(start + from).toString(16)}")
                    progress(FlashStage.VERIFY, (i + 1) * 100 / blocks)
                }
                log("verify OK")
            } catch (e: IOException) {
                log("read-back is not supported by this bootloader (${e.message}); the write itself was acknowledged")
            }
        }
        progress(FlashStage.REBOOT, 0)
        // Leave DFU: point at the application and send a zero-length download; the device resets itself.
        special(CMD_SET_ADDRESS, start)
        link.out(DNLOAD, 0, ByteArray(0))
        try { status(); status() } catch (_: IOException) { /* it already reset */ }
    }

    companion object {
        private const val DNLOAD = 1
        private const val UPLOAD = 2
        private const val GETSTATUS = 3
        private const val CLRSTATUS = 4
        private const val ABORT = 6
        private const val STATE_IDLE = 2
        private const val STATE_ERROR = 10
        private const val CMD_SET_ADDRESS = 0x21
        private const val CMD_ERASE = 0x41

        /** Parses the DfuSe memory layout string, e.g. "@Internal Flash /0x08000000/04*016Kg,01*064Kg,03*128Kg". */
        fun parseLayout(name: String): List<DfuseSector> {
            val parts = name.trim().split('/')
            val out = ArrayList<DfuseSector>()
            var i = 1
            while (i + 1 < parts.size) {
                var addr = parts[i].trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: return emptyList()
                for (grp in parts[i + 1].split(',')) {
                    val m = Regex("""(\d+)\*(\d+)([ BKM]?)([a-g])""").matchEntire(grp.trim()) ?: return emptyList()
                    val count = m.groupValues[1].toInt()
                    val unit = when (m.groupValues[3]) { "K" -> 1024; "M" -> 1024 * 1024; else -> 1 }
                    val size = m.groupValues[2].toInt() * unit
                    val erasable = (m.groupValues[4][0] - 'a' + 1) and 2 != 0
                    repeat(count) { out += DfuseSector(addr, size, erasable); addr += size }
                }
                i += 2
            }
            return out
        }
    }
}
