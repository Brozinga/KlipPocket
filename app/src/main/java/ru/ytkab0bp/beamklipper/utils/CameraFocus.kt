package ru.ytkab0bp.beamklipper.utils

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle

// Tap-to-focus is optional hardware: many UVC webcams (and LEGACY-level phone
// cameras) report no AF regions or no triggerable AF mode, so every entry
// point checks the characteristics of the camera that will really be opened.
// Everything used here exists since API 21, so no version guards are needed.
object CameraFocus {
    fun isSupported(chars: CameraCharacteristics): Boolean {
        val regions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
        val modes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: return false
        return regions > 0 && modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) &&
            chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) != null
    }

    // Same choice CameraService makes: a pinned id, else a USB webcam if one is
    // plugged in, else the first camera.
    fun isSupportedForSelectedCamera(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = manager.cameraIdList
        val pinned = Prefs.cameraId
        val id = CameraRules.resolveId(ids.toList(), pinned) {
            try {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_EXTERNAL
            } catch (_: Throwable) { false }
        }
        id != null && isSupported(manager.getCameraCharacteristics(id))
    } catch (_: Throwable) {
        false
    }

    // (nx, ny) is a point in 0..1 over the frame as served (already rotated
    // clockwise by [rotation] and zoomed); returns the AF region in sensor
    // active-array coordinates.
    fun region(chars: CameraCharacteristics, zoom: Float, rotation: Int, nx: Float, ny: Float): MeteringRectangle? {
        val active: Rect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        val box = FocusRegion.compute(active.left, active.top, active.right, active.bottom, zoom, rotation, nx, ny)
        return MeteringRectangle(box.left, box.top, box.side, box.side, MeteringRectangle.METERING_WEIGHT_MAX)
    }
}
