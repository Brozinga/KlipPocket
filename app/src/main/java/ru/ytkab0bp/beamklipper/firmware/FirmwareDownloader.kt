package ru.ytkab0bp.beamklipper.firmware

import ru.ytkab0bp.beamklipper.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Downloads a firmware from a URL into [FirmwareStore]. https only (http is allowed in debug builds). */
object FirmwareDownloader {
    /** Validates the URL and returns the file name it would be stored under. */
    fun fileNameFor(url: String): String {
        val u = try { URL(url.trim()) } catch (e: Exception) { throw FirmwareException("invalid URL") }
        if (u.protocol != "https" && !(BuildConfig.DEBUG && u.protocol == "http"))
            throw FirmwareException("only https links are accepted")
        val name = FirmwareStore.safeName(u.path)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext != "zip" && !FirmwareStore.isAllowedName(name))
            throw FirmwareException("the link must point to a .bin, .hex, .uf2 or .zip file")
        return name
    }

    /** Blocking; call off the main thread. [progress] gets 0..100, or -1 when the size is unknown. */
    fun download(url: String, progress: (Int) -> Unit, cancelled: () -> Boolean = { false }): List<FirmwareStore.Entry> {
        val name = fileNameFor(url)
        val tmp = File(FirmwareStore.dir(), ".download.tmp")
        try {
            val c = URL(url.trim()).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "KlipPocket")
            if (c.responseCode != 200) throw FirmwareException("server answered HTTP ${c.responseCode}")
            val length = c.contentLengthLong
            if (length > FirmwareStore.MAX_BYTES) throw FirmwareException("the file is too big")
            var total = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32768)
                    while (true) {
                        if (cancelled()) throw FirmwareException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > FirmwareStore.MAX_BYTES) throw FirmwareException("the file is too big")
                        out.write(buf, 0, n)
                        progress(if (length > 0) (total * 100 / length).toInt() else -1)
                    }
                }
            }
            if (total == 0L) throw FirmwareException("the download is empty")
            return if (name.endsWith(".zip", true)) unzip(tmp) else {
                val dest = FirmwareStore.target(name)
                if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true) }
                listOf(FirmwareStore.Entry(dest, FirmwareKind.of(dest.name)))
            }
        } finally {
            tmp.delete()
        }
    }

    /** Extracts the .bin/.hex/.uf2 entries, flattening names (zip-slip proof). */
    private fun unzip(zip: File): List<FirmwareStore.Entry> {
        val result = ArrayList<FirmwareStore.Entry>()
        var budget = FirmwareStore.MAX_BYTES
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val name = FirmwareStore.safeName(e.name)
                if (!FirmwareStore.isAllowedName(name)) continue
                val dest = FirmwareStore.target(name)
                dest.outputStream().use { out ->
                    val buf = ByteArray(32768)
                    while (true) {
                        val n = z.read(buf)
                        if (n < 0) break
                        budget -= n
                        if (budget < 0) { dest.delete(); throw FirmwareException("the archive is too big") }
                        out.write(buf, 0, n)
                    }
                }
                result += FirmwareStore.Entry(dest, FirmwareKind.of(dest.name))
            }
        }
        if (result.isEmpty()) throw FirmwareException("the .zip has no .bin, .hex or .uf2 file")
        return result
    }
}
