package ru.ytkab0bp.beamklipper.serial

import android.annotation.SuppressLint
import android.util.Log
import ru.ytkab0bp.beamklipper.KlipperApp
import java.io.File

class NativeSerialPort : SerialProxy {
    private val TAG = "beam_native_serial"
    private var pointer: Long = 0
    private var proxy: SerialProxy? = null
    var file: File? = null
        private set

    constructor(name: String) : this(File(KlipperApp.INSTANCE.filesDir, "serial/$name"))

    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    constructor(file: File) {
        this.file = file
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        pointer = SerialNative.create(file.absolutePath, this)
        if (pointer == 0L) {
            Log.e(TAG, "Failed to open native port at ${file.absolutePath}")
            return
        }
        file.setReadable(true, false)
        file.setWritable(true, false)
    }

    fun write(data: ByteArray, len: Int) {
        SerialNative.write(pointer, data, len)
    }

    fun setProxy(proxy: SerialProxy): NativeSerialPort {
        this.proxy = proxy
        return this
    }

    override fun onDataReceived(data: ByteArray) {
        proxy?.onDataReceived(data)
    }

    fun release() {
        SerialNative.release(pointer)
        file?.delete()
    }
}
