package com.example.lightsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Relance le service d'ecoute apres un redemarrage (ou une mise a jour de l'app). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Recu $action")

        if (action !in HANDLED_ACTIONS) return
        if (!Prefs.isEnabled(context)) return

        LightService.start(context)
    }

    private companion object {
        const val TAG = "BootReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
