package com.luistureo.voicereminderapp.core.utils

import com.luistureo.voicereminderapp.domain.model.ReminderType

object ReminderTypeResolver {

    private val birthdayKeywords = listOf(
        "cumpleanos",
        "cumpleaÃ±os",
        "cumple",
        "birthday"
    )

    fun resolve(title: String, detail: String): ReminderType {
        val normalizedContent = "$title $detail".lowercase()

        return when {
            birthdayKeywords.any { normalizedContent.contains(it) } -> ReminderType.BIRTHDAY
            else -> ReminderType.DEFAULT
        }
    }
}
