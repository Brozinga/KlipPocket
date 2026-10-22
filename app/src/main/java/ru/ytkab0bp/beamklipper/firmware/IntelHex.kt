package ru.ytkab0bp.beamklipper.firmware

/** A contiguous flash image that is not padded to sectors (STM32 / AVR). */
class RawImage(val address: Long, val data: ByteArray)

object IntelHex {
    private const val MAX_SPAN = 16L * 1024 * 1024

    /** Parses Intel HEX text into one contiguous image; gaps are filled with 0xFF. */
    fun parse(text: String): RawImage {
        val chunks = ArrayList<Pair<Long, ByteArray>>()
        var base = 0L
        var min = Long.MAX_VALUE
        var max = 0L
        var eof = false
        for ((n, raw) in text.lineSequence().withIndex()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (eof) break
            if (line[0] != ':' || line.length < 11 || line.length % 2 == 0)
                throw FirmwareException("not a valid Intel HEX file (line ${n + 1})")
            val b = try {
                ByteArray((line.length - 1) / 2) { line.substring(1 + it * 2, 3 + it * 2).toInt(16).toByte() }
            } catch (e: NumberFormatException) { throw FirmwareException("not a valid Intel HEX file (line ${n + 1})") }
            val len = b[0].toInt() and 0xFF
            if (b.size != len + 5) throw FirmwareException("bad record length in Intel HEX line ${n + 1}")
            if (b.sumOf { it.toInt() and 0xFF } and 0xFF != 0) throw FirmwareException("bad checksum in Intel HEX line ${n + 1}")
            val addr = ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)
            when (b[3].toInt()) {
                0x00 -> {
                    val abs = base + addr
                    chunks += abs to b.copyOfRange(4, 4 + len)
                    min = minOf(min, abs)
                    max = maxOf(max, abs + len)
                }
                0x01 -> eof = true
                0x02 -> base = (((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)).toLong() shl 4
                0x04 -> base = (((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)).toLong() shl 16
                else -> {} // 03 / 05 start address records are not needed
            }
        }
        if (chunks.isEmpty()) throw FirmwareException("the Intel HEX file has no data")
        if (max - min > MAX_SPAN) throw FirmwareException("the Intel HEX file spans too much memory")
        val out = ByteArray((max - min).toInt()) { 0xFF.toByte() }
        for ((a, d) in chunks) System.arraycopy(d, 0, out, (a - min).toInt(), d.size)
        return RawImage(min, out)
    }

    fun fromBin(bytes: ByteArray, start: Long): RawImage {
        if (bytes.isEmpty()) throw FirmwareException("the file is empty")
        return RawImage(start, bytes)
    }
}
