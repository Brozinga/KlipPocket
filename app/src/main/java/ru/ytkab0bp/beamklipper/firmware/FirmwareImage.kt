package ru.ytkab0bp.beamklipper.firmware

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A flash image ready for an RP2040 / RP2350: [data] starts at [address] and is padded to whole sectors. */
class FlashImage(val address: Int, val data: ByteArray)

/** The Raspberry Pi chips PICOBOOT can flash; [bootPid] is the USB id of their boot ROM (vendor 2e8a). */
enum class PicoChip(val bootPid: Int) { RP2040(0x0003), RP2350(0x000F) }

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

open class FirmwareException(message: String) : Exception(message)

/** The file is well formed but belongs to another chip. */
class WrongChipException(message: String) : FirmwareException(message)

object FirmwareImage {
    const val UF2_FAMILY_RP2040 = 0xE48BFF56.toInt()
    /** RP2350 families: absolute, ARM secure, RISC-V, ARM non-secure. Klipper's own RP2350 UF2 reuses the RP2040 id. */
    private val RP2350_FAMILIES = setOf(UF2_FAMILY_RP2040, 0xE48BFF57.toInt(), 0xE48BFF59.toInt(), 0xE48BFF5A.toInt(), 0xE48BFF5B.toInt())
    private const val MAGIC0 = 0x0A324655
    private const val MAGIC1 = 0x9E5D5157.toInt()
    private const val MAGIC_END = 0x0AB16F30
    private const val FLAG_NOT_MAIN_FLASH = 0x1
    private const val FLAG_FAMILY = 0x2000
    private const val MAX_SPAN = 16 * 1024 * 1024
    /** Start of the RP2350 IMAGE_DEF block (PICOBIN_BLOCK_MARKER_START) that its boot ROM looks for in the first 4 KiB. */
    private const val PICOBIN_MARKER = 0xFFFFDED3.toInt()

    private fun flashEnd(chip: PicoChip): Long = if (chip == PicoChip.RP2350) 0x12000000L else 0x11000000L

    /** Reads the family id of the first block (null when the file is not a UF2 or has no family). */
    fun uf2Family(bytes: ByteArray): Int? {
        if (bytes.size < 512) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (b.getInt(0) != MAGIC0 || b.getInt(4) != MAGIC1) return null
        return if (b.getInt(8) and FLAG_FAMILY != 0) b.getInt(28) else null
    }

    /** UF2 -> flash image for [chip]. Throws [FirmwareException] (or [WrongChipException]) with a readable reason. */
    fun fromUf2(bytes: ByteArray, chip: PicoChip = PicoChip.RP2040): FlashImage {
        if (bytes.size % 512 != 0 || bytes.isEmpty()) throw FirmwareException("not a valid UF2 file (size is not a multiple of 512)")
        var min = Int.MAX_VALUE
        var max = 0
        val blocks = bytes.size / 512
        val families = if (chip == PicoChip.RP2350) RP2350_FAMILIES else setOf(UF2_FAMILY_RP2040)
        // getInt(index) is absolute: the block offset has to be part of the index.
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until blocks) {
            val o = i * 512
            if (b.getInt(o) != MAGIC0 || b.getInt(o + 4) != MAGIC1 || b.getInt(o + 508) != MAGIC_END)
                throw FirmwareException("UF2 block $i has a bad magic number")
            val flags = b.getInt(o + 8)
            if (flags and FLAG_NOT_MAIN_FLASH != 0) continue
            if (flags and FLAG_FAMILY == 0 || b.getInt(o + 28) !in families)
                throw WrongChipException("this UF2 is not for the $chip (family 0x${Integer.toHexString(b.getInt(o + 28))})")
            val addr = b.getInt(o + 12)
            val size = b.getInt(o + 16)
            if (size != Picoboot.PAGE) throw FirmwareException("UF2 block $i has an unsupported payload size ($size)")
            if (addr.toLong() and 0xFFFFFFFFL < Picoboot.FLASH_START || (addr.toLong() and 0xFFFFFFFFL) + size > flashEnd(chip))
                throw FirmwareException("UF2 block $i targets 0x${Integer.toHexString(addr)}, outside the flash")
            min = minOf(min, addr)
            max = maxOf(max, addr + size)
        }
        if (min == Int.MAX_VALUE) throw FirmwareException("the UF2 has no flash data")
        if (max - min > MAX_SPAN) throw FirmwareException("the UF2 spans more than 16 MiB of flash")
        val start = min and (Picoboot.SECTOR - 1).inv()
        val end = (max + Picoboot.SECTOR - 1) and (Picoboot.SECTOR - 1).inv()
        val out = ByteArray(end - start) { 0xFF.toByte() }
        for (i in 0 until blocks) {
            val o = i * 512
            if (b.getInt(o + 8) and FLAG_NOT_MAIN_FLASH != 0) continue
            System.arraycopy(bytes, o + 32, out, b.getInt(o + 12) - start, Picoboot.PAGE)
        }
        // The family id cannot tell the chips apart (Klipper writes the RP2040 id for both): look at the image itself.
        if (start == Picoboot.FLASH_START) checkChip(out, chip)
        return FlashImage(start, out)
    }

    /** CRC32 (MPEG-2: poly 0x04C11DB7, init 0xFFFFFFFF, no reflection) that the RP2040 boot ROM checks in boot2. */
    internal fun boot2Crc(b: ByteArray, len: Int): Int {
        var crc = -1
        for (i in 0 until len) {
            crc = crc xor ((b[i].toInt() and 0xFF) shl 24)
            repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        }
        return crc
    }

    internal fun hasBoot2(b: ByteArray): Boolean =
        b.size >= 256 && ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(252) == boot2Crc(b, 252)

    internal fun hasImageDef(b: ByteArray): Boolean {
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var o = 0
        while (o + 4 <= minOf(b.size, 4096)) { if (buf.getInt(o) == PICOBIN_MARKER) return true; o += 4 }
        return false
    }

    /** RP2040 images start with a boot2 block with a valid CRC; RP2350 images carry an IMAGE_DEF block near the start. */
    private fun checkChip(flash: ByteArray, chip: PicoChip) {
        when (chip) {
            PicoChip.RP2040 -> if (!hasBoot2(flash))
                throw WrongChipException("this image does not start with a valid RP2040 boot2 block: it is not an RP2040 firmware")
            PicoChip.RP2350 -> if (!hasImageDef(flash))
                throw WrongChipException("this image has no RP2350 IMAGE_DEF block near its start: it is not an RP2350 firmware")
        }
    }

    /** Raw .bin: written at the start of the flash, so it must look like a firmware of [chip] (guards against other chips' files). */
    fun fromBin(bytes: ByteArray, chip: PicoChip = PicoChip.RP2040): FlashImage {
        if (bytes.isEmpty()) throw FirmwareException("the file is empty")
        checkChip(bytes, chip)
        if (bytes.size > MAX_SPAN) throw FirmwareException("the file is bigger than 16 MiB")
        val end = (bytes.size + Picoboot.SECTOR - 1) and (Picoboot.SECTOR - 1).inv()
        val out = ByteArray(end) { 0xFF.toByte() }
        System.arraycopy(bytes, 0, out, 0, bytes.size)
        return FlashImage(Picoboot.FLASH_START, out)
    }
}
