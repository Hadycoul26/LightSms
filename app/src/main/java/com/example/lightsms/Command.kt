package com.example.lightsms

/** Les commandes reconnues dans un SMS. */
enum class Command { ON, OFF }

object CommandParser {

    /**
     * Normalise le texte (minuscules, ponctuation -> espace) puis cherche la commande.
     * Accepte donc "Light ON!", "light-off", "LIGHT   On", etc.
     */
    fun parse(raw: String?): Command? {
        if (raw.isNullOrBlank()) return null
        val text = raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

        return when {
            text.contains("light off") -> Command.OFF
            text.contains("light on") -> Command.ON
            else -> null
        }
    }
}
