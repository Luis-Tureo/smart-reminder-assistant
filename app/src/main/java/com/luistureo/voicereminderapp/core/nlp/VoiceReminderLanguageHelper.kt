package com.luistureo.voicereminderapp.core.nlp

import java.text.Normalizer

object VoiceReminderLanguageHelper {

    private val recurringPhrases = listOf(
        "todos los dias",
        "todas las semanas",
        "todos los meses",
        "todos los anos",
        "cada dia",
        "cada semana",
        "cada mes",
        "cada ano",
        "cada lunes",
        "cada martes",
        "cada miercoles",
        "cada jueves",
        "cada viernes",
        "cada sabado",
        "cada domingo",
        "todos los lunes",
        "todos los martes",
        "todos los miercoles",
        "todos los jueves",
        "todos los viernes",
        "todos los sabados",
        "todos los domingos",
        "diario",
        "diariamente",
        "semanal",
        "semanalmente",
        "mensual",
        "mensualmente",
        "anual",
        "anualmente",
        "repite",
        "repetir",
        "repeticion"
    )

    private val urgentPhrases = listOf(
        "urgente",
        "urgentemente",
        "muy importante",
        "super importante",
        "prioridad alta",
        "alta prioridad",
        "de alta prioridad",
        "no se me puede olvidar",
        "que no se me olvide",
        "no me lo puedo olvidar",
        "no me puedo olvidar",
        "no puedo olvidarlo",
        "recordamelo si o si",
        "recuerdamelo si o si",
        "si o si",
        "importantisimo"
    )

    // Normaliza el texto del asistente para evaluar intencion y urgencia.
    fun normalize(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(",", " ")
            .replace(";", " ")
            .replace(".", " ")
            .replace(":", " : ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsRecurringRequest(text: String): Boolean {
        val normalizedText = normalize(text)
        return recurringPhrases.any { normalizedText.contains(it) }
    }

    fun containsUrgentSignal(text: String): Boolean {
        val normalizedText = normalize(text)
        return urgentPhrases.any { normalizedText.contains(it) }
    }

    // Limpia marcadores de urgencia del texto libre antes de guardarlo.
    fun stripUrgencyPhrases(text: String): String {
        var sanitizedText = normalize(text)

        urgentPhrases.forEach { phrase ->
            sanitizedText = sanitizedText.replace(phrase, " ")
        }

        return sanitizedText
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
