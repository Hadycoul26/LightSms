package com.example.lightsms

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/** Resultat d'une tentative d'allumage, avec le motif exact en cas d'echec. */
data class TorchResult(val ok: Boolean, val detail: String)

/**
 * Allume / eteint la lampe torche via CameraManager.
 * Aucune permission runtime necessaire depuis Android 6 pour setTorchMode().
 */
object TorchController {

    private const val TAG = "TorchController"

    fun setTorch(context: Context, on: Boolean): TorchResult {
        if (!hasFlash(context)) {
            return TorchResult(false, "aucun flash sur cet appareil")
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return TorchResult(false, "CameraManager indisponible")

        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return TorchResult(false, "aucune camera ne declare de flash")

            manager.setTorchMode(cameraId, on)
            Prefs.setTorchOn(context, on)
            TorchResult(true, if (on) "lampe allumee" else "lampe eteinte")
        } catch (e: Exception) {
            // CameraAccessException typiquement : camera occupee par une autre app.
            Log.e(TAG, "Changement d'etat de la lampe impossible", e)
            TorchResult(false, e.javaClass.simpleName + " : " + (e.message ?: "sans message"))
        }
    }

    fun hasFlash(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
}
