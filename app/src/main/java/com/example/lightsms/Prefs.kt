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

    // --- Serveur web ------------------------------------------------------

    /** true = le serveur HTTP local est actif. */
    fun isWebEnabled(context: Context) = sp(context).getBoolean(KEY_WEB_ENABLED, false)

    fun setWebEnabled(context: Context, value: Boolean) =
        sp(context).edit().putBoolean(KEY_WEB_ENABLED, value).apply()

    fun webPort(context: Context) = sp(context).getInt(KEY_WEB_PORT, DEFAULT_WEB_PORT)

    /**
     * Cle d'acces au serveur, generee au premier usage.
     *
     * Toute personne connectee au point d'acces peut joindre le serveur : sans
     * cle, elle pourrait couper la connexion du telephone. Ce n'est pas de la
     * cryptographie, juste de quoi empecher un acces par accident ou par
     * curiosite sur un reseau qu'on ne maitrise pas entierement.
     */
    fun webToken(context: Context): String {
        sp(context).getString(KEY_WEB_TOKEN, null)?.let { return it }

        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        val token = (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")

        sp(context).edit().putString(KEY_WEB_TOKEN, token).apply()
        return token
    }

    fun regenerateWebToken(context: Context): String {
        sp(context).edit().remove(KEY_WEB_TOKEN).apply()
        return webToken(context)
    }

    fun isTokenValid(context: Context, candidate: String?): Boolean =
        candidate != null && candidate.equals(webToken(context), ignoreCase = true)

    private const val KEY_WEB_ENABLED = "web_enabled"
    private const val KEY_WEB_TOKEN = "web_token"
    private const val KEY_WEB_PORT = "web_port"
    const val DEFAULT_WEB_PORT = 8080
}
