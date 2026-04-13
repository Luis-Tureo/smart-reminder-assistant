package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.core.utils.DateInputValidationResult
import com.luistureo.voicereminderapp.core.utils.DateTimeInputValidator
import com.luistureo.voicereminderapp.core.utils.TimeInputValidationResult
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

        when (DateTimeInputValidator.validateDateInput(draft.date)) {
            DateInputValidationResult.Missing -> return ReminderDraftValidationIssue.MISSING_DATE
            DateInputValidationResult.Incomplete,
            DateInputValidationResult.Invalid -> return ReminderDraftValidationIssue.INVALID_DATE_TIME
            is DateInputValidationResult.Valid -> Unit
        }

        when (DateTimeInputValidator.validateTimeInput(draft.time)) {
            TimeInputValidationResult.Missing -> return ReminderDraftValidationIssue.MISSING_TIME
            TimeInputValidationResult.Incomplete,
            TimeInputValidationResult.Invalid -> return ReminderDraftValidationIssue.INVALID_DATE_TIME
            is TimeInputValidationResult.Valid -> Unit
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
