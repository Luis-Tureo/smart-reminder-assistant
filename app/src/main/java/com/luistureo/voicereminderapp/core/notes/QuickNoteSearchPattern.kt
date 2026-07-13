package com.luistureo.voicereminderapp.core.notes

object QuickNoteSearchPattern {
    fun contains(query: String): String {
        val escaped = buildString {
            query.trim().forEach { character ->
                if (character == '\\' || character == '%' || character == '_') append('\\')
                append(character)
            }
        }
        return "%$escaped%"
    }
}
