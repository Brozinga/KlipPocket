package ru.ytkab0bp.beamklipper.firmware

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ru.ytkab0bp.beamklipper.KlipperApp
import java.io.File
import java.security.MessageDigest

/** The firmware files the user downloaded or imported: files/firmware/. */
object FirmwareStore {
    const val MAX_BYTES = 16L * 1024 * 1024
    private val allowed = setOf("bin", "hex", "uf2")

    data class Entry(val file: File, val kind: FirmwareKind) {
        val name: String get() = file.name
        val size: Long get() = file.length()
    }

    fun dir(): File = File(KlipperApp.INSTANCE.filesDir, "firmware").apply { mkdirs() }

    fun list(): List<Entry> = (dir().listFiles() ?: emptyArray())
        .filter { it.isFile && it.extension.lowercase() in allowed }
        .sortedByDescending { it.lastModified() }
        .map { Entry(it, FirmwareKind.of(it.name)) }

    fun isAllowedName(name: String) = name.substringAfterLast('.', "").lowercase() in allowed

    /** Keeps only a safe file name (no paths, no odd characters). */
    fun safeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringBefore('?').substringBefore('#')
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(96)
    }

    /** A free name inside the store: "x.bin", "x (1).bin", ... */
    fun target(name: String): File {
        var f = File(dir(), name)
        var n = 1
        while (f.exists()) {
            f = File(dir(), name.substringBeforeLast('.') + " ($n)." + name.substringAfterLast('.')); n++
        }
        return f
    }

    fun delete(e: Entry) = e.file.delete()

    /** Copies a file the user picked with the system picker. Returns the stored entry. */
    fun import(ctx: Context, uri: Uri): Entry {
        var name = "firmware.bin"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> name = n }
        }
        name = safeName(name)
        if (!isAllowedName(name)) throw FirmwareException("only .bin, .hex and .uf2 files are accepted")
        val dest = target(name)
        var total = 0L
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) { dest.delete(); throw FirmwareException("the file is too big") }
                    out.write(buf, 0, n)
                }
            }
        } ?: throw FirmwareException("could not open the file")
        return Entry(dest, FirmwareKind.of(dest.name))
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(65536); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
