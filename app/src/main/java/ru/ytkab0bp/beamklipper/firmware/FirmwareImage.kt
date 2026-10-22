package ru.ytkab0bp.beamklipper.firmware

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A flash image ready for the RP2040: [data] starts at [address] and is padded to whole sectors. */
class FlashImage(val address: Int, val data: ByteArray)

enum class FirmwareKind { UF2, BIN, HEX, UNKNOWN;
    companion object {
        fun of(name: String): FirmwareKind = when (name.substringAfterLast('.', "").lowercase()) {
            "uf2" -> UF2
            "bin" -> BIN
            "hex" -> HEX
            else -> UNKNOWN
        }
    }
}

class FirmwareException(message: String) : Exception(message)

object FirmwareImage {
    const val UF2_FAMILY_RP2040 = 0xE48BFF56.toInt()
    private const val MAGIC0 = 0x0A324655
    private const val MAGIC1 = 0x9E5D5157.toInt()
    private const val MAGIC_END = 0x0AB16F30
    private const val FLAG_NOT_MAIN_FLASH = 0x1
    private const val FLAG_FAMILY = 0x2000

    /** Reads the family id of the first block (null when the file is not a UF2 or has no family). */
    fun uf2Family(bytes: ByteArray): Int? {
        if (bytes.size < 512) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (b.getInt(0) != MAGIC0 || b.getInt(4) != MAGIC1) return null
        return if (b.getInt(8) and FLAG_FAMILY != 0) b.getInt(28) else null
    }

    /** UF2 -> RP2040 flash image. Throws [FirmwareException] with a readable reason. */
    fun fromUf2(bytes: ByteArray): FlashImage {
        if (bytes.size % 512 != 0 || bytes.isEmpty()) throw FirmwareException("not a valid UF2 file (size is not a multiple of 512)")
        var min = Int.MAX_VALUE
        var max = 0
        val blocks = bytes.size / 512
        // getInt(index) is absolute: the block offset has to be part of the index.
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until blocks) {
            val o = i * 512
            if (b.getInt(o) != MAGIC0 || b.getInt(o + 4) != MAGIC1 || b.getInt(o + 508) != MAGIC_END)
                throw FirmwareException("UF2 block $i has a bad magic number")
            val flags = b.getInt(o + 8)
            if (flags and FLAG_NOT_MAIN_FLASH != 0) continue
            if (flags and FLAG_FAMILY == 0 || b.getInt(o + 28) != UF2_FAMILY_RP2040)
                throw FirmwareException("this UF2 is not for the RP2040 (family 0x${Integer.toHexString(b.getInt(o + 28))})")
            val addr = b.getInt(o + 12)
            val size = b.getInt(o + 16)
            if (size != Picoboot.PAGE) throw FirmwareException("UF2 block $i has an unsupported payload size ($size)")
            if (addr < Picoboot.FLASH_START || addr.toLong() + size > Picoboot.FLASH_START.toLong() + Picoboot.FLASH_MAX)
                throw FirmwareException("UF2 block $i targets 0x${Integer.toHexString(addr)}, outside the flash")
            min = minOf(min, addr)
            max = maxOf(max, addr + size)
        }
        if (min == Int.MAX_VALUE) throw FirmwareException("the UF2 has no flash data")
        val start = min and (Picoboot.SECTOR - 1).inv()
        val end = (max + Picoboot.SECTOR - 1) and (Picoboot.SECTOR - 1).inv()
        val out = ByteArray(end - start) { 0xFF.toByte() }
        for (i in 0 until blocks) {
            val o = i * 512
            if (b.getInt(o + 8) and FLAG_NOT_MAIN_FLASH != 0) continue
            System.arraycopy(bytes, o + 32, out, b.getInt(o + 12) - start, Picoboot.PAGE)
        }
        return FlashImage(start, out)
    }

    /** Raw .bin: written at the start of the flash (where boot2 lives). */
    fun fromBin(bytes: ByteArray): FlashImage {
        if (bytes.isEmpty()) throw FirmwareException("the file is empty")
        if (bytes.size > Picoboot.FLASH_MAX) throw FirmwareException("the file is bigger than the 2 MiB flash")
        val end = (bytes.size + Picoboot.SECTOR - 1) and (Picoboot.SECTOR - 1).inv()
        val out = ByteArray(end) { 0xFF.toByte() }
        System.arraycopy(bytes, 0, out, 0, bytes.size)
        return FlashImage(Picoboot.FLASH_START, out)
    }
}
