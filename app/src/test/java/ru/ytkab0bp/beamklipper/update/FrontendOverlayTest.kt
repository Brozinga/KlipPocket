package ru.ytkab0bp.beamklipper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FrontendOverlayTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun buildZip(vararg entries: Pair<String, String?>): ByteArrayInputStream {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                content?.let { zos.write(it.toByteArray()) }
                zos.closeEntry()
            }
        }
        return ByteArrayInputStream(bos.toByteArray())
    }

    @Test
    fun `extractZip lays out files and directories`() {
        val dest = tmp.newFolder("out")
        val zip = buildZip(
            "index.html" to "<html></html>",
            "assets/" to null,
            "assets/app.js" to "console.log(1)"
        )
        FrontendOverlay.extractZip(zip, dest)
        assertEquals("<html></html>", File(dest, "index.html").readText())
        assertEquals("console.log(1)", File(dest, "assets/app.js").readText())
    }

    @Test(expected = SecurityException::class)
    fun `extractZip rejects an entry that escapes the destination`() {
        val dest = tmp.newFolder("out")
        val zip = buildZip("../evil.txt" to "pwned")
        FrontendOverlay.extractZip(zip, dest)
    }

    @Test
    fun `writeInstalledVersionMarker and activeVersionOverride round trip`() {
        val overrideRoot = tmp.newFolder("overrides")
        val frontendDir = File(overrideRoot, "fluidd").apply { mkdirs() }
        FrontendOverlay.writeInstalledVersionMarker(frontendDir, "v1.38.0")
        assertEquals("v1.38.0", FrontendOverlay.activeVersionOverride(overrideRoot, "fluidd"))
    }

    @Test
    fun `activeVersionOverride is null when no override exists`() {
        val overrideRoot = tmp.newFolder("overrides")
        assertNull(FrontendOverlay.activeVersionOverride(overrideRoot, "fluidd"))
    }

    @Test
    fun `swapIn installs a first-ever override with no pre-existing live dir`() {
        val overrideRoot = tmp.newFolder("overrides")
        val newDir = File(overrideRoot, "fluidd.new").apply { mkdirs() }
        File(newDir, "index.html").writeText("new")

        assertTrue(FrontendOverlay.swapIn(overrideRoot, "fluidd", newDir))

        assertEquals("new", File(overrideRoot, "fluidd/index.html").readText())
        assertFalse(File(overrideRoot, "fluidd.old").exists())
        assertFalse(newDir.exists())
    }

    @Test
    fun `swapIn replaces an existing populated live dir atomically`() {
        val overrideRoot = tmp.newFolder("overrides")
        val live = File(overrideRoot, "fluidd").apply { mkdirs() }
        File(live, "index.html").writeText("old")
        File(live, "stale.js").writeText("stale")

        val newDir = File(overrideRoot, "fluidd.new").apply { mkdirs() }
        File(newDir, "index.html").writeText("new")

        assertTrue(FrontendOverlay.swapIn(overrideRoot, "fluidd", newDir))

        assertEquals("new", File(overrideRoot, "fluidd/index.html").readText())
        assertFalse(File(overrideRoot, "fluidd/stale.js").exists()) // fully old-then-fully-new, never mixed
        assertFalse(File(overrideRoot, "fluidd.old").exists())
        assertFalse(newDir.exists())
    }

    @Test
    fun `cleanupOrphans deletes a stray new dir from an aborted update`() {
        val overrideRoot = tmp.newFolder("overrides")
        File(overrideRoot, "fluidd").apply { mkdirs() }
        File(overrideRoot, "fluidd.new").apply { mkdirs() }

        FrontendOverlay.cleanupOrphans(overrideRoot, "fluidd")

        assertTrue(File(overrideRoot, "fluidd").exists())
        assertFalse(File(overrideRoot, "fluidd.new").exists())
    }

    @Test
    fun `cleanupOrphans deletes a leftover old dir when live already exists`() {
        val overrideRoot = tmp.newFolder("overrides")
        File(overrideRoot, "fluidd").apply { mkdirs() }
        File(overrideRoot, "fluidd.old").apply { mkdirs() }

        FrontendOverlay.cleanupOrphans(overrideRoot, "fluidd")

        assertTrue(File(overrideRoot, "fluidd").exists())
        assertFalse(File(overrideRoot, "fluidd.old").exists())
    }

    @Test
    fun `cleanupOrphans restores old to live when killed between the two renames`() {
        val overrideRoot = tmp.newFolder("overrides")
        val old = File(overrideRoot, "fluidd.old").apply { mkdirs() }
        File(old, "index.html").writeText("survivor")
        // live ("fluidd") is missing, simulating a death right after the
        // first rename (live -> .old) but before the second (.new -> live).

        FrontendOverlay.cleanupOrphans(overrideRoot, "fluidd")

        assertEquals("survivor", File(overrideRoot, "fluidd/index.html").readText())
        assertFalse(File(overrideRoot, "fluidd.old").exists())
    }
}
