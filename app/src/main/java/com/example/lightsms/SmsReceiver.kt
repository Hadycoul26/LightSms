package com.example.lightsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Recoit les SMS entrants et applique la commande trouvee dans le texte.
 * Declare dans le manifest : Android le reveille meme si l'app est fermee.
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

        Log.i(TAG, "SMS de $sender -> normalise=\"$normalized\" commande=$command")

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
                "aucune commande reconnue (attendu : \"light on\" ou \"light off\")"
            )
            return
        }

        val result = TorchController.setTorch(context, command == Command.ON)

        EventLog.add(
            context, sender, body, normalized, command.name,
            if (result.ok) "OK - " + result.detail else "ECHEC - " + result.detail
        )

        Prefs.setLastEvent(
            context,
            if (result.ok) result.detail.replaceFirstChar { it.uppercase() } + " (SMS de $sender)"
            else "Echec : " + result.detail + " (SMS de $sender)"
        )

        LightService.refresh(context)
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
