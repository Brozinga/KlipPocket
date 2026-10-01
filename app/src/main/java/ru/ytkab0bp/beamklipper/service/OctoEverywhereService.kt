package ru.ytkab0bp.beamklipper.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Base64
import android.util.Log
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.R
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

// Runs the real OctoEverywhere Klipper/Moonraker companion (vendored at
// app/src/main/octoeverywhere, from github.com/QuinnDamerell/OctoPrint-OctoEverywhere)
// as its own Chaquopy process, the same way KlippyService/MoonrakerService run
// their own vendored Python. Unlike those, this is a single app-wide instance
// (like CameraService/WebService) bound to whichever KlipperInstance first
// reaches RUNNING — see KlipperInstance.onOctoEverywhereConfigChanged.
class OctoEverywhereService : BasePythonService() {
    companion object {
        private const val TAG = "beam_octoeverywhere"
        private const val ID = 600000
    }

    private var wifiLock: WifiManager.WifiLock? = null
    private val stoppedByUser = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? {
        val b = super.onBind(intent) ?: return null
        acquireLocks()
        val inst = instance
        if (inst != null) {
            val not = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(this, KlipperApp.SERVICES_CHANNEL)
            else
                Notification.Builder(this)
            not.setContentTitle(getString(R.string.OctoEverywhereTitle))
                .setContentText(getString(R.string.OctoEverywhereDescription))
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
            notificationManager.notify(ID, not.build())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, not.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(ID, not.build())
            }
        }
        return b
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (stoppedByUser.get()) return
    }

    private fun acquireLocks() {
        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL,
                    "BeamKlipper::OctoEverywhereWiFiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to acquire wifilock", t)
        }
    }

    private fun releaseLocks() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {}
        wifiLock = null
    }

    override fun onDestroy() {
        stoppedByUser.set(true)
        releaseLocks()
        super.onDestroy()
        stopForeground(true)
        notificationManager.cancel(ID)
    }

    override fun onStartPython() {
        val inst = instance ?: return
        try {
            val oeDir = File(KlipperApp.INSTANCE.filesDir, "octoeverywhere")
            if (!File(oeDir, "pyproject.toml").exists()) {
                Log.e(TAG, "OctoEverywhere bundle not unpacked yet, aborting")
                return
            }

            // Instance-scoped, persisted across restarts: this is where the
            // real printer id / private key OctoEverywhere generates on first
            // link get written (octoeverywhere.secrets), so re-linking isn't
            // needed every time the service restarts.
            val localStorage = File(inst.directory, "octoeverywhere")
            localStorage.mkdirs()

            val configFolder = File(inst.publicDirectory, "config")
            val logFolder = File(inst.publicDirectory, "logs")
            val moonrakerCfg = File(configFolder, "moonraker.conf")
            // "Moonraker connected" (which is what gates this service being
            // bound at all, see KlipperInstance.notifyStateChanged) only means
            // MoonrakerService's process finished binding — BaseMoonrakerService
            // writes moonraker.conf asynchronously afterward, on its own
            // pythonHandler thread, so it isn't guaranteed to exist yet the
            // instant we get here. Poll instead of aborting on the first miss.
            val deadline = System.currentTimeMillis() + 15_000L
            while (!moonrakerCfg.exists() && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(300) } catch (_: InterruptedException) { break }
            }
            if (!moonrakerCfg.exists()) {
                Log.e(TAG, "moonraker.conf missing for instance ${inst.id} after waiting, aborting")
                return
            }

            val config = OctoEverywhereConfig.build(
                inst.id, oeDir.absolutePath, localStorage.absolutePath,
                configFolder.absolutePath, logFolder.absolutePath, moonrakerCfg.absolutePath
            )
            val configB64 = Base64.encodeToString(
                config.toString().toByteArray(StandardCharsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )

            // Mirrors BaseKlippyService/BaseMoonrakerService: write the
            // bootstrap fresh into the unpacked bundle dir rather than
            // shipping it as a static asset.
            val bsFile = File(oeDir, "octoeverywhere_bs.py")
            try {
                bsFile.writeText(
                    BootstrapScripts.OCTOEVERYWHERE,
                    StandardCharsets.UTF_8
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Bootstrap write failed", e)
            }

            // A present-but-empty dev config flips Startup's isDevMode to
            // true, which is the plugin's own supported switch for disabling
            // its Sentry crash telemetry (octoeverywhere/sentry.py) — without
            // it also touching which server it talks to (that's a *different*
            // dev-config key we deliberately leave unset), so it still links
            // against the real octoeverywhere.com.
            runPython(oeDir, "octoeverywhere_bs", "octoeverywhere.py", configB64, "{}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OctoEverywhere", e)
        }
    }
}
