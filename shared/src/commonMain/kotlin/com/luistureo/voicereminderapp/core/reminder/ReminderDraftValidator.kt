package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.core.utils.DateTimeFormStateResolver
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import kotlinx.datetime.Clock

enum class ReminderDraftValidationIssue {
    MISSING_TEXT,
    MISSING_DATE,
    MISSING_TIME,
    RECURRENCE_NOT_ALLOWED,
    INVALID_DATE_TIME,
    PAST_DATE_TIME
}

// Reune reglas portables de validacion del borrador.
object ReminderDraftValidator {

    fun validate(
        draft: ReminderDraft,
        allowRecurrence: Boolean,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): ReminderDraftValidationIssue? {
        if (draft.text.isNullOrBlank()) {
            return ReminderDraftValidationIssue.MISSING_TEXT
        }

        val formState = DateTimeFormStateResolver.resolve(
            dateValue = draft.date,
            timeValue = draft.time
        )

        if (formState.date.isMissing) {
            return ReminderDraftValidationIssue.MISSING_DATE
        }

        if (formState.time.isMissing) {
            return ReminderDraftValidationIssue.MISSING_TIME
        }

        if (formState.hasIncompleteField || formState.hasInvalidField) {
            return ReminderDraftValidationIssue.INVALID_DATE_TIME
        }

        if (!allowRecurrence && draft.recurrence != null) {
            return ReminderDraftValidationIssue.RECURRENCE_NOT_ALLOWED
        }

        val triggerTimeMillis = draft.buildScheduledAtEpochMillis()
            ?: return ReminderDraftValidationIssue.INVALID_DATE_TIME

        if (triggerTimeMillis <= nowEpochMillis) {
            return ReminderDraftValidationIssue.PAST_DATE_TIME
        }

        return null
    }
}
