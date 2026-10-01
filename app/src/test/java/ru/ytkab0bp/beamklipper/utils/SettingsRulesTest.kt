package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRulesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the engine tile toggles between klipper and kalico`() {
        assertEquals(Prefs.ENGINE_KALICO, Engines.next(Prefs.ENGINE_KLIPPER))
        assertEquals(Prefs.ENGINE_KLIPPER, Engines.next(Prefs.ENGINE_KALICO))
        assertEquals(Prefs.ENGINE_KLIPPER, Engines.next("unknown"))
    }

    @Test
    fun `kalico is only available once its files are unpacked`() {
        assertFalse(Engines.isInstalled(tmp.root, Prefs.ENGINE_KALICO))
        File(tmp.root, "kalico/klippy").mkdirs()
        File(tmp.root, Engines.KALICO_ENTRY).writeText("")
        assertTrue(Engines.isInstalled(tmp.root, Prefs.ENGINE_KALICO))
    }

    @Test
    fun `klipper is always available`() {
        assertTrue(Engines.isInstalled(tmp.root, Prefs.ENGINE_KLIPPER))
    }

    @Test
    fun `engines have distinct names`() {
        assertTrue(Engines.nameRes(Prefs.ENGINE_KLIPPER) != Engines.nameRes(Prefs.ENGINE_KALICO))
    }

    @Test
    fun `usb naming toggles between path and vid pid`() {
        assertEquals(Prefs.USB_DEVICE_NAMING_BY_VID_PID, UsbNaming.next(Prefs.USB_DEVICE_NAMING_BY_PATH))
        assertEquals(Prefs.USB_DEVICE_NAMING_BY_PATH, UsbNaming.next(Prefs.USB_DEVICE_NAMING_BY_VID_PID))
        assertTrue(UsbNaming.nameRes(0) != UsbNaming.nameRes(1))
    }

    @Test
    fun `every language has its own name and unknown ones read as system`() {
        val names = Languages.ALL.map { Languages.nameRes(it) }
        assertEquals(Languages.ALL.size, names.toSet().size)
        assertEquals(Languages.nameRes(Prefs.LANGUAGE_SYSTEM), Languages.nameRes("xx"))
    }

    @Test
    fun `the language picker starts with the device language`() {
        assertEquals(Prefs.LANGUAGE_SYSTEM, Languages.ALL.first())
        assertEquals(6, Languages.ALL.size)
    }

    @Test
    fun `camera resolutions have distinct names and unknown ones read as low`() {
        val names = listOf(Prefs.CAMERA_RESOLUTION_LOW, Prefs.CAMERA_RESOLUTION_MEDIUM, Prefs.CAMERA_RESOLUTION_HIGH)
            .map { CameraResolutions.nameRes(it) }
        assertEquals(3, names.toSet().size)
        assertEquals(CameraResolutions.nameRes(Prefs.CAMERA_RESOLUTION_LOW), CameraResolutions.nameRes(9))
    }

    private fun instance(name: String): File = tmp.newFolder(name)

    @Test
    fun `the octoeverywhere link uses the first instance that has a printer id`() {
        val a = instance("a")
        val b = instance("b")
        File(b, "octoeverywhere").mkdirs()
        File(b, CompanionFiles.OCTOEVERYWHERE_SECRETS).writeText("printer_id = PID42\n")
        assertEquals(
            "https://octoeverywhere.com/getstarted?printerid=PID42",
            CompanionFiles.octoEverywhereLinkUrl(listOf(a, b))
        )
    }

    @Test
    fun `no secrets file means no link yet`() {
        assertNull(CompanionFiles.octoEverywhereLinkUrl(listOf(instance("a"))))
        assertNull(CompanionFiles.octoEverywhereLinkUrl(emptyList()))
    }

    @Test
    fun `a secrets file without an id is skipped`() {
        val a = instance("a")
        File(a, "octoeverywhere").mkdirs()
        File(a, CompanionFiles.OCTOEVERYWHERE_SECRETS).writeText("[secrets]\n")
        assertNull(CompanionFiles.octoEverywhereLinkUrl(listOf(a)))
    }

    @Test
    fun `the obico status is read from the first instance that has one`() {
        val a = instance("a")
        File(a, "obico").mkdirs()
        File(a, CompanionFiles.OBICO_STATUS).writeText("""{"is_linked": false, "one_time_passcode": "999111"}""")
        val status = CompanionFiles.obicoStatus(listOf(instance("none"), a))!!
        assertEquals("999111", status.passcode)
        assertFalse(status.isLinked)
    }

    @Test
    fun `no status file means nothing to show`() {
        assertNull(CompanionFiles.obicoStatus(listOf(instance("a"))))
    }

    @Test
    fun `an unreadable status file is not skipped in favour of a later one`() {
        val a = instance("a")
        val b = instance("b")
        File(a, "obico").mkdirs()
        File(a, CompanionFiles.OBICO_STATUS).writeText("broken")
        File(b, "obico").mkdirs()
        File(b, CompanionFiles.OBICO_STATUS).writeText("""{"is_linked": true}""")
        assertNull(CompanionFiles.obicoStatus(listOf(a, b)))
    }

    @Test
    fun `the obico token is read from the companion's config`() {
        val a = instance("a")
        File(a, "obico").mkdirs()
        File(a, CompanionFiles.OBICO_CONFIG).writeText("[server]\nauth_token = t0k3n\n")
        assertEquals("t0k3n", CompanionFiles.obicoToken(listOf(a)))
    }

    @Test
    fun `an unlinked config has no token and the search continues`() {
        val a = instance("a")
        val b = instance("b")
        File(a, "obico").mkdirs()
        File(a, CompanionFiles.OBICO_CONFIG).writeText("[server]\nurl = x\n")
        File(b, "obico").mkdirs()
        File(b, CompanionFiles.OBICO_CONFIG).writeText("[server]\nauth_token = second\n")
        assertEquals("second", CompanionFiles.obicoToken(listOf(a, b)))
        assertNull(CompanionFiles.obicoToken(listOf(a)))
    }
}
