package ru.ytkab0bp.beamklipper.service

import java.io.File

// The port WebService currently listens on (4408 Fluidd, 4409 Mainsail, 4410
// Voyager UI — see Frontends) is a runtime setting, not a fixed value, so the
// Python beam_ext extras (PLAY_TONE, SET_CAMERA_FLASHLIGHT, SET_CAMERA_FOCUS)
// have no constant they could call. WebService keeps this file updated with
// the active port instead, and BundleInstaller patches those extras'
// WEB_PORT_FILE placeholder to this file's absolute path.
object WebPortFile {
    const val NAME = "web_port"

    fun path(filesDir: File): File = File(filesDir, NAME)

    // Best-effort: a failed write just leaves the extras on their fallback
    // port until the next successful one.
    fun write(filesDir: File, port: Int) {
        try {
            path(filesDir).writeText(port.toString())
        } catch (_: Throwable) {}
    }
}
