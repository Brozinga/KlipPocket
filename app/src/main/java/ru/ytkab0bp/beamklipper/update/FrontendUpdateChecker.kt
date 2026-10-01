package ru.ytkab0bp.beamklipper.update

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.BundleInstaller
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.service.WebPortFile
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

sealed class FrontendUpdateStep {
    data class Downloading(val percent: Int) : FrontendUpdateStep()
    object Extracting : FrontendUpdateStep()
    object Replacing : FrontendUpdateStep()
    object Done : FrontendUpdateStep()
    data class Error(val message: String?) : FrontendUpdateStep()
}

sealed class FrontendUpdateResult {
    object Success : FrontendUpdateResult()
    data class Failure(val message: String?) : FrontendUpdateResult()
}

// On-demand version checking + in-app update for Fluidd/Mainsail/Voyager-UI,
// plus informational-only checking for Klipper/Moonraker. Runs only when
// asked (checkAllNow(), called from ConfigScreen whenever Settings opens) —
// no background timer, per product decision.
object FrontendUpdateChecker {
    private val overrideRoot: File get() = File(KlipperApp.INSTANCE.filesDir, FrontendOverlay.OVERRIDE_DIR_NAME)

    private val _klipperStatus = MutableStateFlow(VersionStatus(null, null, 0L))
    val klipperStatus: StateFlow<VersionStatus> = _klipperStatus

    private val _moonrakerStatus = MutableStateFlow(VersionStatus(null, null, 0L))
    val moonrakerStatus: StateFlow<VersionStatus> = _moonrakerStatus

    private val _fluiddStatus = MutableStateFlow(FrontendVersionStatus(bundledVersion(Prefs.FRONTEND_FLUIDD), null, 0L))
    val fluiddStatus: StateFlow<FrontendVersionStatus> = _fluiddStatus

    private val _mainsailStatus = MutableStateFlow(FrontendVersionStatus(bundledVersion(Prefs.FRONTEND_MAINSAIL), null, 0L))
    val mainsailStatus: StateFlow<FrontendVersionStatus> = _mainsailStatus

    private val _voyagerStatus = MutableStateFlow(FrontendVersionStatus(bundledVersion(Prefs.FRONTEND_VOYAGER), null, 0L))
    val voyagerStatus: StateFlow<FrontendVersionStatus> = _voyagerStatus

    private val inFlight = ConcurrentHashMap<String, Deferred<FrontendUpdateResult>>()

    // One-time self-heal for a process kill mid-update from a previous run —
    // call once at app start, before anything else touches the override tree.
    fun cleanupOrphansOnStartup() {
        for (frontend in FrontendRepos.FRONTENDS.keys) {
            FrontendOverlay.cleanupOrphans(overrideRoot, frontend)
        }
    }

    private fun readAsset(key: String): String? = try {
        BundleInstaller.readString(KlipperApp.INSTANCE.assets, key).trim().takeIf { it.isNotBlank() && it != "unknown" }
    } catch (_: Exception) {
        null
    }

    // The version actually being served right now: an in-app override if one
    // was ever installed, else whatever the APK was built with.
    private fun activeVersion(frontend: String): String =
        FrontendOverlay.activeVersionOverride(overrideRoot, frontend)
            ?: readAsset("${frontend}_version")
            ?: "?"

    private fun bundledVersion(frontend: String): String = activeVersion(frontend)

    fun checkAllNow() {
        KlipperApp.appScope.launch(Dispatchers.IO) {
            checkKlipper()
            checkMoonraker()
            checkFrontend(Prefs.FRONTEND_FLUIDD, _fluiddStatus)
            checkFrontend(Prefs.FRONTEND_MAINSAIL, _mainsailStatus)
            checkFrontend(Prefs.FRONTEND_VOYAGER, _voyagerStatus)
        }
    }

    private fun checkKlipper() {
        val bundled = readAsset("klipper_version")
        try {
            val latest = GitHubReleases.latestVersionTag(FrontendRepos.KLIPPER_OWNER, FrontendRepos.KLIPPER_REPO)
            _klipperStatus.value = VersionStatus(bundled, latest, System.currentTimeMillis())
        } catch (_: Exception) {
            _klipperStatus.value = _klipperStatus.value.copy(bundled = bundled, checkedAtMs = System.currentTimeMillis(), error = true)
        }
    }

    private fun checkMoonraker() {
        val bundled = readAsset("moonraker_version")
        try {
            val latest = GitHubReleases.latestVersionTag(FrontendRepos.MOONRAKER_OWNER, FrontendRepos.MOONRAKER_REPO)
            _moonrakerStatus.value = VersionStatus(bundled, latest, System.currentTimeMillis())
        } catch (_: Exception) {
            _moonrakerStatus.value = _moonrakerStatus.value.copy(bundled = bundled, checkedAtMs = System.currentTimeMillis(), error = true)
        }
    }

    private fun checkFrontend(frontend: String, flow: MutableStateFlow<FrontendVersionStatus>) {
        val active = activeVersion(frontend)
        val repo = FrontendRepos.FRONTENDS[frontend] ?: return
        try {
            val latest = GitHubReleases.latestReleaseTag(repo.owner, repo.repo)
            flow.value = FrontendVersionStatus(active, latest, System.currentTimeMillis())
        } catch (_: Exception) {
            flow.value = flow.value.copy(active = active, checkedAtMs = System.currentTimeMillis(), error = true)
        }
    }

    // Best-effort: goes through the app's own embedded web server (whichever
    // port it's listening on right now), which already proxies /printer/...
    // to whichever Moonraker instance is currently active — same path a
    // browser tab open on Fluidd/Mainsail would hit. Any failure just means
    // "don't warn," never blocks the update itself.
    fun isPrintActive(): Boolean = try {
        val port = WebPortFile.path(KlipperApp.INSTANCE.filesDir).readText().trim().toInt()
        val conn = URL("http://127.0.0.1:$port/printer/objects/query?print_stats").openConnection() as HttpURLConnection
        conn.connectTimeout = 3_000
        conn.readTimeout = 3_000
        val state = try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optJSONObject("result")
                    ?.optJSONObject("status")
                    ?.optJSONObject("print_stats")
                    ?.optString("state")
            }
        } finally {
            conn.disconnect()
        }
        state == "printing" || state == "paused"
    } catch (_: Exception) {
        false
    }

    // Guarded by `inFlight` so a double-tap (or the confirm dialog somehow
    // firing twice) awaits the same download+extract+swap instead of
    // starting a second one — mirrors KlipperApp's memoized bundleInstallJob.
    suspend fun updateFrontend(
        frontend: String,
        targetTag: String,
        onProgress: (FrontendUpdateStep) -> Unit,
    ): FrontendUpdateResult {
        val existing = inFlight[frontend]
        if (existing != null) return existing.await()

        val job = KlipperApp.appScope.async(Dispatchers.IO) {
            runUpdate(frontend, targetTag, onProgress)
        }
        inFlight[frontend] = job
        try {
            return job.await()
        } finally {
            inFlight.remove(frontend, job)
        }
    }

    private fun runUpdate(frontend: String, targetTag: String, onProgress: (FrontendUpdateStep) -> Unit): FrontendUpdateResult {
        val zipUrl = FrontendRepos.zipUrl(frontend, targetTag)
            ?: return FrontendUpdateResult.Failure("Unknown frontend: $frontend")

        val tempZip = File(KlipperApp.INSTANCE.cacheDir, "${frontend}_update.zip.part")
        val newDir = File(overrideRoot, "$frontend.new")
        try {
            newDir.deleteRecursively()
            overrideRoot.mkdirs()

            onProgress(FrontendUpdateStep.Downloading(0))
            GitHubReleases.downloadToFile(zipUrl, tempZip) { percent ->
                onProgress(FrontendUpdateStep.Downloading(percent))
            }

            onProgress(FrontendUpdateStep.Extracting)
            tempZip.inputStream().use { FrontendOverlay.extractZip(it, newDir) }
            FrontendOverlay.writeInstalledVersionMarker(newDir, targetTag)

            onProgress(FrontendUpdateStep.Replacing)
            if (!FrontendOverlay.swapIn(overrideRoot, frontend, newDir)) {
                throw java.io.IOException("Failed to swap in new $frontend build")
            }

            val statusFlow = when (frontend) {
                Prefs.FRONTEND_FLUIDD -> _fluiddStatus
                Prefs.FRONTEND_MAINSAIL -> _mainsailStatus
                Prefs.FRONTEND_VOYAGER -> _voyagerStatus
                else -> null
            }
            statusFlow?.let { it.value = it.value.copy(active = targetTag) }

            onProgress(FrontendUpdateStep.Done)
            return FrontendUpdateResult.Success
        } catch (e: Exception) {
            newDir.deleteRecursively()
            onProgress(FrontendUpdateStep.Error(e.message))
            return FrontendUpdateResult.Failure(e.message)
        } finally {
            tempZip.delete()
        }
    }
}
