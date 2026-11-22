package ru.ytkab0bp.beamklipper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// The chelper library is compiled by the NDK from the vendored C sources and
// never on the device, so a .c file that Klipper or Kalico starts to need
// (like Kalico's steppersync.c in v2026.11.00) must be added to CMakeLists.txt
// by hand when the tree is re-synced. These compare the two lists.
class ChelperSourcesTest {
    private val cmake = File("CMakeLists.txt").readText()

    private fun cmakeSources(engine: String): Set<String> =
        Regex("src/main/$engine/klippy/chelper/(\\w+\\.c)").findAll(cmake).map { it.groupValues[1] }.toSet()

    private fun engineSources(engine: String): Set<String> {
        val init = File("src/main/$engine/klippy/chelper/__init__.py").readText()
        val block = init.substringAfter("SOURCE_FILES = [").substringBefore("]")
        return Regex("[\"'](\\w+\\.c)[\"']").findAll(block).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `klipper chelper is built from every source it lists`() {
        val wanted = engineSources("klipper")
        assertTrue(wanted.isNotEmpty())
        assertEquals(wanted, cmakeSources("klipper"))
    }

    @Test
    fun `kalico chelper is built from every source it lists`() {
        val wanted = engineSources("kalico")
        assertTrue(wanted.isNotEmpty())
        assertEquals(wanted, cmakeSources("kalico"))
    }
}
