package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft

enum class ReminderDraftPromptToken {
    REMINDER_TEXT_PROMPT,
    REMINDER_DATE_PROMPT,
    REMINDER_TIME_PROMPT,
    REMINDER_CONFIRMATION_PROMPT,
    REMINDER_READY_TO_CONTINUE_PROMPT
}

enum class ReminderDraftPromptTokenReason {
    MISSING_REQUIRED_FIELD,
    INCOMPLETE_FIELD,
    INVALID_FIELD,
    CONFIRMATION,
    READY_TO_CONTINUE
}

data class ReminderDraftPromptTokenGuidance(
    val promptToken: ReminderDraftPromptToken,
    val reason: ReminderDraftPromptTokenReason,
    val suggestedFocusTarget: ReminderDraftField?
) {
    val shouldPromptForFieldInput: Boolean
        get() = promptToken == ReminderDraftPromptToken.REMINDER_TEXT_PROMPT ||
                promptToken == ReminderDraftPromptToken.REMINDER_DATE_PROMPT ||
                promptToken == ReminderDraftPromptToken.REMINDER_TIME_PROMPT
}

// Genera claves portables para seleccionar respuestas sin copiar la logica local.
object ReminderDraftPromptTokenResolver {

    fun resolve(draft: ReminderDraft): ReminderDraftPromptTokenGuidance {
        return resolve(ReminderDraftPromptIntentResolver.resolve(draft))
    }

    fun resolve(guidance: ReminderDraftPromptIntentGuidance): ReminderDraftPromptTokenGuidance {
        return when (guidance.promptIntent) {
            ReminderDraftPromptIntent.ASK_REMINDER_TEXT -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_TEXT_PROMPT,
                reason = ReminderDraftPromptTokenReason.MISSING_REQUIRED_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.ASK_REMINDER_DATE -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_DATE_PROMPT,
                reason = ReminderDraftPromptTokenReason.MISSING_REQUIRED_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.ASK_REMINDER_TIME -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_TIME_PROMPT,
                reason = ReminderDraftPromptTokenReason.MISSING_REQUIRED_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.CORRECT_INCOMPLETE_TEXT -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_TEXT_PROMPT,
                reason = ReminderDraftPromptTokenReason.INCOMPLETE_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.CORRECT_INCOMPLETE_DATE -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_DATE_PROMPT,
                reason = ReminderDraftPromptTokenReason.INCOMPLETE_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.CORRECT_INCOMPLETE_TIME -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_TIME_PROMPT,
                reason = ReminderDraftPromptTokenReason.INCOMPLETE_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.CORRECT_INVALID_TEXT -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_TEXT_PROMPT,
                reason = ReminderDraftPromptTokenReason.INVALID_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.CORRECT_INVALID_DATE -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_DATE_PROMPT,
                reason = ReminderDraftPromptTokenReason.INVALID_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.CORRECT_INVALID_TIME -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_TIME_PROMPT,
                reason = ReminderDraftPromptTokenReason.INVALID_FIELD,
                suggestedFocusTarget = guidance.suggestedFocusTarget
            )

            ReminderDraftPromptIntent.SHOW_CONFIRMATION -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_CONFIRMATION_PROMPT,
                reason = ReminderDraftPromptTokenReason.CONFIRMATION,
                suggestedFocusTarget = null
            )

            ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE -> ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_READY_TO_CONTINUE_PROMPT,
                reason = ReminderDraftPromptTokenReason.READY_TO_CONTINUE,
                suggestedFocusTarget = null
            )
        }
    }
}
