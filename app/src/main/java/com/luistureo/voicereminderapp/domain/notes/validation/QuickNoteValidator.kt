package com.luistureo.voicereminderapp.domain.notes.validation

import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft

object QuickNoteValidator {
    fun normalizeOrNull(draft: QuickNoteDraft): QuickNoteDraft? {
        val normalizedTitle = draft.title
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val normalizedContent = draft.content.trim()

        if (normalizedTitle == null && normalizedContent.isEmpty()) return null

        return draft.copy(
            title = normalizedTitle,
            content = normalizedContent
        )
    }

    fun isMeaningful(draft: QuickNoteDraft): Boolean = normalizeOrNull(draft) != null
}
