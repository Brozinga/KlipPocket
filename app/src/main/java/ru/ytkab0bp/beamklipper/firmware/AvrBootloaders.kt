package ru.ytkab0bp.beamklipper.firmware

import java.io.IOException

private fun RawImage.pages(size: Int): List<Pair<Long, ByteArray>> {
    val out = ArrayList<Pair<Long, ByteArray>>()
    var off = 0
    while (off < data.size) {
        val chunk = data.copyOfRange(off, minOf(data.size, off + size))
        out += (address + off) to if (chunk.size == size) chunk else chunk + ByteArray(size - chunk.size) { 0xFF.toByte() }
        off += size
    }
    return out
}

/** avrdude "-c arduino": STK500v1 as spoken by Optiboot / the Arduino Uno and Nano bootloaders. */
class Stk500v1(private val link: SerialLink, private val sleep: (Long) -> Unit = { Thread.sleep(it) }) {
    private fun transact(req: ByteArray, replyData: Int = 0, timeoutMs: Int = 1000): ByteArray {
        link.write(req + byteArrayOf(0x20))
        val r = link.readExactly(2 + replyData, timeoutMs)
        if (r.size != 2 + replyData || r[0] != INSYNC || r.last() != OK)
            throw IOException("STK500 sync lost (command 0x${(req[0].toInt() and 0xFF).toString(16)})")
        return r.copyOfRange(1, 1 + replyData)
    }

    /** Pulses the reset line (Arduino auto-reset) and syncs with the bootloader. */
    fun connect() {
        link.setLines(false, false)
        sleep(250)
        link.setLines(true, true)
        sleep(50)
        link.drain()
        var ok = false
        for (i in 0 until 10) {
            link.drain()
            link.write(byteArrayOf(0x30, 0x20))
            val r = link.readExactly(2, 300)
            if (r.size == 2 && r[0] == INSYNC && r[1] == OK) { ok = true; break }
            sleep(100)
        }
        if (!ok) throw NoAnswerException("the AVR bootloader did not answer (reset line / baud rate?)")
    }

    fun flash(image: RawImage, verify: Boolean, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        progress(FlashStage.HANDSHAKE, 0)
        connect()
        transact(byteArrayOf(0x50)) // enter programming mode
        progress(FlashStage.HANDSHAKE, 100)
        val pages = image.pages(PAGE)
        pages.forEachIndexed { i, (addr, data) ->
            load(addr)
            transact(byteArrayOf(0x64, (PAGE shr 8).toByte(), PAGE.toByte(), 'F'.code.toByte()) + data)
            progress(FlashStage.WRITE, (i + 1) * 100 / pages.size)
        }
        if (verify) {
            pages.forEachIndexed { i, (addr, data) ->
                load(addr)
                val got = transact(byteArrayOf(0x74, (PAGE shr 8).toByte(), PAGE.toByte(), 'F'.code.toByte()), PAGE)
                if (!got.contentEquals(data)) throw FirmwareException("the flash differs from the file near 0x${addr.toString(16)}")
                progress(FlashStage.VERIFY, (i + 1) * 100 / pages.size)
            }
            log("verify OK")
        }
        progress(FlashStage.REBOOT, 0)
        transact(byteArrayOf(0x51)) // leave programming mode: the bootloader starts the application
    }

    private fun load(byteAddr: Long) {
        val w = (byteAddr / 2).toInt()
        transact(byteArrayOf(0x55, w.toByte(), (w shr 8).toByte()))
    }

    companion object {
        const val INSYNC = 0x14.toByte()
        const val OK = 0x10.toByte()
        const val PAGE = 128
    }
}

/** avrdude "-c wiring": STK500v2 framing as used by the ATmega2560 (Arduino Mega) bootloader. */
class Stk500v2(private val link: SerialLink, private val sleep: (Long) -> Unit = { Thread.sleep(it) }) {
    private var seq = 1

    private fun message(body: ByteArray, timeoutMs: Int = 2000): ByteArray {
        val head = byteArrayOf(0x1B, seq.toByte(), (body.size shr 8).toByte(), body.size.toByte(), 0x0E)
        val frame = head + body
        var cs = 0
        for (b in frame) cs = cs xor (b.toInt() and 0xFF)
        link.write(frame + byteArrayOf(cs.toByte()))
        val h = link.readExactly(5, timeoutMs)
        if (h.size != 5 || h[0] != 0x1B.toByte() || h[4] != 0x0E.toByte()) throw IOException("no STK500v2 answer")
        val len = ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
        val rest = link.readExactly(len + 1, timeoutMs)
        if (rest.size != len + 1) throw IOException("short STK500v2 answer")
        var c = 0
        for (b in h) c = c xor (b.toInt() and 0xFF)
        for (i in 0 until len) c = c xor (rest[i].toInt() and 0xFF)
        if (c.toByte() != rest[len]) throw IOException("bad STK500v2 checksum")
        if ((h[1].toInt() and 0xFF) != seq) throw IOException("STK500v2 sequence mismatch")
        seq = (seq + 1) and 0xFF
        val answer = rest.copyOf(len)
        if (answer.isEmpty() || answer[0] != body[0]) throw IOException("unexpected STK500v2 answer")
        if (answer.size > 1 && answer[1] != 0.toByte()) throw IOException("STK500v2 command failed (status ${answer[1]})")
        return answer
    }

    fun connect() {
        link.setLines(false, false)
        sleep(250)
        link.setLines(true, true)
        sleep(50)
        var ok = false
        for (i in 0 until 10) {
            link.drain()
            seq = 1
            try { message(byteArrayOf(0x01), 400); ok = true; break } catch (_: IOException) { sleep(100) }
        }
        if (!ok) throw NoAnswerException("the AVR (wiring / STK500v2) bootloader did not answer")
    }

    private fun load(byteAddr: Long) {
        val w = (byteAddr / 2) or 0x80000000L // extended addressing, as avrdude does for the ATmega2560
        message(byteArrayOf(0x06, (w shr 24).toByte(), (w shr 16).toByte(), (w shr 8).toByte(), w.toByte()))
    }

    fun flash(image: RawImage, verify: Boolean, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        progress(FlashStage.HANDSHAKE, 0)
        connect()
        progress(FlashStage.HANDSHAKE, 100)
        val pages = image.pages(PAGE)
        pages.forEachIndexed { i, (addr, data) ->
            load(addr)
            message(byteArrayOf(0x13, (PAGE shr 8).toByte(), PAGE.toByte(), 0x41, 0x06, 0x40, 0x4C, 0x20, 0xFF.toByte(), 0xFF.toByte()) + data, 5000)
            progress(FlashStage.WRITE, (i + 1) * 100 / pages.size)
        }
        if (verify) {
            pages.forEachIndexed { i, (addr, data) ->
                load(addr)
                val ans = message(byteArrayOf(0x14, (PAGE shr 8).toByte(), PAGE.toByte(), 0x20), 5000)
                // answer: id, status, data..., status
                val got = ans.copyOfRange(2, ans.size - 1)
                if (!got.contentEquals(data)) throw FirmwareException("the flash differs from the file near 0x${addr.toString(16)}")
                progress(FlashStage.VERIFY, (i + 1) * 100 / pages.size)
            }
            log("verify OK")
        }
        progress(FlashStage.REBOOT, 0)
        message(byteArrayOf(0x11, 0x01, 0x01)) // leave programming mode
    }

    companion object { const val PAGE = 256 }
}

/** avrdude "-c avr109": the Caterina bootloader of ATmega32U4 / AT90USB boards, reached with a 1200 bps request. */
class Avr109(private val link: SerialLink, private val sleep: (Long) -> Unit = { Thread.sleep(it) }) {
    private fun expectCr(what: String) {
        val r = link.readExactly(1, 2000)
        if (r.size != 1 || r[0] != '\r'.code.toByte()) throw IOException("AVR109 bootloader did not confirm ($what)")
    }

    fun flash(image: RawImage, verify: Boolean, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        progress(FlashStage.HANDSHAKE, 0)
        link.drain()
        link.write(byteArrayOf('S'.code.toByte()))
        val id = link.readExactly(7, 2000)
        if (id.size != 7) throw NoAnswerException("the AVR109 bootloader did not answer")
        log("bootloader id: " + String(id))
        link.write(byteArrayOf('b'.code.toByte()))
        val blk = link.readExactly(3, 1000)
        val blockSize = if (blk.size == 3 && blk[0] == 'Y'.code.toByte()) ((blk[1].toInt() and 0xFF) shl 8) or (blk[2].toInt() and 0xFF) else 128
        if (blockSize <= 0 || blockSize > 1024) throw IOException("unsupported AVR109 block size $blockSize")
        link.write(byteArrayOf('P'.code.toByte())); expectCr("enter programming mode")
        progress(FlashStage.HANDSHAKE, 100)
        val pages = image.pages(blockSize)
        pages.forEachIndexed { i, (addr, data) ->
            setAddress(addr)
            link.write(byteArrayOf('B'.code.toByte(), (blockSize shr 8).toByte(), blockSize.toByte(), 'F'.code.toByte()) + data)
            expectCr("block at 0x${addr.toString(16)}")
            progress(FlashStage.WRITE, (i + 1) * 100 / pages.size)
        }
        if (verify) {
            pages.forEachIndexed { i, (addr, data) ->
                setAddress(addr)
                link.write(byteArrayOf('g'.code.toByte(), (blockSize shr 8).toByte(), blockSize.toByte(), 'F'.code.toByte()))
                val got = link.readExactly(blockSize, 2000)
                if (!got.contentEquals(data)) throw FirmwareException("the flash differs from the file near 0x${addr.toString(16)}")
                progress(FlashStage.VERIFY, (i + 1) * 100 / pages.size)
            }
            log("verify OK")
        }
        progress(FlashStage.REBOOT, 0)
        link.write(byteArrayOf('L'.code.toByte())); expectCr("leave programming mode")
        link.write(byteArrayOf('E'.code.toByte()))
        try { expectCr("exit bootloader") } catch (_: IOException) { /* it may already have reset */ }
    }

    private fun setAddress(byteAddr: Long) {
        val w = (byteAddr / 2).toInt()
        link.write(byteArrayOf('A'.code.toByte(), (w shr 8).toByte(), w.toByte()))
        expectCr("set address")
    }
}
