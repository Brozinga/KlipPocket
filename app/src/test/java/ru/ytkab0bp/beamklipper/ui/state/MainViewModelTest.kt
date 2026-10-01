package ru.ytkab0bp.beamklipper.ui.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.testing.RobolectricSupport
import ru.ytkab0bp.beamklipper.testing.TestApp

// Only the paths that never touch a real Android service (start()/stop() bind
// real services and are out of scope for a unit test): the decision logic
// itself is covered by InstanceActions/InstanceRulesTest.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class MainViewModelTest {
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        RobolectricSupport.setUpApp()
        vm = MainViewModel(RobolectricSupport.context())
    }

    private fun instance(id: String, name: String) =
        KlipperInstance().apply { this.id = id; this.name = name }

    @Test
    fun `nothing is running when there are no instances`() {
        assertFalse(vm.anyRunning)
    }

    @Test
    fun `deleting an idle instance removes it from the database without starting anything`() {
        val inst = instance("a", "Printer")
        KlipperApp.DATABASE.insert(inst)
        vm.delete(inst)
        val deadline = System.currentTimeMillis() + 5000
        while (KlipperApp.DATABASE.getInstances().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(KlipperApp.DATABASE.getInstances().isEmpty())
    }

    @Test
    fun `toggling an instance with no id is a no op`() {
        vm.toggle(KlipperInstance().apply { id = null; name = "x" })
    }

    @Test
    fun `run stop all does nothing without any instance`() {
        vm.runStopAll()
    }
}
