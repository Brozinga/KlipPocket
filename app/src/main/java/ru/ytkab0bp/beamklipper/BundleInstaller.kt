package ru.ytkab0bp.beamklipper

import android.content.Context
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.service.WebPortFile
import java.io.File
import java.nio.charset.StandardCharsets

object BundleInstaller {
    private const val INSTALLED_VERSION_FILE = ".bundle_installed_version"

    @JvmStatic
    fun init(ctx: Context) {
        val assets = ctx.assets
        try {
            val pm = ctx.packageManager
            val info = pm.getPackageInfo(ctx.packageName, 0)
            var ver = readString(assets, "bundle_version") + "_beam-" + info.versionName

            val root = ctx.filesDir
            // The installed-version marker is a plain file, not SharedPreferences:
            // every Klippy/Moonraker service runs this in its own process (under
            // withBundleInstallLock), and SharedPreferences is cached per process
            // and written asynchronously, so a second process could still read the
            // OLD version, unpack the raw templates again (placeholders like
            // ${TTY_PATH}/${DEST_LIB} not yet substituted) and delete the tree
            // while the first process's Python was already importing from it —
            // which made the first start after an update fail with a SyntaxError /
            // "did not manage to locate a library called '${DEST_LIB}'".
            // It is written only once the whole install (unpack + patches) is done.
            val marker = File(root, INSTALLED_VERSION_FILE)
            val needsUnpack = try { marker.readText() != ver } catch (_: Exception) { true }
            if (needsUnpack) {
                val index = JSONObject(readString(assets, "index.json"))
                unpack(assets, index, root, "klipper")
                unpack(assets, index, root, "kalico")
                unpack(assets, index, root, "moonraker")
                unpack(assets, index, root, "octoeverywhere")
                unpack(assets, index, root, "obico")
            }

            // Moonraker's file_manager registers "<klipper_path>/docs" as the
            // "docs" root and adds a startup warning if it does not exist. We
            // don't bundle Klipper's docs tree, so create the (empty) folder to
            // silence "Supplied path (…/klipper/docs) for (docs) is invalid".
            File(root, "klipper/docs").mkdirs()
            File(root, "kalico/docs").mkdirs()

            val nativeDir = File(info.applicationInfo!!.nativeLibraryDir)

            patchBundledFile(root, assets, "klipper", "klippy/chelper/__init__.py") {
                it.replace(BundlePatches.DEST_LIB, File(nativeDir, "libklippy_chelper.so").absolutePath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/chelper/__init__.py") {
                it.replace(BundlePatches.DEST_LIB, File(nativeDir, "libkalico_chelper.so").absolutePath)
            }

            var str = readString(assets, "moonraker/moonraker/utils/sysfs_devs.py")
            str = str.replace(BundlePatches.SYSFS_TTY_ORIGINAL,
                BundlePatches.sysfsTty(File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath))
            BundleFiles.writeIfChanged(File(root, "moonraker/moonraker/utils/sysfs_devs.py"), str.toByteArray(StandardCharsets.UTF_8))

            val tempPath = File(KlipperApp.INSTANCE.cacheDir, "resonances").absolutePath
            patchBundledFile(root, assets, "klipper", "klippy/extras/resonance_tester.py") {
                it.replace(BundlePatches.TEMP_PATH, tempPath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/extras/resonance_tester.py") {
                it.replace(BundlePatches.TEMP_PATH, tempPath)
            }

            val ttyPath = "'" + File(KlipperApp.INSTANCE.filesDir, "serial").absolutePath + "'"
            patchBundledFile(root, assets, "klipper", "klippy/mcu.py") {
                it.replace(BundlePatches.TTY_PATH, ttyPath)
            }
            patchBundledFile(root, assets, "kalico", "klippy/mcu.py") {
                it.replace(BundlePatches.TTY_PATH, ttyPath)
            }

            // moonraker_obico.janus_config_builder calls board_id() at MODULE
            // IMPORT time (not deferred to actual Janus startup, which never
            // happens here — see docs/obico.md), and board_id() only guards
            // the devicetree read with os.path.isfile(), not a try/except
            // around open(). On Android that file exists but reading it is
            // denied by SELinux (untrusted_app), so the open() throws
            // PermissionError, which crashes app.py's entire import chain
            // before it even reaches main() — no log file gets written, the
            // service just silently never connects. pi_version() one
            // function above already wraps the identical read in a bare
            // except; this mirrors that.
            patchBundledFile(root, assets, "obico", "moonraker_obico/utils.py") {
                BundlePatches.patchObicoBoardId(it)
            }
            patchBundledFile(root, assets, "obico", "moonraker_obico/printer_discovery.py") {
                BundlePatches.patchObicoLinkStatus(it)
            }

            // Both moonraker_conn.py patches below: find_all_thermal_presets()
            // crashes on Beam's Moonraker, which returns the mainsail
            // "presets" database entry as an empty list instead of a dict
            // when no preset exists yet — an unhandled AttributeError that
            // kills the whole obico process before it reaches its persistent
            // server connection. Separately, upstream's _setup_include_cfgs()
            // shells out to a scripts/*.sh helper we don't vendor as an
            // executable, reimplemented in pure Python instead. (See
            // BundlePatches.patchObicoThermalPresets/patchObicoIncludeCfgs
            // for the full story on each; applied together since
            // patchBundledFile re-reads the pristine asset every call, so
            // separate calls on the same file would clobber each other.)
            patchBundledFile(root, assets, "obico", "moonraker_obico/moonraker_conn.py") {
                BundlePatches.patchObicoIncludeCfgs(BundlePatches.patchObicoThermalPresets(it))
            }

            // PLAY_TONE / SET_CAMERA_FLASHLIGHT / SET_CAMERA_FOCUS call the
            // app's own web server, whose port depends on the selected front
            // end (see WebPortFile) rather than being fixed. The two extras
            // live under klippy/extras/ (not beam_ext/) so Klipper's config
            // loader — importlib.import_module('extras.' + name) — can
            // actually resolve a [beam_beeper]/[beam_camera] section to them.
            val webPortFile = WebPortFile.path(KlipperApp.INSTANCE.filesDir).absolutePath
            for (bundleKey in arrayOf("klipper", "kalico")) {
                for (extra in arrayOf("klippy/extras/beam_beeper.py", "klippy/extras/beam_camera.py")) {
                    patchBundledFile(root, assets, bundleKey, extra) {
                        it.replace(BundlePatches.WEB_PORT_FILE, webPortFile)
                    }
                }
            }
            if (needsUnpack) marker.writeText(ver)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun unpack(assets: android.content.res.AssetManager, index: JSONObject, root: File, key: String) {
        val arr = index.optJSONArray(key)
        val files = arr?.let { a -> (0 until a.length()).map { a.optString(it) } }
        BundleFiles.unpack({ assets.open(it) }, files, root, key)
    }

    private fun patchBundledFile(
        root: File,
        assets: android.content.res.AssetManager,
        bundleKey: String,
        relativePath: String,
        transform: (String) -> String,
    ) {
        val target = File(root, "$bundleKey/$relativePath")
        if (!target.exists()) {
            return
        }
        val updated = transform(readString(assets, "$bundleKey/$relativePath"))
        BundleFiles.writeIfChanged(target, updated.toByteArray(StandardCharsets.UTF_8))
    }

    @JvmStatic
    fun readString(assets: android.content.res.AssetManager, key: String): String {
        return assets.open(key).use { inp ->
            inp.readBytes().toString(StandardCharsets.UTF_8)
        }
    }
}
