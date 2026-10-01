package ru.ytkab0bp.beamklipper.serial

object KlipperMessageBlockConstants {
    const val MESSAGE_MIN = 5
    const val MESSAGE_MAX = 64
    const val MESSAGE_HEADER_SIZE = 2
    const val MESSAGE_TRAILER_SIZE = 3
    const val MESSAGE_POS_LEN = 0
    const val MESSAGE_POS_SEQ = 1
    const val MESSAGE_TRAILER_CRC = 3
    const val MESSAGE_TRAILER_SYNC = 1
    const val MESSAGE_PAYLOAD_MAX = MESSAGE_MAX - MESSAGE_MIN
    const val MESSAGE_SEQ_MASK = 0x0f
    const val MESSAGE_DEST = 0x10
    const val MESSAGE_SYNC = 0x7E

    const val CF_NEED_SYNC = 1
    const val CF_NEED_VALID = 1 shl 1
}
