package ru.ytkab0bp.beamklipper.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun `tagDiffers is false for identical tags`() {
        assertFalse(VersionCompare.tagDiffers("v1.37.5", "v1.37.5"))
    }

    @Test
    fun `tagDiffers is true for different tags`() {
        assertTrue(VersionCompare.tagDiffers("v1.37.5", "v1.38.0"))
    }

    @Test
    fun `tagDiffers is false when the remote check failed`() {
        // A failed check (latest == null) must never claim an update exists.
        assertFalse(VersionCompare.tagDiffers("v1.37.5", null))
    }

    @Test
    fun `tagBehind is false when the bundled marker is unknown`() {
        assertFalse(VersionCompare.tagBehind(null, "v0.13.0"))
        assertFalse(VersionCompare.tagBehind("", "v0.13.0"))
    }

    @Test
    fun `tagBehind is false when the remote check failed`() {
        assertFalse(VersionCompare.tagBehind("v0.13.0", null))
    }

    @Test
    fun `tagBehind is false for the same tag`() {
        assertFalse(VersionCompare.tagBehind("v0.13.0", "v0.13.0"))
    }

    @Test
    fun `tagBehind is true only when upstream is strictly newer`() {
        assertTrue(VersionCompare.tagBehind("v0.13.0", "v0.14.0"))
        assertTrue(VersionCompare.tagBehind("v0.11.0", "v0.11.1"))
        assertFalse(VersionCompare.tagBehind("v0.13.0", "v0.12.9"))
    }

    @Test
    fun `tagBehind compares numerically not lexically`() {
        assertTrue(VersionCompare.tagBehind("v0.9.0", "v0.10.0"))
    }

    @Test
    fun `highestTag picks the highest plain version and ignores the rest`() {
        assertEquals("v0.13.0", VersionCompare.highestTag(listOf("v0.9.1", "v0.13.0", "v0.12.0", "v0.14.0-rc1", "latest")))
        assertNull(VersionCompare.highestTag(listOf("latest", "nightly")))
    }
}
