package com.luistureo.voicereminderapp.core.nlp

import com.google.mlkit.nl.entityextraction.EntityAnnotation

class ReminderTextCleaner {

    fun removeDetectedSpans(
        originalText: String,
        annotations: List<EntityAnnotation>
    ): String {
        if (annotations.isEmpty()) return originalText.trim()

        val ranges = annotations
            .map { annotation -> annotation.getStart() to annotation.getEnd() }
            .sortedByDescending { it.first }

        var result = originalText

        for ((start, end) in ranges) {
            if (start >= 0 && end <= result.length && start < end) {
                result = result.removeRange(start, end)
            }
        }

        return result
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[,.:;\\-\\s]+|[,.:;\\-\\s]+$"), "")
            .trim()
    }
}