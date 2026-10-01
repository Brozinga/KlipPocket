package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.utils.CameraResolutions
import ru.ytkab0bp.beamklipper.utils.CameraRules
import ru.ytkab0bp.beamklipper.utils.CompanionFiles
import ru.ytkab0bp.beamklipper.utils.Engines
import ru.ytkab0bp.beamklipper.utils.Languages
import ru.ytkab0bp.beamklipper.utils.UsbNaming
import ru.ytkab0bp.beamklipper.utils.CameraZoom
import ru.ytkab0bp.beamklipper.utils.Frontends
import ru.ytkab0bp.beamklipper.update.FrontendUpdateChecker
import ru.ytkab0bp.beamklipper.update.FrontendUpdateResult
import ru.ytkab0bp.beamklipper.update.FrontendUpdateStep
import ru.ytkab0bp.beamklipper.update.FrontendVersionStatus
import ru.ytkab0bp.beamklipper.update.VersionStatus
import ru.ytkab0bp.beamklipper.utils.ObicoLink
import ru.ytkab0bp.beamklipper.utils.OctoEverywhereLink
import ru.ytkab0bp.beamklipper.utils.PrefValues
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    // KlipperApp.INSTANCE.getString() reads the raw Application resources,
    // which AppCompatDelegate.setApplicationLocales() (Prefs.applyAppLanguage)
    // does NOT keep in sync on API < 33 — that compat path only wraps
    // Activity contexts via attachBaseContext, so it falls back to the
    // device's system locale instead of the user's in-app language choice.
    // Compose's stringResource() (Activity-scoped) gets this right on its
    // own; anywhere in this ViewModel that needs a string outside Compose
    // must build its own locale-correct context instead.
    private fun localizedContext(): Context {
        val language = Prefs.appLanguage
        val base = KlipperApp.INSTANCE
        if (language == Prefs.LANGUAGE_SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(config)
    }

    val engine: StateFlow<String> = AppState.engine
    val webFrontend: StateFlow<String> = AppState.webFrontend
    val usbNaming: StateFlow<Int> = AppState.usbNaming
    val cameraEnabled: StateFlow<Boolean> = AppState.cameraEnabled
    val cameraSourceId: StateFlow<String?> = AppState.cameraSourceId
    val cameraRotation: StateFlow<Int> = AppState.cameraRotation
    val cameraResolution: StateFlow<Int> = AppState.cameraResolution
    val cameraZoom: StateFlow<Float> = AppState.cameraZoom
    val octoEverywhereEnabled: StateFlow<Boolean> = AppState.octoEverywhereEnabled
    val obicoEnabled: StateFlow<Boolean> = AppState.obicoEnabled
    val obicoServerUrl: StateFlow<String> = AppState.obicoServerUrl
    val obicoLinked: StateFlow<Boolean> = AppState.obicoLinked
    val appLanguage: StateFlow<String> = AppState.appLanguage
    val klipperVersion: StateFlow<VersionStatus> = AppState.klipperVersion
    val moonrakerVersion: StateFlow<VersionStatus> = AppState.moonrakerVersion
    val fluiddVersion: StateFlow<FrontendVersionStatus> = AppState.fluiddVersion
    val mainsailVersion: StateFlow<FrontendVersionStatus> = AppState.mainsailVersion
    val voyagerVersion: StateFlow<FrontendVersionStatus> = AppState.voyagerVersion

    // Settings-open is the only trigger for a version check — no background
    // timer (see ConfigScreen's LaunchedEffect(Unit)).
    fun refreshVersionChecks() = FrontendUpdateChecker.checkAllNow()

    fun isPrintActive(): Boolean = FrontendUpdateChecker.isPrintActive()

    suspend fun updateFrontend(frontend: String, targetTag: String, onProgress: (FrontendUpdateStep) -> Unit): FrontendUpdateResult =
        FrontendUpdateChecker.updateFrontend(frontend, targetTag, onProgress)

    fun cycleEngine() {
        setEngine(Engines.next(Prefs.engine))
    }

    fun setEngine(engine: String) {
        if (!Engines.isInstalled(KlipperApp.INSTANCE.filesDir, engine)) return
        Prefs.engine = engine
    }

    fun cycleFrontend() {
        Prefs.webFrontend = Frontends.next(Prefs.webFrontend)
    }

    fun setFrontend(frontend: String) {
        Prefs.webFrontend = frontend
    }

    fun cycleUsbNaming() {
        Prefs.usbDeviceNaming = UsbNaming.next(Prefs.usbDeviceNaming)
    }

    fun setCameraEnabled(enabled: Boolean) {
        Prefs.isCameraEnabled = enabled
        KlipperInstance.onCameraConfigChanged(enabled)
    }

    fun refreshCameraSwitch(granted: Boolean) {
        if (granted) {
            Prefs.isCameraEnabled = true
            KlipperInstance.onCameraConfigChanged(true)
        }
    }

    fun setOctoEverywhereEnabled(enabled: Boolean) {
        Prefs.isOctoEverywhereEnabled = enabled
        KlipperInstance.onOctoEverywhereConfigChanged(enabled)
    }

    // OctoEverywhere writes its generated printer id to an INI-style secrets
    // file under the instance's own storage dir once it first starts (see
    // linux_host/secrets.py). Only one instance ever runs the companion at a
    // time, so scanning all of them for whichever has it is simplest.
    fun octoEverywhereLinkUrl(): String? =
        CompanionFiles.octoEverywhereLinkUrl(KlipperInstance.getInstances().map { it.directory })

    fun setObicoEnabled(enabled: Boolean) {
        Prefs.isObicoEnabled = enabled
        KlipperInstance.onObicoConfigChanged(enabled)
    }


    // moonraker_obico's own PrinterDiscovery (running inside ObicoService
    // once Obico is enabled and not yet linked) polls the configured server
    // every ~2s and gets back a fresh one-time passcode to show the user —
    // the same code Obico's "Klipper (self-installed)" onboarding expects
    // you to enter manually when it can't auto-detect the printer on the
    // LAN. Upstream only exposes it via a Klipper gcode_macro variable
    // (meant for a printer.cfg macro + KlipperScreen panel this app doesn't
    // require); BundleInstaller patches it to also write a small JSON
    // status file next to moonraker-obico.cfg, read here instead.
    fun obicoDiscoveryStatus(): ObicoLink.DiscoveryStatus? =
        CompanionFiles.obicoStatus(KlipperInstance.getInstances().map { it.directory })

    // Discovery linking a printer completes entirely inside the Python
    // process — it writes the auth_token straight into moonraker-obico.cfg
    // itself, with no way to notify the Android side directly. Called while
    // polling obicoDiscoveryStatus(): the moment that file reports
    // is_linked, pull the token it already wrote back into Prefs so the
    // rest of the app (the "Linked ✓" row, future service restarts) agrees.
    fun syncObicoLinkStatus(): ObicoLink.DiscoveryStatus? {
        val status = obicoDiscoveryStatus() ?: return null
        if (status.isLinked && Prefs.obicoAuthToken.isNullOrBlank()) {
            CompanionFiles.obicoToken(KlipperInstance.getInstances().map { it.directory })
                ?.let { Prefs.obicoAuthToken = it }
        }
        return status
    }

    fun isObicoCloud(url: String): Boolean = ObicoLink.isCloud(url)

    fun obicoServerLabel(url: String): String =
        if (isObicoCloud(url)) localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.ObicoServerCloud)
        else url

    fun setObicoServer(cloud: Boolean, selfHostedUrl: String) {
        Prefs.obicoServerUrl = if (cloud) Prefs.OBICO_CLOUD_URL else selfHostedUrl
    }

    fun unlinkObico() {
        Prefs.obicoAuthToken = null
    }

    // Obico's own linking flow (moonraker_obico.link) is an interactive
    // terminal script: it either waits for a UDP-discovered "Link Now" tap
    // in the Obico app, or falls back to reading a 6-digit code from stdin.
    // Neither fits a Service with no terminal, so this replicates just the
    // one HTTP call that flow makes underneath (moonraker_obico.utils.
    // verify_link_code: POST {server}/api/v1/octo/verify/?code=XXXXXX ->
    // {"printer": {"auth_token": "..."}}) directly, from a code the user
    // gets from the Obico app/website and types into our own dialog.
    suspend fun linkObico(code: String): ObicoLink.Result = withContext(Dispatchers.IO) {
        try {
            if (code.trim().isEmpty()) return@withContext ObicoLink.Result.InvalidCode
            val url = URL(ObicoLink.verifyUrl(Prefs.obicoServerUrl, code))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val responseCode = conn.responseCode
            ObicoLink.classifyResponse(responseCode) ?: run {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Prefs.obicoAuthToken = ObicoLink.authTokenFromResponse(body)
                ObicoLink.Result.Success
            }
        } catch (e: Exception) {
            ObicoLink.Result.NetworkError(e.message)
        }
    }

    data class CameraSourceOption(val id: String?, val label: String)

    fun setCameraSource(id: String?) {
        Prefs.cameraId = id
    }

    fun cycleCameraRotation() {
        Prefs.cameraRotation = PrefValues.nextRotation(Prefs.cameraRotation)
    }

    fun cycleCameraResolution() {
        Prefs.cameraResolution = PrefValues.nextResolution(Prefs.cameraResolution, Prefs.CAMERA_RESOLUTION_PRESETS.size)
    }

    // Mirrors CameraService.resolveCameraId() so the zoom steps offered here
    // match the camera the service will really open (a pinned id, else a USB
    // webcam if one is plugged in, else the first camera).
    private fun zoomOptionsForSelectedCamera(): List<Float> {
        return try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = manager.cameraIdList
            val pinned = Prefs.cameraId
            val id = CameraRules.resolveId(ids.toList(), pinned) {
                try {
                    manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_EXTERNAL
                } catch (_: Throwable) { false }
            } ?: return listOf(1f)
            CameraZoom.options(CameraZoom.maxZoom(manager.getCameraCharacteristics(id)))
        } catch (_: Throwable) {
            listOf(1f)
        }
    }

    fun cameraZoomOptions(): List<Float> = zoomOptionsForSelectedCamera()

    // Saved zoom may exceed what a newly selected camera supports.
    fun effectiveCameraZoom(zoom: Float): Float = CameraZoom.clamp(zoom, zoomOptionsForSelectedCamera())

    fun cycleCameraZoom() {
        val options = zoomOptionsForSelectedCamera()
        if (options.size <= 1) return
        Prefs.cameraZoom = CameraRules.nextZoom(Prefs.cameraZoom, options)
    }

    fun cameraResolutionTitle(resolution: Int): String =
        localizedContext().getString(CameraResolutions.nameRes(resolution))

    fun cameraSourceOptions(): List<CameraSourceOption> {
        val options = mutableListOf(
            CameraSourceOption(null, localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto))
        )
        try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = manager.cameraIdList.toList()
            for (id in ids) {
                options.add(CameraSourceOption(id, cameraLabel(manager, id, ids)))
            }
        } catch (_: Throwable) {}
        return options
    }

    fun cameraSourceTitle(id: String?): String {
        if (id == null) return localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        return try {
            val manager = KlipperApp.INSTANCE.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = manager.cameraIdList.toList()
            if (ids.contains(id)) cameraLabel(manager, id, ids)
            else localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        } catch (_: Throwable) {
            localizedContext().getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceAuto)
        }
    }

    // Devices with multiple lenses per facing (main/ultra-wide/telephoto on the
    // back, sometimes two on the front) expose each as its own top-level
    // Camera2 id — cameraIdList already lists all of them, nothing special
    // needed there. What's missing is telling them apart: the raw id (e.g.
    // "0"/"2"/"3") means nothing to a user. Since there's no cross-OEM API for
    // "this is the ultra-wide", the generic, accurate option is the 35mm-
    // equivalent focal length (the same number phones are marketed with,
    // e.g. ~13mm/26mm/52mm), computed from LENS_INFO_AVAILABLE_FOCAL_LENGTHS
    // + SENSOR_INFO_PHYSICAL_SIZE — falls back to just numbering them if that
    // metadata isn't available.
    private fun cameraLabel(manager: CameraManager, id: String, allIds: List<String>): String {
        val ctx = localizedContext()
        val chars = try { manager.getCameraCharacteristics(id) } catch (_: Throwable) { null }
        val facing = chars?.get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
            return ctx.getString(ru.ytkab0bp.beamklipper.R.string.CameraSourceUsbWebcam, id)
        }
        val sameFacingIds = allIds.filter { other ->
            try { manager.getCameraCharacteristics(other).get(CameraCharacteristics.LENS_FACING) == facing } catch (_: Throwable) { false }
        }.sorted()
        val baseRes = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> ru.ytkab0bp.beamklipper.R.string.CameraSourceBuiltInBack
            CameraCharacteristics.LENS_FACING_FRONT -> ru.ytkab0bp.beamklipper.R.string.CameraSourceBuiltInFront
            else -> ru.ytkab0bp.beamklipper.R.string.CameraSourceOther
        }
        if (sameFacingIds.size <= 1) return ctx.getString(baseRes, id)

        val multiRes = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> ru.ytkab0bp.beamklipper.R.string.CameraSourceBackN
            CameraCharacteristics.LENS_FACING_FRONT -> ru.ytkab0bp.beamklipper.R.string.CameraSourceFrontN
            else -> baseRes
        }
        val index = sameFacingIds.indexOf(id) + 1
        val label = ctx.getString(multiRes, index)
        val focalHint = chars?.let { focalLengthHint(it) }
        return if (focalHint != null) "$label ($focalHint)" else label
    }

    private fun focalLengthHint(chars: CameraCharacteristics): String? {
        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
        val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        return CameraRules.equivalentFocalLength35mm(focal, sensor.width, sensor.height)?.let { "${it}mm" }
    }

    fun engineTitle(engine: String): String = localizedContext().getString(Engines.nameRes(engine))

    fun frontendTitle(frontend: String): String =
        localizedContext().getString(Frontends.nameRes(frontend))

    fun usbNamingTitle(naming: Int): String = localizedContext().getString(UsbNaming.nameRes(naming))

    fun languageTitle(language: String): String = localizedContext().getString(Languages.nameRes(language))
}
