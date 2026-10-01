package ru.ytkab0bp.beamklipper.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.beamklipper.serial.KlipperMessageBlockConstants as K

// Klipper's MCU message block framing (klippy/msgproto.py): a block is
// length, sequence, payload, CRC16, sync byte.
class KlipperMessageBlockConstantsTest {
    @Test
    fun `a block is at least the header plus the trailer`() {
        assertEquals(K.MESSAGE_HEADER_SIZE + K.MESSAGE_TRAILER_SIZE, K.MESSAGE_MIN)
    }

    @Test
    fun `the payload fills what is left of a maximum block`() {
        assertEquals(K.MESSAGE_MAX - K.MESSAGE_MIN, K.MESSAGE_PAYLOAD_MAX)
        assertEquals(59, K.MESSAGE_PAYLOAD_MAX)
    }

    @Test
    fun `the sequence number uses the low four bits and the destination the next`() {
        assertEquals(0x0f, K.MESSAGE_SEQ_MASK)
        assertEquals(0, K.MESSAGE_SEQ_MASK and K.MESSAGE_DEST)
        assertEquals(0x10, K.MESSAGE_DEST)
    }

    @Test
    fun `the block ends with the sync byte after the crc`() {
        assertEquals(0x7E, K.MESSAGE_SYNC)
        assertEquals(K.MESSAGE_TRAILER_SIZE, K.MESSAGE_TRAILER_CRC + K.MESSAGE_TRAILER_SYNC - 1)
    }

    @Test
    fun `length comes first and the sequence second`() {
        assertEquals(0, K.MESSAGE_POS_LEN)
        assertEquals(1, K.MESSAGE_POS_SEQ)
    }

    @Test
    fun `the connection flags are distinct bits`() {
        assertEquals(0, K.CF_NEED_SYNC and K.CF_NEED_VALID)
        assertTrue(K.CF_NEED_VALID > K.CF_NEED_SYNC)
    }
}
