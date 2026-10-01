package ru.ytkab0bp.beamklipper.serial

import ru.ytkab0bp.beamklipper.utils.Prefs

// Name of the virtual serial port a USB device gets, which is what the
// printer.cfg [mcu] serial: line refers to.
object UsbDeviceId {
    fun uid(naming: Int, vendorId: Int, productId: Int, deviceName: String): String = when (naming) {
        // Survives re-plugging into another port, but two identical boards clash.
        Prefs.USB_DEVICE_NAMING_BY_VID_PID ->
            Integer.toHexString(vendorId) + "_" + Integer.toHexString(productId)
        // The bus path, e.g. /dev/bus/usb/001/002 -> _dev_bus_usb_001_002.
        else -> deviceName.replace("/", "_")
    }
}
