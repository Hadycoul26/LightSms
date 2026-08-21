package com.example.lightsms

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

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
    fun setTorch(context: Context, on: Boolean, attempts: Int = 3): ActionResult {
        if (!hasFlash(context)) {
            return ActionResult(false, "aucun flash sur cet appareil")
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ActionResult(false, "CameraManager indisponible")

        val cameraId = try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            return ActionResult(false, "liste des cameras illisible : " + describe(e))
        } ?: return ActionResult(false, "aucune camera ne declare de flash")

        var lastError = "erreur inconnue"

        for (attempt in 1..attempts) {
            try {
                manager.setTorchMode(cameraId, on)
                Prefs.setTorchOn(context, on)
                return ActionResult(true, if (on) "lampe allumee" else "lampe eteinte")
            } catch (e: Exception) {
                lastError = describe(e)
                Log.w(TAG, "Tentative $attempt/$attempts echouee : $lastError")
                if (attempt < attempts) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return ActionResult(false, lastError + " (interrompu)")
                    }
                }
            }
        }

        return ActionResult(false, lastError + " (apres $attempts tentatives)")
    }

    private fun describe(e: Exception) =
        e.javaClass.simpleName + " : " + (e.message ?: "sans message")

    fun hasFlash(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
}
