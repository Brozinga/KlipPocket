package ru.ytkab0bp.beamklipper.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

// The flash is optional hardware: front cameras and USB webcams have none, so
// every entry point checks the camera that will really be opened.
object CameraFlash {
    fun isSupported(chars: CameraCharacteristics): Boolean =
        chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    fun isSupportedForSelectedCamera(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = CameraRules.resolveId(manager.cameraIdList.toList(), Prefs.cameraId) {
            try {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_EXTERNAL
            } catch (_: Throwable) { false }
        }
        id != null && isSupported(manager.getCameraCharacteristics(id))
    } catch (_: Throwable) {
        false
    }
}
