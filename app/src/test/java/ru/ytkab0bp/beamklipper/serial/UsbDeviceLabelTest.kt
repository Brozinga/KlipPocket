package ru.ytkab0bp.beamklipper.serial

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbDeviceLabelTest {
    @Test
    fun `uses the product name the device reports`() {
        assertEquals("rp2040", UsbDeviceLabel.baseName("rp2040"))
    }

    @Test
    fun `trims the product name`() {
        assertEquals("USB Serial", UsbDeviceLabel.baseName("  USB Serial \n"))
    }

    @Test
    fun `a device without a product name has an empty label`() {
        assertEquals("", UsbDeviceLabel.baseName(null))
        assertEquals("", UsbDeviceLabel.baseName(""))
        assertEquals("", UsbDeviceLabel.baseName("   "))
    }

    @Test
    fun `unnamed devices are never numbered`() {
        assertEquals(listOf("", "rp2040 (1)", "", "rp2040 (2)"), UsbDeviceLabel.numbered(listOf("", "rp2040", "", "rp2040")))
    }

    @Test
    fun `a name used once stays as it is`() {
        assertEquals(listOf("rp2040", "USB Serial"), UsbDeviceLabel.numbered(listOf("rp2040", "USB Serial")))
    }

    @Test
    fun `repeated names are numbered in order`() {
        assertEquals(
            listOf("rp2040 (1)", "USB Serial", "rp2040 (2)", "rp2040 (3)"),
            UsbDeviceLabel.numbered(listOf("rp2040", "USB Serial", "rp2040", "rp2040"))
        )
    }

    @Test
    fun `only the repeated name is numbered`() {
        assertEquals(
            listOf("a (1)", "b", "a (2)"),
            UsbDeviceLabel.numbered(listOf("a", "b", "a"))
        )
    }

    @Test
    fun `empty list gives empty list`() {
        assertEquals(emptyList<String>(), UsbDeviceLabel.numbered(emptyList()))
    }
}
