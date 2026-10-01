package ru.ytkab0bp.beamklipper.service

import org.json.JSONObject

// moonraker-obico.cfg written before every start of the Obico companion.
object ObicoConfig {
    fun render(serverUrl: String, authToken: String?, moonrakerPort: Int, logPath: String): String =
        buildString {
            append("[server]\n")
            append("url = ").append(serverUrl).append('\n')
            // Left unset when not yet linked: moonraker_obico.app reacts to
            // that itself by running its own linking flow.
            if (!authToken.isNullOrBlank()) {
                append("auth_token = ").append(authToken).append('\n')
            }
            append('\n')
            append("[moonraker]\n")
            append("host = 127.0.0.1\n")
            append("port = ").append(moonrakerPort).append('\n')
            append('\n')
            // The real-time WebRTC preview needs a native janus-gateway and
            // ffmpeg that are not bundled; periodic JPEG snapshots still work.
            append("[webcam]\n")
            append("disable_video_streaming = True\n")
            append('\n')
            append("[logging]\n")
            append("path = ").append(logPath).append('\n')
            append("level = INFO\n")
            append('\n')
            // Opt out of crash telemetry; the server connection is unaffected.
            append("[misc]\n")
            append("sentry_opt = out\n")
        }
}

// The JSON config the OctoEverywhere companion is started with.
object OctoEverywhereConfig {
    fun build(
        instanceId: String?,
        bundleDir: String,
        localStorage: String,
        configFolder: String,
        logFolder: String,
        moonrakerConfig: String
    ): JSONObject = JSONObject().apply {
        put("ServiceName", "klippocket-$instanceId")
        put("VirtualEnvPath", bundleDir)
        put("RepoRootFolder", bundleDir)
        put("LocalFileStoragePath", localStorage)
        put("ConfigFolder", configFolder)
        put("LogFolder", logFolder)
        put("IsCompanion", false)
        put("IsDockerContainer", false)
        put("MoonrakerConfigFile", moonrakerConfig)
        // moonraker.conf is templated by the app; never let the plugin's own
        // git/systemd-oriented logic touch it.
        put("DisableMoonrakerConfigFileWrites", true)
    }
}
