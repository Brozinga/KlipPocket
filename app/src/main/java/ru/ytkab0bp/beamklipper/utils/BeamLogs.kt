package ru.ytkab0bp.beamklipper.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.LogSources
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects the diagnostic logs the user is most likely to need when something
 * won't start: the app / WebService process (via logcat), and the per-instance
 * Klipper and Moonraker log files (which those services write themselves, since
 * they run in separate processes).
 *
 * Everything here is best-effort and never throws to the caller.
 */
object BeamLogs {

    data class Source(
        val id: String,
        /** Short human label for the picker. */
        val label: String,
        /** Producer of the (already tail-trimmed) text. Runs off the main thread. */
        val load: () -> String,
    )

    fun crashFile(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "last_crash.txt")

    /**
     * Build the list of log sources available right now. The instance list is
     * whatever exists; a not-yet-created log file just shows an explanatory line.
     */
    fun sources(ctx: Context): List<Source> {
        val list = ArrayList<Source>()

        list += Source("app", "App / Web") { readLogcat() }

        val crash = crashFile(ctx)
        if (crash.exists() && crash.length() > 0) {
            list += Source("crash", "Última falha") { tail(readTextOrEmpty(crash)) }
        }

        for (inst in KlipperInstance.getInstances()) {
            val logs = File(inst.publicDirectory, "logs")
            for (entry in LogSources.forInstance(inst.id, inst.name)) {
                list += Source(entry.id, entry.label) { readInstanceLog(File(logs, entry.fileName)) }
            }
        }
        return list
    }

    /** One combined blob of every source, for the Share button. */
    fun combined(ctx: Context): String = buildString {
        append("=== KlipPocket — diagnóstico ===\n")
        append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append("  sdk=").append(Build.VERSION.SDK_INT)
            .append("  abi=").append(Build.SUPPORTED_ABIS.joinToString(",")).append('\n')
        append("time: ").append(System.currentTimeMillis()).append("\n\n")
        for (s in sources(ctx)) {
            append("\n########## ").append(s.label).append(" ##########\n")
            append(runCatching(s.load).getOrElse { "<falha ao ler: ${it.message}>" })
            append('\n')
        }
    }.let { LogText.capCombined(it) }

    /**
     * Write [combined] to a file the user can find with a file manager and
     * returns a human-readable location, or null on failure.
     *
     * API 29+: the public Downloads collection (no permission needed).
     * Older: the app's external files dir (Android/data/<pkg>/files/logs/),
     * also visible without any permission.
     */
    fun saveForSharing(ctx: Context): String? {
        val name = "klippocket-logs-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".txt"
        val body = combined(ctx).toByteArray()
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(body) } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Downloads/$name"
            } else {
                val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "logs")
                    .apply { mkdirs() }
                val f = File(dir, name)
                f.writeBytes(body)
                f.absolutePath
            }
        } catch (t: Throwable) {
            Log.w("BeamLogs", "saveForSharing failed", t)
            null
        }
    }

    /**
     * Wipes what the Logs screen shows, so a later error is known to be new.
     * Instance log files are truncated in place (Klipper/Moonraker keep them
     * open), the last-crash report is deleted, and since an app can't empty
     * the system logcat buffer, the time of the clear is remembered and older
     * logcat lines are hidden. Returns false if any file couldn't be cleared.
     */
    fun clear(ctx: Context): Boolean {
        var ok = true
        try {
            File(ctx.filesDir, CLEARED_AT_FILE)
                .writeText(SimpleDateFormat(LOGCAT_TIME_FORMAT, Locale.US).format(Date()))
        } catch (t: Throwable) {
            Log.w("BeamLogs", "couldn't record clear time", t)
            ok = false
        }
        runCatching { ProcessBuilder("logcat", "-c").start().waitFor() }

        val crash = crashFile(ctx)
        if (crash.exists() && !crash.delete()) ok = false

        for (inst in KlipperInstance.getInstances()) {
            val files = File(inst.publicDirectory, "logs").listFiles() ?: continue
            for (f in files) {
                if (!f.isFile) continue
                try {
                    java.io.RandomAccessFile(f, "rw").use { it.setLength(0) }
                } catch (t: Throwable) {
                    Log.w("BeamLogs", "couldn't clear ${f.name}", t)
                    ok = false
                }
            }
        }
        return ok
    }

    // --- internals -----------------------------------------------------------

    private const val CLEARED_AT_FILE = "logs_cleared_at"
    // `logcat -v time` line prefix, e.g. "09-28 21:10:29.467".
    private const val LOGCAT_TIME_FORMAT = "MM-dd HH:mm:ss.SSS"

    private fun clearedAt(): String? = try {
        File(KlipperApp.INSTANCE.filesDir, CLEARED_AT_FILE).takeIf { it.exists() }?.readText()?.trim()
    } catch (_: Throwable) {
        null
    }

    private fun readInstanceLog(f: File): String {
        return LogText.describeInstanceLog(f.name, f.exists(), if (f.exists()) readTextOrEmpty(f) else "")
    }

    /** Dump this process's own logcat (main process → app + WebService + instance orchestration). */
    private fun readLogcat(): String {
        return try {
            val pid = Process.myPid()
            val cmd = if (Build.VERSION.SDK_INT >= 24)
                arrayOf("logcat", "-d", "-v", "time", "--pid=$pid")
            else
                arrayOf("logcat", "-d", "-v", "time")
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            val pidFiltered = if (Build.VERSION.SDK_INT >= 24) out else out.lines()
                .filter { it.contains(" $pid ") || it.contains("beam_") || it.contains("WebService")
                        || it.contains("moonraker_") || it.contains("KlipperInstance") }
                .joinToString("\n")
            val filtered = afterCleared(pidFiltered)
            if (filtered.isBlank()) "(logcat vazio — o buffer do sistema pode ter sido limpo)" else tail(filtered)
        } catch (t: Throwable) {
            Log.w("BeamLogs", "logcat failed", t)
            "(não consegui ler o logcat neste aparelho: ${t.message})"
        }
    }

    /** Drops logcat lines older than the last "clear logs" (timestamp prefixes compare lexically). */
    private fun afterCleared(text: String): String {
        val cutoff = clearedAt() ?: return text
        val len = LOGCAT_TIME_FORMAT.length
        var keep = true
        return text.lineSequence().filter { line ->
            if (line.length > len && line[2] == '-' && line[5] == ' ') keep = line.substring(0, len) > cutoff
            keep
        }.joinToString("\n")
    }

    private fun readTextOrEmpty(f: File): String =
        try { f.readText() } catch (t: Throwable) { "<erro ao ler ${f.name}: ${t.message}>" }

    private fun tail(s: String): String = LogText.tail(s)
}
