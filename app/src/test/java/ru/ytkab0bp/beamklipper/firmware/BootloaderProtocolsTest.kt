package ru.ytkab0bp.beamklipper.firmware

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

private val noProgress: (FlashStage, Int) -> Unit = { _, _ -> }
private val noLog: (String) -> Unit = { }

private fun image(addr: Long, size: Int) = RawImage(addr, ByteArray(size) { (it * 7 + 3).toByte() })

/** A serial line whose "board" is a lambda: every write may queue answer bytes. */
private abstract class FakeSerial : SerialLink {
    val rx = ArrayDeque<Byte>()
    override fun read(n: Int, timeoutMs: Int): ByteArray {
        val out = ArrayList<Byte>()
        while (out.size < n && rx.isNotEmpty()) out += rx.removeFirst()
        return out.toByteArray()
    }
    override fun setLines(dtr: Boolean, rts: Boolean) {}
    override fun drain() { rx.clear() }
    fun reply(vararg b: Int) { b.forEach { rx.addLast(it.toByte()) } }
    fun reply(b: ByteArray) { b.forEach { rx.addLast(it) } }
}

class BootloaderProtocolsTest {
    // --- Intel HEX ---------------------------------------------------------------

    private fun hexLine(addr: Int, type: Int, data: ByteArray): String {
        val body = byteArrayOf(data.size.toByte(), (addr shr 8).toByte(), addr.toByte(), type.toByte()) + data
        val sum = (-body.sumOf { it.toInt() and 0xFF }) and 0xFF
        return ":" + (body + sum.toByte()).joinToString("") { "%02X".format(it) }
    }

    @Test fun intelHexUsesLinearAddressesAndFillsGaps() {
        val text = listOf(
            hexLine(0, 4, byteArrayOf(0x08, 0x00)),
            hexLine(0x0000, 0, byteArrayOf(1, 2, 3, 4)),
            hexLine(0x0008, 0, byteArrayOf(9, 9)),
            hexLine(0, 1, ByteArray(0)),
        ).joinToString("\n")
        val img = IntelHex.parse(text)
        assertEquals(0x08000000L, img.address)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, -1, -1, -1, -1, 9, 9), img.data)
    }

    @Test fun intelHexRejectsABadChecksum() {
        try { IntelHex.parse(":0400000001020304FF\n:00000001FF"); fail() } catch (e: FirmwareException) { assertTrue(e.message!!.contains("checksum")) }
    }

    // --- progress ------------------------------------------------------------------

    @Test fun overallProgressNeverGoesBackwardsAndEndsAtOneHundred() {
        val order = listOf(FlashStage.PREPARE, FlashStage.ENTER_BOOTSEL, FlashStage.WAIT_BOOTSEL, FlashStage.PERMISSION, FlashStage.HANDSHAKE,
            FlashStage.ERASE, FlashStage.WRITE, FlashStage.VERIFY, FlashStage.REBOOT, FlashStage.WAIT_DEVICE)
        var last = -1
        for (st in order) for (p in listOf(0, 50, 100)) {
            val v = FlashProgress.overall(st, p)
            assertTrue("$st $p", v >= last)
            last = v
        }
        assertEquals(100, FlashProgress.overall(FlashStage.WAIT_DEVICE, 100))
        assertEquals(0, FlashProgress.overall(FlashStage.PREPARE, 0))
    }

    @Test fun onlyTheRp2040PathsAreNotExperimental() {
        assertFalse(isExperimental(FlashMode.RP2040, FirmwareKind.UF2))
        assertFalse(isExperimental(FlashMode.USB, FirmwareKind.UF2))
        assertTrue(isExperimental(FlashMode.USB, FirmwareKind.BIN))
        assertTrue(isExperimental(FlashMode.UART, FirmwareKind.HEX))
        assertTrue(isExperimental(FlashMode.DFU, FirmwareKind.BIN))
    }

    // --- DfuSe -----------------------------------------------------------------------

    private class FakeDfu(val ts: Int) : DfuLink {
        val base = 0x08000000L
        val mem = ByteArray(0x20000) { 0x00 }
        var state = 2
        var pointer = 0L
        var pending: ByteArray? = null
        var block = -1
        var left = false
        var busy = false
        val erased = ArrayList<Long>()
        override fun out(request: Int, value: Int, data: ByteArray?) {
            if (left) throw IOException("gone")
            when (request) {
                1 -> if (value == 0 && data!!.isEmpty()) { left = true } else {
                    pending = data; block = value; busy = true; state = 4
                }
                4 -> state = 2
                6 -> state = 2
            }
        }
        override fun inn(request: Int, value: Int, length: Int): ByteArray {
            if (left) throw IOException("gone")
            when (request) {
                3 -> {
                    if (busy) { execute(); busy = false; return byteArrayOf(0, 1, 0, 0, 4, 0) }
                    if (state == 4) state = 5
                    return byteArrayOf(0, 0, 0, 0, state.toByte(), 0)
                }
                2 -> {
                    if (state != 2) throw IOException("upload needs dfuIDLE")
                    val from = (pointer - base).toInt() + (value - 2) * ts
                    return mem.copyOfRange(from, from + length)
                }
            }
            throw IOException("bad request")
        }
        private fun execute() {
            val d = pending!!
            if (block == 0) {
                val a = (d[1].toLong() and 0xFF) or ((d[2].toLong() and 0xFF) shl 8) or ((d[3].toLong() and 0xFF) shl 16) or ((d[4].toLong() and 0xFF) shl 24)
                when (d[0].toInt()) {
                    0x21 -> pointer = a
                    0x41 -> { // erase the 16 KiB sector that holds the address
                        val s = (a - base) / 0x4000 * 0x4000
                        for (i in 0 until 0x4000) mem[(s + i).toInt()] = 0xFF.toByte()
                        erased += base + s
                    }
                }
            } else {
                val at = (pointer - base).toInt() + (block - 2) * ts
                d.copyInto(mem, at)
            }
            state = 5
        }
    }

    @Test fun dfuseLayoutIsParsed() {
        val l = DfuseFlasher.parseLayout("@Internal Flash  /0x08000000/04*016Kg,01*064Kg,03*128Kg")
        assertEquals(8, l.size)
        assertEquals(0x08004000L, l[1].start)
        assertEquals(64 * 1024, l[4].size)
        assertTrue(l.all { it.erasable })
        assertTrue(DfuseFlasher.parseLayout("nonsense").isEmpty())
    }

    @Test fun dfuseErasesWritesVerifiesAndLeaves() {
        val dev = FakeDfu(1024)
        val img = image(0x08000000L, 20000) // crosses two 16 KiB sectors
        val stages = ArrayList<FlashStage>()
        DfuseFlasher(dev, 1024, DfuseFlasher.parseLayout("@Internal Flash /0x08000000/04*016Kg,01*064Kg"), sleep = {})
            .flash(img, true, { s, _ -> if (stages.lastOrNull() != s) stages += s }, noLog)
        assertEquals(listOf(0x08000000L, 0x08004000L), dev.erased)
        assertArrayEquals(img.data, dev.mem.copyOf(img.data.size))
        assertTrue(dev.left)
        assertEquals(listOf(FlashStage.HANDSHAKE, FlashStage.ERASE, FlashStage.WRITE, FlashStage.VERIFY, FlashStage.REBOOT), stages)
    }

    @Test fun dfuseRefusesAddressesOutsideTheFlash() {
        try {
            DfuseFlasher(FakeDfu(1024), 1024, DfuseFlasher.parseLayout("@Internal Flash /0x08000000/04*016Kg"), sleep = {})
                .flash(image(0x20000000L, 100), false, noProgress, noLog)
            fail()
        } catch (_: FirmwareException) {}
    }

    // --- STM32 serial bootloader (AN3155) ----------------------------------------------------

    private class FakeStm32 : FakeSerial() {
        val flash = ByteArray(0x10000) { 0x55 }
        private var phase = "cmd"
        private var addr = 0L
        var erasedAll = false
        var wentTo: Long? = null
        override fun write(data: ByteArray) {
            when (phase) {
                "cmd" -> {
                    if (data.size == 1 && data[0] == 0x7F.toByte()) { reply(0x79); return }
                    assertEquals((data[0].toInt() xor 0xFF).toByte(), data[1])
                    reply(0x79)
                    when (data[0].toInt() and 0xFF) {
                        0x00 -> { reply(11, 0x31, 0x00, 0x01, 0x02, 0x11, 0x21, 0x31, 0x44, 0x63, 0x73, 0x82, 0x92, 0x79) }
                        0x44 -> phase = "erase"
                        0x31 -> phase = "waddr"
                        0x11 -> phase = "raddr"
                        0x21 -> phase = "gaddr"
                    }
                }
                "erase" -> { assertArrayEquals(byteArrayOf(-1, -1, 0), data); erasedAll = true; flash.fill(0xFF.toByte()); reply(0x79); phase = "cmd" }
                "waddr", "raddr", "gaddr" -> {
                    addr = data.take(4).fold(0L) { a, b -> (a shl 8) or (b.toLong() and 0xFF) }
                    assertEquals(data.take(4).fold(0) { a, b -> a xor b.toInt() }.toByte(), data[4])
                    reply(0x79)
                    if (phase == "gaddr") { wentTo = addr; phase = "cmd" } else phase = if (phase == "waddr") "wdata" else "rlen"
                }
                "wdata" -> {
                    val n = (data[0].toInt() and 0xFF) + 1
                    var cs = 0; for (b in data.copyOfRange(0, n + 1)) cs = cs xor b.toInt()
                    assertEquals(cs.toByte(), data[n + 1])
                    assertEquals(0, n % 4)
                    data.copyOfRange(1, n + 1).copyInto(flash, (addr - 0x08000000L).toInt())
                    reply(0x79); phase = "cmd"
                }
                "rlen" -> {
                    val n = (data[0].toInt() and 0xFF) + 1
                    reply(0x79)
                    reply(flash.copyOfRange((addr - 0x08000000L).toInt(), (addr - 0x08000000L).toInt() + n))
                    phase = "cmd"
                }
            }
        }
    }

    @Test fun stm32UartMassErasesWritesVerifiesAndGoes() {
        val dev = FakeStm32()
        val img = image(0x08000000L, 1000) // 1000 = 3 full pages of 256 + 232 bytes
        Stm32SerialBootloader(dev, sleep = {}).flash(img, true, noProgress, noLog)
        assertTrue(dev.erasedAll)
        assertArrayEquals(img.data, dev.flash.copyOf(1000))
        assertEquals(0x08000000L, dev.wentTo)
    }

    @Test fun stm32UartReportsSilenceAsNoAnswer() {
        val silent = object : FakeSerial() { override fun write(data: ByteArray) {} }
        try { Stm32SerialBootloader(silent, sleep = {}).connect(); fail() } catch (_: NoAnswerException) {}
    }

    // --- AVR: STK500v1 (arduino) -------------------------------------------------------------

    private class FakeStk1 : FakeSerial() {
        val flash = ByteArray(0x2000) { 0xFF.toByte() }
        private var word = 0
        var left = false
        override fun write(data: ByteArray) {
            assertEquals(0x20.toByte(), data.last())
            when (data[0].toInt() and 0xFF) {
                0x30, 0x50 -> reply(0x14, 0x10)
                0x55 -> { word = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8); reply(0x14, 0x10) }
                0x64 -> {
                    val n = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                    assertEquals('F'.code.toByte(), data[3])
                    data.copyOfRange(4, 4 + n).copyInto(flash, word * 2); reply(0x14, 0x10)
                }
                0x74 -> {
                    val n = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                    reply(0x14); reply(flash.copyOfRange(word * 2, word * 2 + n)); reply(0x10)
                }
                0x51 -> { left = true; reply(0x14, 0x10) }
            }
        }
    }

    @Test fun stk500v1WritesPagesVerifiesAndLeaves() {
        val dev = FakeStk1()
        val img = image(0, 300)
        Stk500v1(dev, sleep = {}).flash(img, true, noProgress, noLog)
        assertArrayEquals(img.data, dev.flash.copyOf(300))
        assertTrue(dev.left)
    }

    // --- AVR: STK500v2 (wiring) -------------------------------------------------------------

    private class FakeStk2 : FakeSerial() {
        val flash = ByteArray(0x4000) { 0xFF.toByte() }
        private var word = 0L
        var extended = false
        var left = false
        private fun frame(seq: Int, body: ByteArray) {
            val f = byteArrayOf(0x1B, seq.toByte(), (body.size shr 8).toByte(), body.size.toByte(), 0x0E) + body
            reply(f + byteArrayOf(f.fold(0) { a, b -> a xor b.toInt() }.toByte()))
        }
        override fun write(data: ByteArray) {
            assertEquals(0x1B.toByte(), data[0])
            assertEquals(data.dropLast(1).fold(0) { a, b -> a xor b.toInt() }.toByte(), data.last())
            val seq = data[1].toInt() and 0xFF
            val len = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val body = data.copyOfRange(5, 5 + len)
            when (body[0].toInt()) {
                0x01 -> frame(seq, byteArrayOf(1, 0, 8) + "AVRISP_2".toByteArray())
                0x06 -> {
                    val a = body.copyOfRange(1, 5).fold(0L) { x, b -> (x shl 8) or (b.toLong() and 0xFF) }
                    extended = a and 0x80000000L != 0L; word = a and 0x7FFFFFFFL; frame(seq, byteArrayOf(6, 0))
                }
                0x13 -> {
                    val n = ((body[1].toInt() and 0xFF) shl 8) or (body[2].toInt() and 0xFF)
                    body.copyOfRange(10, 10 + n).copyInto(flash, (word * 2).toInt()); frame(seq, byteArrayOf(0x13, 0))
                }
                0x14 -> {
                    val n = ((body[1].toInt() and 0xFF) shl 8) or (body[2].toInt() and 0xFF)
                    frame(seq, byteArrayOf(0x14, 0) + flash.copyOfRange((word * 2).toInt(), (word * 2).toInt() + n) + byteArrayOf(0))
                }
                0x11 -> { left = true; frame(seq, byteArrayOf(0x11, 0)) }
            }
        }
    }

    @Test fun stk500v2WritesPagesVerifiesAndLeaves() {
        val dev = FakeStk2()
        val img = image(0, 600)
        Stk500v2(dev, sleep = {}).flash(img, true, noProgress, noLog)
        assertArrayEquals(img.data, dev.flash.copyOf(600))
        assertTrue(dev.extended)
        assertTrue(dev.left)
    }

    // --- AVR109 (Caterina) --------------------------------------------------------------------

    private class FakeCaterina : FakeSerial() {
        val flash = ByteArray(0x4000) { 0xFF.toByte() }
        private var word = 0
        var exited = false
        override fun write(data: ByteArray) {
            when (data[0].toInt().toChar()) {
                'S' -> reply("CATERIN".toByteArray())
                'b' -> reply('Y'.code, 0x00, 0x80)
                'P', 'L' -> reply('\r'.code)
                'A' -> { word = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF); reply('\r'.code) }
                'B' -> {
                    val n = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                    data.copyOfRange(4, 4 + n).copyInto(flash, word * 2); reply('\r'.code)
                }
                'g' -> {
                    val n = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                    reply(flash.copyOfRange(word * 2, word * 2 + n))
                }
                'E' -> { exited = true; reply('\r'.code) }
            }
        }
    }

    @Test fun avr109WritesBlocksVerifiesAndExits() {
        val dev = FakeCaterina()
        val img = image(0, 400)
        Avr109(dev, sleep = {}).flash(img, true, noProgress, noLog)
        assertArrayEquals(img.data, dev.flash.copyOf(400))
        assertTrue(dev.exited)
        assertNotNull(dev)
    }
}
