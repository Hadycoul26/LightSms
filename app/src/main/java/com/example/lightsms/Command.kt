package com.example.lightsms

/** Les commandes reconnues dans un SMS. */
enum class Command { ON, OFF }

object CommandParser {

    /**
     * Minuscules, puis toute suite de caracteres non alphanumeriques devient un
     * espace unique. "Light ON!", "light-off", "LIGHT   On" convergent ainsi
     * vers "light on" / "light off".
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    fun parse(raw: String?): Command? {
        val text = normalize(raw)
        return when {
            text.contains("light off") -> Command.OFF
            text.contains("light on") -> Command.ON
            else -> null
        }
    }
}
