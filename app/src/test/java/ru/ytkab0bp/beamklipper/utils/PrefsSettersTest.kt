package ru.ytkab0bp.beamklipper.utils

import android.Manifest
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.testing.RobolectricSupport
import ru.ytkab0bp.beamklipper.testing.TestApp

// Prefs against the real SharedPreferences: what a setter stores is what the
// getter (and a later app start) reads back.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class PrefsSettersTest {
    private val app get() = RobolectricSupport.context()

    @Before
    fun setUp() {
        RobolectricSupport.setUpApp()
    }

    private fun raw() = app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE)

    @Test
    fun `the front end is stored and the legacy switch removed`() {
        raw().edit().putBoolean("mainsail", false).commit()
        Prefs.webFrontend = Prefs.FRONTEND_VOYAGER
        assertEquals(Prefs.FRONTEND_VOYAGER, Prefs.webFrontend)
        assertFalse(raw().contains("mainsail"))
        assertEquals(Prefs.FRONTEND_VOYAGER, raw().getString("web_frontend", null))
    }

    @Test
    fun `the engine is stored`() {
        Prefs.engine = Prefs.ENGINE_KALICO
        assertEquals(Prefs.ENGINE_KALICO, Prefs.engine)
        assertEquals(Prefs.ENGINE_KALICO, Prefs.engineKey)
    }

    @Test
    fun `the app language is stored`() {
        Prefs.appLanguage = Prefs.LANGUAGE_PORTUGUESE_BRAZIL
        assertEquals("pt-BR", Prefs.appLanguage)
    }

    @Test
    fun `a rotation is normalised when saved`() {
        Prefs.cameraRotation = 450
        assertEquals(90, Prefs.cameraRotation)
        Prefs.cameraRotation = -90
        assertEquals(270, raw().getInt("camera_rotation", -1))
    }

    @Test
    fun `a resolution outside the presets is clamped when saved`() {
        Prefs.cameraResolution = 9
        assertEquals(Prefs.CAMERA_RESOLUTION_HIGH, Prefs.cameraResolution)
        Prefs.cameraResolution = -1
        assertEquals(Prefs.CAMERA_RESOLUTION_LOW, Prefs.cameraResolution)
    }

    @Test
    fun `a zoom below 1x is saved as 1x`() {
        Prefs.cameraZoom = 0.3f
        assertEquals(1f, raw().getFloat("camera_zoom", 0f), 0f)
        Prefs.cameraZoom = 2f
        assertEquals(2f, Prefs.cameraZoom, 0f)
    }

    @Test
    fun `the camera id can be set and cleared`() {
        Prefs.cameraId = "1"
        assertEquals("1", Prefs.cameraId)
        Prefs.cameraId = null
        assertNull(Prefs.cameraId)
        assertFalse(raw().contains("camera_id"))
    }

    @Test
    fun `the camera switch needs the camera permission`() {
        Prefs.isCameraEnabled = true
        assertFalse(Prefs.isCameraEnabled)
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertTrue(Prefs.isCameraEnabled)
        Prefs.isCameraEnabled = false
        assertFalse(Prefs.isCameraEnabled)
    }

    @Test
    fun `the remote access switches are stored`() {
        Prefs.isOctoEverywhereEnabled = true
        Prefs.isObicoEnabled = true
        assertTrue(Prefs.isOctoEverywhereEnabled)
        assertTrue(Prefs.isObicoEnabled)
        Prefs.isObicoEnabled = false
        assertFalse(Prefs.isObicoEnabled)
    }

    @Test
    fun `the obico server is normalised and a blank one is the cloud`() {
        Prefs.obicoServerUrl = "  https://obico.example/ "
        assertEquals("https://obico.example", Prefs.obicoServerUrl)
        Prefs.obicoServerUrl = "   "
        assertEquals(Prefs.OBICO_CLOUD_URL, Prefs.obicoServerUrl)
    }

    @Test
    fun `changing the obico server forgets the link because the token belongs to the old server`() {
        Prefs.obicoAuthToken = "tok"
        assertTrue(Prefs.isObicoLinked)
        Prefs.obicoServerUrl = "https://other.example"
        assertNull(Prefs.obicoAuthToken)
        assertFalse(Prefs.isObicoLinked)
    }

    @Test
    fun `the obico token can be set and cleared`() {
        Prefs.obicoAuthToken = "tok"
        assertEquals("tok", Prefs.obicoAuthToken)
        Prefs.obicoAuthToken = null
        assertFalse(Prefs.isObicoLinked)
        assertFalse(raw().contains("obico_auth_token"))
    }

    @Test
    fun `usb naming is stored`() {
        Prefs.usbDeviceNaming = Prefs.USB_DEVICE_NAMING_BY_VID_PID
        assertEquals(Prefs.USB_DEVICE_NAMING_BY_VID_PID, Prefs.usbDeviceNaming)
    }

    @Test
    fun `flashlight and focus settings are stored`() {
        Prefs.isFlashlightEnabled = true
        Prefs.isAutofocusEnabled = true
        Prefs.focusDistance = 2.5f
        assertTrue(Prefs.isFlashlightEnabled)
        assertTrue(Prefs.isAutofocusEnabled)
        assertEquals(2.5f, Prefs.focusDistance, 0f)
    }

    @Test
    fun `the tap to focus is stored per camera and can be cleared`() {
        Prefs.savedFocusCamera = "0"
        Prefs.savedFocusDistance = 1.5f
        Prefs.savedFocusX = 0.25f
        Prefs.savedFocusY = 0.75f
        assertEquals("0", Prefs.savedFocusCamera)
        assertEquals(1.5f, Prefs.savedFocusDistance, 0f)
        assertEquals(0.25f, Prefs.savedFocusX, 0f)
        assertEquals(0.75f, Prefs.savedFocusY, 0f)
        Prefs.clearSavedFocus()
        assertNull(Prefs.savedFocusCamera)
        assertEquals(-1f, Prefs.savedFocusDistance, 0f)
    }

    @Test
    fun `the last commit is remembered`() {
        assertNull(Prefs.getLastCommit())
        Prefs.setLastCommit()
        assertEquals(BuildConfig.COMMIT, Prefs.getLastCommit())
    }

    @Test
    fun `values survive re initialising the prefs like an app restart`() {
        Prefs.webFrontend = Prefs.FRONTEND_FLUIDD
        Prefs.cameraRotation = 180
        Prefs.init(app)
        assertEquals(Prefs.FRONTEND_FLUIDD, Prefs.webFrontend)
        assertEquals(180, Prefs.cameraRotation)
    }
}
