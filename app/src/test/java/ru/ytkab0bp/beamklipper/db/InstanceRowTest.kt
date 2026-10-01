package ru.ytkab0bp.beamklipper.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceRowTest {
    @Test
    fun `autostart is read from any type sqlite may return`() {
        assertTrue(InstanceRow.autostart(true))
        assertTrue(InstanceRow.autostart(1))
        assertTrue(InstanceRow.autostart(5L))
        assertTrue(InstanceRow.autostart("1"))
    }

    @Test
    fun `autostart off values`() {
        assertFalse(InstanceRow.autostart(false))
        assertFalse(InstanceRow.autostart(0))
        assertFalse(InstanceRow.autostart(0L))
        assertFalse(InstanceRow.autostart("0"))
        assertFalse(InstanceRow.autostart("true"))
    }

    @Test
    fun `a missing autostart is off`() {
        assertFalse(InstanceRow.autostart(null))
        assertFalse(InstanceRow.autostart(1.0))
    }
}
