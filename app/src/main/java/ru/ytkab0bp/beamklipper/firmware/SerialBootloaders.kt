package ru.ytkab0bp.beamklipper.firmware

import java.io.IOException

/** The bootloader did not answer at all (wrong mode, wiring, reset line or baud rate). */
class NoAnswerException(message: String) : IOException(message)

/** A serial line, so the bootloader protocols can run over a real USB-serial adapter or a test double. */
interface SerialLink {
    fun write(data: ByteArray)
    /** Reads up to [n] bytes; returns fewer (possibly none) when [timeoutMs] runs out. */
    fun read(n: Int, timeoutMs: Int): ByteArray
    fun setLines(dtr: Boolean, rts: Boolean)
    fun drain()
}

internal fun SerialLink.readExactly(n: Int, timeoutMs: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val end = System.currentTimeMillis() + timeoutMs
    while (out.size() < n) {
        val left = (end - System.currentTimeMillis()).toInt()
        if (left <= 0) break
        out.write(read(n - out.size(), left))
    }
    return out.toByteArray()
}

/** STM32 system bootloader over UART (ST application note AN3155; 115200 8E1). Same sequence as stm32flash -w -v -g. */
class Stm32SerialBootloader(
    private val link: SerialLink,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private fun ack(timeoutMs: Int = 2000, what: String): Unit {
        val b = link.readExactly(1, timeoutMs)
        if (b.isEmpty()) throw IOException("no answer from the bootloader ($what)")
        if (b[0] == NACK) throw IOException("the bootloader refused the request ($what)")
        if (b[0] != ACK) throw IOException("unexpected byte 0x${(b[0].toInt() and 0xFF).toString(16)} ($what)")
    }

    private fun command(cmd: Int) {
        link.write(byteArrayOf(cmd.toByte(), (cmd xor 0xFF).toByte()))
        ack(what = "command 0x${cmd.toString(16)}")
    }

    private fun address(addr: Long) {
        val a = byteArrayOf((addr shr 24).toByte(), (addr shr 16).toByte(), (addr shr 8).toByte(), addr.toByte())
        link.write(a + byteArrayOf((a[0].toInt() xor a[1].toInt() xor a[2].toInt() xor a[3].toInt()).toByte()))
        ack(what = "address")
    }

    /** Returns the command list reported by the bootloader. */
    fun connect(): Set<Int> {
        var alive = false
        for (attempt in 0 until 5) {
            link.drain()
            link.write(byteArrayOf(0x7F))
            val b = link.readExactly(1, 1000)
            if (b.isNotEmpty() && (b[0] == ACK || b[0] == NACK)) { alive = true; break }
            sleep(200)
        }
        if (!alive) throw NoAnswerException("the STM32 bootloader did not answer (BOOT0 high and reset? wiring?)")
        command(0x00)
        val n = link.readExactly(1, 1000).firstOrNull()?.toInt()?.and(0xFF) ?: throw IOException("short Get answer")
        val body = link.readExactly(n + 1, 1000)
        if (body.size != n + 1) throw IOException("short Get answer")
        ack(what = "Get")
        return body.drop(1).map { it.toInt() and 0xFF }.toSet()
    }

    fun massErase(commands: Set<Int>) {
        if (0x44 in commands) {
            command(0x44)
            link.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00))
        } else {
            command(0x43)
            link.write(byteArrayOf(0xFF.toByte(), 0x00))
        }
        ack(60_000, "mass erase")
    }

    fun write(addr: Long, data: ByteArray) {
        command(0x31)
        address(addr)
        val padded = if (data.size % 4 == 0) data else data + ByteArray(4 - data.size % 4) { 0xFF.toByte() }
        val n = (padded.size - 1).toByte()
        var cs = n.toInt() and 0xFF
        for (b in padded) cs = cs xor (b.toInt() and 0xFF)
        link.write(byteArrayOf(n) + padded + byteArrayOf(cs.toByte()))
        ack(5000, "write at 0x${addr.toString(16)}")
    }

    fun read(addr: Long, size: Int): ByteArray {
        command(0x11)
        address(addr)
        link.write(byteArrayOf((size - 1).toByte(), ((size - 1) xor 0xFF).toByte()))
        ack(what = "read length")
        val d = link.readExactly(size, 3000)
        if (d.size != size) throw IOException("short read from the bootloader")
        return d
    }

    fun go(addr: Long) {
        command(0x21)
        address(addr)
    }

    fun flash(image: RawImage, verify: Boolean, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        progress(FlashStage.HANDSHAKE, 0)
        val cmds = connect()
        log("bootloader commands: " + cmds.joinToString(",") { "0x" + it.toString(16) })
        if (0x31 !in cmds) throw IOException("this bootloader has no Write Memory command (read-protected chip?)")
        progress(FlashStage.HANDSHAKE, 100)
        massErase(cmds)
        progress(FlashStage.ERASE, 100)
        val blocks = (image.data.size + 255) / 256
        for (i in 0 until blocks) {
            val from = i * 256
            write(image.address + from, image.data.copyOfRange(from, minOf(image.data.size, from + 256)))
            progress(FlashStage.WRITE, (i + 1) * 100 / blocks)
        }
        if (verify && 0x11 in cmds) {
            for (i in 0 until blocks) {
                val from = i * 256
                val want = image.data.copyOfRange(from, minOf(image.data.size, from + 256))
                val got = read(image.address + from, want.size)
                if (!got.contentEquals(want)) throw FirmwareException("the flash differs from the file near 0x${(image.address + from).toString(16)}")
                progress(FlashStage.VERIFY, (i + 1) * 100 / blocks)
            }
            log("verify OK")
        }
        progress(FlashStage.REBOOT, 0)
        go(image.address)
    }

    companion object {
        const val ACK = 0x79.toByte()
        const val NACK = 0x1F.toByte()
    }
}
