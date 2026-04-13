package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft

enum class ReminderDraftSemanticAction {
    REQUEST_MISSING_FIELD,
    CORRECT_INCOMPLETE_FIELD,
    CORRECT_INVALID_FIELD,
    CONFIRM_DRAFT,
    ALLOW_SAVE_OR_CONTINUE
}

data class ReminderDraftSemanticActionGuidance(
    val nextAction: ReminderDraftSemanticAction,
    val field: ReminderDraftField?,
    val canConfirmDraft: Boolean,
    val canSaveOrContinue: Boolean
) {
    val shouldRequestFieldInput: Boolean
        get() = nextAction == ReminderDraftSemanticAction.REQUEST_MISSING_FIELD

    val shouldCorrectFieldInput: Boolean
        get() = nextAction == ReminderDraftSemanticAction.CORRECT_INCOMPLETE_FIELD ||
                nextAction == ReminderDraftSemanticAction.CORRECT_INVALID_FIELD
}

// Traduce el estado portable del borrador a una intencion semantica siguiente.
object ReminderDraftSemanticActionGuidanceResolver {

    fun resolve(draft: ReminderDraft): ReminderDraftSemanticActionGuidance {
        return resolve(ReminderDraftMissingDataGuidanceResolver.resolve(draft))
    }

    fun resolve(guidance: ReminderDraftMissingDataGuidance): ReminderDraftSemanticActionGuidance {
        val nextAction = when {
            guidance.nextMissingField != null ->
                ReminderDraftSemanticAction.REQUEST_MISSING_FIELD

            guidance.isBlockedByIncompleteField ->
                ReminderDraftSemanticAction.CORRECT_INCOMPLETE_FIELD

            guidance.isBlockedByInvalidField ->
                ReminderDraftSemanticAction.CORRECT_INVALID_FIELD

            guidance.isReadyToSave ->
                ReminderDraftSemanticAction.ALLOW_SAVE_OR_CONTINUE

            guidance.isReadyToConfirm ->
                ReminderDraftSemanticAction.CONFIRM_DRAFT

            else ->
                ReminderDraftSemanticAction.ALLOW_SAVE_OR_CONTINUE
        }

        val field = when (nextAction) {
            ReminderDraftSemanticAction.REQUEST_MISSING_FIELD -> guidance.nextMissingField
            ReminderDraftSemanticAction.CORRECT_INCOMPLETE_FIELD,
            ReminderDraftSemanticAction.CORRECT_INVALID_FIELD -> guidance.blockingField
            ReminderDraftSemanticAction.CONFIRM_DRAFT,
            ReminderDraftSemanticAction.ALLOW_SAVE_OR_CONTINUE -> null
        }

        return ReminderDraftSemanticActionGuidance(
            nextAction = nextAction,
            field = field,
            canConfirmDraft = guidance.isReadyToConfirm,
            canSaveOrContinue = guidance.isReadyToSave
        )
    }
}
