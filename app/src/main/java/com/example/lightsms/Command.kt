package com.example.lightsms

import java.text.Normalizer

/** Resultat d'une action, avec le motif exact en cas d'echec. */
data class ActionResult(val ok: Boolean, val detail: String)

/** Les commandes reconnues dans un SMS. */
enum class Command {
    LIGHT_ON, LIGHT_OFF,
    DATA_ON, DATA_OFF,
    HOTSPOT_ON, HOTSPOT_OFF
}

object CommandParser {

    /** Mots-cles acceptes pour chaque cible, avec la commande ON puis OFF. */
    private val TARGETS = listOf(
        Triple(listOf("light", "lampe", "torche"), Command.LIGHT_ON, Command.LIGHT_OFF),
        Triple(listOf("data", "donnees", "internet"), Command.DATA_ON, Command.DATA_OFF),
        Triple(listOf("hotspot", "point d acces", "partage"), Command.HOTSPOT_ON, Command.HOTSPOT_OFF)
    )

    /**
     * Retire les accents, passe en minuscules, puis remplace toute suite de
     * caracteres non alphanumeriques par un espace unique.
     *
     * Le repli des accents compte : sans lui "donnees" ecrit "données" donnerait
     * "donn es" une fois la ponctuation ecrasee, et ne correspondrait a rien.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    fun parse(raw: String?): Command? {
        val text = normalize(raw)
        if (text.isEmpty()) return null

        for ((keywords, on, off) in TARGETS) {
            for (keyword in keywords) {
                // "off" teste avant "on" : un mot-cle ne doit pas etre capte
                // par la mauvaise branche.
                if (text.contains("$keyword off")) return off
                if (text.contains("$keyword on")) return on
            }
        }
        return null
    }
}
