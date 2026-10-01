package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test
    fun `a camera without zoom only offers 1x`() {
        assertEquals(listOf(1f), CameraZoom.options(1f))
        assertEquals(listOf(1f), CameraZoom.options(1.2f))
    }

    @Test
    fun `only the steps the camera supports are offered`() {
        assertEquals(listOf(1f, 1.5f, 2f, 3f), CameraZoom.options(3f))
    }

    @Test
    fun `a camera reporting 3_9999 still offers 4x`() {
        assertEquals(4f, CameraZoom.options(3.9999f).last())
    }

    @Test
    fun `clamp snaps a saved value down to the closest offered step`() {
        val options = CameraZoom.options(4f)
        assertEquals(2f, CameraZoom.clamp(2.4f, options))
        assertEquals(4f, CameraZoom.clamp(10f, options))
        assertEquals(1f, CameraZoom.clamp(0.5f, options))
    }

    @Test
    fun `labels drop the decimals of whole steps`() {
        assertEquals("2×", CameraZoom.label(2f))
        assertEquals("1.5×", CameraZoom.label(1.5f))
    }
}
