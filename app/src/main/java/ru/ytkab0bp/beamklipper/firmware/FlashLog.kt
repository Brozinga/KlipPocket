package ru.ytkab0bp.beamklipper.firmware

import ru.ytkab0bp.beamklipper.KlipperApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-text log of every firmware flash, shown as its own source in the Logs tab. */
object FlashLog {
    private const val MAX_BYTES = 200_000

    fun file(): File = File(File(KlipperApp.INSTANCE.filesDir, "logs").apply { mkdirs() }, "firmware_flash.log")

    @Synchronized
    fun append(line: String) {
        val f = file()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        f.appendText("[$stamp] $line\n")
        if (f.length() > MAX_BYTES) {
            val t = f.readText()
            f.writeText(t.substring(t.length - MAX_BYTES / 2).substringAfter('\n'))
        }
    }

    fun read(): String = file().takeIf { it.exists() }?.readText().orEmpty()
}
