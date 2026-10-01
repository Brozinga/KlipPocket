package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefValuesTest {
    @Test
    fun `booleans stored as other types are converted`() {
        assertTrue(PrefValues.boolFromAny(true, false))
        assertTrue(PrefValues.boolFromAny(1, false))
        assertFalse(PrefValues.boolFromAny(0L, true))
        assertTrue(PrefValues.boolFromAny("true", false))
        assertFalse(PrefValues.boolFromAny("false", true))
    }

    @Test
    fun `a boolean that cannot be converted uses the default`() {
        assertTrue(PrefValues.boolFromAny("yes", true))
        assertFalse(PrefValues.boolFromAny(null, false))
        assertTrue(PrefValues.boolFromAny(listOf(1), true))
    }

    @Test
    fun `numbers stored as other types are converted`() {
        assertEquals(7, PrefValues.intFromAny(7L, 0))
        assertEquals(7, PrefValues.intFromAny(7.9f, 0))
        assertEquals(7, PrefValues.intFromAny("7", 0))
        assertEquals(3, PrefValues.intFromAny("seven", 3))
        assertEquals(1.5f, PrefValues.floatFromAny("1.5", 0f), 0f)
        assertEquals(2f, PrefValues.floatFromAny(2, 0f), 0f)
        assertEquals(9f, PrefValues.floatFromAny(null, 9f), 0f)
    }

    @Test
    fun `rotation wraps into 0 to 359`() {
        assertEquals(0, PrefValues.rotation(360))
        assertEquals(90, PrefValues.rotation(450))
        assertEquals(270, PrefValues.rotation(-90))
        assertEquals(180, PrefValues.rotation(-540))
    }

    @Test
    fun `rotation cycles a quarter turn at a time`() {
        assertEquals(listOf(90, 180, 270, 0), generateSequence(0) { PrefValues.nextRotation(it) }.drop(1).take(4).toList())
    }

    @Test
    fun `resolution stays inside the presets`() {
        assertEquals(0, PrefValues.resolution(-4, 3))
        assertEquals(2, PrefValues.resolution(9, 3))
        assertEquals(1, PrefValues.resolution(1, 3))
    }

    @Test
    fun `resolution cycles through every preset`() {
        assertEquals(1, PrefValues.nextResolution(0, 3))
        assertEquals(2, PrefValues.nextResolution(1, 3))
        assertEquals(0, PrefValues.nextResolution(2, 3))
    }

    @Test
    fun `zoom is never below 1x`() {
        assertEquals(1f, PrefValues.zoom(0.2f), 0f)
        assertEquals(3f, PrefValues.zoom(3f), 0f)
    }

    @Test
    fun `obico server url is trimmed and loses trailing slashes`() {
        assertEquals("https://obico.example", PrefValues.obicoServerUrl("  https://obico.example/// ", "cloud"))
    }

    @Test
    fun `a blank obico server url falls back to the default`() {
        assertEquals("cloud", PrefValues.obicoServerUrl("   ", "cloud"))
        assertEquals("cloud", PrefValues.obicoServerUrl("/", "cloud"))
    }

    @Test
    fun `the legacy mainsail flag maps to a front end`() {
        assertEquals(Prefs.FRONTEND_MAINSAIL, PrefValues.frontendFromLegacyFlag(true))
        assertEquals(Prefs.FRONTEND_FLUIDD, PrefValues.frontendFromLegacyFlag(false))
    }

    @Test
    fun `the old kalico front end is migrated to Mainsail`() {
        @Suppress("DEPRECATION")
        assertEquals(Prefs.FRONTEND_MAINSAIL, PrefValues.migrateFrontend(Prefs.FRONTEND_KALICO))
        assertEquals(Prefs.FRONTEND_VOYAGER, PrefValues.migrateFrontend(Prefs.FRONTEND_VOYAGER))
    }
}
