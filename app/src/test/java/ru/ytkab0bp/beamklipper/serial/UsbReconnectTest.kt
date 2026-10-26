package ru.ytkab0bp.beamklipper.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbReconnectTest {
    @Test
    fun `retries use growing delays and then give up`() {
        val delays = generateSequence(0) { it + 1 }.map { UsbReconnect.retryDelay(it) }.takeWhile { it != null }.toList()
        assertTrue(delays.size >= 3)
        assertEquals(delays.sortedBy { it }, delays)
        assertNull(UsbReconnect.retryDelay(delays.size))
    }

    @Test
    fun `waits a little after the board is attached`() {
        assertTrue(UsbReconnect.ATTACH_SETTLE_MS in 100..1000)
    }

    @Test
    fun `put returns the connection it replaced`() {
        val slots = PortSlots<Any>()
        val old = Any()
        val new = Any()
        assertNull(slots.put("a", old))
        assertEquals(old, slots.put("a", new))
        assertEquals(new, slots.get("a"))
    }

    @Test
    fun `a late close of an old connection leaves the new one alone`() {
        val slots = PortSlots<Any>()
        val old = Any()
        val new = Any()
        slots.put("a", old)
        slots.put("a", new)
        assertFalse(slots.removeIfCurrent("a", old))
        assertEquals(new, slots.get("a"))
    }

    @Test
    fun `closing the current connection frees the slot`() {
        val slots = PortSlots<Any>()
        val cur = Any()
        slots.put("a", cur)
        assertTrue(slots.removeIfCurrent("a", cur))
        assertNull(slots.get("a"))
        assertFalse(slots.removeIfCurrent("a", cur))
    }

    @Test
    fun `remove and keys work per name`() {
        val slots = PortSlots<Any>()
        slots.put("a", Any())
        slots.put("b", Any())
        assertEquals(setOf("a", "b"), slots.keys().toSet())
        assertNotNull(slots.remove("a"))
        assertNull(slots.remove("a"))
        assertEquals(listOf("b"), slots.keys())
    }
}
