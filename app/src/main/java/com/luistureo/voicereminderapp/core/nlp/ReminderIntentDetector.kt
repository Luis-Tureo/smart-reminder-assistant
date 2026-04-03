package com.luistureo.voicereminderapp.core.nlp

class ReminderIntentDetector {

    private val reminderKeywords = listOf(
        "recuerdame",
        "recordarme",
        "recuérdame",
        "recordar",
        "anota",
        "agenda"
    )

    fun isReminderIntent(text: String): Boolean {
        val normalized = normalize(text)

        if (reminderKeywords.any { normalize(it) in normalized }) return true

        val hasTimeSignal = Regex("\\b(a las|alas|mañana|pasado mañana|hoy|lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo)\\b")
            .containsMatchIn(normalized)

        return hasTimeSignal
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .trim()
    }
}