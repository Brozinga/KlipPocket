package ru.ytkab0bp.beamklipper.utils

import ru.ytkab0bp.beamklipper.R
import java.io.File

// Rules behind the Settings tiles, free of Android state so they can be tested.
object Engines {
    // Where the Kalico firmware engine is unpacked; it is missing until the
    // bundle install has run, and Klipper is then the only choice.
    const val KALICO_ENTRY = "kalico/klippy/klippy.py"

    fun isInstalled(filesDir: File, engine: String): Boolean =
        engine != Prefs.ENGINE_KALICO || File(filesDir, KALICO_ENTRY).exists()

    fun next(current: String): String =
        if (current == Prefs.ENGINE_KLIPPER) Prefs.ENGINE_KALICO else Prefs.ENGINE_KLIPPER

    fun nameRes(engine: String): Int =
        if (engine == Prefs.ENGINE_KALICO) R.string.Kalico else R.string.Klipper
}

object UsbNaming {
    fun next(current: Int): Int =
        if (current == Prefs.USB_DEVICE_NAMING_BY_PATH) Prefs.USB_DEVICE_NAMING_BY_VID_PID
        else Prefs.USB_DEVICE_NAMING_BY_PATH

    fun nameRes(naming: Int): Int =
        if (naming == Prefs.USB_DEVICE_NAMING_BY_PATH) R.string.USBDeviceNamingByPath
        else R.string.USBDeviceNamingByVidPid
}

object Languages {
    // The languages the in-app picker offers, in order; "system" follows the device.
    val ALL: List<String> = listOf(
        Prefs.LANGUAGE_SYSTEM, Prefs.LANGUAGE_ENGLISH, Prefs.LANGUAGE_PORTUGUESE_BRAZIL,
        Prefs.LANGUAGE_RUSSIAN, Prefs.LANGUAGE_CHINESE_SIMPLIFIED, Prefs.LANGUAGE_CHINESE_TRADITIONAL
    )

    fun nameRes(language: String): Int = when (language) {
        Prefs.LANGUAGE_ENGLISH -> R.string.LanguageEnglish
        Prefs.LANGUAGE_PORTUGUESE_BRAZIL -> R.string.LanguagePortuguese
        Prefs.LANGUAGE_RUSSIAN -> R.string.LanguageRussian
        Prefs.LANGUAGE_CHINESE_SIMPLIFIED -> R.string.LanguageChineseSimplified
        Prefs.LANGUAGE_CHINESE_TRADITIONAL -> R.string.LanguageChineseTraditional
        else -> R.string.LanguageSystem
    }
}

object CameraResolutions {
    fun nameRes(resolution: Int): Int = when (resolution) {
        Prefs.CAMERA_RESOLUTION_MEDIUM -> R.string.CameraResolutionMedium
        Prefs.CAMERA_RESOLUTION_HIGH -> R.string.CameraResolutionHigh
        else -> R.string.CameraResolutionLow
    }
}

// Files a running companion writes under an instance's own directory, which
// the app reads for the Settings -> Link printer flows.
object CompanionFiles {
    const val OCTOEVERYWHERE_SECRETS = "octoeverywhere/octoeverywhere.secrets"
    const val OBICO_STATUS = "obico/obico_link_status.json"
    const val OBICO_CONFIG = "obico/moonraker-obico.cfg"

    // Only one instance runs the companion, so the first that has the file wins.
    fun octoEverywhereLinkUrl(instanceDirs: List<File>): String? {
        for (dir in instanceDirs) {
            val secrets = File(dir, OCTOEVERYWHERE_SECRETS)
            if (!secrets.exists()) continue
            val id = try { OctoEverywhereLink.printerId(secrets.readText()) } catch (_: Throwable) { null }
            if (id != null) return OctoEverywhereLink.linkUrl(id)
        }
        return null
    }

    // The first status file found decides, even when it cannot be read.
    fun obicoStatus(instanceDirs: List<File>): ObicoLink.DiscoveryStatus? {
        for (dir in instanceDirs) {
            val file = File(dir, OBICO_STATUS)
            if (!file.exists()) continue
            return try { ObicoLink.parseDiscoveryStatus(file.readText()) } catch (_: Throwable) { null }
        }
        return null
    }

    fun obicoToken(instanceDirs: List<File>): String? {
        for (dir in instanceDirs) {
            val cfg = File(dir, OBICO_CONFIG)
            if (!cfg.exists()) continue
            val token = try { ObicoLink.authTokenFromConfig(cfg.readText()) } catch (_: Throwable) { null }
            if (token != null) return token
        }
        return null
    }
}
