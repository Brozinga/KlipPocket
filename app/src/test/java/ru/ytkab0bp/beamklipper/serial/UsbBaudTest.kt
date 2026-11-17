package ru.ytkab0bp.beamklipper.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbBaudTest {
    @Test
    fun `the default is the speed the app always used`() {
        assertEquals(250000, UsbBaud.DEFAULT)
        assertTrue(UsbBaud.OPTIONS.contains(250000))
        assertTrue(UsbBaud.OPTIONS.contains(115200))
    }

    @Test
    fun `a board nobody touched uses the default`() {
        assertEquals(250000, UsbBaud.resolve(emptyMap(), "_dev_bus_usb_001_002"))
        assertEquals(250000, UsbBaud.resolve(mapOf("other" to 115200), "_dev_bus_usb_001_002"))
    }

    @Test
    fun `each board keeps its own speed`() {
        var saved = emptyMap<String, Int>()
        saved = UsbBaud.with(saved, "printer", 115200)
        saved = UsbBaud.with(saved, "mmu", 500000)
        assertEquals(115200, UsbBaud.resolve(saved, "printer"))
        assertEquals(500000, UsbBaud.resolve(saved, "mmu"))
        assertEquals(250000, UsbBaud.resolve(saved, "toolhead"))
    }

    @Test
    fun `picking the default forgets the entry`() {
        val saved = UsbBaud.with(mapOf("printer" to 115200), "printer", 250000)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a speed that is not on the list changes nothing`() {
        val saved = mapOf("printer" to 115200)
        assertEquals(saved, UsbBaud.with(saved, "printer", 12345))
        assertEquals(saved, UsbBaud.with(saved, "printer", 0))
        assertEquals(saved, UsbBaud.with(saved, "", 115200))
        assertFalse(UsbBaud.isValid(9600))
    }

    @Test
    fun `a saved speed that is no longer valid reads as the default`() {
        assertEquals(250000, UsbBaud.resolve(mapOf("printer" to 12345), "printer"))
    }

    @Test
    fun `speeds survive being stored as text`() {
        val saved = mapOf("a" to 115200, "b" to 57600)
        assertEquals(saved, UsbBaud.decode(UsbBaud.encode(saved)))
    }

    @Test
    fun `broken or empty stored text reads as nothing saved`() {
        assertTrue(UsbBaud.decode(null).isEmpty())
        assertTrue(UsbBaud.decode("").isEmpty())
        assertTrue(UsbBaud.decode("not json").isEmpty())
        assertTrue(UsbBaud.decode("{\"a\":12345,\"b\":\"x\",\"c\":250000}").isEmpty())
    }
}
