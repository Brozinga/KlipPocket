package ru.ytkab0bp.beamklipper.service

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BootstrapScriptsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scripts = mapOf(
        "klippy" to BootstrapScripts.KLIPPY,
        "moonraker" to BootstrapScripts.MOONRAKER,
        "octoeverywhere" to BootstrapScripts.OCTOEVERYWHERE,
        "obico" to BootstrapScripts.OBICO
    )

    @Test
    fun `every bootstrap defines the main function Chaquopy calls`() {
        scripts.forEach { (name, src) -> assertTrue("$name has no main()", src.contains("def main():")) }
    }

    @Test
    fun `the klippy bootstrap puts the beam extensions on the path`() {
        assertTrue(BootstrapScripts.KLIPPY.contains("beam_ext"))
        assertTrue(BootstrapScripts.MOONRAKER.contains("beam_ext"))
    }

    @Test
    fun `the companions run their vendored module`() {
        assertTrue(BootstrapScripts.OCTOEVERYWHERE.contains("moonraker_octoeverywhere"))
        assertTrue(BootstrapScripts.OBICO.contains("moonraker_obico.app"))
    }

    @Test
    fun `every bootstrap is valid python`() {
        val python = listOf("python3", "python").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").redirectErrorStream(true).start().waitFor() == 0 }.getOrDefault(false)
        }
        assumeTrue("no python on this machine", python != null)
        scripts.forEach { (name, src) ->
            val f = File(tmp.root, "$name.py").apply { writeText(src) }
            val p = ProcessBuilder(python, "-m", "py_compile", f.path).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            assertTrue("$name: $out", p.waitFor() == 0)
        }
    }
}
