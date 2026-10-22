package ru.ytkab0bp.beamklipper.firmware

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FirmwareImageTest {
    /** 256 bytes that pass the RP2040 boot2 CRC check. */
    private fun boot2(fill: Int = 0x11): ByteArray {
        val p = ByteArray(256) { fill.toByte() }
        ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN).putInt(252, FirmwareImage.boot2Crc(p, 252))
        return p
    }

    /** First 256 bytes of an RP2350 image: an IMAGE_DEF marker at offset 640 would be in the next block, so put it early here. */
    private fun imageDef(): ByteArray {
        val p = ByteArray(256) { 0x22 }
        ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN).putInt(64, 0xFFFFDED3.toInt())
        return p
    }

    private fun block(addr: Int, fill: Int, family: Int = FirmwareImage.UF2_FAMILY_RP2040, flags: Int = 0x2000, n: Int = 0, total: Int = 1, payload: ByteArray? = null): ByteArray {
        val b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x0A324655).putInt(0x9E5D5157.toInt()).putInt(flags).putInt(addr).putInt(256).putInt(n).putInt(total).putInt(family)
        for (i in 0 until 256) b.put(32 + i, payload?.get(i) ?: fill.toByte())
        b.putInt(508, 0x0AB16F30)
        return b.array()
    }

    @Test fun uf2BlocksLandAtTheirOwnAddresses() {
        // Regression: every block used to be read as block 0, so only one sector was produced.
        val uf2 = block(0x10000000, 0x11, n = 0, total = 3, payload = boot2(0x11)) + block(0x10001100, 0x22, n = 1, total = 3) + block(0x10002000, 0x33, n = 2, total = 3)
        val img = FirmwareImage.fromUf2(uf2)
        assertEquals(0x10000000, img.address)
        assertEquals(3 * 4096, img.data.size)
        assertEquals(0x11, img.data[0].toInt())
        assertTrue(FirmwareImage.hasBoot2(img.data))
        assertEquals(0x22, img.data[0x1100].toInt())
        assertEquals(0x33, img.data[0x2000].toInt())
        assertEquals(0xFF, img.data[0x200].toInt() and 0xFF) // gap stays erased
    }

    @Test fun uf2ImageStartsAtTheFirstSectorOfTheLowestBlock() {
        val img = FirmwareImage.fromUf2(block(0x10003100, 0x44))
        assertEquals(0x10003000, img.address)
        assertEquals(4096, img.data.size)
        assertEquals(0x44, img.data[0x100].toInt())
    }

    @Test fun uf2ForAnotherFamilyIsRefused() {
        try {
            FirmwareImage.fromUf2(block(0x10000000, 1, family = 0xE48BFF59.toInt()))
            fail()
        } catch (e: FirmwareException) {
            assertTrue(e.message!!.contains("not for the RP2040"))
        }
    }

    @Test fun uf2OutsideTheFlashIsRefused() {
        try { FirmwareImage.fromUf2(block(0x20000000, 1)); fail() } catch (_: FirmwareException) {}
    }

    @Test fun uf2FamilyIsReadFromTheFirstBlock() {
        assertEquals(FirmwareImage.UF2_FAMILY_RP2040, FirmwareImage.uf2Family(block(0x10000000, 1, payload = boot2())))
        assertNull(FirmwareImage.uf2Family(ByteArray(600)))
    }

    private fun picoBin(size: Int): ByteArray {
        val b = ByteArray(size) { 7 }
        val crc = FirmwareImage.boot2Crc(b, 252)
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(252, crc)
        return b
    }

    // --- RP2350 (experimental) ----------------------------------------------------------------

    @Test fun rp2350Uf2WithKlippersFamilyIdIsToldApartByItsImageDef() {
        // Klipper's elf2uf2 writes the RP2040 family id for an RP2350 build too.
        val uf2 = block(0x10000000, 0, payload = imageDef()) + block(0x10000100, 0x33, n = 1, total = 2)
        val img = FirmwareImage.fromUf2(uf2, PicoChip.RP2350)
        assertEquals(0x10000000, img.address)
        assertTrue(FirmwareImage.hasImageDef(img.data))
        try { FirmwareImage.fromUf2(uf2, PicoChip.RP2040); fail() } catch (e: WrongChipException) { assertTrue(e.message!!.contains("boot2")) }
    }

    @Test fun rp2040Uf2IsRefusedByAnRp2350() {
        val uf2 = block(0x10000000, 0, payload = boot2())
        try { FirmwareImage.fromUf2(uf2, PicoChip.RP2350); fail() } catch (e: WrongChipException) { assertTrue(e.message!!.contains("IMAGE_DEF")) }
    }

    @Test fun rp2350FamilyIdsAreAcceptedOnlyForTheRp2350() {
        for (fam in listOf(0xE48BFF57.toInt(), 0xE48BFF59.toInt(), 0xE48BFF5A.toInt(), 0xE48BFF5B.toInt())) {
            val uf2 = block(0x10000000, 0, family = fam, payload = imageDef())
            assertEquals(0x10000000, FirmwareImage.fromUf2(uf2, PicoChip.RP2350).address)
            try { FirmwareImage.fromUf2(uf2, PicoChip.RP2040); fail() } catch (_: WrongChipException) {}
        }
    }

    @Test fun rp2350ReachesFlashAddressesTheRp2040DoesNot() {
        val uf2 = block(0x11000000, 0x44, family = 0xE48BFF59.toInt())
        assertEquals(0x11000000, FirmwareImage.fromUf2(uf2, PicoChip.RP2350).address)
        try { FirmwareImage.fromUf2(block(0x11000000, 0x44), PicoChip.RP2040); fail() } catch (_: FirmwareException) {}
    }

    @Test fun rp2350BinNeedsAnImageDefAndRp2040BinNeedsBoot2() {
        val bin = ByteArray(2000) { 5 }
        ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN).putInt(640, 0xFFFFDED3.toInt())
        assertEquals(0x10000000, FirmwareImage.fromBin(bin, PicoChip.RP2350).address)
        try { FirmwareImage.fromBin(bin, PicoChip.RP2040); fail() } catch (_: WrongChipException) {}
        try { FirmwareImage.fromBin(picoBin(2000), PicoChip.RP2350); fail() } catch (_: WrongChipException) {}
    }

    @Test fun reboot2UsesTheRp2350CommandLayout() {
        val args = Picoboot.reboot2Args(0, 500, 0, 0)
        val pkt = Picoboot.buildCommand(7, Picoboot.CMD_REBOOT2_ID, 16, 0, args)
        val b = ByteBuffer.wrap(pkt).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x431fd10b, b.getInt(0))
        assertEquals(7, b.getInt(4))
        assertEquals(0x0A, pkt[8].toInt())
        assertEquals(16, pkt[9].toInt())
        assertEquals(0, b.getInt(12))
        assertEquals(0, b.getInt(16))   // flags: normal boot
        assertEquals(500, b.getInt(20)) // delay ms
        assertEquals(32, pkt.size)
    }

    @Test fun theRp2350IsAlwaysExperimental() {
        assertTrue(isExperimental(FlashMode.RP2040, FirmwareKind.UF2, PicoChip.RP2350))
        assertTrue(isExperimental(FlashMode.USB, FirmwareKind.UF2, PicoChip.RP2350))
        assertFalse(isExperimental(FlashMode.RP2040, FirmwareKind.UF2, PicoChip.RP2040))
    }

    @get:org.junit.Rule val tmp = org.junit.rules.TemporaryFolder()

    @Test fun deleteAllRemovesOnlyFirmwareFilesAndTheLeftoverDownload() {
        val dir = tmp.newFolder("fw")
        listOf("a.bin", "b.UF2", "c.hex", "keep.txt", ".download.tmp").forEach { java.io.File(dir, it).writeText("x") }
        assertEquals(3, FirmwareStore.deleteAllIn(dir))
        assertEquals(listOf("keep.txt"), dir.list()!!.toList())
        assertEquals(0, FirmwareStore.deleteAllIn(dir))
    }

    @Test fun binIsPaddedToWholeSectors() {
        val img = FirmwareImage.fromBin(picoBin(5000))
        assertEquals(0x10000000, img.address)
        assertEquals(8192, img.data.size)
        assertArrayEquals(byteArrayOf(7, 7), img.data.copyOfRange(4998, 5000))
        assertEquals(0xFF, img.data[5000].toInt() and 0xFF)
    }

    @Test fun binOfAnotherChipIsRefused() {
        try { FirmwareImage.fromBin(ByteArray(5000) { 7 }); fail() } catch (e: FirmwareException) { assertTrue(e.message!!.contains("boot2")) }
    }

    @Test fun safeNameKeepsOnlyAFileName() {
        assertEquals("stm32f401.bin", FirmwareStore.safeName("/a/b/stm32f401.bin?x=1"))
        assertEquals("passwd", FirmwareStore.safeName("../../etc/passwd"))
        assertEquals("a_b.uf2", FirmwareStore.safeName("a b.uf2"))
    }

    @Test fun kindFollowsTheExtension() {
        assertEquals(FirmwareKind.UF2, FirmwareKind.of("x.UF2"))
        assertEquals(FirmwareKind.UNKNOWN, FirmwareKind.of("x.zip"))
    }
}
