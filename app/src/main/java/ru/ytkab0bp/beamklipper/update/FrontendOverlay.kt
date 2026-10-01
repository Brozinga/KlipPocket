package ru.ytkab0bp.beamklipper.update

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

// How an in-app-downloaded frontend build (Fluidd/Mainsail/Voyager-UI) is
// extracted and swapped into place, kept free of Android so it can be
// unit-tested against real zip fixtures — mirrors BundleRules.kt's
// BundleFiles in spirit, but the swap itself must be safe to do while
// WebService.serveStatic() (a live web server) might be serving a request
// for the very directory being replaced, which BundleFiles.unpack()'s
// deleteRecursively()-then-repopulate approach is not.
object FrontendOverlay {
    // Shared by WebService (reads it) and FrontendUpdateChecker (writes it) —
    // a single constant so both sides can never drift apart on the path.
    const val OVERRIDE_DIR_NAME = "web_overrides"
    const val INSTALLED_VERSION_MARKER = ".installed_version"

    // Extraction happens into a fresh, not-yet-live directory (<frontend>.new),
    // so a failure here never touches whatever is currently being served.
    // Zip entries are untrusted (a network response, unlike the zip URL the
    // developer chose at build time in app/build.gradle's registerFrontendBundle),
    // so a "../" escape attempt is rejected rather than mirrored.
    fun extractZip(zipInput: InputStream, destDir: File) {
        val destCanonical = destDir.canonicalFile
        destDir.mkdirs()
        ZipInputStream(zipInput).use { zis ->
            var entry = zis.nextEntry
            val buf = ByteArray(64 * 1024)
            while (entry != null) {
                val target = File(destDir, entry.name)
                if (!target.canonicalFile.path.startsWith(destCanonical.path + File.separator) &&
                    target.canonicalFile != destCanonical
                ) {
                    throw SecurityException("Zip entry escapes destination: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos ->
                        while (true) {
                            val read = zis.read(buf)
                            if (read == -1) break
                            fos.write(buf, 0, read)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // Last step of extraction, into the not-yet-live dir, so "extraction
    // succeeded" and "the marker is present" are the same atomic fact from
    // swapIn's caller's point of view.
    fun writeInstalledVersionMarker(dir: File, version: String) {
        File(dir, INSTALLED_VERSION_MARKER).writeText(version)
    }

    fun activeVersionOverride(overrideRoot: File, frontend: String): String? {
        val marker = File(File(overrideRoot, frontend), INSTALLED_VERSION_MARKER)
        return if (marker.isFile) marker.readText().trim().takeIf { it.isNotBlank() } else null
    }

    // File.renameTo can't atomically overwrite a non-empty directory, so the
    // swap is two atomic renames (move the live dir aside, move the new dir
    // in) rather than a direct replace. There is a brief instant between the
    // two renames where "<frontend>" doesn't exist — cleanupOrphans() is the
    // self-heal for a process death exactly there.
    fun swapIn(overrideRoot: File, frontend: String, newDir: File): Boolean {
        val live = File(overrideRoot, frontend)
        val old = File(overrideRoot, "$frontend.old")
        old.deleteRecursively()

        val hadLive = live.exists()
        if (hadLive && !live.renameTo(old)) return false

        if (!newDir.renameTo(live)) {
            if (hadLive) old.renameTo(live) // best-effort rollback
            return false
        }

        old.deleteRecursively()
        return true
    }

    // Startup self-heal for a process kill mid-update, called once per
    // frontend before anything else touches the override tree.
    fun cleanupOrphans(overrideRoot: File, frontend: String) {
        File(overrideRoot, "$frontend.new").deleteRecursively()

        val live = File(overrideRoot, frontend)
        val old = File(overrideRoot, "$frontend.old")
        if (!live.exists() && old.exists()) {
            old.renameTo(live)
        } else {
            old.deleteRecursively()
        }
    }
}
