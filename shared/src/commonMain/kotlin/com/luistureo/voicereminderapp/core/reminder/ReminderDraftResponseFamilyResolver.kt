package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft

enum class ReminderDraftResponseFamily {
    REQUEST_MISSING_VALUE,
    CORRECT_INCOMPLETE_VALUE,
    CORRECT_INVALID_VALUE,
    CONFIRMATION_SCENARIO,
    READY_TO_CONTINUE_SCENARIO
}

data class ReminderDraftResponseFamilyGuidance(
    val responseFamily: ReminderDraftResponseFamily,
    val promptToken: ReminderDraftPromptToken,
    val targetField: ReminderDraftField?,
    val suggestedFocusTarget: ReminderDraftField?
) {
    val requiresValueInput: Boolean
        get() = responseFamily == ReminderDraftResponseFamily.REQUEST_MISSING_VALUE ||
                responseFamily == ReminderDraftResponseFamily.CORRECT_INCOMPLETE_VALUE ||
                responseFamily == ReminderDraftResponseFamily.CORRECT_INVALID_VALUE

    val isConfirmationScenario: Boolean
        get() = responseFamily == ReminderDraftResponseFamily.CONFIRMATION_SCENARIO
}

// Agrupa tokens portables en familias de conversacion reutilizables.
object ReminderDraftResponseFamilyResolver {

    fun resolve(draft: ReminderDraft): ReminderDraftResponseFamilyGuidance {
        return resolve(ReminderDraftPromptTokenResolver.resolve(draft))
    }

    fun resolve(guidance: ReminderDraftPromptTokenGuidance): ReminderDraftResponseFamilyGuidance {
        val responseFamily = when (guidance.reason) {
            ReminderDraftPromptTokenReason.MISSING_REQUIRED_FIELD ->
                ReminderDraftResponseFamily.REQUEST_MISSING_VALUE

            ReminderDraftPromptTokenReason.INCOMPLETE_FIELD ->
                ReminderDraftResponseFamily.CORRECT_INCOMPLETE_VALUE

            ReminderDraftPromptTokenReason.INVALID_FIELD ->
                ReminderDraftResponseFamily.CORRECT_INVALID_VALUE

            ReminderDraftPromptTokenReason.CONFIRMATION ->
                ReminderDraftResponseFamily.CONFIRMATION_SCENARIO

            ReminderDraftPromptTokenReason.READY_TO_CONTINUE ->
                ReminderDraftResponseFamily.READY_TO_CONTINUE_SCENARIO
        }

        return ReminderDraftResponseFamilyGuidance(
            responseFamily = responseFamily,
            promptToken = guidance.promptToken,
            targetField = guidance.suggestedFocusTarget,
            suggestedFocusTarget = guidance.suggestedFocusTarget
        )
    }
}
