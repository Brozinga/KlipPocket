package ru.ytkab0bp.beamklipper.utils

import java.net.HttpURLConnection
import java.net.URL

// The camera server lives in its own process (":camera") and keeps the flash
// state there, so the app and the Dashboard ask it over the loopback address
// the way Moonraker does, instead of trusting their own copy of the settings.
// Blocking: call it off the main thread.
object CameraTorchClient {
    private const val BASE = "http://127.0.0.1:8889/torch/"

    // [action] is "status", "on" or "off". The state the flash is in, or null
    // when the camera server does not answer (it is off) or refuses.
    fun request(action: String): Boolean? = try {
        val c = URL(BASE + action).openConnection() as HttpURLConnection
        c.connectTimeout = 1500
        c.readTimeout = 1500
        try {
            if (c.responseCode == 200) CameraHttp.parseTorchState(c.inputStream.bufferedReader().readText()) else null
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}
