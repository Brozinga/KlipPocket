package ru.ytkab0bp.beamklipper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.ytkab0bp.beamklipper.KlipperInstance.State

class InstanceRulesTest {
    @Test
    fun `the first instance takes slot 0`() {
        assertEquals(0, SlotAllocator.allocate("a", emptyMap(), 4))
    }

    @Test
    fun `an instance keeps the slot it already has`() {
        assertEquals(2, SlotAllocator.allocate("a", mapOf("a" to 2, "b" to 0), 4))
    }

    @Test
    fun `a new instance takes the lowest free slot`() {
        assertEquals(1, SlotAllocator.allocate("c", mapOf("a" to 0, "b" to 2), 4))
        assertEquals(0, SlotAllocator.allocate("c", mapOf("a" to 1, "b" to 2), 4))
        assertEquals(3, SlotAllocator.allocate("d", mapOf("a" to 0, "b" to 1, "c" to 2), 4))
    }

    @Test
    fun `with every slot taken a new instance cannot start`() {
        try {
            SlotAllocator.allocate("e", mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 3), 4)
            fail("expected out of slots")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("out of slots"))
        }
    }

    @Test
    fun `an instance that already holds a slot starts even when all are taken`() {
        assertEquals(3, SlotAllocator.allocate("d", mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 3), 4))
    }

    @Test
    fun `the first death restarts the service`() {
        val d = WatchdogPolicy.decide(previousAttempts = 0, lastRunningMs = 0, nowMs = 1000)
        assertEquals(1, d.attempt)
        assertTrue(d.shouldRestart)
        assertFalse(d.wasShort)
    }

    @Test
    fun `it restarts up to three times after a healthy run`() {
        for (previous in 0..2) {
            assertTrue("attempt ${previous + 1}", WatchdogPolicy.decide(previous, 0, 60_000).shouldRestart)
        }
        assertFalse(WatchdogPolicy.decide(3, 0, 60_000).shouldRestart)
    }

    @Test
    fun `dying again within 8 seconds is a crash loop and gives up`() {
        val d = WatchdogPolicy.decide(previousAttempts = 1, lastRunningMs = 10_000, nowMs = 15_000)
        assertTrue(d.wasShort)
        assertFalse(d.shouldRestart)
    }

    @Test
    fun `a first death right after start is not a crash loop`() {
        assertFalse(WatchdogPolicy.decide(0, 10_000, 10_500).wasShort)
    }

    @Test
    fun `running longer than 8 seconds is not short`() {
        assertFalse(WatchdogPolicy.decide(1, 10_000, 18_000).wasShort)
        assertTrue(WatchdogPolicy.decide(1, 10_000, 17_999).wasShort)
    }

    @Test
    fun `the restart delay grows with each attempt`() {
        assertEquals(1700L, WatchdogPolicy.restartDelayMs(1))
        assertEquals(2900L, WatchdogPolicy.restartDelayMs(2))
        assertEquals(4100L, WatchdogPolicy.restartDelayMs(3))
        assertEquals(1500L, WatchdogPolicy.restartDelayMs(0))
    }

    @Test
    fun `a reconnect gives one attempt back but never goes below zero`() {
        assertEquals(1, WatchdogPolicy.attemptsAfterReconnect(2))
        assertEquals(0, WatchdogPolicy.attemptsAfterReconnect(0))
    }

    @Test
    fun `running and starting instances are active`() {
        assertTrue(InstanceActions.isActive(State.RUNNING))
        assertTrue(InstanceActions.isActive(State.STARTING))
        assertFalse(InstanceActions.isActive(State.IDLE))
        assertFalse(InstanceActions.isActive(State.STOPPING))
    }

    @Test
    fun `an idle instance starts only with a free slot`() {
        assertTrue(InstanceActions.canStart(State.IDLE, hasFreeSlots = true))
        assertFalse(InstanceActions.canStart(State.IDLE, hasFreeSlots = false))
    }

    @Test
    fun `an instance that is stopping can be started again`() {
        assertTrue(InstanceActions.canStart(State.STOPPING, hasFreeSlots = false))
    }

    @Test
    fun `an active instance cannot be started`() {
        assertFalse(InstanceActions.canStart(State.RUNNING, true))
        assertFalse(InstanceActions.canStart(State.STARTING, true))
    }

    private fun instance(id: String?, name: String) = KlipperInstance().also { it.id = id; it.name = name }

    @Test
    fun `the same instances in the same order are the same list`() {
        val a = listOf(instance("1", "A"), instance("2", "B"))
        val b = listOf(instance("1", "renamed"), instance("2", "other"))
        assertTrue(InstanceLists.sameInstances(a, b))
    }

    @Test
    fun `a different order or size is a different list`() {
        val a = listOf(instance("1", "A"), instance("2", "B"))
        assertFalse(InstanceLists.sameInstances(a, a.reversed()))
        assertFalse(InstanceLists.sameInstances(a, a.take(1)))
        assertTrue(InstanceLists.sameInstances(emptyList(), emptyList()))
    }

    @Test
    fun `an instance without an id is told apart by its name`() {
        assertTrue(InstanceLists.sameInstances(listOf(instance(null, "A")), listOf(instance(null, "A"))))
        assertFalse(InstanceLists.sameInstances(listOf(instance(null, "A")), listOf(instance(null, "B"))))
    }
}

class NewInstanceAndLogSourcesTest {
    @Test
    fun `a profile can be created while a slot is free and a config is chosen`() {
        assertTrue(NewInstance.canCreate(0, 4, "example.cfg"))
        assertTrue(NewInstance.canCreate(3, 4, "example.cfg"))
    }

    @Test
    fun `no profile is created when every slot is taken`() {
        assertFalse(NewInstance.canCreate(4, 4, "example.cfg"))
        assertFalse(NewInstance.canCreate(10, 4, "example.cfg"))
    }

    @Test
    fun `no profile is created without a starter config`() {
        assertFalse(NewInstance.canCreate(0, 4, null))
        assertFalse(NewInstance.canCreate(0, 4, ""))
    }

    @Test
    fun `each instance gets four log entries in a fixed order`() {
        val entries = LogSources.forInstance("abc", "Neptune")
        assertEquals(
            listOf("Klipper · Neptune", "Moonraker · Neptune", "OctoEverywhere · Neptune", "Obico · Neptune"),
            entries.map { it.label }
        )
        assertEquals(
            listOf("klippy.log", "moonraker.log", "octoeverywhere.log", "obico.log"),
            entries.map { it.fileName }
        )
    }

    @Test
    fun `log entry ids are unique per instance`() {
        val ids = LogSources.forInstance("a", "x").map { it.id } + LogSources.forInstance("b", "x").map { it.id }
        assertEquals(8, ids.toSet().size)
        assertEquals("klippy_a", LogSources.forInstance("a", "x")[0].id)
    }

    @Test
    fun `a blank name falls back to the id and then to a question mark`() {
        assertEquals("Klipper · abc", LogSources.forInstance("abc", " ")[0].label)
        assertEquals("Klipper · ?", LogSources.forInstance(null, "")[0].label)
    }
}
