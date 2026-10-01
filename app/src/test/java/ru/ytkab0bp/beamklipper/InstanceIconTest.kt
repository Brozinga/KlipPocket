package ru.ytkab0bp.beamklipper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InstanceIconTest {
    @Test
    fun `every icon is found by its stored key`() {
        InstanceIcon.values().forEach { assertEquals(it, InstanceIcon.byKey(it.name)) }
    }

    @Test
    fun `an unknown or empty key is the printer icon`() {
        assertEquals(InstanceIcon.PRINTER, InstanceIcon.byKey("removed_icon"))
        assertEquals(InstanceIcon.PRINTER, InstanceIcon.byKey(""))
        assertEquals(InstanceIcon.PRINTER, InstanceIcon.byKey("printer"))
    }

    @Test
    fun `every icon has its own drawable`() {
        val drawables = InstanceIcon.values().map { it.drawable }
        assertEquals(drawables.size, drawables.toSet().size)
    }

    @Test
    fun `the printer icon is the default so a stored key must not be renamed`() {
        assertNotEquals(0, InstanceIcon.PRINTER.drawable)
        assertEquals("PRINTER", InstanceIcon.PRINTER.name)
    }
}
