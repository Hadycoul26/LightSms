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
 *
 * ATTENTION : la doc Android precise que "if the latest application that turned
 * on the torch mode exits, the torch mode will be turned off". Appeler ceci
 * depuis un BroadcastReceiver reveille a froid ne sert donc a rien pour un
 * allumage : le processus meurt des la fin de onReceive et la lampe avec lui.
 * L'appelant doit etre [LightService], dont le processus reste vivant.
 */
object TorchController {

    private const val TAG = "TorchController"
    private const val RETRY_DELAY_MS = 250L

    /**
     * @param attempts la camera peut etre temporairement occupee par une autre
     *   app ; on reessaie plutot que d'abandonner au premier refus.
     */
    fun setTorch(context: Context, on: Boolean, attempts: Int = 3): TorchResult {
        if (!hasFlash(context)) {
            return TorchResult(false, "aucun flash sur cet appareil")
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return TorchResult(false, "CameraManager indisponible")

        val cameraId = try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            return TorchResult(false, "liste des cameras illisible : " + describe(e))
        } ?: return TorchResult(false, "aucune camera ne declare de flash")

        var lastError = "erreur inconnue"

        for (attempt in 1..attempts) {
            try {
                manager.setTorchMode(cameraId, on)
                Prefs.setTorchOn(context, on)
                return TorchResult(true, if (on) "lampe allumee" else "lampe eteinte")
            } catch (e: Exception) {
                lastError = describe(e)
                Log.w(TAG, "Tentative $attempt/$attempts echouee : $lastError")
                if (attempt < attempts) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return TorchResult(false, lastError + " (interrompu)")
                    }
                }
            }
        }

        return TorchResult(false, lastError + " (apres $attempts tentatives)")
    }

    private fun describe(e: Exception) =
        e.javaClass.simpleName + " : " + (e.message ?: "sans message")

    fun hasFlash(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
}
