package ru.ytkab0bp.beamklipper.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.ytkab0bp.beamklipper.BuildConfig
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.MainActivity
import ru.ytkab0bp.beamklipper.R
import ru.ytkab0bp.beamklipper.utils.Prefs
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** The newest KlipPocket release on GitHub: its version, its text and the APK that fits this device. */
data class AppReleaseInfo(
    val tag: String,
    val notes: String,
    val pageUrl: String?,
    val apkName: String?,
    val apkUrl: String?,
    val sumsUrl: String?,
)

data class AppUpdateStatus(
    val current: String,
    val latest: AppReleaseInfo?,
    val checkedAtMs: Long,
    val error: Boolean = false,
) {
    val available: Boolean get() = latest != null && VersionCompare.tagNewer(current, latest.tag)
}

sealed class AppUpdateStep {
    object Idle : AppUpdateStep()
    data class Downloading(val percent: Int) : AppUpdateStep()
    object Verifying : AppUpdateStep()
    // The system installer is on screen (or a notification waits for a tap).
    object Ready : AppUpdateStep()
    // Android has to be told once to let KlipPocket install apps.
    object NeedsPermission : AppUpdateStep()
    data class Error(val message: String?) : AppUpdateStep()
}

// KlipPocket updating itself from its own GitHub releases. Looks for a newer
// release twice a day while the app process lives and every time Settings
// opens; a failed check (no network, rate limit) is silent. Installing needs
// the user's tap in Android's installer — only system apps can do it silently.
object AppUpdateChecker {
    const val OWNER = "Brozinga"
    const val REPO = "KlipPocket"
    const val UPDATE_CHANNEL = "app_update"
    private const val NOTIFICATION_ID = 0x4B50
    private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val RETRY_INTERVAL_MS = 3L * 60 * 60 * 1000
    private const val FIRST_CHECK_DELAY_MS = 10_000L
    private const val MIN_GAP_MS = 10L * 60 * 1000

    private val _status = MutableStateFlow(AppUpdateStatus(BuildConfig.VERSION_NAME, null, 0L))
    val status: StateFlow<AppUpdateStatus> = _status

    private val _step = MutableStateFlow<AppUpdateStep>(AppUpdateStep.Idle)
    val step: StateFlow<AppUpdateStep> = _step

    // The "update available" notification asks the app to open Settings at the
    // software versions. NavHost switches the tab, ConfigScreen scrolls there
    // and clears the request.
    const val EXTRA_OPEN_UPDATE = "open_app_update"
    private val _openRequest = MutableStateFlow(false)
    val openRequest: StateFlow<Boolean> = _openRequest
    fun requestOpen() { _openRequest.value = true }
    fun consumeOpenRequest() { _openRequest.value = false }

    private val periodicStarted = AtomicBoolean(false)
    private val installing = AtomicBoolean(false)
    private val checking = AtomicBoolean(false)

    private val updatesDir: File get() = File(KlipperApp.INSTANCE.cacheDir, "app_update")

    // Call once from the main process. While the app process lives it looks
    // again every 12 h (twice a day); a failed look is retried after 3 h.
    fun startPeriodic() {
        if (!periodicStarted.compareAndSet(false, true)) return
        KlipperApp.appScope.launch(Dispatchers.IO) {
            // an APK left from an earlier update is no longer needed once it ran
            updatesDir.deleteRecursively()
            delay(FIRST_CHECK_DELAY_MS)
            while (true) {
                val ok = checkIfDue()
                delay(if (ok) CHECK_INTERVAL_MS else RETRY_INTERVAL_MS)
            }
        }
    }

    // The app screen was opened (also from a process that kept running in the
    // background): look now. Only a look from the last few minutes holds it back,
    // so switching back and forth never hammers GitHub.
    fun onAppOpened() {
        KlipperApp.appScope.launch(Dispatchers.IO) { checkIfDue() }
    }

    @Volatile private var lastAttemptMs = 0L

    private fun checkIfDue(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAttemptMs in 0 until MIN_GAP_MS) return true
        lastAttemptMs = now
        return check(notify = true)
    }

    // Opening Settings (or the Dashboard) asks right away.
    fun checkNow() {
        KlipperApp.appScope.launch(Dispatchers.IO) { check(notify = false) }
    }

    // True when GitHub answered. A failure keeps the previous answer and says nothing.
    internal fun check(notify: Boolean): Boolean {
        if (!checking.compareAndSet(false, true)) return true
        try {
            val release = try {
                GitHubReleases.latestRelease(OWNER, REPO)
            } catch (_: Exception) {
                _status.value = _status.value.copy(checkedAtMs = System.currentTimeMillis(), error = true)
                return false
            }
            val tag = release?.tagName ?: return false
            val info = toInfo(tag, release)
            val now = System.currentTimeMillis()
            val status = AppUpdateStatus(BuildConfig.VERSION_NAME, info, now)
            _status.value = status
            // One notification per version; the look from Settings stays quiet (the screen already shows it).
            if (notify && status.available && Prefs.appUpdateNotifiedTag != tag) {
                postAvailableNotification(KlipperApp.INSTANCE, info)
                Prefs.appUpdateNotifiedTag = tag
            }
            return true
        } finally {
            checking.set(false)
        }
    }

    private fun toInfo(tag: String, release: GitHubReleases.ReleaseDetails): AppReleaseInfo {
        val assets = release.assets.orEmpty()
        val suffix = abiSuffix()
        val apk = assets.firstOrNull { it.name?.endsWith("_$suffix.apk") == true }
            ?: assets.firstOrNull { it.name?.endsWith(".apk") == true && assets.count { a -> a.name?.endsWith(".apk") == true } == 1 }
        val sums = assets.firstOrNull { it.name == "SHA256SUMS.txt" }
        return AppReleaseInfo(
            tag = tag,
            notes = ReleaseNotes.plain(release.body),
            pageUrl = release.htmlUrl,
            apkName = apk?.name,
            apkUrl = apk?.url,
            sumsUrl = sums?.url,
        )
    }

    // The release has one APK per processor. Keep the one that is installed
    // (its native libraries tell which), else the first the device supports.
    internal fun abiSuffix(): String {
        val libDir = KlipperApp.INSTANCE.applicationInfo.nativeLibraryDir?.substringAfterLast('/')
        return suffixFor(libDir) ?: Build.SUPPORTED_ABIS.firstNotNullOfOrNull { suffixFor(it) } ?: "armv7"
    }

    internal fun suffixFor(abi: String?): String? = when (abi) {
        "arm64", "arm64-v8a" -> "arm64"
        "arm", "armeabi-v7a", "armeabi" -> "armv7"
        "x86_64", "x86", "amd64" -> "amd64"
        else -> null
    }

    // "hash  file" lines, as `sha256sum` writes them.
    internal fun expectedHash(sums: String, fileName: String): String? =
        sums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].trimStart('*') == fileName }
            ?.get(0)?.lowercase()

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // Downloads the APK of the newest release (checking it against the
    // release's SHA256SUMS.txt) and opens Android's installer. [fromUi]: a
    // screen is open, so the installer can be opened right away; otherwise (the
    // Dashboard asked) a notification carries the tap that opens it.
    fun startInstall(fromUi: Boolean) {
        val info = _status.value.takeIf { it.available }?.latest ?: return
        if (!installing.compareAndSet(false, true)) return
        KlipperApp.appScope.launch(Dispatchers.IO) {
            val ctx = KlipperApp.INSTANCE
            try {
                val apkUrl = info.apkUrl ?: throw java.io.IOException("This release has no APK for this device")
                val apkName = info.apkName ?: "KlipPocket_${info.tag}.apk"
                updatesDir.mkdirs()
                val apk = File(updatesDir, apkName)
                val part = File(updatesDir, "$apkName.part")
                if (!apk.exists()) {
                    _step.value = AppUpdateStep.Downloading(0)
                    GitHubReleases.downloadToFile(apkUrl, part) { _step.value = AppUpdateStep.Downloading(it) }
                    _step.value = AppUpdateStep.Verifying
                    val expected = info.sumsUrl?.let { u ->
                        try { expectedHash(GitHubReleases.fetchText(u), apkName) } catch (_: Exception) { null }
                    }
                    if (expected != null && expected != sha256(part)) {
                        part.delete()
                        throw java.io.IOException("Checksum mismatch")
                    }
                    if (!part.renameTo(apk)) throw java.io.IOException("Could not save the update")
                }
                openInstaller(ctx, apk, fromUi)
            } catch (e: Exception) {
                _step.value = AppUpdateStep.Error(e.message)
            } finally {
                installing.set(false)
            }
        }
    }

    private fun installIntent(ctx: Context, apk: File): Intent {
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        } else {
            Uri.fromFile(apk)
        }
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun openInstaller(ctx: Context, apk: File, fromUi: Boolean) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            _step.value = AppUpdateStep.NeedsPermission
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val intent = installIntent(ctx, apk)
        _step.value = AppUpdateStep.Ready
        if (fromUi) {
            ctx.startActivity(intent)
        } else {
            postNotification(
                ctx,
                ctx.getString(R.string.AppUpdateReadyTitle),
                ctx.getString(R.string.AppUpdateReadyText, _status.value.latest?.tag ?: ""),
                PendingIntent.getActivity(ctx, 1, intent, pendingFlags())
            )
        }
    }

    private fun pendingFlags() =
        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

    private fun postAvailableNotification(ctx: Context, info: AppReleaseInfo) {
        val open = Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_UPDATE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        postNotification(
            ctx,
            ctx.getString(R.string.AppUpdateAvailableTitle),
            ctx.getString(R.string.AppUpdateAvailableText, info.tag),
            PendingIntent.getActivity(ctx, 0, open, pendingFlags())
        )
    }

    // Without the notification permission (Android 13+) there is simply no notification.
    private fun postNotification(ctx: Context, title: String, text: String, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val notif = NotificationCompat.Builder(ctx, UPDATE_CHANNEL)
                .setSmallIcon(R.drawable.icon_static)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notif)
        } catch (_: Throwable) {
        }
    }

    fun resetStep() { _step.value = AppUpdateStep.Idle }
}
