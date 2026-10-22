package ru.ytkab0bp.beamklipper.firmware

import java.io.IOException

/** The SAM-BA ROM monitor of the Atmel SAM3/SAM4/SAME70 over USB, as bossac speaks it (text commands, binary payloads). */
class SamBaMonitor(private val link: SerialLink) {
    private fun cmd(text: String) = link.write(text.toByteArray(Charsets.US_ASCII))

    /** "N#" selects the binary mode; "V#" must answer with the monitor version. */
    fun connect(): String {
        link.drain()
        cmd("N#")
        link.readExactly(2, 500)
        cmd("V#")
        // the answer ends with "\n\r"; take all of it, or it would shift every later answer
        val v = java.io.ByteArrayOutputStream()
        val end = System.currentTimeMillis() + 500
        while (System.currentTimeMillis() < end && !v.toString(Charsets.US_ASCII).endsWith("\n\r")) v.write(link.read(255, 100))
        val text = v.toString(Charsets.US_ASCII).takeWhile { it.code in 32..126 }
        if (text.isEmpty()) throw NoAnswerException("the SAM-BA monitor did not answer (is the board in its ROM bootloader?)")
        return text
    }

    fun readWord(addr: Long): Long {
        cmd("w%08X,4#".format(addr))
        val b = link.readExactly(4, 1000)
        if (b.size != 4) throw IOException("short answer from the SAM-BA monitor at 0x${addr.toString(16)}")
        return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
    }

    fun writeWord(addr: Long, value: Long) = cmd("W%08X,%08X#".format(addr, value and 0xFFFFFFFFL))

    /** Writes [data] into RAM. Command and payload go in separate USB packets: the ROM gets confused otherwise. */
    fun write(addr: Long, data: ByteArray) {
        cmd("S%08X,%08X#".format(addr, data.size))
        link.write(data)
    }

    private fun readByte(addr: Long): Byte {
        cmd("o%08X,4#".format(addr))
        return link.readExactly(1, 1000).firstOrNull() ?: throw IOException("short answer from the SAM-BA monitor")
    }

    fun read(addr: Long, size: Int): ByteArray {
        var a = addr
        var left = size
        val out = java.io.ByteArrayOutputStream()
        // the ROM has a bug reading a power of two over 32 bytes through USB: take the first byte apart
        if (left > 32 && left and (left - 1) == 0) { out.write(readByte(a).toInt()); a++; left-- }
        cmd("R%08X,%08X#".format(a, left))
        val b = link.readExactly(left, 3000)
        if (b.size != left) throw IOException("short read from the SAM-BA monitor at 0x${a.toString(16)}")
        out.write(b)
        return out.toByteArray()
    }

    fun go(addr: Long) = cmd("G%08X#".format(addr))
}

/** One SAM3 / SAM4 / SAME70 flash controller (EEFC): where things are, from bossac's device table. */
class EefcChip(
    val name: String, val family: Family, val flash: Long, val pages: Int, val pageSize: Int, val planes: Int,
    val user: Long, val stack: Long, val regs: Long,
) {
    enum class Family(val resetReg: Long, val resetValue: Long) {
        SAM3X(0x400E1A00, 0xA500000D), SAM3S(0x400E1A00, 0xA500000D), SAM3A(0x400E1A00, 0xA500000D),
        SAM3U(0x400E1200, 0xA500000D), SAM3N(0x400E1400, 0xA500000D), SAM4S(0x400E1400, 0xA500000D),
        SAM4E(0x400E1800, 0xA500000D), SAME70(0xE000ED0C, 0x05FA0004),
    }
    val size: Long get() = pages.toLong() * pageSize
}

object EefcChips {
    private fun c(name: String, f: EefcChip.Family, flash: Long, pages: Int, size: Int, planes: Int, user: Long, stack: Long, regs: Long) =
        EefcChip(name, f, flash, pages, size, planes, user, stack, regs)

    private val sam4s = EefcChip.Family.SAM4S
    private val sam3s = EefcChip.Family.SAM3S
    private val sam3n = EefcChip.Family.SAM3N
    private val e70 = EefcChip.Family.SAME70

    /** CHIPID_CIDR & 0x7fffffe0 -> chip (the SAM4E needs the extension id too, see [find]). */
    private val table: Map<Long, EefcChip> = buildMap {
        fun add(ids: List<Long>, chip: EefcChip) { ids.forEach { put(it, chip) } }
        add(listOf(0x29870ee0, 0x29970ee0, 0x29A70ee0), c("ATSAM4SD32", sam4s, 0x400000, 4096, 512, 2, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x29870c30, 0x29970c30, 0x29a70c30), c("ATSAM4SD16", sam4s, 0x400000, 2048, 512, 2, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x28870ce0, 0x28970ce0, 0x28A70ce0), c("ATSAM4SA16", sam4s, 0x400000, 2048, 512, 1, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x288c0ce0, 0x289c0ce0, 0x28ac0ce0), c("ATSAM4S16", sam4s, 0x400000, 2048, 512, 1, 0x20001000, 0x20020000, 0x400e0a00))
        add(listOf(0x288c0ae0, 0x289c0ae0, 0x28ac0ae0), c("ATSAM4S8", sam4s, 0x400000, 1024, 512, 1, 0x20001000, 0x20020000, 0x400e0a00))
        add(listOf(0x288b09e0, 0x289b09e0, 0x28ab09e0), c("ATSAM4S4", sam4s, 0x400000, 512, 512, 1, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x288b07e0, 0x289b07e0, 0x28ab07e0), c("ATSAM4S2", sam4s, 0x400000, 256, 512, 1, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x29340960, 0x29440960, 0x29540960), c("ATSAM3N4", sam3n, 0x400000, 1024, 256, 1, 0x20001000, 0x20006000, 0x400e0a00))
        add(listOf(0x29390760, 0x29490760, 0x29590760), c("ATSAM3N2", sam3n, 0x400000, 512, 256, 1, 0x20001000, 0x20004000, 0x400e0a00))
        add(listOf(0x29380560, 0x29480560, 0x29580560), c("ATSAM3N1", sam3n, 0x400000, 256, 256, 1, 0x20000800, 0x20002000, 0x400e0a00))
        add(listOf(0x29380360, 0x29480360, 0x29580360), c("ATSAM3N0", sam3n, 0x400000, 128, 256, 1, 0x20000800, 0x20002000, 0x400e0a00))
        add(listOf(0x299b0a60, 0x29ab0a60), c("ATSAM3SD8", sam3s, 0x400000, 2048, 256, 1, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x289b0a60, 0x28ab0a60), c("ATSAM3S8", sam3s, 0x400000, 2048, 256, 1, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x28800960, 0x28900960, 0x28a00960), c("ATSAM3S4", sam3s, 0x400000, 1024, 256, 1, 0x20001000, 0x2000c000, 0x400e0a00))
        add(listOf(0x288a0760, 0x289a0760, 0x28aa0760), c("ATSAM3S2", sam3s, 0x400000, 512, 256, 1, 0x20000800, 0x20008000, 0x400e0a00))
        add(listOf(0x288a0560, 0x289a0560, 0x28aa0560), c("ATSAM3S1", sam3s, 0x400000, 256, 256, 1, 0x20000800, 0x20004000, 0x400e0a00))
        add(listOf(0x28000960, 0x28100960), c("ATSAM3U4", EefcChip.Family.SAM3U, 0xE0000, 1024, 256, 2, 0x20001000, 0x20008000, 0x400e0800))
        add(listOf(0x280a0760, 0x281a0760), c("ATSAM3U2", EefcChip.Family.SAM3U, 0x80000, 512, 256, 1, 0x20001000, 0x20004000, 0x400e0800))
        add(listOf(0x28090560, 0x28190560), c("ATSAM3U1", EefcChip.Family.SAM3U, 0x80000, 256, 256, 1, 0x20001000, 0x20002000, 0x400e0800))
        add(listOf(0x286e0a60, 0x285e0a60, 0x284e0a60), c("ATSAM3X8", EefcChip.Family.SAM3X, 0x80000, 2048, 256, 2, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x285b0960, 0x284b0960), c("ATSAM3X4", EefcChip.Family.SAM3X, 0x80000, 1024, 256, 2, 0x20001000, 0x20008000, 0x400e0a00))
        add(listOf(0x283e0A60), c("ATSAM3A8", EefcChip.Family.SAM3A, 0x80000, 2048, 256, 2, 0x20001000, 0x20010000, 0x400e0a00))
        add(listOf(0x283b0960), c("ATSAM3A4", EefcChip.Family.SAM3A, 0x80000, 1024, 256, 2, 0x20001000, 0x20008000, 0x400e0a00))
        add(listOf(0x210d0a00, 0x211d0a00, 0x213d0a00, 0x212d0a00), c("ATSAMx70x19", e70, 0x400000, 1024, 512, 1, 0x20401000, 0x20404000, 0x400e0c00))
        add(listOf(0x21020c00, 0x21120c00, 0x21320c00, 0x21220c00), c("ATSAMx70x20", e70, 0x400000, 2048, 512, 1, 0x20401000, 0x20404000, 0x400e0c00))
        add(listOf(0x21020e00, 0x21120e00, 0x21220e00), c("ATSAMx70x21", e70, 0x400000, 4096, 512, 1, 0x20401000, 0x20404000, 0x400e0c00))
    }

    private val sam4e16 = c("ATSAM4E16", EefcChip.Family.SAM4E, 0x400000, 2048, 512, 1, 0x20001000, 0x20020000, 0x400e0a00)
    private val sam4e8 = c("ATSAM4E8", EefcChip.Family.SAM4E, 0x400000, 1024, 512, 1, 0x20001000, 0x20020000, 0x400e0a00)

    fun find(chipId: Long, extChipId: Long): EefcChip? {
        val id = chipId and 0x7fffffe0
        if (id == 0x23cc0ce0L) return when (extChipId) {
            0x00120200L, 0x00120201L -> sam4e16
            0x00120208L, 0x00120209L -> sam4e8
            else -> null
        }
        return table[id]
    }
}

/** Erase/write/verify of an EEFC flash through SAM-BA, same register sequence and "word copy" applet as bossac. */
class SamBaFlasher(
    private val mon: SamBaMonitor,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private var chip: EefcChip? = null
    private var onBufferA = true

    private fun fsr(plane: Int) = chip!!.regs + 0x08 + plane * 0x200
    private fun fcr(plane: Int) = chip!!.regs + 0x04 + plane * 0x200
    private fun bufferA() = (chip!!.user + APPLET.size + 3) / 4 * 4
    private fun bufferB() = bufferA() + chip!!.pageSize

    private fun waitFsr(seconds: Int = 1) {
        val c = chip!!
        var tries = seconds * 1000
        while (tries-- > 0) {
            val f0 = mon.readWord(fsr(0))
            if (f0 and 0x2L != 0L) throw IOException("the flash controller refused a command")
            if (f0 and 0x4L != 0L) throw IOException("the flash region is locked")
            var f1 = 1L
            if (c.planes == 2) {
                f1 = mon.readWord(fsr(1))
                if (f1 and 0x2L != 0L) throw IOException("the flash controller refused a command")
                if (f1 and 0x4L != 0L) throw IOException("the flash region is locked")
            }
            if (f0 and f1 and 0x1L != 0L) return
            sleep(1)
        }
        throw IOException("the flash controller did not become ready")
    }

    private fun command(plane: Int, cmd: Int, arg: Long) = mon.writeWord(fcr(plane), (KEY shl 24) or (arg shl 8) or cmd.toLong())

    /** Identifies the chip, loads the applet into RAM. */
    fun connect(log: (String) -> Unit): EefcChip {
        val version = mon.connect()
        var chipId = 0L
        var ext = 0L
        if (mon.readWord(0x0) and 0xff000000L == 0xea000000L) throw FirmwareException("this is an ARM7 SAM chip, which is not supported")
        val cpu = mon.readWord(0xe000ed00) and 0xfff0L
        if (cpu == 0xC600L) throw FirmwareException("this is a SAMD chip: the Arduino SAM-BA bootloader is not supported")
        if (cpu == 0xC240L && (mon.readWord(0x4) and 0xfff00000L) != 0x800000L) throw FirmwareException("this is a SAMD5x chip, which is not supported")
        chipId = mon.readWord(0x400e0740)
        if (chipId != 0L) ext = mon.readWord(0x400e0744)
        else { chipId = mon.readWord(0x400e0940); ext = mon.readWord(0x400e0944) }
        val c = EefcChips.find(chipId, ext)
            ?: throw FirmwareException("unknown chip id 0x${chipId.toString(16)} (only the SAM3, SAM4 and SAME70 are supported)")
        chip = c
        log("SAM-BA $version, ${c.name}: ${c.pages} pages of ${c.pageSize} bytes at 0x${c.flash.toString(16)}")
        mon.writeWord(c.regs, 0x6L shl 8)   // 6 flash wait states while we work
        if (c.planes == 2) mon.writeWord(c.regs + 0x200, 0x6L shl 8)
        mon.write(c.user, APPLET)
        mon.writeWord(c.user + WORDS, (c.pageSize / 4).toLong())
        mon.writeWord(c.user + STACK, c.stack)
        return c
    }

    private fun runApplet(dst: Long, src: Long) {
        val u = chip!!.user
        mon.writeWord(u + DST, dst)
        mon.writeWord(u + SRC, src)
        waitFsr()
        mon.writeWord(u + RESET, u + START + 1)   // Thumb
        mon.go(u + STACK)                          // the stack word is followed by the reset word: a tiny vector table
    }

    fun eraseAll() {
        val c = chip!!
        waitFsr()
        command(0, FCMD_EA, 0)
        if (c.planes == 2) { waitFsr(); command(1, FCMD_EA, 0) }
        waitFsr(30)
    }

    private fun writePage(page: Int, data: ByteArray) {
        val buffer = if (onBufferA) bufferA() else bufferB()
        mon.write(buffer, data)
        runAppletForWrite(page, buffer)
        onBufferA = !onBufferA
    }

    private fun runAppletForWrite(page: Int, buffer: Long) {
        val c = chip!!
        runApplet(c.flash + page.toLong() * c.pageSize, buffer)
        if (c.planes == 2 && page >= c.pages / 2) command(1, FCMD_WP, (page - c.pages / 2).toLong())
        else command(0, FCMD_WP, page.toLong())
    }

    private fun readPage(page: Int): ByteArray {
        val c = chip!!
        val buffer = if (onBufferA) bufferA() else bufferB()
        runApplet(buffer, c.flash + page.toLong() * c.pageSize)
        return mon.read(buffer, c.pageSize)
    }

    private fun bootFromFlash() {
        waitFsr()
        command(0, FCMD_GGPB, 0)
        waitFsr()
        val gpnvm = mon.readWord(chip!!.regs + 0x0C)
        if (gpnvm and 0x2L == 0L) { waitFsr(); command(0, FCMD_SGPB, 1) }
    }

    /** Erases the whole flash, writes [data] at the start of it, checks it page by page, selects the flash as boot memory and resets. */
    fun flash(data: ByteArray, progress: (FlashStage, Int) -> Unit, log: (String) -> Unit) {
        progress(FlashStage.HANDSHAKE, 0)
        val c = connect(log)
        if (data.isEmpty() || data.size > c.size) throw FirmwareException("the file does not fit in the ${c.size / 1024} KiB of flash of the ${c.name}")
        progress(FlashStage.HANDSHAKE, 100)
        eraseAll()
        progress(FlashStage.ERASE, 100)
        val pages = (data.size + c.pageSize - 1) / c.pageSize
        fun page(i: Int) = ByteArray(c.pageSize) { 0xFF.toByte() }.also { System.arraycopy(data, i * c.pageSize, it, 0, minOf(c.pageSize, data.size - i * c.pageSize)) }
        for (i in 0 until pages) {
            writePage(i, page(i))
            progress(FlashStage.WRITE, (i + 1) * 100 / pages)
        }
        waitFsr()
        for (i in 0 until pages) {
            val got = readPage(i)
            if (!got.contentEquals(page(i))) throw FirmwareException("the flash differs from the file in page $i")
            progress(FlashStage.VERIFY, (i + 1) * 100 / pages)
        }
        log("verify OK")
        bootFromFlash()
        progress(FlashStage.REBOOT, 0)
        try { mon.writeWord(c.family.resetReg, c.family.resetValue) } catch (_: IOException) { /* the chip resets under our feet */ }
    }

    companion object {
        private const val KEY = 0x5AL
        private const val FCMD_WP = 0x1
        private const val FCMD_EA = 0x5
        private const val FCMD_SGPB = 0xB
        private const val FCMD_GGPB = 0xD

        // bossac's WordCopyApplet (Thumb): copies `words` 32-bit words from src to dst, because SAM-BA cannot write the flash buffer itself
        private const val START = 0x00L
        private const val STACK = 0x20L
        private const val RESET = 0x24L
        private const val DST = 0x28L
        private const val SRC = 0x2CL
        private const val WORDS = 0x30L
        internal val APPLET = intArrayOf(
            0x09, 0x48, 0x0a, 0x49, 0x0a, 0x4a, 0x02, 0xe0, 0x08, 0xc9, 0x08, 0xc0, 0x01, 0x3a, 0x00, 0x2a,
            0xfa, 0xd1, 0x04, 0x48, 0x00, 0x28, 0x01, 0xd1, 0x01, 0x48, 0x85, 0x46, 0x70, 0x47, 0xc0, 0x46,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        ).let { a -> ByteArray(a.size) { a[it].toByte() } }
    }
}
