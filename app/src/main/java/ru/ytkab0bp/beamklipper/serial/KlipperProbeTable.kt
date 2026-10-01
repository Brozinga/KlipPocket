package ru.ytkab0bp.beamklipper.serial

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber

object KlipperProbeTable {
    private var mInstance: ProbeTable? = null

    @JvmStatic
    fun getInstance(): ProbeTable {
        if (mInstance == null) {
            val inst = UsbSerialProber.getDefaultProbeTable()
            mInstance = inst
            inst.addProduct(0x1D50, 0x614E, CdcAcmSerialDriver::class.java)
            inst.addProduct(0xDD8, 0x3701, Ch34xSerialDriver::class.java)
        }
        return mInstance!!
    }
}
