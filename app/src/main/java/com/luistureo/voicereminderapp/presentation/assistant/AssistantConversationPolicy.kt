package com.luistureo.voicereminderapp.presentation.assistant

import com.luistureo.voicereminderapp.domain.model.ReminderDraft

enum class AssistantMissingSlot {
    TITLE_DETAIL,
    DATE,
    TIME,
    AMBIGUOUS_TIME,
    RECURRENCE,
    NONE
}

object AssistantConversationPolicy {

    fun missingSlot(
        draft: ReminderDraft?,
        hasPendingAmbiguousTime: Boolean,
        requiresRecurrence: Boolean = false
    ): AssistantMissingSlot {
        return when {
            draft == null || draft.text.isNullOrBlank() -> AssistantMissingSlot.TITLE_DETAIL
            draft.date.isNullOrBlank() -> AssistantMissingSlot.DATE
            draft.time.isNullOrBlank() -> AssistantMissingSlot.TIME
            hasPendingAmbiguousTime -> AssistantMissingSlot.AMBIGUOUS_TIME
            requiresRecurrence && draft.recurrence == null -> AssistantMissingSlot.RECURRENCE
            else -> AssistantMissingSlot.NONE
        }
    }

    fun isReadyToSave(
        draft: ReminderDraft?,
        hasPendingAmbiguousTime: Boolean,
        requiresRecurrence: Boolean = false
    ): Boolean {
        return missingSlot(
            draft = draft,
            hasPendingAmbiguousTime = hasPendingAmbiguousTime,
            requiresRecurrence = requiresRecurrence
        ) == AssistantMissingSlot.NONE
    }

    fun questionFor(
        missingSlot: AssistantMissingSlot,
        ambiguousHour: Int? = null
    ): String {
        return when (missingSlot) {
            AssistantMissingSlot.TITLE_DETAIL -> AssistantPhrases.START_LISTENING
            AssistantMissingSlot.DATE -> AssistantPhrases.ASK_DATE
            AssistantMissingSlot.TIME -> AssistantPhrases.ASK_TIME
            AssistantMissingSlot.AMBIGUOUS_TIME ->
                AssistantPhrases.ambiguousTimeQuestion(ambiguousHour ?: 0)
            AssistantMissingSlot.RECURRENCE ->
                "Por voz dejo recordatorios simples. Para repetirlo, usa el formulario."
            AssistantMissingSlot.NONE -> "Perfecto."
        }
    }
}
