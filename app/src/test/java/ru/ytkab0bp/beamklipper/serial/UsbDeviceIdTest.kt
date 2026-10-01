package ru.ytkab0bp.beamklipper.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.ytkab0bp.beamklipper.utils.Prefs

class UsbDeviceIdTest {
    @Test
    fun `naming by path uses the bus path with slashes replaced`() {
        assertEquals(
            "_dev_bus_usb_001_002",
            UsbDeviceId.uid(Prefs.USB_DEVICE_NAMING_BY_PATH, 0x1d50, 0x614e, "/dev/bus/usb/001/002")
        )
    }

    @Test
    fun `naming by vid and pid uses lower case hex`() {
        assertEquals(
            "1d50_614e",
            UsbDeviceId.uid(Prefs.USB_DEVICE_NAMING_BY_VID_PID, 0x1d50, 0x614e, "/dev/bus/usb/001/002")
        )
        assertEquals("dd8_3701", UsbDeviceId.uid(Prefs.USB_DEVICE_NAMING_BY_VID_PID, 0xdd8, 0x3701, "x"))
    }

    @Test
    fun `naming by vid and pid does not change when the device is moved to another port`() {
        val a = UsbDeviceId.uid(Prefs.USB_DEVICE_NAMING_BY_VID_PID, 0x1d50, 0x614e, "/dev/bus/usb/001/002")
        val b = UsbDeviceId.uid(Prefs.USB_DEVICE_NAMING_BY_VID_PID, 0x1d50, 0x614e, "/dev/bus/usb/002/007")
        assertEquals(a, b)
    }

    @Test
    fun `naming by path tells two identical boards apart`() {
        val a = UsbDeviceId.uid(Prefs.USB_DEVICE_NAMING_BY_PATH, 0x1d50, 0x614e, "/dev/bus/usb/001/002")
        val b = UsbDeviceId.uid(Prefs.USB_DEVICE_NAMING_BY_PATH, 0x1d50, 0x614e, "/dev/bus/usb/001/003")
        assertNotEquals(a, b)
    }

    @Test
    fun `an unknown naming mode behaves like by path`() {
        assertEquals("_a_b", UsbDeviceId.uid(99, 1, 2, "/a/b"))
    }
}
