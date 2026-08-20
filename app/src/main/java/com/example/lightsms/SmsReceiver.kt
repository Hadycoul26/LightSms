package com.example.lightsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Recoit les SMS entrants et applique la commande trouvee dans le texte.
 * Declare dans le manifest : Android le reveille meme si l'app est fermee.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.isEnabled(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Un SMS long arrive en plusieurs morceaux : on recolle le tout.
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val sender = messages.first().displayOriginatingAddress ?: "inconnu"

        val command = CommandParser.parse(body) ?: return
        Log.i(TAG, "Commande $command recue de $sender")

        val ok = TorchController.setTorch(context, command == Command.ON)

        val label = if (command == Command.ON) "allumee" else "eteinte"
        Prefs.setLastEvent(
            context,
            if (ok) "Lampe $label (SMS de $sender)"
            else "Echec : lampe indisponible (SMS de $sender)"
        )

        // Rafraichit la notification si le service tourne.
        LightService.refresh(context)
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
