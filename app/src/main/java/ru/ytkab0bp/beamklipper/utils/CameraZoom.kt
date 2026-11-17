package ru.ytkab0bp.beamklipper.utils

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import java.math.BigDecimal
import java.math.RoundingMode

// Zoom support differs per camera (a phone's tele lens, its ultra-wide and a
// USB webcam all report different limits), so the selectable steps are always
// derived from the characteristics of the camera that will actually be opened.
object CameraZoom {
    private val STEPS = floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)

    fun maxZoom(chars: CameraCharacteristics): Float {
        val max = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper
        } else null
        return (max ?: chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f)
    }

    fun options(chars: CameraCharacteristics): List<Float> {
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.lower ?: 1f else 1f
        return options(maxZoom(chars), min)
    }

    fun options(max: Float, min: Float = 1f): List<Float> {
        // Never offer ratios outside the range reported by the camera.
        val steps = STEPS.filter { it >= min && it <= max }
        return (if (min < 1f) listOf(min) + steps.filter { label(it) != label(min) } else steps).ifEmpty { listOf(1f) }
    }

    // Snaps a saved value to the closest step the current camera offers.
    fun clamp(zoom: Float, options: List<Float>): Float =
        options.lastOrNull { it <= zoom } ?: options.first()

    fun label(zoom: Float): String =
        BigDecimal(zoom.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "×"

    fun apply(builder: CaptureRequest.Builder, chars: CameraCharacteristics, requested: Float) {
        val zoom = clamp(requested, options(chars))
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
