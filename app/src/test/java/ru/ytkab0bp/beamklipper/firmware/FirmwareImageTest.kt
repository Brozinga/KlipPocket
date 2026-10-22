package ru.ytkab0bp.beamklipper.firmware

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FirmwareImageTest {
    private fun block(addr: Int, fill: Int, family: Int = FirmwareImage.UF2_FAMILY_RP2040, flags: Int = 0x2000, n: Int = 0, total: Int = 1): ByteArray {
        val b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x0A324655).putInt(0x9E5D5157.toInt()).putInt(flags).putInt(addr).putInt(256).putInt(n).putInt(total).putInt(family)
        for (i in 0 until 256) b.put(32 + i, fill.toByte())
        b.putInt(508, 0x0AB16F30)
        return b.array()
    }

    @Test fun uf2BlocksLandAtTheirOwnAddresses() {
        // Regression: every block used to be read as block 0, so only one sector was produced.
        val uf2 = block(0x10000000, 0x11, n = 0, total = 3) + block(0x10001100, 0x22, n = 1, total = 3) + block(0x10002000, 0x33, n = 2, total = 3)
        val img = FirmwareImage.fromUf2(uf2)
        assertEquals(0x10000000, img.address)
        assertEquals(3 * 4096, img.data.size)
        assertEquals(0x11, img.data[0].toInt())
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
        assertEquals(FirmwareImage.UF2_FAMILY_RP2040, FirmwareImage.uf2Family(block(0x10000000, 1)))
        assertNull(FirmwareImage.uf2Family(ByteArray(600)))
    }

    private fun picoBin(size: Int): ByteArray {
        val b = ByteArray(size) { 7 }
        val crc = FirmwareImage.boot2Crc(b, 252)
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(252, crc)
        return b
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
