package com.example.lightsms

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Allume / eteint la lampe torche via CameraManager.
 * Aucune permission runtime necessaire depuis Android 6 pour setTorchMode().
 */
object TorchController {

    private const val TAG = "TorchController"

    /** @return true si la commande est passee. */
    fun setTorch(context: Context, on: Boolean): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return false

        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId == null) {
                Log.w(TAG, "Aucune camera avec flash sur cet appareil")
                return false
            }

            manager.setTorchMode(cameraId, on)
            Prefs.setTorchOn(context, on)
            true
        } catch (e: Exception) {
            // Typiquement CameraAccessException si la camera est occupee par une autre app.
            Log.e(TAG, "Impossible de changer l'etat de la lampe", e)
            false
        }
    }

    fun hasFlash(context: Context): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FLASH)
}
