package com.example.lightsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Recoit les SMS entrants et transmet la commande a [LightService].
 *
 * Ce recepteur ne pilote PAS la lampe lui-meme : Android tue le processus
 * quasi immediatement apres onReceive quand il a ete reveille a froid, et le
 * systeme eteint la torche avec le processus qui l'avait allumee. Le service
 * de premier plan, lui, survit.
 *
 * Chaque SMS vu est journalise dans [EventLog], commande reconnue ou non.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            EventLog.add(context, "?", "", "", "-", "aucun message extrait de l'intent")
            return
        }

        // Un SMS long arrive en plusieurs morceaux : on recolle le tout.
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val sender = messages.first().displayOriginatingAddress ?: "inconnu"
        val normalized = CommandParser.normalize(body)
        val command = CommandParser.parse(body)

        Log.i(TAG, "SMS de $sender -> normalise=[$normalized] commande=$command")

        if (!Prefs.isEnabled(context)) {
            EventLog.add(
                context, sender, body, normalized,
                command?.name ?: "aucune",
                "ignore : l'ecoute est desactivee dans l'app"
            )
            return
        }

        if (command == null) {
            EventLog.add(
                context, sender, body, normalized, "aucune",
                "aucune commande reconnue (attendu : light on / light off)"
            )
            return
        }

        // Entree posee maintenant ; le service completera le resultat reel.
        EventLog.add(context, sender, body, normalized, command.name, "transmis au service...")

        // goAsync() empeche Android de tuer le processus avant que le service
        // ait eu le temps de demarrer et de prendre la main sur la lampe.
        val pending = goAsync()
        try {
            if (!LightService.applyCommand(context, command)) {
                // Repli : le systeme a refuse le service de premier plan. On agit
                // directement, en sachant qu'un allumage risque de ne pas tenir.
                val result = CommandExecutor.execute(context, command)
                EventLog.updateLastResult(
                    context,
                    if (result.ok) "repli direct - " + result.detail
                    else "ECHEC (repli) - " + result.detail
                )
            }
        } catch (e: Exception) {
            EventLog.updateLastResult(context, "ECHEC - " + e.javaClass.simpleName)
        } finally {
            pending.finish()
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
