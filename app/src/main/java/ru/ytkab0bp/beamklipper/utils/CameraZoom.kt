package ru.ytkab0bp.beamklipper.utils

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build

// Zoom support differs per camera (a phone's tele lens, its ultra-wide and a
// USB webcam all report different limits), so the selectable steps are always
// derived from the characteristics of the camera that will actually be opened.
object CameraZoom {
    private val STEPS = floatArrayOf(1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)

    fun maxZoom(chars: CameraCharacteristics): Float {
        val max = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper
        } else null
        return (max ?: chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f)
    }

    fun options(max: Float): List<Float> {
        // Tolerance so a camera reporting e.g. 3.9999 still offers 4x.
        val steps = STEPS.filter { it <= max + 0.01f }
        return if (steps.size > 1) steps else listOf(1f)
    }

    // Snaps a saved value to the closest step the current camera offers.
    fun clamp(zoom: Float, options: List<Float>): Float =
        options.lastOrNull { it <= zoom + 0.01f } ?: options.first()

    fun label(zoom: Float): String =
        if (zoom % 1f == 0f) "${zoom.toInt()}×" else "${zoom}×"

    fun apply(builder: CaptureRequest.Builder, chars: CameraCharacteristics, requested: Float) {
        val zoom = clamp(requested, options(maxZoom(chars)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null
        ) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
            return
        }
        if (zoom <= 1f) return
        val active: Rect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val w = (active.width() / zoom).toInt()
        val h = (active.height() / zoom).toInt()
        val left = active.left + (active.width() - w) / 2
        val top = active.top + (active.height() - h) / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + w, top + h))
    }
}
