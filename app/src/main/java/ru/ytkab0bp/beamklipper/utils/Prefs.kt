package ru.ytkab0bp.beamklipper.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.events.CameraSourceChangedEvent
import ru.ytkab0bp.beamklipper.events.EngineChangedEvent
import ru.ytkab0bp.beamklipper.events.ObicoConfigChangedEvent
import ru.ytkab0bp.beamklipper.events.WebFrontendChangedEvent
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.ui.state.AppState

object Prefs {
    const val USB_DEVICE_NAMING_BY_PATH = 0
    const val USB_DEVICE_NAMING_BY_VID_PID = 1
    const val ENGINE_KLIPPER = "klipper"
    const val ENGINE_KALICO = "kalico"
    const val FRONTEND_FLUIDD = "fluidd"
    const val FRONTEND_MAINSAIL = "mainsail"
    const val FRONTEND_VOYAGER = "voyager"
    @Deprecated("Kalico was never a frontend; duplicates Mainsail assets. Migrate to FRONTEND_MAINSAIL.")
    const val FRONTEND_KALICO = "kalico_frontend"
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_PORTUGUESE_BRAZIL = "pt-BR"
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_CHINESE_SIMPLIFIED = "zh-CN"
    const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"

    private lateinit var mPrefs: SharedPreferences

    private fun getSafeString(key: String, default: String): String {
        return try {
            mPrefs.getString(key, default) ?: default
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            if (raw != null) {
                val migrated = raw.toString()
                try { mPrefs.edit().putString(key, migrated).apply() } catch (_: Throwable) {}
                migrated
            } else default
        } catch (_: Throwable) {
            default
        }
    }

    private fun getSafeStringNullable(key: String): String? {
        return try {
            mPrefs.getString(key, null)
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            if (raw != null) {
                val migrated = raw.toString()
                try { mPrefs.edit().putString(key, migrated).apply() } catch (_: Throwable) {}
                migrated
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSafeInt(key: String, default: Int): Int {
        return try {
            mPrefs.getInt(key, default)
        } catch (_: ClassCastException) {
            val raw = mPrefs.all[key]
            val migrated = PrefValues.intFromAny(raw, default)
            if (raw is Number) {
                try { mPrefs.edit().putInt(key, migrated).apply() } catch (_: Throwable) {}
            }
            migrated
        } catch (_: Throwable) {
            default
        }
    }

    private fun getSafeBoolean(key: String, default: Boolean): Boolean {
        return try {
            mPrefs.getBoolean(key, default)
        } catch (_: ClassCastException) {
            PrefValues.boolFromAny(mPrefs.all[key], default)
        } catch (_: Throwable) {
            default
        }
    }

    private fun getSafeFloat(key: String, default: Float): Float {
        return try {
            mPrefs.getFloat(key, default)
        } catch (_: ClassCastException) {
            PrefValues.floatFromAny(mPrefs.all[key], default)
        } catch (_: Throwable) {
            default
        }
    }

    fun init(ctx: Context) {
        attach(ctx.getSharedPreferences("${ctx.packageName}_preferences", Context.MODE_PRIVATE))
    }

    internal fun attach(prefs: SharedPreferences) {
        mPrefs = prefs
    }

    var webFrontend: String
        get() {
            val legacyMainsail = mPrefs.contains("mainsail")
            val raw = if (legacyMainsail) {
                val migrated = PrefValues.frontendFromLegacyFlag(getSafeBoolean("mainsail", true))
                try {
                    mPrefs.edit().putString("web_frontend", migrated).remove("mainsail").apply()
                } catch (_: Throwable) {}
                migrated
            } else {
                getSafeString("web_frontend", FRONTEND_MAINSAIL)
            }
            val frontend = PrefValues.migrateFrontend(raw)
            if (frontend != raw) {
                try { mPrefs.edit().putString("web_frontend", frontend).apply() } catch (_: Throwable) {}
            }
            return frontend
        }
        set(value) {
            mPrefs.edit().putString("web_frontend", value).remove("mainsail").apply()
            KlipperApp.EVENT_BUS.fireEvent(WebFrontendChangedEvent())
        }

    var engine: String
        get() = getSafeString("engine", ENGINE_KLIPPER)
        set(value) {
            mPrefs.edit().putString("engine", value).apply()
            KlipperApp.EVENT_BUS.fireEvent(EngineChangedEvent())
        }

    val engineKey: String
        get() = engine

    var appLanguage: String
        get() = getSafeString("app_language", LANGUAGE_SYSTEM)
        set(value) {
            mPrefs.edit().putString("app_language", value).apply()
            AppState.updateAppLanguage()
        }

    const val CAMERA_RESOLUTION_LOW = 0
    const val CAMERA_RESOLUTION_MEDIUM = 1
    const val CAMERA_RESOLUTION_HIGH = 2

    // Low (640x480) stays the default rather than 720p+: this feed gets
    // relayed through OctoEverywhere's cloud connection (shared with
    // command/API traffic) when remote access is on, not just served over
    // LAN. Measured on real hardware: 1280x720 @ ~15fps JPEG q85 pushed
    // ~1.25MB/s continuously, which saturates a typical uplink and stalls
    // command execution over the same connection, not just the webcam feed.
    // Medium/High are opt-in for users on a good LAN who want more detail.
    internal val CAMERA_RESOLUTION_PRESETS = arrayOf(
        640 to 480,
        1280 to 720,
        1920 to 1080
    )

    // Cross-process restart story is the same as cameraId/cameraRotation
    // below: CameraService only reads this at camera-open time, in its own
    // ":camera" process, so a change needs a restart to take effect.
    var cameraResolution: Int
        get() = PrefValues.resolution(getSafeInt("camera_resolution", CAMERA_RESOLUTION_LOW), CAMERA_RESOLUTION_PRESETS.size)
        set(value) {
            val normalized = PrefValues.resolution(value, CAMERA_RESOLUTION_PRESETS.size)
            mPrefs.edit().putInt("camera_resolution", normalized).apply()
            AppState.updateCameraSourceId()
            KlipperApp.EVENT_BUS.fireEvent(CameraSourceChangedEvent())
        }

    val cameraWidth: Int
        get() = CAMERA_RESOLUTION_PRESETS[cameraResolution].first

    val cameraHeight: Int
        get() = CAMERA_RESOLUTION_PRESETS[cameraResolution].second

    // null means "auto": prefer a LENS_FACING_EXTERNAL (USB UVC webcam) camera
    // when one is plugged in, falling back to the first camera otherwise. See
    // CameraService.resolveCameraId(). CameraService runs in its own ":camera"
    // process, so it never observes this write directly — KlipperInstance
    // (main process, same as this setter) restarts it on CameraSourceChangedEvent
    // so the new pick is read fresh on the next process start.
    var cameraId: String?
        get() = getSafeStringNullable("camera_id")
        set(value) {
            mPrefs.edit().apply {
                if (value == null) remove("camera_id") else putString("camera_id", value)
            }.apply()
            AppState.updateCameraSourceId()
            KlipperApp.EVENT_BUS.fireEvent(CameraSourceChangedEvent())
        }

    // Clockwise degrees applied to every frame before it's served, for a
    // camera mounted sideways/upside-down. Only 0/90/180/270 are valid;
    // anything else is normalized. Same cross-process restart story as
    // cameraId above.
    var cameraRotation: Int
        get() = PrefValues.rotation(getSafeInt("camera_rotation", 0))
        set(value) {
            val normalized = PrefValues.rotation(value)
            mPrefs.edit().putInt("camera_rotation", normalized).apply()
            AppState.updateCameraSourceId()
            KlipperApp.EVENT_BUS.fireEvent(CameraSourceChangedEvent())
        }

    // Digital/optical zoom ratio applied by CameraService (1.0 = no zoom).
    // The selectable steps depend on the camera in use — see CameraZoom — so
    // the service clamps this to whatever the opened camera actually
    // supports. Same cross-process restart story as cameraId above.
    var cameraZoom: Float
        get() = PrefValues.zoom(getSafeFloat("camera_zoom", 1f))
        set(value) {
            mPrefs.edit().putFloat("camera_zoom", PrefValues.zoom(value)).apply()
            AppState.updateCameraSourceId()
            KlipperApp.EVENT_BUS.fireEvent(CameraSourceChangedEvent())
        }

    var isCameraEnabled: Boolean
        get() = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || KlipperApp.INSTANCE.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
                getSafeBoolean("camera_enabled", false)
        set(value) {
            mPrefs.edit().putBoolean("camera_enabled", value).apply()
            AppState.updateCameraEnabled()
        }

    var isOctoEverywhereEnabled: Boolean
        get() = getSafeBoolean("octoeverywhere_enabled", false)
        set(value) {
            mPrefs.edit().putBoolean("octoeverywhere_enabled", value).apply()
            AppState.updateOctoEverywhereEnabled()
        }

    const val OBICO_CLOUD_URL = "https://app.obico.io"

    var isObicoEnabled: Boolean
        get() = getSafeBoolean("obico_enabled", false)
        set(value) {
            mPrefs.edit().putBoolean("obico_enabled", value).apply()
            AppState.updateObicoEnabled()
        }

    // Any URL is accepted here so this doubles as "Obico Cloud or a
    // self-hosted server" — OBICO_CLOUD_URL is just the default. Changing it
    // invalidates any existing link (an auth_token is only valid for the
    // server that issued it), so the token is cleared alongside it.
    var obicoServerUrl: String
        get() = getSafeString("obico_server_url", OBICO_CLOUD_URL)
        set(value) {
            val normalized = PrefValues.obicoServerUrl(value, OBICO_CLOUD_URL)
            mPrefs.edit()
                .putString("obico_server_url", normalized)
                .remove("obico_auth_token")
                .apply()
            AppState.updateObicoServer()
            KlipperApp.EVENT_BUS.fireEvent(ObicoConfigChangedEvent())
        }

    // Set once linking succeeds (see SettingsViewModel.linkObico) and read
    // fresh by ObicoService (its own ":obico" process) at every start, same
    // cross-process story as cameraId/cameraRotation above.
    var obicoAuthToken: String?
        get() = getSafeStringNullable("obico_auth_token")
        set(value) {
            mPrefs.edit().apply {
                if (value == null) remove("obico_auth_token") else putString("obico_auth_token", value)
            }.apply()
            AppState.updateObicoServer()
            KlipperApp.EVENT_BUS.fireEvent(ObicoConfigChangedEvent())
        }

    val isObicoLinked: Boolean
        get() = !obicoAuthToken.isNullOrBlank()

    var usbDeviceNaming: Int
        get() = getSafeInt("usb_device_naming", USB_DEVICE_NAMING_BY_PATH)
        set(value) {
            UsbSerialManager.disconnectAll()
            mPrefs.edit().putInt("usb_device_naming", value).apply()
            UsbSerialManager.connectAll()
            AppState.updateUsbNaming()
        }

    var isFlashlightEnabled: Boolean
        get() = getSafeBoolean("flashlight", false)
        set(value) { mPrefs.edit().putBoolean("flashlight", value).apply() }

    var isAutofocusEnabled: Boolean
        get() = getSafeBoolean("autofocus", false)
        set(value) { mPrefs.edit().putBoolean("autofocus", value).apply() }

    var focusDistance: Float
        get() = getSafeFloat("focus", 0f)
        set(value) { mPrefs.edit().putFloat("focus", value).apply() }

    // Focus set by tap-to-focus, kept so it survives an app restart. Tied to
    // the camera it was set on: a different camera starts unfocused again.
    // distance < 0 = the lens position was not reported (re-run AF at x/y).
    var savedFocusCamera: String?
        get() = getSafeStringNullable("saved_focus_camera")
        set(value) { mPrefs.edit().putString("saved_focus_camera", value).apply() }

    var savedFocusDistance: Float
        get() = getSafeFloat("saved_focus_distance", -1f)
        set(value) { mPrefs.edit().putFloat("saved_focus_distance", value).apply() }

    var savedFocusX: Float
        get() = getSafeFloat("saved_focus_x", -1f)
        set(value) { mPrefs.edit().putFloat("saved_focus_x", value).apply() }

    var savedFocusY: Float
        get() = getSafeFloat("saved_focus_y", -1f)
        set(value) { mPrefs.edit().putFloat("saved_focus_y", value).apply() }

    fun clearSavedFocus() {
        mPrefs.edit()
            .remove("saved_focus_camera").remove("saved_focus_distance")
            .remove("saved_focus_x").remove("saved_focus_y")
            .apply()
    }

    fun getLastCommit(): String? = getSafeStringNullable("last_commit")

    fun setLastCommit() {
        mPrefs.edit().putString("last_commit", BuildConfig.COMMIT).apply()
    }

    fun applyAppLanguage() {
        val locales = if (appLanguage == LANGUAGE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(appLanguage)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
