package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewUtilsTest {
    @Test
    fun `lerp interpolates between two values`() {
        assertEquals(10f, ViewUtils.lerp(10f, 20f, 0f), 0f)
        assertEquals(15f, ViewUtils.lerp(10f, 20f, 0.5f), 0f)
        assertEquals(20f, ViewUtils.lerp(10f, 20f, 1f), 0f)
    }

    @Test
    fun `lerp can go backwards and past the ends`() {
        assertEquals(5f, ViewUtils.lerp(10f, 0f, 0.5f), 0f)
        assertEquals(30f, ViewUtils.lerp(10f, 20f, 2f), 0f)
    }
}
