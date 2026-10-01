package ru.ytkab0bp.beamklipper

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.ytkab0bp.beamklipper.testing.RobolectricSupport
import ru.ytkab0bp.beamklipper.testing.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class PermissionsCheckerTest {
    @Before
    fun setUp() {
        RobolectricSupport.setUpApp()
        PermissionsChecker.setIgnoreNotificationsChannel(false)
    }

    @Test
    fun `notifications need the runtime permission on recent android`() {
        assertFalse(PermissionsChecker.hasNotificationPerm())
        shadowOf(RobolectricSupport.context()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PermissionsChecker.hasNotificationPerm())
    }

    @Test
    @Config(sdk = [31])
    fun `older android needs no notification permission`() {
        assertTrue(PermissionsChecker.hasNotificationPerm())
    }

    @Test
    fun `the notifications channel check is disabled`() {
        assertTrue(PermissionsChecker.isNotificationsChannelHidden())
    }

    @Test
    fun `the ignore flag can be set and read`() {
        PermissionsChecker.setIgnoreNotificationsChannel(true)
        assertTrue(PermissionsChecker.ignoreNotificationsChannel())
    }

    @Test
    fun `an app installed on internal storage is not broken by an sd card`() {
        assertTrue(PermissionsChecker.isNotBrokenBySDCard())
    }

    @Test
    fun `background activity is allowed by default`() {
        assertTrue(PermissionsChecker.hasBatteryPerm())
    }

    @Test
    fun `starting is blocked until the notification permission is granted`() {
        assertTrue(PermissionsChecker.needBlockStart() || PermissionsChecker.hasBatteryOptimizationIgnored())
        shadowOf(RobolectricSupport.context()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PermissionsChecker.hasNotificationPerm())
    }
}
