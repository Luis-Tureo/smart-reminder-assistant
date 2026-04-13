package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft

enum class ReminderDraftField {
    TEXT,
    DATE,
    TIME
}

enum class ReminderDraftBlockingReason {
    INCOMPLETE,
    INVALID
}

data class ReminderDraftMissingDataGuidance(
    val nextMissingField: ReminderDraftField?,
    val blockingField: ReminderDraftField?,
    val blockingReason: ReminderDraftBlockingReason?,
    val isReadyToConfirm: Boolean,
    val isReadyToSave: Boolean
) {
    val shouldRequestMissingFieldFirst: Boolean
        get() = nextMissingField != null

    val isBlockedByIncompleteField: Boolean
        get() = blockingReason == ReminderDraftBlockingReason.INCOMPLETE

    val isBlockedByInvalidField: Boolean
        get() = blockingReason == ReminderDraftBlockingReason.INVALID
}

// Centraliza la prioridad portable de que dato pedir primero.
object ReminderDraftMissingDataGuidanceResolver {

    fun resolve(draft: ReminderDraft): ReminderDraftMissingDataGuidance {
        return resolve(ReminderDraftFormStateResolver.resolve(draft))
    }

    fun resolve(formState: ReminderDraftFormState): ReminderDraftMissingDataGuidance {
        val nextMissingField = when {
            formState.hasMissingText -> ReminderDraftField.TEXT
            formState.hasMissingDate -> ReminderDraftField.DATE
            formState.hasMissingTime -> ReminderDraftField.TIME
            else -> null
        }

        val blockingState = when {
            formState.dateTime.date.isIncomplete ->
                ReminderDraftField.DATE to ReminderDraftBlockingReason.INCOMPLETE

            formState.dateTime.time.isIncomplete ->
                ReminderDraftField.TIME to ReminderDraftBlockingReason.INCOMPLETE

            formState.dateTime.date.isInvalid ->
                ReminderDraftField.DATE to ReminderDraftBlockingReason.INVALID

            formState.dateTime.time.isInvalid ->
                ReminderDraftField.TIME to ReminderDraftBlockingReason.INVALID

            else -> null
        }

        return ReminderDraftMissingDataGuidance(
            nextMissingField = nextMissingField,
            blockingField = blockingState?.first,
            blockingReason = blockingState?.second,
            isReadyToConfirm = formState.isReadyToConfirm,
            isReadyToSave = formState.isReadyToSave
        )
    }
}
