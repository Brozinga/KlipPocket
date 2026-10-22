package ru.ytkab0bp.beamklipper.firmware

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Katapult (formerly CanBoot) bootloader protocol over a byte stream, same sequence as Klipper's lib/canboot/flash_can.py:
 * connect, write the blocks, check the stream with SHA-1, then tell the bootloader to start the application.
 * The stream is a USB CDC / UART line, or the CAN node behind [CanNodeLink].
 */
class KatapultFlasher(
    private val link: SerialLink,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val answerTimeoutMs: Int = 2000,
) {
    class Info(val protocol: String, val appStart: Long, val blockSize: Int, val mcu: String)

    private fun crc16(buf: ByteArray, from: Int, to: Int): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            var d = (buf[i].toInt() and 0xFF) xor (crc and 0xFF)
            d = d xor ((d and 0x0F) shl 4)
            crc = ((d shl 8) or (crc shr 8)) xor (d shr 4) xor (d shl 3)
            crc = crc and 0xFFFF
        }
        return crc
    }

    private fun frame(cmd: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(HEADER)
        out.write(cmd)
        out.write((payload.size / 4) and 0xFF)
        out.write(payload)
        val body = out.toByteArray()
        val crc = crc16(body, 2, body.size)
        out.write(crc and 0xFF)
        out.write(crc shr 8)
        out.write(TRAILER)
        return out.toByteArray()
    }

    /** Reads one complete answer frame, dropping bytes until the header shows up. */
    private fun readFrame(timeoutMs: Int): ByteArray {
        var data = ByteArray(0)
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val more = link.read(256, 100)
            if (more.isNotEmpty()) data += more
            while (data.size > 7) {
                if (data[0] != HEADER[0] || data[1] != HEADER[1]) { data = data.copyOfRange(1, data.size); continue }
                val total = (data[3].toInt() and 0xFF) * 4 + 8
                if (data.size >= total) return data.copyOf(total)
                break
            }
        }
        throw IOException("no complete answer from the bootloader")
    }

    private fun command(name: String, cmd: Int, payload: ByteArray = ByteArray(0), tries: Int = 5): ByteArray {
        var last = "no answer"
        for (attempt in 0 until tries) {
            try {
                link.write(frame(cmd, payload))
                val r = readFrame(answerTimeoutMs)
                val len = (r[3].toInt() and 0xFF) * 4
                val crc = (r[r.size - 4].toInt() and 0xFF) or ((r[r.size - 3].toInt() and 0xFF) shl 8)
                val answered = if (len >= 4) leInt(r, 4).toInt() else 0
                when {
                    r[r.size - 2] != TRAILER[0] || r[r.size - 1] != TRAILER[1] -> last = "bad frame trailer"
                    crc != crc16(r, 2, r.size - 4) -> last = "frame CRC mismatch"
                    r[2] != ACK_SUCCESS -> last = "NACK"
                    answered != cmd -> last = "the answer was for another command (0x${answered.toString(16)})"
                    else -> return if (len <= 4) ByteArray(0) else r.copyOfRange(8, len + 4)
                }
            } catch (e: IOException) {
                last = e.message.orEmpty()
            }
            link.drain()
            sleep(100)
        }
        throw NoAnswerException("Katapult did not accept the $name command ($last)")
    }

    private fun leInt(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    fun connect(): Info {
        val r = command("CONNECT", CMD_CONNECT)
        if (r.size < 12) throw IOException("short CONNECT answer from the bootloader")
        val block = leInt(r, 8).toInt()
        if (block !in intArrayOf(64, 128, 256, 512)) throw IOException("the bootloader reports an invalid block size ($block)")
        val mcu = String(r, 12, r.size - 12, Charsets.US_ASCII).trimEnd('\u0000')
        return Info("${r[2].toInt() and 0xFF}.${r[1].toInt() and 0xFF}.${r[0].toInt() and 0xFF}", leInt(r, 4), block, mcu)
    }

    /** The CAN node answers GET_CANBUS_ID with its 6-byte UUID. */
    fun canbusUuid(): Long {
        val r = command("GET_CANBUS_ID", CMD_GET_CANBUS_ID)
        if (r.size < 6) throw IOException("short GET_CANBUS_ID answer")
        var uuid = 0L
        for (i in 0 until 6) uuid = (uuid shl 8) or (r[i].toLong() and 0xFF)
        return uuid
    }

    fun complete() { command("COMPLETE", CMD_COMPLETE) }

    /** Writes [data] (raw .bin, padded to whole blocks with 0xFF), verifies it and starts the application. */
    fun flash(data: ByteArray, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit, expectedUuid: Long? = null) {
        progress(FlashStage.HANDSHAKE, 0)
        val info = connect()
        log("Katapult ${info.protocol}, mcu ${info.mcu}, application at 0x${info.appStart.toString(16)}, block ${info.blockSize} bytes")
        if (expectedUuid != null && canbusUuid() != expectedUuid) throw FirmwareException("the bootloader answered with another CAN UUID")
        progress(FlashStage.HANDSHAKE, 100)
        var failed = true
        try {
            val blocks = (data.size + info.blockSize - 1) / info.blockSize
            val sha = MessageDigest.getInstance("SHA-1")
            for (i in 0 until blocks) {
                val from = i * info.blockSize
                val block = ByteArray(info.blockSize) { 0xFF.toByte() }
                System.arraycopy(data, from, block, 0, minOf(info.blockSize, data.size - from))
                sha.update(block)
                val addr = info.appStart + from
                var ok = false
                for (t in 0 until 3) {
                    val r = command("SEND_BLOCK", CMD_SEND_BLOCK, le32(addr) + block)
                    if (r.size >= 4 && leInt(r, 0) == addr) { ok = true; break }
                    sleep(100)
                }
                if (!ok) throw IOException("the flash write failed at 0x${addr.toString(16)}")
                progress(FlashStage.WRITE, (i + 1) * 100 / blocks)
            }
            val pages = command("SEND_EOF", CMD_SEND_EOF)
            log("write complete: ${if (pages.size >= 4) leInt(pages, 0) else 0} pages")
            val check = MessageDigest.getInstance("SHA-1")
            for (i in 0 until blocks) {
                val addr = info.appStart + i.toLong() * info.blockSize
                var got: ByteArray? = null
                for (t in 0 until 3) {
                    val r = command("REQUEST_BLOCK", CMD_REQUEST_BLOCK, le32(addr))
                    if (r.size >= 4 && leInt(r, 0) == addr) { got = r.copyOfRange(4, r.size); break }
                    sleep(100)
                }
                check.update(got ?: throw IOException("could not read back the block at 0x${addr.toString(16)}"))
                progress(FlashStage.VERIFY, (i + 1) * 100 / blocks)
            }
            if (!MessageDigest.isEqual(sha.digest(), check.digest())) throw FirmwareException("the flash differs from the file (SHA-1 mismatch)")
            log("verify OK")
            failed = false
        } finally {
            // Always send COMPLETE: on an error it makes the bootloader leave without a half-written application.
            progress(FlashStage.REBOOT, 0)
            try { complete() } catch (e: IOException) { if (!failed) throw e }
        }
    }

    companion object {
        private val HEADER = byteArrayOf(0x01, 0x88.toByte())
        private val TRAILER = byteArrayOf(0x99.toByte(), 0x03)
        private const val CMD_CONNECT = 0x11
        private const val CMD_SEND_BLOCK = 0x12
        private const val CMD_SEND_EOF = 0x13
        private const val CMD_REQUEST_BLOCK = 0x14
        private const val CMD_COMPLETE = 0x15
        private const val CMD_GET_CANBUS_ID = 0x16
        private const val ACK_SUCCESS = 0xA0.toByte()
    }
}
