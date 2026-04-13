package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft

enum class ReminderDraftResponseTemplateKey {
    REQUEST_MISSING_TEXT,
    REQUEST_MISSING_DATE,
    REQUEST_MISSING_TIME,
    CORRECT_INCOMPLETE_TEXT,
    CORRECT_INCOMPLETE_DATE,
    CORRECT_INCOMPLETE_TIME,
    CORRECT_INVALID_TEXT,
    CORRECT_INVALID_DATE,
    CORRECT_INVALID_TIME,
    CONFIRMATION,
    READY_TO_CONTINUE
}

// Expone claves portables estables para enlazar familias compartidas con plantillas locales.
object ReminderDraftResponseTemplateKeyResolver {

    fun resolve(draft: ReminderDraft): ReminderDraftResponseTemplateKey {
        return resolve(ReminderDraftResponseFamilyResolver.resolve(draft))
    }

    fun resolve(guidance: ReminderDraftResponseFamilyGuidance): ReminderDraftResponseTemplateKey {
        return resolve(guidance.responseFamily, guidance.targetField)
    }

    fun resolve(
        responseFamily: ReminderDraftResponseFamily,
        targetField: ReminderDraftField?
    ): ReminderDraftResponseTemplateKey {
        return when (responseFamily) {
            ReminderDraftResponseFamily.REQUEST_MISSING_VALUE -> when (requireTargetField(responseFamily, targetField)) {
                ReminderDraftField.TEXT -> ReminderDraftResponseTemplateKey.REQUEST_MISSING_TEXT
                ReminderDraftField.DATE -> ReminderDraftResponseTemplateKey.REQUEST_MISSING_DATE
                ReminderDraftField.TIME -> ReminderDraftResponseTemplateKey.REQUEST_MISSING_TIME
            }

            ReminderDraftResponseFamily.CORRECT_INCOMPLETE_VALUE -> when (requireTargetField(responseFamily, targetField)) {
                ReminderDraftField.TEXT -> ReminderDraftResponseTemplateKey.CORRECT_INCOMPLETE_TEXT
                ReminderDraftField.DATE -> ReminderDraftResponseTemplateKey.CORRECT_INCOMPLETE_DATE
                ReminderDraftField.TIME -> ReminderDraftResponseTemplateKey.CORRECT_INCOMPLETE_TIME
            }

            ReminderDraftResponseFamily.CORRECT_INVALID_VALUE -> when (requireTargetField(responseFamily, targetField)) {
                ReminderDraftField.TEXT -> ReminderDraftResponseTemplateKey.CORRECT_INVALID_TEXT
                ReminderDraftField.DATE -> ReminderDraftResponseTemplateKey.CORRECT_INVALID_DATE
                ReminderDraftField.TIME -> ReminderDraftResponseTemplateKey.CORRECT_INVALID_TIME
            }

            ReminderDraftResponseFamily.CONFIRMATION_SCENARIO ->
                ReminderDraftResponseTemplateKey.CONFIRMATION

            ReminderDraftResponseFamily.READY_TO_CONTINUE_SCENARIO ->
                ReminderDraftResponseTemplateKey.READY_TO_CONTINUE
        }
    }

    private fun requireTargetField(
        responseFamily: ReminderDraftResponseFamily,
        targetField: ReminderDraftField?
    ): ReminderDraftField {
        return requireNotNull(targetField) {
            "Target field is required for $responseFamily."
        }
    }
}
