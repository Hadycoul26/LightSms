package com.example.lightsms

import android.content.Context

/**
 * Applique une commande. Point d'entree unique, appele par [LightService] pour
 * que tout passe par un processus qui reste vivant.
 */
object CommandExecutor {

    /**
     * `cmd wifi start-softap` cree une configuration de session : ce n'est pas
     * le point d'acces configure dans les Reglages, et le changement n'y sera
     * pas visible. Le SSID et le mot de passe sont donc ceux-ci.
     */
    const val HOTSPOT_SSID = "LightSms"
    const val HOTSPOT_PASSWORD = "lightsms1234"

    fun execute(context: Context, command: Command): ActionResult = when (command) {
        Command.LIGHT_ON -> TorchController.setTorch(context, true)
        Command.LIGHT_OFF -> TorchController.setTorch(context, false)

        Command.DATA_ON -> shell("svc data enable", "donnees mobiles activees")
        Command.DATA_OFF -> shell("svc data disable", "donnees mobiles desactivees")

        Command.HOTSPOT_ON -> startHotspot()
        Command.HOTSPOT_OFF -> shell("cmd wifi stop-softap", "point d'acces arrete")
    }

    private fun shell(command: String, successLabel: String): ActionResult {
        val result = ShizukuShell.run(command)
        return if (result.ok) ActionResult(true, successLabel) else result
    }

    /**
     * La syntaxe varie selon la version d'Android, et Android 16 a durci les
     * permissions de tethering. On essaie les variantes connues dans l'ordre et
     * on rapporte ce que chacune a repondu : c'est l'appareil qui tranche.
     */
    private fun startHotspot(): ActionResult {
        val candidates = listOf(
            "cmd wifi start-softap $HOTSPOT_SSID wpa2 $HOTSPOT_PASSWORD",
            "cmd wifi start-softap $HOTSPOT_SSID open",
            "cmd -w wifi start-softap $HOTSPOT_SSID wpa2 $HOTSPOT_PASSWORD"
        )

        val failures = mutableListOf<String>()

        for (candidate in candidates) {
            val result = ShizukuShell.run(candidate)
            if (result.ok) {
                return ActionResult(true, "point d'acces demarre ($HOTSPOT_SSID)")
            }
            // Si Shizuku lui-meme manque, insister avec d'autres syntaxes ne sert a rien.
            if (result.detail.contains("Shizuku", ignoreCase = true)) return result
            failures += result.detail
        }

        return ActionResult(
            false,
            "aucune syntaxe acceptee : " + failures.joinToString(" | ").take(200)
        )
    }
}
