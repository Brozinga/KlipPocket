package ru.ytkab0bp.beamklipper.serial

object SerialNative {
    init {
        System.loadLibrary("serial")
    }

    @JvmStatic external fun create(file: String, proxy: SerialProxy): Long
    @JvmStatic external fun write(pointer: Long, data: ByteArray, len: Int)
    @JvmStatic external fun release(pointer: Long)
}
