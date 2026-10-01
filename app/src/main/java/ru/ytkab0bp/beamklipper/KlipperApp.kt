package ru.ytkab0bp.beamklipper

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.IOException
import androidx.multidex.MultiDexApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ytkab0bp.beamklipper.db.BeamDB
import ru.ytkab0bp.beamklipper.serial.UsbSerialManager
import ru.ytkab0bp.beamklipper.update.FrontendUpdateChecker
import ru.ytkab0bp.beamklipper.utils.CrashReport
import ru.ytkab0bp.beamklipper.utils.FileLocks
import ru.ytkab0bp.beamklipper.utils.Prefs
import ru.ytkab0bp.eventbus.EventBus

class KlipperApp : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        installCrashLogger()
        Prefs.init(this)
        EventBus.registerImpl(this)

        val isMainProcess = getProcessNameCompat() == packageName

        if (isMainProcess) {
            DATABASE = BeamDB(this)
        }

        if (Prefs.appLanguage != Prefs.LANGUAGE_SYSTEM) {
            Prefs.applyAppLanguage()
        }

        hasUpdateInfo = try {
            assets.open("update.json").close()
            true
        } catch (_: java.io.IOException) {
            false
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(SERVICES_CHANNEL, getString(R.string.ServicesChannel), NotificationManager.IMPORTANCE_LOW))
            val watchdog = NotificationChannel(WATCHDOG_CHANNEL, getString(R.string.WatchdogChannel), NotificationManager.IMPORTANCE_HIGH)
            watchdog.description = getString(R.string.WatchdogChannelDesc)
            watchdog.enableLights(true)
            watchdog.enableVibration(true)
            watchdog.setShowBadge(true)
            nm.createNotificationChannel(watchdog)
        }

        if (isMainProcess) {
            appScope.launch(Dispatchers.IO) {
                // Self-heals a process kill mid-frontend-update from a
                // previous run (see FrontendOverlay.cleanupOrphans) before
                // WebService or Settings ever look at the override tree.
                FrontendUpdateChecker.cleanupOrphansOnStartup()
            }
            appScope.launch {
                Log.i("beam_app", "Loading instances from DB (bundle install deferred until START press)")
                KlipperInstance.resetSlotsForFreshStart()
                val instances = withContext(Dispatchers.IO) {
                    DATABASE.getInstances()
                }
                Log.i("beam_app", "DB.getInstances() returned ${instances.size} rows")
                KlipperInstance.onInstancesLoadedFromDB(instances)
            }
            appScope.launch(Dispatchers.IO) {
                UsbSerialManager.init(this@KlipperApp)
            }
        }
    }

    /**
     * Persist any uncaught exception (from any process) to a file under the app's
     * external files dir so a "opens and closes" crash on devices we can't attach
     * to (e.g. Galaxy S10+) leaves a readable stack trace behind.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                val f = File(dir, "last_crash.txt")
                f.writeText(CrashReport.format(
                    System.currentTimeMillis(), getProcessNameCompatInternal(), thread.name,
                    Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT,
                    Build.SUPPORTED_ABIS.toList(), Log.getStackTraceString(throwable)
                ))
                Log.e("beam_crash", "Uncaught exception on ${thread.name}", throwable)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun getProcessNameCompat(): String {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentProcessName")
            method.invoke(null) as String
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        @JvmStatic
        internal fun getProcessNameCompatInternal(): String {
            if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
            return try {
                val activityThread = Class.forName("android.app.ActivityThread")
                val method = activityThread.getDeclaredMethod("currentProcessName")
                method.invoke(null) as String
            } catch (_: ReflectiveOperationException) {
                try { File("/proc/self/cmdline").readBytes().takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8) }
                catch (_: Throwable) { "" }
            }
        }

        private const val CHAQUOPY_SEED_MARKER = ".chaquopy_seed_v1"
        private const val CHAQUOPY_LOCK_NAME = ".chaquopy_lock"
        private const val MOONRAKER_LOCK_NAME = ".moonraker_port_lock"
        private const val BUNDLE_INSTALL_LOCK_NAME = ".bundle_install_lock"

        fun withChaquopyLock(ctx: Context, action: () -> Unit) =
            FileLocks.withFileLock(File(ctx.filesDir, CHAQUOPY_LOCK_NAME), action)

        // BundleInstaller.init() unpacks the vendored klipper/kalico/moonraker/
        // octoeverywhere bundles into shared app storage. Every KlippyService,
        // MoonrakerService and (now) OctoEverywhereService runs in its own
        // process and independently races to call it on first bind; bundleInstallJob's
        // `synchronized` only dedupes within a single process. Without a
        // cross-process lock, two processes racing to delete+recreate the same
        // directory at once corrupts each other's unpack (Kotlin's
        // deleteRecursively() throws "rootDir must be verified to be directory
        // beforehand" when the directory is mutated mid-walk by another
        // process) — this crashed KlippyService_0 in practice once
        // OctoEverywhereService made a 3-way race common instead of a 2-way one.
        fun withBundleInstallLock(ctx: Context, action: () -> Unit) =
            FileLocks.withFileLock(File(ctx.filesDir, BUNDLE_INSTALL_LOCK_NAME), action)

        fun withMoonrakerPortLock(ctx: Context, action: () -> Unit) =
            FileLocks.withMoonrakerPortLock(File(ctx.filesDir, MOONRAKER_LOCK_NAME), android.os.Process.myPid(), action = action)

        fun seedChaquopyDirLocked(ctx: Context) {
            withChaquopyLock(ctx) {
                val marker = File(ctx.filesDir, CHAQUOPY_SEED_MARKER)
                if (marker.exists()) return@withChaquopyLock
                try {
                    val platform = AndroidPlatform(ctx)
                    platform.path
                    try {
                        if (!Python.isStarted()) {
                            Python.start(platform)
                        }
                    } catch (_: IllegalStateException) {
                    } catch (_: Throwable) {
                        try { File(ctx.filesDir, "chaquopy").deleteRecursively() } catch (_: Throwable) {}
                        try {
                            if (!Python.isStarted()) {
                                Python.start(AndroidPlatform(ctx))
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}
                try {
                    File(ctx.filesDir, "chaquopy").mkdirs()
                    marker.createNewFile()
                } catch (_: Throwable) {}
            }
        }

        val PERMISSION: String
            get() = INSTANCE.packageName + ".permission.INTERNAL_BROADCASTS"
        @JvmField
        val SERVICES_CHANNEL = "services"
        @JvmField
        val WATCHDOG_CHANNEL = "watchdog"
        @JvmField
        val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val bundleInstallLock = Any()
        @Volatile
        private var _bundleInstallJob: Deferred<Unit>? = null
        val bundleInstallJob: Deferred<Unit>
            get() = _bundleInstallJob ?: synchronized(bundleInstallLock) {
                _bundleInstallJob ?: appScope.async(Dispatchers.IO) {
                    withBundleInstallLock(INSTANCE) { BundleInstaller.init(INSTANCE) }
                }.also { _bundleInstallJob = it }
            }

        lateinit var INSTANCE: KlipperApp
        @Volatile
        private var _DATABASE: BeamDB? = null
        var DATABASE: BeamDB
            get() = _DATABASE
                ?: synchronized(this) {
                    _DATABASE
                        ?: run {
                            val app = INSTANCE
                            if (getProcessNameCompatInternal() != app.packageName) {
                                throw IllegalStateException("DATABASE accessed in child process ${getProcessNameCompatInternal()}; not allowed — use instance.id")
                            }
                            BeamDB(app).also { _DATABASE = it }
                        }
                }
            set(v) { _DATABASE = v }
        fun getDatabaseOrNull(): BeamDB? = _DATABASE
        @JvmField
        var EVENT_BUS: EventBus = EventBus.newBus("main")
        @JvmField
        var hasUpdateInfo = false
    }
}
