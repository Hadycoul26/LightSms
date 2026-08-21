package com.example.lightsms

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal circulaire des derniers SMS vus par [SmsReceiver].
 *
 * On enregistre TOUS les SMS recus, y compris ceux qui ne contiennent aucune
 * commande : un journal vide apres un envoi prouve que le broadcast n'est
 * jamais arrive jusqu'a l'app, ce qu'un journal filtre ne permettrait pas de
 * distinguer d'une commande non reconnue.
 */
object EventLog {

    private const val FILE = "light_sms_log"
    private const val KEY = "events"
    private const val MAX = 5
    private const val TAG = "EventLog"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun add(
        context: Context,
        sender: String,
        body: String,
        normalized: String,
        command: String,
        result: String
    ) {
        try {
            val entry = JSONObject()
                .put("t", System.currentTimeMillis())
                .put("from", sender)
                .put("body", body)
                .put("norm", normalized)
                .put("cmd", command)
                .put("res", result)

            val previous = read(context)
            val out = JSONArray().put(entry)
            for (i in 0 until minOf(previous.length(), MAX - 1)) {
                out.put(previous.getJSONObject(i))
            }
            sp(context).edit().putString(KEY, out.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Ecriture du journal impossible", e)
        }
    }

    private fun read(context: Context): JSONArray = try {
        JSONArray(sp(context).getString(KEY, "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    fun count(context: Context): Int = read(context).length()

    fun clear(context: Context) = sp(context).edit().remove(KEY).apply()

    /** Rendu lisible, du plus recent au plus ancien. */
    fun format(context: Context): String {
        val events = read(context)
        if (events.length() == 0) return ""

        val clock = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()

        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            if (i > 0) sb.append('\n')
            sb.append(clock.format(Date(e.optLong("t"))))
                .append("  de ").append(e.optString("from")).append('\n')
                .append("   recu      : ").append(quote(e.optString("body"))).append('\n')
                .append("   normalise : ").append(quote(e.optString("norm"))).append('\n')
                .append("   commande  : ").append(e.optString("cmd")).append('\n')
                .append("   resultat  : ").append(e.optString("res")).append('\n')
        }
        return sb.toString()
    }

    private fun quote(s: String) = "\"" + s + "\""
}
