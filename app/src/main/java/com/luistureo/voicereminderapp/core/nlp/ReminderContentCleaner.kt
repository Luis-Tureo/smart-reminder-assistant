package com.luistureo.voicereminderapp.core.nlp

import java.text.Normalizer
import java.util.Locale

object ReminderContentCleaner {

    fun cleanDetail(input: String?): String? {
        var cleaned = normalize(input.orEmpty())
        if (cleaned.isBlank()) return null

        listOf(
            "recuerdame",
            "recordame",
            "recordar",
            "agenda",
            "agendar",
            "por favor",
            "que tengo que",
            "tengo que"
        ).forEach { phrase ->
            cleaned = cleaned.replace(Regex("\\b${Regex.escape(phrase)}\\b"), " ")
        }

        cleaned = cleaned
            .replace(Regex("\\ben\\s+\\d{1,3}\\s+minutos?\\b"), " ")
            .replace(Regex("\\ben\\s+\\d{1,2}\\s+horas?\\b"), " ")
            .replace(Regex("\\bpasado\\s+manana\\b"), " ")
            .replace(Regex("\\bmanana\\b"), " ")
            .replace(Regex("\\bhoy\\b"), " ")
            .replace(Regex("\\b(?:proximo\\s+)?(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b"), " ")
            .replace(Regex("\\b(?:el\\s+dia|dia|el)?\\s*\\d{1,2}\\s+de\\s+[a-z]+(?:\\s+de\\s+\\d{2,4})?\\b"), " ")
            .replace(Regex("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b"), " ")
            .replace(Regex("\\b(?:el\\s+dia|dia|el)\\s+\\d{1,2}\\b"), " ")
            .replace(Regex("\\b(?:a\\s+las|a\\s+la|las|la)\\s+\\d{1,2}(?::\\d{1,2})?\\s*(?:am|pm)?\\b"), " ")
            .replace(Regex("\\b(?:a\\s+las|a\\s+la|las|la)\\s+(?:una|uno|un|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintidos|veintitres)(?:\\s+y\\s+(?:media|cuarto|\\d{1,2}))?(?:\\s+de\\s+la\\s+(?:manana|tarde|noche|madrugada))?\\b"), " ")
            .replace(Regex("\\b\\d{1,2}:\\d{1,2}\\b"), " ")
            .replace(Regex("\\b\\d{1,2}\\s*(?:am|pm|hrs|hr|h)\\b"), " ")
            .replace(Regex("\\bde\\s+la\\s+(?:manana|tarde|noche|madrugada)\\b"), " ")
            .replace(Regex("\\b(?:mediodia|medianoche)\\b"), " ")
            .replace(Regex("\\btengo\\s+(?:un|una)?\\s*"), " ")
            .replace(Regex("\\b(?:para|el|la|los|las)\\s*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '.', ',')

        return cleaned.takeIf { it.isNotBlank() }
    }

    fun buildTitle(detail: String?): String? {
        val cleaned = cleanDetail(detail) ?: return null
        val base = cleaned
            .substringBefore(".")
            .substringBefore(",")
            .take(48)
            .trim()
            .takeIf { it.isNotBlank() }
            ?: return null

        return base.replaceFirstChar { first ->
            if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
        }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(",", " ")
            .replace(";", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
