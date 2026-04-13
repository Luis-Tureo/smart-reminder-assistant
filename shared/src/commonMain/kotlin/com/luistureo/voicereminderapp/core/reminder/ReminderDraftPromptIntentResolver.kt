package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft

enum class ReminderDraftPromptIntent {
    ASK_REMINDER_TEXT,
    ASK_REMINDER_DATE,
    ASK_REMINDER_TIME,
    CORRECT_INCOMPLETE_TEXT,
    CORRECT_INCOMPLETE_DATE,
    CORRECT_INCOMPLETE_TIME,
    CORRECT_INVALID_TEXT,
    CORRECT_INVALID_DATE,
    CORRECT_INVALID_TIME,
    SHOW_CONFIRMATION,
    ALLOW_SAVE_OR_CONTINUE
}

data class ReminderDraftPromptIntentGuidance(
    val promptIntent: ReminderDraftPromptIntent,
    val suggestedFocusTarget: ReminderDraftField?
) {
    val shouldShowConfirmation: Boolean
        get() = promptIntent == ReminderDraftPromptIntent.SHOW_CONFIRMATION

    val shouldAllowSaveOrContinue: Boolean
        get() = promptIntent == ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE
}

// Traduce la accion semantica portable a una intencion reutilizable de UX/dominio.
object ReminderDraftPromptIntentResolver {

    fun resolve(draft: ReminderDraft): ReminderDraftPromptIntentGuidance {
        return resolve(ReminderDraftSemanticActionGuidanceResolver.resolve(draft))
    }

    fun resolve(guidance: ReminderDraftSemanticActionGuidance): ReminderDraftPromptIntentGuidance {
        return when (guidance.nextAction) {
            ReminderDraftSemanticAction.REQUEST_MISSING_FIELD -> ReminderDraftPromptIntentGuidance(
                promptIntent = resolveMissingFieldPromptIntent(guidance.field),
                suggestedFocusTarget = guidance.field
            )

            ReminderDraftSemanticAction.CORRECT_INCOMPLETE_FIELD -> ReminderDraftPromptIntentGuidance(
                promptIntent = resolveIncompleteFieldPromptIntent(guidance.field),
                suggestedFocusTarget = guidance.field
            )

            ReminderDraftSemanticAction.CORRECT_INVALID_FIELD -> ReminderDraftPromptIntentGuidance(
                promptIntent = resolveInvalidFieldPromptIntent(guidance.field),
                suggestedFocusTarget = guidance.field
            )

            ReminderDraftSemanticAction.CONFIRM_DRAFT -> ReminderDraftPromptIntentGuidance(
                promptIntent = ReminderDraftPromptIntent.SHOW_CONFIRMATION,
                suggestedFocusTarget = null
            )

            ReminderDraftSemanticAction.ALLOW_SAVE_OR_CONTINUE -> ReminderDraftPromptIntentGuidance(
                promptIntent = ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE,
                suggestedFocusTarget = null
            )
        }
    }

    private fun resolveMissingFieldPromptIntent(field: ReminderDraftField?): ReminderDraftPromptIntent {
        return when (field) {
            ReminderDraftField.TEXT -> ReminderDraftPromptIntent.ASK_REMINDER_TEXT
            ReminderDraftField.DATE -> ReminderDraftPromptIntent.ASK_REMINDER_DATE
            ReminderDraftField.TIME -> ReminderDraftPromptIntent.ASK_REMINDER_TIME
            null -> ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE
        }
    }

    private fun resolveIncompleteFieldPromptIntent(field: ReminderDraftField?): ReminderDraftPromptIntent {
        return when (field) {
            ReminderDraftField.TEXT -> ReminderDraftPromptIntent.CORRECT_INCOMPLETE_TEXT
            ReminderDraftField.DATE -> ReminderDraftPromptIntent.CORRECT_INCOMPLETE_DATE
            ReminderDraftField.TIME -> ReminderDraftPromptIntent.CORRECT_INCOMPLETE_TIME
            null -> ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE
        }
    }

    private fun resolveInvalidFieldPromptIntent(field: ReminderDraftField?): ReminderDraftPromptIntent {
        return when (field) {
            ReminderDraftField.TEXT -> ReminderDraftPromptIntent.CORRECT_INVALID_TEXT
            ReminderDraftField.DATE -> ReminderDraftPromptIntent.CORRECT_INVALID_DATE
            ReminderDraftField.TIME -> ReminderDraftPromptIntent.CORRECT_INVALID_TIME
            null -> ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE
        }
    }
}
