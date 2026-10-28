package ru.ytkab0bp.beamklipper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.ytkab0bp.beamklipper.update.VersionCompare
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException

// The patches are only worth anything while the vendored sources still carry
// the exact text they replace, so these read the real files under src/main.
class BundlePatchesTest {
    private fun source(path: String) = File("src/main/$path").readText()

    @Test
    fun `klipper and kalico chelper still carry the library placeholder`() {
        assertTrue(source("klipper/klippy/chelper/__init__.py").contains(BundlePatches.DEST_LIB))
        assertTrue(source("kalico/klippy/chelper/__init__.py").contains(BundlePatches.DEST_LIB))
    }

    @Test
    fun `kalico keeps the changes the app makes to its host`() {
        // Re-applied by hand each time the vendored tree is re-synced.
        assertTrue(source("kalico/klippy/printer.py").contains("--beam-input"))
        assertTrue(source("kalico/klippy/gcode.py").contains("self.is_beam"))
        assertTrue(source("kalico/klippy/mcu.py").contains("cfg_restart_method"))
        // Python on Android has no os.getloadavg(), which would end the run.
        assertFalse(Regex("(?m)^\\s+self\\.last_load_avg = os\\.getloadavg").containsMatchIn(source("kalico/klippy/extras/statistics.py")))
    }

    @Test
    fun `the kalico version marker is a plain release tag`() {
        val tag = source("kalico/kalico_version").trim()
        assertEquals(tag, VersionCompare.highestTag(listOf(tag)))
    }

    @Test
    fun `klipper and kalico mcu still carry the serial directory placeholder`() {
        assertTrue(source("klipper/klippy/mcu.py").contains(BundlePatches.TTY_PATH))
        assertTrue(source("kalico/klippy/mcu.py").contains(BundlePatches.TTY_PATH))
    }

    @Test
    fun `kalico's mcu maps any device path to the app's virtual serial like klipper's`() {
        val kalico = source("kalico/klippy/mcu.py")
        assertTrue(kalico.contains("startswith(\"/dev/\")"))
        assertTrue(kalico.contains("\"<auto>\""))
        // The baud stays 0, so the link is opened as a pipe, with no port lock.
        assertFalse(Regex("(?m)^\\s+self\\._baud = config\\.getint").containsMatchIn(kalico))
    }

    @Test
    fun `klipper's resonance tester still carries the temp directory placeholder`() {
        assertTrue(source("klipper/klippy/extras/resonance_tester.py").contains(BundlePatches.TEMP_PATH))
    }

    @Test
    fun `moonraker's sysfs helper still has the tty path the app redirects`() {
        assertTrue(source("moonraker/moonraker/utils/sysfs_devs.py").contains(BundlePatches.SYSFS_TTY_ORIGINAL))
    }

    @Test
    fun `the tty path is redirected into the app's serial folder`() {
        val patched = source("moonraker/moonraker/utils/sysfs_devs.py")
            .replace(BundlePatches.SYSFS_TTY_ORIGINAL, BundlePatches.sysfsTty("/data/files/serial"))
        assertTrue(patched.contains("TTY_PATH = \"/data/files/serial\""))
        assertFalse(patched.contains("/sys/class/tty"))
    }

    @Test
    fun `the obico board_id function is still what the patch expects`() {
        assertTrue(source("obico/moonraker_obico/utils.py").contains(BundlePatches.OBICO_BOARD_ID_ORIGINAL))
    }

    @Test
    fun `the board_id patch guards the devicetree read`() {
        val patched = BundlePatches.patchObicoBoardId(source("obico/moonraker_obico/utils.py"))
        assertFalse(patched.contains(BundlePatches.OBICO_BOARD_ID_ORIGINAL))
        assertTrue(patched.contains(BundlePatches.OBICO_BOARD_ID_PATCHED))
        assertTrue(patched.contains("except OSError:"))
    }

    @Test
    fun `the obico discovery function is still what the patch expects`() {
        assertTrue(source("obico/moonraker_obico/printer_discovery.py").contains(BundlePatches.OBICO_LINK_STATUS_ORIGINAL))
    }

    @Test
    fun `the link status patch writes the json file the app reads`() {
        val patched = BundlePatches.patchObicoLinkStatus(source("obico/moonraker_obico/printer_discovery.py"))
        assertTrue(patched.contains("obico_link_status.json"))
        assertTrue(patched.contains("one_time_passcode"))
        assertEquals(1, patched.split("obico_link_status.json").size - 1)
    }

    @Test
    fun `the obico thermal presets loop is still what the patch expects`() {
        assertTrue(source("obico/moonraker_obico/moonraker_conn.py").contains(BundlePatches.OBICO_THERMAL_PRESETS_ORIGINAL))
    }

    @Test
    fun `the thermal presets patch tolerates a list as well as a dict`() {
        val patched = BundlePatches.patchObicoThermalPresets(source("obico/moonraker_obico/moonraker_conn.py"))
        assertFalse(patched.contains(BundlePatches.OBICO_THERMAL_PRESETS_ORIGINAL))
        assertTrue(patched.contains("isinstance(presets_raw, dict)"))
    }

    @Test
    fun `the obico include_cfgs setup is still what the patch expects`() {
        assertTrue(source("obico/moonraker_obico/moonraker_conn.py").contains(BundlePatches.OBICO_INCLUDE_CFGS_ORIGINAL))
    }

    @Test
    fun `the include_cfgs patch drops the subprocess call and writes the include line itself`() {
        val patched = BundlePatches.patchObicoIncludeCfgs(source("obico/moonraker_obico/moonraker_conn.py"))
        assertFalse(patched.contains(BundlePatches.OBICO_INCLUDE_CFGS_ORIGINAL))
        assertFalse(patched.contains("ensure_include_cfgs.sh"))
        assertTrue(patched.contains("os.symlink(macro_cfg, linked_macro_cfg)"))
        assertTrue(patched.contains("[include moonraker_obico_macros.cfg]"))
    }

    @Test
    fun `both moonraker_conn patches can be applied together`() {
        val patched = BundlePatches.patchObicoIncludeCfgs(BundlePatches.patchObicoThermalPresets(source("obico/moonraker_obico/moonraker_conn.py")))
        assertTrue(patched.contains("isinstance(presets_raw, dict)"))
        assertTrue(patched.contains("os.symlink(macro_cfg, linked_macro_cfg)"))
    }

    @Test
    fun `the vendored obico macros file the include_cfgs patch links to actually exists`() {
        val macros = File("src/main/obico/include_cfgs/moonraker_obico_macros.cfg")
        assertTrue(macros.exists())
        assertTrue(macros.readText().contains("[gcode_macro OBICO_LINK_STATUS]"))
    }

    @Test
    fun `beam_beeper and beam_camera still carry the web port file placeholder`() {
        // klipper_beam_ext is the single canonical source; the build copies it
        // verbatim into klippy/extras/ for both klipper and kalico so Klipper's
        // config loader (extras.<name>) can resolve [beam_beeper]/[beam_camera].
        assertTrue(source("klipper_beam_ext/beam_beeper.py").contains(BundlePatches.WEB_PORT_FILE))
        assertTrue(source("klipper_beam_ext/beam_camera.py").contains(BundlePatches.WEB_PORT_FILE))
        // kalico's generated copy is tracked in git (like print_recovery's),
        // so it stays available even without running a build first.
        assertTrue(source("assets/kalico/klippy/extras/beam_beeper.py").contains(BundlePatches.WEB_PORT_FILE))
        assertTrue(source("assets/kalico/klippy/extras/beam_camera.py").contains(BundlePatches.WEB_PORT_FILE))
    }

    @Test
    fun `patching twice does not duplicate the change`() {
        val once = BundlePatches.patchObicoLinkStatus(source("obico/moonraker_obico/printer_discovery.py"))
        // the patched text no longer contains the original, so a second pass is a no-op
        assertEquals(once, BundlePatches.patchObicoLinkStatus(once))
        val board = BundlePatches.patchObicoBoardId(source("obico/moonraker_obico/utils.py"))
        assertEquals(board, BundlePatches.patchObicoBoardId(board))
        val presets = BundlePatches.patchObicoThermalPresets(source("obico/moonraker_obico/moonraker_conn.py"))
        assertEquals(presets, BundlePatches.patchObicoThermalPresets(presets))
        val includeCfgs = BundlePatches.patchObicoIncludeCfgs(source("obico/moonraker_obico/moonraker_conn.py"))
        assertEquals(includeCfgs, BundlePatches.patchObicoIncludeCfgs(includeCfgs))
    }

    @Test
    fun `happy hare is vendored whole, with no leftover of the old single-file module`() {
        // A stray extras/mmu.py (v3) next to the mmu/ package is imported instead of the
        // package whenever the package has no __init__.py, and nothing of v4 then loads.
        assertFalse(File("src/main/happyhare/extras/mmu.py").exists())
        assertTrue(File("src/main/happyhare/extras/mmu/__init__.py").isFile)
        assertTrue(File("src/main/happyhare/extras/mmu/unit/__init__.py").isFile)
        assertTrue(File("src/main/happyhare/components/mmu_server.py").isFile)
        assertTrue(Regex("(?m)^VERSION = \"4\\.").containsMatchIn(source("happyhare/extras/mmu/mmu_constants.py")))
    }

    @Test
    fun `klipper and kalico save_variables still carry the line the patch builds on`() {
        assertTrue(source("klipper/klippy/extras/save_variables.py").contains(BundlePatches.SAVE_VARIABLES_KLIPPER_ORIGINAL))
        assertTrue(source("kalico/klippy/extras/save_variables.py").contains(BundlePatches.SAVE_VARIABLES_KALICO_ORIGINAL))
    }

    @Test
    fun `the save_variables patch re-roots the file into the config folder`() {
        listOf("klipper", "kalico").forEach { engine ->
            val patched = BundlePatches.patchSaveVariables(source("$engine/klippy/extras/save_variables.py"))
            assertTrue(engine, patched.contains(BundlePatches.SAVE_VARIABLES_MARKER))
            assertTrue(engine, patched.contains("get_start_args()['config_file']"))
            // applying it again changes nothing
            assertEquals(engine, patched, BundlePatches.patchSaveVariables(patched))
        }
    }

    @Test
    fun `a variables file under a folder that is missing here lands next to printer_cfg`() {
        val python = listOf("python3", "python").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").redirectErrorStream(true).start().waitFor() == 0 }.getOrDefault(false)
        } ?: return
        val dir = createTempDir()
        try {
            val harness = """
                import importlib.util, os, sys
                class Gcode:
                    def register_command(self, *a, **k): pass
                class Executor:
                    def submit(self, fn, *a): return fn(*a)
                class Aio:
                    def allocate_executor(self, name): return Executor()
                class Printer:
                    command_error = Exception
                    def get_start_args(self): return {'config_file': sys.argv[2]}
                    def load_object(self, config, name): return Aio()
                    def lookup_object(self, name): return Gcode()
                class Config:
                    error = Exception
                    def __init__(self, filename): self.filename = filename
                    def get_printer(self): return Printer()
                    def get(self, key): return self.filename
                spec = importlib.util.spec_from_file_location('sv', sys.argv[1])
                mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
                for name in sys.argv[3:]:
                    print(mod.SaveVariables(Config(name)).filename)
            """.trimIndent()
            val h = File(dir, "harness.py").apply { writeText(harness) }
            val cfgDir = File(dir, "inst/config").apply { mkdirs() }
            val printerCfg = File(cfgDir, "printer.cfg").apply { writeText("") }
            val here = File(dir, "elsewhere").apply { mkdirs() }
            listOf("klipper", "kalico").forEach { engine ->
                val sv = File(dir, "$engine.py").apply { writeText(BundlePatches.patchSaveVariables(source("$engine/klippy/extras/save_variables.py"))) }
                val p = ProcessBuilder(
                    python, h.path, sv.path, printerCfg.path,
                    "/no/such/printer_data/config/mmu/mmu_vars.cfg",
                    File(here, "vars.cfg").path
                ).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText().lines().filter { it.isNotBlank() }
                val code = p.waitFor()
                assertEquals("$engine: $out", 0, code)
                assertEquals(File(cfgDir, "mmu/mmu_vars.cfg").path, out[0])
                assertTrue(File(cfgDir, "mmu/mmu_vars.cfg").isFile)
                // a folder that exists is left alone
                assertEquals(File(here, "vars.cfg").path, out[1])
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the patched python is still valid`() {
        val python = listOf("python3", "python").firstOrNull {
            runCatching { ProcessBuilder(it, "--version").redirectErrorStream(true).start().waitFor() == 0 }.getOrDefault(false)
        } ?: return
        val dir = createTempDir()
        try {
            listOf(
                "utils.py" to BundlePatches.patchObicoBoardId(source("obico/moonraker_obico/utils.py")),
                "printer_discovery.py" to BundlePatches.patchObicoLinkStatus(source("obico/moonraker_obico/printer_discovery.py")),
                "moonraker_conn.py" to BundlePatches.patchObicoIncludeCfgs(BundlePatches.patchObicoThermalPresets(source("obico/moonraker_obico/moonraker_conn.py"))),
                "save_variables_klipper.py" to BundlePatches.patchSaveVariables(source("klipper/klippy/extras/save_variables.py")),
                "save_variables_kalico.py" to BundlePatches.patchSaveVariables(source("kalico/klippy/extras/save_variables.py"))
            ).forEach { (name, src) ->
                val f = File(dir, name).apply { writeText(src) }
                val p = ProcessBuilder(python, "-m", "py_compile", f.path).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals("$name: $out", 0, p.waitFor())
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

class BundleFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a new file is written`() {
        val f = File(tmp.root, "a.txt")
        BundleFiles.writeIfChanged(f, "hello".toByteArray())
        assertEquals("hello", f.readText())
    }

    @Test
    fun `identical content is not rewritten`() {
        val f = tmp.newFile("a.txt").apply { writeText("same"); setLastModified(1000) }
        BundleFiles.writeIfChanged(f, "same".toByteArray())
        assertEquals(1000L, f.lastModified())
    }

    @Test
    fun `changed content replaces the file and leaves no temp file`() {
        val f = tmp.newFile("a.txt").apply { writeText("old") }
        BundleFiles.writeIfChanged(f, "new".toByteArray())
        assertEquals("new", f.readText())
        assertFalse(File(tmp.root, "a.txt.tmp").exists())
    }

    private fun assets(vararg entries: Pair<String, String>): (String) -> java.io.InputStream {
        val map = mapOf(*entries)
        return { name -> ByteArrayInputStream((map[name] ?: throw FileNotFoundException(name)).toByteArray()) }
    }

    @Test
    fun `unpack copies every listed file into its folder`() {
        BundleFiles.unpack(
            assets("klipper/a.py" to "A", "klipper/sub/b.py" to "B"),
            listOf("a.py", "sub/b.py"), tmp.root, "klipper"
        )
        assertEquals("A", File(tmp.root, "klipper/a.py").readText())
        assertEquals("B", File(tmp.root, "klipper/sub/b.py").readText())
    }

    @Test
    fun `unpack replaces what was there before`() {
        File(tmp.root, "klipper").mkdirs()
        File(tmp.root, "klipper/stale.py").writeText("old")
        BundleFiles.unpack(assets("klipper/a.py" to "A"), listOf("a.py"), tmp.root, "klipper")
        assertFalse(File(tmp.root, "klipper/stale.py").exists())
        assertTrue(File(tmp.root, "klipper/a.py").exists())
    }

    @Test
    fun `a key missing from the index is removed`() {
        File(tmp.root, "obico").mkdirs()
        File(tmp.root, "obico/x.py").writeText("old")
        BundleFiles.unpack(assets(), null, tmp.root, "obico")
        assertFalse(File(tmp.root, "obico").exists())
    }

    @Test
    fun `binary files are copied byte for byte`() {
        val bytes = byteArrayOf(0, 1, 2, -1, -128, 127)
        BundleFiles.unpack({ ByteArrayInputStream(bytes) }, listOf("lib.so"), tmp.root, "klipper")
        assertArrayEquals(bytes, File(tmp.root, "klipper/lib.so").readBytes())
    }
}
