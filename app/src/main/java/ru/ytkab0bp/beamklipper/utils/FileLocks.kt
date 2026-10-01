package ru.ytkab0bp.beamklipper.utils

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

// Locks shared by the app's processes (each Klippy/Moonraker/companion service
// runs in its own process), free of Android so they can be unit-tested.
object FileLocks {
    // OS-level lock on [lockFile] for the duration of [action].
    fun withFileLock(lockFile: File, action: () -> Unit) {
        var raf: RandomAccessFile? = null
        var lock: FileLock? = null
        try {
            raf = RandomAccessFile(lockFile, "rw")
            lock = raf.channel.lock()
            action()
        } finally {
            try { lock?.release() } catch (_: Throwable) {}
            try { raf?.close() } catch (_: Throwable) {}
        }
    }

    fun isProcessAlive(pid: Int): Boolean = try {
        val f = File("/proc/$pid/cmdline")
        f.exists() && f.readBytes().isNotEmpty()
    } catch (_: Throwable) {
        false
    }

    // A lock file holding a pid is stale when that process is gone; one with
    // unreadable content is stale when it is older than [staleAfterMs].
    fun isStale(content: String, ageMs: Long, staleAfterMs: Long, alive: (Int) -> Boolean = ::isProcessAlive): Boolean {
        val staleByTime = ageMs > staleAfterMs
        val staleByPid = if (content.isNotEmpty() && content.all(Char::isDigit)) {
            try { !alive(content.toInt()) } catch (_: Throwable) { true }
        } else staleByTime
        return staleByTime || staleByPid
    }

    // Two instances starting at once must not pick the same Moonraker port:
    // a create-if-absent lock file holding our pid, retried until [timeoutMs].
    // A stale lock is taken over; when nothing works the action still runs.
    fun withMoonrakerPortLock(
        lockFile: File,
        pid: Int,
        timeoutMs: Long = 15_000,
        staleAfterMs: Long = 10_000,
        action: () -> Unit
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var acquired = false
        while (System.currentTimeMillis() < deadline && !acquired) {
            try {
                if (lockFile.createNewFile()) {
                    lockFile.writeText("$pid")
                    acquired = true
                    break
                }
            } catch (_: Throwable) {}
            try {
                val content = try { lockFile.readText().trim() } catch (_: Throwable) { "" }
                val age = System.currentTimeMillis() - lockFile.lastModified()
                if (isStale(content, age, staleAfterMs)) lockFile.delete()
            } catch (_: Throwable) {}
            try { Thread.sleep(30) } catch (_: InterruptedException) { break }
        }
        if (!acquired) {
            try { lockFile.delete() } catch (_: Throwable) {}
            try {
                if (lockFile.createNewFile()) {
                    lockFile.writeText("$pid")
                    acquired = true
                }
            } catch (_: Throwable) {}
        }
        try {
            action()
        } finally {
            if (acquired) {
                try { lockFile.delete() } catch (_: Throwable) {}
            }
        }
    }
}
