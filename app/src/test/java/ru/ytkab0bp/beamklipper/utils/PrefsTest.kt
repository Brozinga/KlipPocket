package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.beamklipper.testing.FakeSharedPreferences

// The readers of Prefs against a fake SharedPreferences (setters also touch
// app-wide state and are covered by PrefValuesTest).
class PrefsTest {
    private fun prefs(vararg entries: Pair<String, Any?>): FakeSharedPreferences =
        FakeSharedPreferences(mapOf(*entries)).also { Prefs.attach(it) }

    @Test
    fun `defaults when nothing was saved`() {
        prefs()
        assertEquals(Prefs.FRONTEND_MAINSAIL, Prefs.webFrontend)
        assertEquals(Prefs.ENGINE_KLIPPER, Prefs.engine)
        assertEquals(Prefs.LANGUAGE_SYSTEM, Prefs.appLanguage)
        assertEquals(Prefs.CAMERA_RESOLUTION_LOW, Prefs.cameraResolution)
        assertEquals(640, Prefs.cameraWidth)
        assertEquals(480, Prefs.cameraHeight)
        assertNull(Prefs.cameraId)
        assertEquals(0, Prefs.cameraRotation)
        assertEquals(1f, Prefs.cameraZoom, 0f)
        assertFalse(Prefs.isOctoEverywhereEnabled)
        assertFalse(Prefs.isObicoEnabled)
        assertEquals(Prefs.OBICO_CLOUD_URL, Prefs.obicoServerUrl)
        assertEquals(Prefs.USB_DEVICE_NAMING_BY_PATH, Prefs.usbDeviceNaming)
    }

    @Test
    fun `camera resolutions map to their sizes`() {
        prefs("camera_resolution" to 1)
        assertEquals(1280 to 720, Prefs.cameraWidth to Prefs.cameraHeight)
        prefs("camera_resolution" to 2)
        assertEquals(1920 to 1080, Prefs.cameraWidth to Prefs.cameraHeight)
    }

    @Test
    fun `a saved resolution outside the presets is clamped`() {
        prefs("camera_resolution" to 42)
        assertEquals(Prefs.CAMERA_RESOLUTION_HIGH, Prefs.cameraResolution)
        prefs("camera_resolution" to -3)
        assertEquals(Prefs.CAMERA_RESOLUTION_LOW, Prefs.cameraResolution)
    }

    @Test
    fun `a saved rotation is normalised`() {
        prefs("camera_rotation" to 450)
        assertEquals(90, Prefs.cameraRotation)
        prefs("camera_rotation" to -90)
        assertEquals(270, Prefs.cameraRotation)
    }

    @Test
    fun `a saved zoom below 1x reads as 1x`() {
        prefs("camera_zoom" to 0.5f)
        assertEquals(1f, Prefs.cameraZoom, 0f)
    }

    @Test
    fun `the front end is read from its own key`() {
        prefs("web_frontend" to Prefs.FRONTEND_VOYAGER)
        assertEquals(Prefs.FRONTEND_VOYAGER, Prefs.webFrontend)
    }

    @Test
    fun `the legacy mainsail switch is migrated and removed`() {
        val fake = prefs("mainsail" to false)
        assertEquals(Prefs.FRONTEND_FLUIDD, Prefs.webFrontend)
        assertEquals(Prefs.FRONTEND_FLUIDD, fake.data["web_frontend"])
        assertFalse(fake.contains("mainsail"))
    }

    @Test
    fun `the legacy mainsail switch set to true selects Mainsail`() {
        prefs("mainsail" to true)
        assertEquals(Prefs.FRONTEND_MAINSAIL, Prefs.webFrontend)
    }

    @Test
    fun `the old kalico front end is rewritten to Mainsail`() {
        @Suppress("DEPRECATION")
        val fake = prefs("web_frontend" to Prefs.FRONTEND_KALICO)
        assertEquals(Prefs.FRONTEND_MAINSAIL, Prefs.webFrontend)
        assertEquals(Prefs.FRONTEND_MAINSAIL, fake.data["web_frontend"])
    }

    @Test
    fun `a value saved with another type by an older build is converted`() {
        val fake = prefs(
            "camera_resolution" to "2",
            "camera_enabled" to 1,
            "octoeverywhere_enabled" to "true",
            "camera_zoom" to "2.5",
            "engine" to 7
        )
        assertEquals(2, Prefs.cameraResolution)
        assertTrue(Prefs.isCameraEnabled)
        assertTrue(Prefs.isOctoEverywhereEnabled)
        assertEquals(2.5f, Prefs.cameraZoom, 0f)
        assertEquals("7", Prefs.engine)
        // numbers are written back in the right type
        prefs("usb_device_naming" to 1L).let {
            assertEquals(1, Prefs.usbDeviceNaming)
            assertEquals(1, it.data["usb_device_naming"])
        }
        assertEquals("7", fake.data["engine"])
    }

    @Test
    fun `a value that cannot be converted reads as its default`() {
        prefs("camera_resolution" to "high", "obico_enabled" to "maybe")
        assertEquals(Prefs.CAMERA_RESOLUTION_LOW, Prefs.cameraResolution)
        assertFalse(Prefs.isObicoEnabled)
    }

    @Test
    fun `obico is linked only with a non blank token`() {
        prefs()
        assertFalse(Prefs.isObicoLinked)
        prefs("obico_auth_token" to "   ")
        assertFalse(Prefs.isObicoLinked)
        prefs("obico_auth_token" to "abc123")
        assertTrue(Prefs.isObicoLinked)
        assertEquals("abc123", Prefs.obicoAuthToken)
    }

    @Test
    fun `saved focus values default to unset`() {
        prefs()
        assertNull(Prefs.savedFocusCamera)
        assertEquals(-1f, Prefs.savedFocusDistance, 0f)
        assertEquals(-1f, Prefs.savedFocusX, 0f)
        assertEquals(-1f, Prefs.savedFocusY, 0f)
    }

    @Test
    fun `clearing the saved focus removes all of its keys`() {
        val fake = prefs(
            "saved_focus_camera" to "1", "saved_focus_distance" to 2f,
            "saved_focus_x" to 0.5f, "saved_focus_y" to 0.5f, "camera_id" to "1"
        )
        Prefs.clearSavedFocus()
        assertEquals(setOf("camera_id"), fake.data.keys)
    }

    @Test
    fun `an unreadable value never throws`() {
        prefs("obico_server_url" to 5)
        assertEquals("5", Prefs.obicoServerUrl)
    }
}
