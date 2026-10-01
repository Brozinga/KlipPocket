package ru.ytkab0bp.beamklipper

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// How the bundled Klipper/Moonraker/companion trees are unpacked and patched,
// free of Android so it can be unit-tested against the real vendored files.
object BundlePatches {
    // Placeholders the vendored sources carry, replaced with app paths at install.
    const val DEST_LIB = "\${DEST_LIB}"
    const val TEMP_PATH = "\${TEMP_PATH}"
    const val TTY_PATH = "\${TTY_PATH}"
    const val WEB_PORT_FILE = "\${WEB_PORT_FILE}"

    const val SYSFS_TTY_ORIGINAL = "TTY_PATH = \"/sys/class/tty\""

    fun sysfsTty(serialDir: String) = "TTY_PATH = \"$serialDir\""

    // moonraker_obico.janus_config_builder calls board_id() at MODULE import
    // time, and board_id() only guards the devicetree read with isfile(). On
    // Android that file exists but is denied by SELinux, so open() throws
    // PermissionError and crashes app.py's whole import chain before main().
    const val OBICO_BOARD_ID_ORIGINAL =
        "def board_id():\n    model_file = \"/sys/firmware/devicetree/base/model\"\n    if os.path.isfile(model_file):\n        with open(model_file, 'r') as file:\n            data = file.read()\n            if \"raspberry\" in data.lower():\n                return \"rpi\"\n            elif \"makerbase\" in data.lower() or \"roc-rk3328-cc\" in data:\n                return \"mks\"\n    return \"NA\""
    const val OBICO_BOARD_ID_PATCHED =
        "def board_id():\n    model_file = \"/sys/firmware/devicetree/base/model\"\n    try:\n        if os.path.isfile(model_file):\n            with open(model_file, 'r') as file:\n                data = file.read()\n                if \"raspberry\" in data.lower():\n                    return \"rpi\"\n                elif \"makerbase\" in data.lower() or \"roc-rk3328-cc\" in data:\n                    return \"mks\"\n    except OSError:\n        pass\n    return \"NA\""

    // printer_discovery only surfaces the one-time passcode through Klipper
    // gcode_macro variables; this also writes a plain JSON status file next
    // to moonraker-obico.cfg, which the app reads for Settings -> Link printer.
    const val OBICO_LINK_STATUS_ORIGINAL =
        "    def set_obico_link_status(self, is_linked, one_time_passcode, one_time_passlink):\n        self.moonrakerconn.set_macro_variables('OBICO_LINK_STATUS',"
    const val OBICO_LINK_STATUS_PATCHED =
        "    def set_obico_link_status(self, is_linked, one_time_passcode, one_time_passlink):\n        try:\n            import json as _json\n            _status_path = os.path.join(os.path.dirname(os.path.abspath(self.config._config_path)), 'obico_link_status.json')\n            with open(_status_path, 'w') as _f:\n                _json.dump({'is_linked': is_linked, 'one_time_passcode': one_time_passcode, 'one_time_passlink': one_time_passlink}, _f)\n        except Exception:\n            pass\n        self.moonrakerconn.set_macro_variables('OBICO_LINK_STATUS',"

    fun patchObicoBoardId(source: String): String = source.replace(OBICO_BOARD_ID_ORIGINAL, OBICO_BOARD_ID_PATCHED)

    fun patchObicoLinkStatus(source: String): String = source.replace(OBICO_LINK_STATUS_ORIGINAL, OBICO_LINK_STATUS_PATCHED)

    // find_all_thermal_presets() assumes Moonraker's mainsail-namespace
    // "presets" database entry is always a dict (upstream's Mainsail always
    // writes it that way once at least one preset exists). Beam's vendored
    // Moonraker returns it as an empty LIST when no preset has ever been
    // saved, and list has no .values() — this is an unhandled AttributeError
    // raised directly from app.py's start() (not inside the per-preset
    // try/except a few lines down), which crashes the whole obico process
    // before it ever opens its persistent connection to the Obico server. The
    // printer still "links" (that HTTP round trip already happened earlier in
    // start()), so Obico shows the printer as registered but it never goes
    // Online, and Android just restarts the crashed service forever.
    const val OBICO_THERMAL_PRESETS_ORIGINAL =
        "        for preset in data.get('value', {}).get('presets', {}).values():"
    const val OBICO_THERMAL_PRESETS_PATCHED =
        "        presets_raw = data.get('value', {}).get('presets', {})\n" +
        "        for preset in (presets_raw.values() if isinstance(presets_raw, dict) else presets_raw):"

    fun patchObicoThermalPresets(source: String): String = source.replace(OBICO_THERMAL_PRESETS_ORIGINAL, OBICO_THERMAL_PRESETS_PATCHED)

    // Upstream's _setup_include_cfgs() shells out to scripts/ensure_include_cfgs.sh
    // (see https://github.com/TheSpaghettiDetective/moonraker-obico/blob/master/scripts/ensure_include_cfgs.sh),
    // which we don't vendor as an executable: Android's asset unpack
    // (BundleFiles.unpack) writes plain files with no +x bit, and even with
    // it set, running an arbitrary on-device shell script from app-private
    // storage is exactly the kind of thing worth avoiding rather than
    // depending on. It's also always run in a background thread
    // (_setup_include_cfgs is only ever called via run_in_thread), so this
    // crash doesn't take the process down — it just silently means
    // printer.cfg never gets `[include moonraker_obico_macros.cfg]`, so
    // OBICO_LINK_STATUS/_OBICO_RELINK/the first-layer-scan macro are never
    // available and every _setup_include_cfgs invocation logs a full
    // FileNotFoundError traceback. Reimplemented the same two-step behavior
    // (symlink moonraker_obico_macros.cfg into the Klipper config dir, then
    // insert the include line into printer.cfg — before the first
    // "-- SAVE_CONFIG --" marker if present, else appended) directly in
    // Python instead of subprocess+shell.
    const val OBICO_INCLUDE_CFGS_ORIGINAL =
        "        ensure_include_cfgs_sh = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'scripts', 'ensure_include_cfgs.sh')\n" +
        "        FNULL = open(os.devnull, 'w')\n" +
        "        cmd = f'{ensure_include_cfgs_sh} {printer_cfg}'\n" +
        "        _logger.debug('Popen: {}'.format(cmd))\n" +
        "        proc = subprocess.Popen(cmd.split(' '), stdout=FNULL, stderr=FNULL)\n" +
        "        proc_exit_code = proc.wait()\n" +
        "        if proc_exit_code != 0:\n" +
        "            _logger.warning(f'{cmd} exited with {proc_exit_code}')"
    const val OBICO_INCLUDE_CFGS_PATCHED =
        "        macro_cfg = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'include_cfgs', 'moonraker_obico_macros.cfg')\n" +
        "        if not os.path.isfile(macro_cfg):\n" +
        "            _logger.warning('Aborted ensuring include_cfgs because {} is missing'.format(macro_cfg))\n" +
        "            return\n" +
        "\n" +
        "        klipper_conf_dir = os.path.dirname(printer_cfg)\n" +
        "        linked_macro_cfg = os.path.join(klipper_conf_dir, 'moonraker_obico_macros.cfg')\n" +
        "        if not os.path.isfile(linked_macro_cfg):\n" +
        "            try:\n" +
        "                if os.path.lexists(linked_macro_cfg):\n" +
        "                    os.remove(linked_macro_cfg)\n" +
        "                os.symlink(macro_cfg, linked_macro_cfg)\n" +
        "            except OSError as e:\n" +
        "                _logger.warning('Failed to symlink {}: {}'.format(linked_macro_cfg, e))\n" +
        "                return\n" +
        "\n" +
        "        try:\n" +
        "            with open(printer_cfg, 'r') as f:\n" +
        "                content = f.read()\n" +
        "        except OSError as e:\n" +
        "            _logger.warning('Failed to read {}: {}'.format(printer_cfg, e))\n" +
        "            return\n" +
        "\n" +
        "        if 'include moonraker_obico_macros.cfg' not in content:\n" +
        "            include_line = '[include moonraker_obico_macros.cfg]'\n" +
        "            marker = '-- SAVE_CONFIG --'\n" +
        "            if marker in content:\n" +
        "                lines = content.split('\\n')\n" +
        "                out = []\n" +
        "                inserted = False\n" +
        "                for line in lines:\n" +
        "                    if not inserted and marker in line:\n" +
        "                        out.append(include_line)\n" +
        "                        inserted = True\n" +
        "                    out.append(line)\n" +
        "                content = '\\n'.join(out)\n" +
        "            else:\n" +
        "                if not content.endswith('\\n'):\n" +
        "                    content += '\\n'\n" +
        "                content += include_line + '\\n'\n" +
        "            try:\n" +
        "                with open(printer_cfg, 'w') as f:\n" +
        "                    f.write(content)\n" +
        "            except OSError as e:\n" +
        "                _logger.warning('Failed to write {}: {}'.format(printer_cfg, e))"

    fun patchObicoIncludeCfgs(source: String): String = source.replace(OBICO_INCLUDE_CFGS_ORIGINAL, OBICO_INCLUDE_CFGS_PATCHED)
}

object BundleFiles {
    // Other processes may be importing these files right now, so never leave
    // one truncated or half-written: skip identical content, otherwise write a
    // temp file and rename it over the target (atomic on the same directory).
    fun writeIfChanged(target: File, bytes: ByteArray) {
        try {
            if (target.exists() && target.readBytes().contentEquals(bytes)) return
        } catch (_: Exception) {}
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes) }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            FileOutputStream(target).use { it.write(bytes) }
        }
    }

    // Replaces <root>/<key> with the files the bundle index lists for [key].
    // A key missing from the index leaves the tree removed (nothing to copy).
    fun unpack(open: (String) -> InputStream, files: List<String>?, root: File, key: String) {
        val dir = File(root, key)
        dir.deleteRecursively()
        if (files == null) return
        for (file in files) {
            val into = File(dir, file)
            into.parentFile?.mkdirs()
            open("$key/$file").use { inp ->
                FileOutputStream(into).use { fos -> inp.copyTo(fos) }
            }
        }
    }
}
