package ru.ytkab0bp.beamklipper.firmwareweb

import org.json.JSONObject
import java.io.InputStream

/** A running camera stream of the app's camera service: [input] is the multipart/x-mixed-replace body. */
class CameraFeed(val contentType: String, val input: InputStream)

/** What the dashboard's Settings, Camera and start/stop controls need from the app; the web server only talks to this. */
interface ControlBackend {
    /** Every setting of the app with its current value and the options the app offers. */
    fun settings(): JSONObject

    /**
     * Changes one setting like the app's own Settings screen does. Returns null when done, else a reason key:
     * "key", "value", "permission" (Android asks for it on the phone), "unavailable", "link" (a link code the server refused),
     * "network".
     */
    fun applySetting(key: String, body: JSONObject): String?

    /** Starts, stops or toggles the printer [id] (null = every printer, like the app's big button). Returns null when done, else "id", "action" or "slots". */
    fun control(id: String?, action: String): String?

    /** What the "new printer" form needs: the starter configs to choose from, the default one, the default name and whether a slot is free. */
    fun newPrinterInfo(): JSONObject

    /** Creates a printer profile like the app's "new profile" sheet. Returns null when done, else "slots", "config" or "server". */
    fun addPrinter(name: String, config: String, autostart: Boolean): String?

    /** Camera state for the camera tab. */
    fun camera(): JSONObject

    /** Opens the camera stream, or null when the camera is off or not running. */
    fun openCameraStream(): CameraFeed?

    /** Tap-to-focus at a point of the frame, 0..1 each way. */
    fun focusAt(x: Float, y: Float)

    fun resetFocus()
}
