package ru.ytkab0bp.beamklipper.utils

import kotlin.math.sqrt

// Camera choices and math, free of Camera2 types so they can be unit-tested.
object CameraRules {
    // The camera the server really opens: a pinned id if it still exists;
    // with none pinned, a USB (external) webcam if one is plugged in, else the
    // first camera. A pinned id that vanished falls back to the first one.
    fun resolveId(ids: List<String>, pinned: String?, isExternal: (String) -> Boolean): String? {
        if (ids.isEmpty()) return null
        if (pinned != null && ids.contains(pinned)) return pinned
        if (pinned == null) {
            ids.firstOrNull { isExternal(it) }?.let { return it }
        }
        return ids[0]
    }

    // Next zoom step in the list the current camera offers (wraps around).
    fun nextZoom(current: Float, options: List<Float>): Float {
        if (options.size <= 1) return current
        val snapped = CameraZoom.clamp(current, options)
        return options[(options.indexOf(snapped) + 1) % options.size]
    }

    // 43.27mm is the diagonal of a 36x24mm full-frame sensor: the standard
    // "35mm equivalent" reference. Returns null without usable sensor data.
    fun equivalentFocalLength35mm(focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Int? {
        val diagonal = sqrt(sensorWidthMm * sensorWidthMm + sensorHeightMm * sensorHeightMm)
        if (diagonal <= 0f) return null
        return Math.round(focalMm * (43.27f / diagonal))
    }
}

// Where a tap on the live preview lands on the sensor, for tap-to-focus.
object FocusRegion {
    // Side of the metering square, as a fraction of the active array's short side.
    const val REGION_FRACTION = 0.12f

    data class Box(val left: Int, val top: Int, val side: Int)

    // (nx, ny) is a point in 0..1 over the frame as served (already rotated
    // clockwise by [rotation] and zoomed); the result is in sensor
    // active-array coordinates.
    fun compute(
        activeLeft: Int, activeTop: Int, activeRight: Int, activeBottom: Int,
        zoom: Float, rotation: Int, nx: Float, ny: Float
    ): Box {
        val width = activeRight - activeLeft
        val height = activeBottom - activeTop
        // Undo the clockwise rotation applied to the served frame.
        val (sx, sy) = when (rotation) {
            90 -> ny to 1f - nx
            180 -> 1f - nx to 1f - ny
            270 -> 1f - ny to nx
            else -> nx to ny
        }
        // The visible area is the centred crop that zoom leaves.
        val z = zoom.coerceAtLeast(1f)
        val viewW = width / z
        val viewH = height / z
        val cx = activeLeft + (width - viewW) / 2f + sx.coerceIn(0f, 1f) * viewW
        val cy = activeTop + (height - viewH) / 2f + sy.coerceIn(0f, 1f) * viewH
        val side = (minOf(width, height) * REGION_FRACTION).toInt().coerceAtLeast(8)
        val left = (cx - side / 2f).toInt().coerceIn(activeLeft, activeRight - side)
        val top = (cy - side / 2f).toInt().coerceIn(activeTop, activeBottom - side)
        return Box(left, top, side)
    }
}

// Shrinks JPEG frames when viewers' connections cannot keep up and restores
// quality when they recover. Fed the time each frame write took.
//
// A per-call streak does not work on real hardware: the OS socket send buffer
// absorbs a burst almost instantly even while the client is starved, so write
// times come back bimodal (near 0ms, then one very long one). An EWMA over
// all samples still converges to the true sustained rate.
class JpegQualityController(
    private val base: Int = 75,
    private val min: Int = 35,
    // ~25fps-equivalent slack over the 33ms a true 30fps frame allows.
    private val frameBudgetMs: Long = 40L
) {
    var quality: Int = base
        private set

    private var ewmaMs = frameBudgetMs.toDouble()
    private var framesSinceAdjust = 0

    // A fresh camera session starts at the base quality again.
    @Synchronized
    fun reset() {
        quality = base
        ewmaMs = frameBudgetMs.toDouble()
        framesSinceAdjust = 0
    }

    // Returns the quality to use for the next frame.
    @Synchronized
    fun onWriteTiming(elapsedMs: Long): Int {
        ewmaMs = ewmaMs * 0.8 + elapsedMs * 0.2
        framesSinceAdjust++
        // Let a handful of samples fold into the average before acting on
        // it, so one adjustment does not immediately chase the next.
        if (framesSinceAdjust < 5) return quality
        if (ewmaMs > frameBudgetMs * 1.5 && quality > min) {
            quality = (quality - 10).coerceAtLeast(min)
            framesSinceAdjust = 0
        } else if (ewmaMs < frameBudgetMs * 0.5 && quality < base) {
            quality = (quality + 5).coerceAtMost(base)
            framesSinceAdjust = 0
        }
        return quality
    }
}

// The HTTP the camera server speaks: an MJPEG stream and single snapshots.
object CameraHttp {
    private val PATH = Regex("GET ([^\\r\\n]+) HTTP/1\\.[0-1]")
    private const val CORS = "Access-Control-Allow-Origin: *\r\n"

    // Fluidd/Mainsail's webcam viewer runs on another origin (their own
    // port), so CORS headers are required.
    const val STREAM_HEADERS = "HTTP/1.0 200 OK\r\nConnection: close\r\nMax-Age: 0\r\nExpires: 0\r\nCache-Control: no-cache, private\r\nPragma: no-cache\r\n" +
        CORS + "Content-Type: multipart/x-mixed-replace; boundary=camera-frame\r\n\r\n"

    // A snapshot is a single raw JPEG, so its Content-Type must not claim
    // multipart: Fluidd validates the snapshot URL with it.
    fun snapshotHeaders(size: Int): String =
        "HTTP/1.0 200 OK\r\nConnection: close\r\nCache-Control: no-cache, private\r\nPragma: no-cache\r\n" +
            CORS + "Content-Type: image/jpeg\r\nContent-Length: $size\r\n\r\n"

    fun framePartHeader(size: Int): String =
        "--camera-frame\r\nContent-Type: image/jpeg\r\nContent-Length: $size\r\n\r\n"

    // "GET /snapshot ..." (with or without a query) asks for one frame;
    // anything else, including a missing request line, gets the stream.
    fun isSnapshotRequest(requestLine: String?): Boolean {
        if (requestLine == null) return false
        val m = PATH.find(requestLine) ?: return false
        return m.groupValues[1].startsWith("/snapshot")
    }
}
