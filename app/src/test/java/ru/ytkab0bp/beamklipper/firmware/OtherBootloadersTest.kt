package ru.ytkab0bp.beamklipper.firmware

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A pretend board from app/src/test/python/fake_bootloaders.py, wired through the process pipes. The very same fakes are
 * checked against Klipper's real flash_can.py, hid-flash and bossac (test_bootloader_fakes.py), so a pass here means the
 * app talks to a board the way those tools do. Skipped when python3 is not installed.
 */
private class PythonBoard(kind: String, vararg options: String, fault: String? = null) : SerialLink, HidLink, CanBus {
    private val dump = File.createTempFile("board", ".json")
    private val process: Process
    private val rx = LinkedBlockingQueue<Byte>()

    init {
        val args = mutableListOf("python3", "-I", script().path, kind, "--dump", dump.path)
        if (fault != null) args += listOf("--fault", fault)
        options.forEach { args += listOf("--option", it) }
        process = ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        Thread {
            val inp = process.inputStream
            try { while (true) { val b = inp.read(); if (b < 0) break; rx.add(b.toByte()) } } catch (_: IOException) {}
        }.apply { isDaemon = true }.start()
    }

    private fun script(): File = listOf("src/test/python", "app/src/test/python").map { File(it, "fake_bootloaders.py") }.first { it.exists() }

    override fun write(data: ByteArray) { process.outputStream.write(data); process.outputStream.flush() }

    override fun read(n: Int, timeoutMs: Int): ByteArray {
        val first = rx.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        out.write(first.toInt())
        while (out.size() < n) out.write((rx.poll() ?: break).toInt())
        return out.toByteArray()
    }

    override fun setLines(dtr: Boolean, rts: Boolean) {}
    override fun drain() { Thread.sleep(30); rx.clear() }

    override fun read(timeoutMs: Int): ByteArray? {
        val out = ByteArray(8)
        for (i in 0 until 8) out[i] = rx.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return null
        return out
    }

    /** CAN frames travel as the 16 bytes of a Linux can_frame: id (LE), length, 3 pad bytes, 8 data bytes. */
    override fun send(frame: CanFrame) {
        val b = ByteArray(16)
        for (i in 0 until 4) b[i] = (frame.id shr (8 * i)).toByte()
        b[4] = frame.data.size.toByte()
        System.arraycopy(frame.data, 0, b, 8, frame.data.size)
        write(b)
    }

    override fun receive(timeoutMs: Int): CanFrame? {
        val b = ByteArray(16)
        val end = System.currentTimeMillis() + timeoutMs
        for (i in 0 until 16) b[i] = rx.poll(maxOf(1L, end - System.currentTimeMillis()), TimeUnit.MILLISECONDS) ?: return null
        var id = 0L
        for (i in 0 until 4) id = id or ((b[i].toLong() and 0xFF) shl (8 * i))
        return CanFrame(id, b.copyOfRange(8, 8 + b[4].toInt()))
    }

    /** Closes the line, waits for the fake to write its final state and returns it. */
    fun state(): String {
        process.outputStream.close()
        process.waitFor(10, TimeUnit.SECONDS)
        return dump.readText().also { dump.delete() }
    }

    fun destroy() { process.destroyForcibly(); dump.delete() }
}

private fun String.text(key: String) = Regex("\"$key\": \"([^\"]*)\"").find(this)?.groupValues?.get(1)
private fun String.flag(key: String) = Regex("\"$key\": (true|false)").find(this)?.groupValues?.get(1) == "true"
private fun String.number(key: String) = Regex("\"$key\": (\\d+)").find(this)?.groupValues?.get(1)?.toInt()
private fun sha1(b: ByteArray) = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }
private fun image(size: Int) = ByteArray(size) { (it * 7 + 3).toByte() }

class OtherBootloadersTest {
    private var board: PythonBoard? = null
    private val stages = ArrayList<Pair<FlashStage, Int>>()
    private val progress: (FlashStage, Int) -> Unit = { s, p -> stages += s to p }
    private val log = ArrayList<String>()

    @Before fun needPython() {
        assumeTrue("python3 is not installed", runCatching { ProcessBuilder("python3", "--version").start().waitFor() == 0 }.getOrDefault(false))
    }

    @After fun stop() { board?.destroy() }

    private fun board(kind: String, vararg o: String, fault: String? = null) = PythonBoard(kind, *o, fault = fault).also { board = it }

    private fun assertProgressEndsAtOneHundred(stage: FlashStage) {
        val of = stages.filter { it.first == stage }.map { it.second }
        assertTrue("$stage reached 100", of.isNotEmpty() && of.last() == 100)
        assertEquals("$stage never goes back", of, of.sorted())
    }

    // --- Katapult ------------------------------------------------------------------

    private fun katapult(link: SerialLink) = KatapultFlasher(link, sleep = {}, answerTimeoutMs = 300)

    @Test fun katapultWritesVerifiesAndCompletes() {
        val b = board("katapult")
        val data = image(5000)
        katapult(b).flash(data, progress, { log += it })
        val st = b.state()
        assertTrue(st.flag("complete"))
        assertTrue(st.flag("eof"))
        val padded = data + ByteArray((64 - data.size % 64) % 64) { 0xFF.toByte() }
        assertEquals(sha1(padded.dropLastWhile { it == 0xFF.toByte() }.toByteArray()), st.text("flash_sha1"))
        assertProgressEndsAtOneHundred(FlashStage.WRITE)
        assertProgressEndsAtOneHundred(FlashStage.VERIFY)
        assertTrue(log.any { it.contains("stm32f446xx") && it.contains("0x8002000") })
    }

    @Test fun katapultUsesTheBlockSizeOfTheBootloader() {
        for (block in listOf(128, 256, 512)) {
            val b = board("katapult", "block=$block")
            katapult(b).flash(image(1000), progress, {})
            assertTrue("block $block", b.state().flag("complete"))
        }
    }

    @Test fun katapultRetriesAfterAGarbledAnswer() {
        val b = board("katapult", fault = "garble-first")
        katapult(b).flash(image(300), progress, {})
        assertTrue(b.state().flag("complete"))
    }

    @Test fun katapultNeverAnsweringIsReported() {
        val b = board("katapult", fault = "no-answer")
        try { katapult(b).flash(image(300), progress, {}); fail() } catch (e: NoAnswerException) { assertTrue(e.message!!.contains("CONNECT")) }
    }

    @Test fun katapultDetectsABadWriteAndStillLeavesTheBootloader() {
        val b = board("katapult", fault = "bad-write")
        try { katapult(b).flash(image(300), progress, {}); fail() } catch (e: FirmwareException) { assertTrue(e.message!!.startsWith("the flash differs")) }
        assertTrue("COMPLETE is sent even after a failure", b.state().flag("complete"))
    }

    @Test fun katapultDetectsABadReadBack() {
        val b = board("katapult", fault = "bad-read")
        try { katapult(b).flash(image(300), progress, {}); fail() } catch (e: FirmwareException) { assertTrue(e.message!!.contains("SHA-1")) }
    }

    @Test fun katapultChecksTheCanUuid() {
        val b = board("katapult")
        katapult(b).flash(image(200), progress, {}, expectedUuid = 0x112233445566L)
        val other = board("katapult")
        try { katapult(other).flash(image(200), progress, {}, expectedUuid = 0x1L); fail() } catch (e: FirmwareException) { assertTrue(e.message!!.contains("UUID")) }
        assertEquals("the wrong node is left alone: no block was written", 0, other.state().number("used"))
    }

    // --- STM32 HID bootloader --------------------------------------------------------

    @Test fun hidSendsWholeSectorsAndReboots() {
        val b = board("hid")
        val data = image(5000)
        HidFlasher(b, sleep = {}, ackTimeoutMs = 500).flash(data, progress, { log += it })
        val st = b.state()
        assertTrue(st.flag("rebooted"))
        assertEquals(5 * 1024, st.number("written"))
        assertEquals(sha1(data + ByteArray(5 * 1024 - data.size)), st.text("flash_sha1"))
        assertProgressEndsAtOneHundred(FlashStage.WRITE)
    }

    @Test fun hidCompletesTheLastPageOfA2KibChip() {
        val b = board("hid", "page=2048")
        val data = image(3 * 1024 - 100)
        HidFlasher(b, sleep = {}, ackTimeoutMs = 500).flash(data, progress, {})
        assertEquals(4 * 1024, b.state().number("written"))
    }

    @Test fun hidWithoutAcknowledgementIsReported() {
        val b = board("hid", fault = "no-ack")
        try { HidFlasher(b, sleep = {}, ackTimeoutMs = 300).flash(image(2000), progress, {}); fail() } catch (e: NoAnswerException) { }
    }

    @Test fun hidRefusesAnEmptyFile() {
        val b = board("hid")
        try { HidFlasher(b, sleep = {}).flash(ByteArray(0), progress, {}); fail() } catch (e: FirmwareException) { }
    }

    // --- Katapult over CAN ---------------------------------------------------------------

    private fun can(b: PythonBoard) = CanKatapult(b, sleep = {}, answerTimeoutMs = 500, queryMs = 300)

    @Test fun canJumpsToKatapultAssignsANodeIdAndFlashes() {
        val b = board("can")
        val data = image(3000)
        can(b).flash(0x112233445566L, data, progress, { log += it })
        val st = b.state()
        assertEquals("katapult", st.text("app"))
        assertEquals(129, st.number("node_id"))
        assertTrue(st.flag("complete"))
        val padded = data + ByteArray((64 - data.size % 64) % 64) { 0xFF.toByte() }
        assertEquals(sha1(padded.dropLastWhile { it == 0xFF.toByte() }.toByteArray()), st.text("flash_sha1"))
        assertTrue(log.any { it.contains("112233445566") && it.contains("Katapult") })
        assertProgressEndsAtOneHundred(FlashStage.WRITE)
    }

    @Test fun canFlashesANodeAlreadyInKatapult() {
        val b = board("can", "app=katapult")
        can(b).flash(0x112233445566L, image(500), progress, {})
        assertTrue(b.state().flag("complete"))
    }

    @Test fun canLeavesAlonePeersWithAnotherUuid() {
        val b = board("can")
        try { can(b).flash(0x1L, image(500), progress, {}); fail() } catch (e: NoAnswerException) { assertTrue(e.message!!.contains("000000000001")) }
        assertEquals("nothing was written", 0, b.state().number("used"))
    }

    @Test fun canBusSilenceIsReported() {
        val b = board("can", fault = "silent")
        try { can(b).flash(0x112233445566L, image(500), progress, {}); fail() } catch (e: NoAnswerException) { }
    }

    @Test fun canUuidIsTwelveHexDigits() {
        assertEquals(0x112233445566L, CanKatapult.parseUuid(" 112233445566 "))
        assertEquals(0xABCDEF012345L, CanKatapult.parseUuid("0xABCDEF012345"))
        assertEquals(null, CanKatapult.parseUuid("1122334455"))
        assertEquals(null, CanKatapult.parseUuid("11223344556g"))
        assertEquals(null, CanKatapult.parseUuid(""))
    }

    @Test fun canBitTimingHitsTheBitrateAndTheSamplePoint() {
        // 48 MHz candleLight-like limits and an 80 MHz controller with wide ranges
        val small = intArrayOf(1, 16, 1, 8, 4, 1, 1024, 1)
        val wide = intArrayOf(1, 256, 1, 128, 128, 1, 512, 1)
        for ((clock, limits) in listOf(48_000_000L to small, 80_000_000L to wide)) for (rate in listOf(125_000, 250_000, 500_000, 800_000, 1_000_000)) {
            val t = GsUsbBus.timing(rate, clock, limits)
            val tq = 1 + t.propSeg + t.phaseSeg1 + t.phaseSeg2
            assertEquals("$clock/$rate", rate.toDouble(), clock.toDouble() / (t.brp * tq), rate * 0.001)
            val sample = (1 + t.propSeg + t.phaseSeg1).toDouble() / tq
            assertTrue("$clock/$rate sample point $sample", Math.abs(sample - 0.875) < 0.05)
            assertTrue(t.sjw in 1..limits[4] && t.phaseSeg2 in limits[2]..limits[3])
        }
    }

    @Test fun canBitTimingRefusesWhatTheClockCannotMake() {
        try { GsUsbBus.timing(1_000_000, 47_000_000L, intArrayOf(1, 16, 1, 8, 4, 1, 1024, 1)); fail() } catch (e: FirmwareException) { }
    }

    @Test fun theCanNodeLinkCutsTheStreamIntoEightByteFrames() {
        val sent = ArrayList<CanFrame>()
        val bus = object : CanBus {
            override fun send(frame: CanFrame) { sent += frame }
            override fun receive(timeoutMs: Int): CanFrame? = null
        }
        CanNodeLink(bus, 0x202, 0x203).write(ByteArray(20) { it.toByte() })
        assertEquals(listOf(8, 8, 4), sent.map { it.data.size })
        assertTrue(sent.all { it.id == 0x202L })
    }

    // --- Atmel SAM-BA -------------------------------------------------------------------

    private fun samba(b: PythonBoard) = SamBaFlasher(SamBaMonitor(b), sleep = {})

    @Test fun samba3xErasesWritesVerifiesBootsFromFlashAndResets() {
        val b = board("samba", "chip=sam3x8e")
        val data = image(10 * 1024 + 77)
        samba(b).flash(data, progress, { log += it })
        val st = b.state()
        assertTrue(st.flag("erased"))
        assertTrue(st.flag("reset"))
        assertEquals("GPNVM1 (boot from flash)", 2, st.number("gpnvm")!! and 2)
        assertEquals(sha1(data.dropLastWhile { it == 0xFF.toByte() }.toByteArray()), st.text("flash_sha1"))
        assertTrue(log.any { it.contains("ATSAM3X8") })
        assertProgressEndsAtOneHundred(FlashStage.WRITE)
        assertProgressEndsAtOneHundred(FlashStage.VERIFY)
    }

    @Test fun samba4eUsesItsOwnPageSizeAndResetRegister() {
        val b = board("samba", "chip=sam4e8e")
        val data = image(4096)
        samba(b).flash(data, progress, { log += it })
        val st = b.state()
        assertTrue(st.flag("reset"))
        assertEquals(sha1(data), st.text("flash_sha1"))
        assertTrue(log.any { it.contains("ATSAM4E8") && it.contains("512 bytes") })
    }

    @Test fun sambaCatchesAPageThatWasNotWrittenRight() {
        val b = board("samba", fault = "bad-write")
        try { samba(b).flash(image(2000), progress, {}); fail() } catch (e: FirmwareException) { assertTrue(e.message!!.startsWith("the flash differs")) }
    }

    @Test fun sambaMonitorNotAnsweringIsReported() {
        val b = board("samba", fault = "no-answer")
        try { samba(b).flash(image(2000), progress, {}); fail() } catch (e: NoAnswerException) { }
    }

    @Test fun sambaRefusesAFileBiggerThanTheFlash() {
        val b = board("samba")
        try { samba(b).flash(ByteArray(600 * 1024), progress, {}); fail() } catch (e: FirmwareException) { assertTrue(e.message!!.contains("does not fit")) }
        assertTrue("nothing was erased", !b.state().flag("erased"))
    }

    @Test fun theAppletIsBossacsWordCopyCode() {
        // the 52 bytes of lib/bossac/src/WordCopyArm.cpp, so a typo in the port shows up
        val source = listOf("../app/src/main/klipper/lib/bossac/src/WordCopyArm.cpp", "src/main/klipper/lib/bossac/src/WordCopyArm.cpp",
            "app/src/main/klipper/lib/bossac/src/WordCopyArm.cpp").map(::File).firstOrNull { it.exists() }
        assumeTrue("bossac sources not found", source != null)
        val bytes = Regex("0x([0-9a-f]{2})[,\\s]").findAll(source!!.readText().substringAfter("0x00000030,")).map { it.groupValues[1].toInt(16).toByte() }.toList()
        assertArrayEquals(bytes.toByteArray(), SamBaFlasher.APPLET)
    }
}
