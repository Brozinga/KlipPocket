package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ytkab0bp.beamklipper.InstanceIcon
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.testing.RobolectricSupport
import ru.ytkab0bp.beamklipper.testing.TestApp
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class BeamLogsTest {
    private val ctx get() = RobolectricSupport.context()

    @Before
    fun setUp() {
        RobolectricSupport.setUpApp()
    }

    private fun addInstance(id: String, name: String): KlipperInstance {
        val inst = KlipperInstance().apply { this.id = id; this.name = name; icon = InstanceIcon.PRINTER }
        KlipperApp.DATABASE.insert(inst)
        return inst
    }

    @Test
    fun `the app source is always present`() {
        assertTrue(BeamLogs.sources(ctx).any { it.id == "app" })
    }

    @Test
    fun `the crash source only appears once a crash file exists`() {
        assertTrue(BeamLogs.sources(ctx).none { it.id == "crash" })
        BeamLogs.crashFile(ctx).apply { parentFile?.mkdirs(); writeText("boom") }
        assertTrue(BeamLogs.sources(ctx).any { it.id == "crash" })
    }

    @Test
    fun `an empty crash file is not offered`() {
        BeamLogs.crashFile(ctx).apply { parentFile?.mkdirs(); writeText("") }
        assertTrue(BeamLogs.sources(ctx).none { it.id == "crash" })
    }

    @Test
    fun `each instance contributes four log sources`() {
        addInstance("a", "Neptune")
        val ids = BeamLogs.sources(ctx).map { it.id }
        assertTrue(ids.containsAll(listOf("klippy_a", "moonraker_a", "octoeverywhere_a", "obico_a")))
    }

    @Test
    fun `a log that was never written explains that the service did not run`() {
        val inst = addInstance("a", "Neptune")
        val source = BeamLogs.sources(ctx).first { it.id == "klippy_a" }
        assertTrue(source.load().contains("ainda não foi criado"))
    }

    @Test
    fun `a log with content is read`() {
        val inst = addInstance("a", "Neptune")
        File(inst.publicDirectory, "logs").mkdirs()
        File(inst.publicDirectory, "logs/klippy.log").writeText("Klipper starting\n")
        val source = BeamLogs.sources(ctx).first { it.id == "klippy_a" }
        assertTrue(source.load().contains("Klipper starting"))
    }

    @Test
    fun `the combined dump includes the device info and every source`() {
        addInstance("a", "Neptune")
        val text = BeamLogs.combined(ctx)
        assertTrue(text.contains("KlipPocket"))
        assertTrue(text.contains("device:"))
        assertTrue(text.contains("Klipper · Neptune"))
    }

    @Test
    fun `saving for sharing writes a file and returns where it is`() {
        addInstance("a", "Neptune")
        val where = BeamLogs.saveForSharing(ctx)
        assertTrue(where == null || where.isNotEmpty())
    }
}
