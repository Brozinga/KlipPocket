package ru.ytkab0bp.beamklipper.serial

fun interface SerialProxy {
    fun onDataReceived(data: ByteArray)
}
