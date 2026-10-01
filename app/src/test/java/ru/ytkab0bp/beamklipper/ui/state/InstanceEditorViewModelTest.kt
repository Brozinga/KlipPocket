package ru.ytkab0bp.beamklipper.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.StateFlow

// InstanceEditorViewModel does its work on Dispatchers.IO (a real thread
// pool), so these tests wait for it to actually finish instead of using a
// test dispatcher.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class InstanceEditorViewModelTest {
    private lateinit var vm: InstanceEditorViewModel

    @Before
    fun setUp() {
        RobolectricSupport.setUpApp()
        val configDir = File(RobolectricSupport.context().filesDir, "klipper/config").apply { mkdirs() }
        File(configDir, "generic-a.cfg").writeText("[mcu]")
        File(configDir, "example-cartesian.cfg").writeText("[mcu]")
        vm = InstanceEditorViewModel(RobolectricSupport.context())
    }

    private fun <T> StateFlow<T>.awaitNonNull(timeoutMs: Long = 5000): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (value == null && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return value ?: error("timed out waiting for a value")
    }

    private fun awaitSave(name: String, autostart: Boolean = false) {
        val latch = CountDownLatch(1)
        vm.save(name, autostart) { latch.countDown() }
        assertTrue("save() did not finish", latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `creating loads the example config and a default name`() {
        vm.loadForCreate()
        assertEquals("Printer 1", vm.defaultName.awaitNonNull())
        assertEquals("example-cartesian.cfg", vm.configFile.awaitNonNull())
        assertEquals(listOf("example-cartesian.cfg", "generic-a.cfg"), vm.filesList.value)
        assertNull(vm.editingInstance.value)
    }

    @Test
    fun `the default name skips profiles already saved`() {
        KlipperApp.DATABASE.insert(KlipperInstance().apply { id = "a"; name = "Printer 1" })
        vm.loadForCreate()
        assertEquals("Printer 2", vm.defaultName.awaitNonNull())
    }

    @Test
    fun `selecting a config updates it`() {
        vm.loadForCreate()
        vm.defaultName.awaitNonNull()
        vm.selectConfig("generic-a.cfg")
        assertEquals("generic-a.cfg", vm.configFile.value)
    }

    @Test
    fun `saving a new profile copies the chosen config and inserts it`() {
        vm.loadForCreate()
        vm.configFile.awaitNonNull()
        awaitSave("My Printer", autostart = true)
        val saved = KlipperApp.DATABASE.getInstances().single()
        assertEquals("My Printer", saved.name)
        assertTrue(saved.autostart)
        assertTrue(File(saved.publicDirectory, "config/printer.cfg").exists())
    }

    @Test
    fun `an empty name gets the default name`() {
        vm.loadForCreate()
        vm.configFile.awaitNonNull()
        awaitSave("   ")
        assertEquals("Printer 1", KlipperApp.DATABASE.getInstances().single().name)
    }

    @Test
    fun `a duplicate name is disambiguated`() {
        KlipperApp.DATABASE.insert(KlipperInstance().apply { id = "a"; name = "Voron" })
        vm.loadForCreate()
        vm.configFile.awaitNonNull()
        awaitSave("Voron")
        assertEquals(setOf("Voron", "Voron 2"), KlipperApp.DATABASE.getInstances().map { it.name }.toSet())
    }

    @Test
    fun `nothing is saved once every slot is used`() {
        repeat(KlipperInstance.SLOTS_COUNT) { KlipperApp.DATABASE.insert(KlipperInstance().apply { id = "p$it"; name = "P$it" }) }
        vm.loadForCreate()
        vm.configFile.awaitNonNull()
        awaitSave("Extra")
        assertEquals(KlipperInstance.SLOTS_COUNT, KlipperApp.DATABASE.getInstances().size)
    }

    @Test
    fun `editing loads the existing profile and keeps its own name on save`() {
        val inst = KlipperInstance().apply { id = "a"; name = "Ender"; icon = InstanceIcon.HOME }
        KlipperApp.DATABASE.insert(inst)
        vm.loadForEdit(inst)
        assertEquals(inst, vm.editingInstance.value)
        assertEquals("Ender", vm.defaultName.value)
        vm.configFile.awaitNonNull()
        awaitSave("Ender", autostart = true)
        val saved = KlipperApp.DATABASE.getInstances().single()
        assertEquals("Ender", saved.name)
        assertTrue(saved.autostart)
    }

    @Test
    fun `renaming during edit still avoids a clash with another profile`() {
        val a = KlipperInstance().apply { id = "a"; name = "Ender" }
        val b = KlipperInstance().apply { id = "b"; name = "Voron" }
        KlipperApp.DATABASE.insert(a)
        KlipperApp.DATABASE.insert(b)
        vm.loadForEdit(a)
        vm.configFile.awaitNonNull()
        awaitSave("Voron")
        val names = KlipperApp.DATABASE.getInstances().map { it.name }.toSet()
        assertTrue(names.contains("Voron"))
        assertTrue(names.contains("Voron 2"))
    }

    @Test
    fun `the saving flag settles back to false after a new profile is created`() {
        vm.loadForCreate()
        vm.configFile.awaitNonNull()
        assertFalse(vm.saving.value)
        awaitSave("X")
        assertFalse(vm.saving.value)
    }
}
