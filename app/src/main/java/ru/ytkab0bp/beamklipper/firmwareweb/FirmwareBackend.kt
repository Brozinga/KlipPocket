package ru.ytkab0bp.beamklipper.firmwareweb

import java.io.File

data class WebFile(val name: String, val kind: String, val size: Long)

data class WebDevice(
    val id: String, val label: String, val vendor: String, val product: String, val path: String,
    val driver: String?, val serialPath: String?,
)

/** [state]: idle, running, done or error. */
data class WebDownload(val state: String, val percent: Int, val message: String, val files: List<String>)

/** [state]: idle, running or done. [file], [device] and [mode] are what the run was started with. */
data class WebFlash(
    val state: String, val stage: String, val percent: Int, val overall: Int, val success: Boolean,
    val failure: String?, val detail: String, val noReturn: Boolean, val file: String, val device: String, val mode: String,
)

/** What the page needs from the app: the web server only talks to this, so it can be tested with a fake. */
interface FirmwareBackend {
    fun language(): String
    fun dark(): Boolean
    fun files(): List<WebFile>
    fun deleteFile(name: String): Boolean
    fun deleteAll(): Int
    /** Copies an uploaded temp file into the firmware folder; throws [IllegalArgumentException] with a reason key. */
    fun saveUpload(name: String, source: File): WebFile
    fun startDownload(url: String): String?
    fun download(): WebDownload
    fun resetDownload()
    fun devices(): List<WebDevice>
    fun experimental(file: String, device: String, mode: String): Boolean
    /** Returns null when started, else a reason key ("busy", "file", "device", "mode", "address"). */
    fun startFlash(file: String, device: String, mode: String, startAddress: Long?): String?
    fun flash(): WebFlash
    fun resetFlash()
    fun log(): String
}
