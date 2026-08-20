package com.example.lightsms

import android.content.Context

/** Petit stockage local : etat d'activation, etat de la lampe, dernier evenement. */
object Prefs {

    private const val FILE = "light_sms_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TORCH_ON = "torch_on"
    private const val KEY_LAST_EVENT = "last_event"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** true = l'app reagit aux SMS. */
    fun isEnabled(context: Context) = sp(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun isTorchOn(context: Context) = sp(context).getBoolean(KEY_TORCH_ON, false)

    fun setTorchOn(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_TORCH_ON, value).apply()

    fun lastEvent(context: Context): String? = sp(context).getString(KEY_LAST_EVENT, null)

    fun setLastEvent(context: Context, value: String) =
        sp(context).edit().putString(KEY_LAST_EVENT, value).apply()
}
