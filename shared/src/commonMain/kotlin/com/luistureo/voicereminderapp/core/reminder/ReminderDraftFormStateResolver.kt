package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.core.utils.DateTimeFormState
import com.luistureo.voicereminderapp.core.utils.DateTimeFormStateResolver
import com.luistureo.voicereminderapp.domain.model.ReminderDraft

data class ReminderTextFieldFormState(
    val value: String?
) {
    val isMissing: Boolean
        get() = value.isNullOrBlank()

    val isReady: Boolean
        get() = !isMissing
}

data class ReminderDraftFormState(
    val text: ReminderTextFieldFormState,
    val dateTime: DateTimeFormState
) {
    val hasMissingText: Boolean
        get() = text.isMissing

    val hasMissingDate: Boolean
        get() = dateTime.date.isMissing

    val hasMissingTime: Boolean
        get() = dateTime.time.isMissing

    val hasMissingRequiredInfo: Boolean
        get() = hasMissingText || hasMissingDate || hasMissingTime

    val hasIncompleteField: Boolean
        get() = dateTime.hasIncompleteField

    val hasInvalidField: Boolean
        get() = dateTime.hasInvalidField

    val hasUsableDraftData: Boolean
        get() = text.isReady || dateTime.canUseAnyPrefill

    val isReadyForDownstreamLogic: Boolean
        get() = text.isReady && dateTime.isReadyForDownstreamLogic

    val isReadyToSave: Boolean
        get() = isReadyForDownstreamLogic

    // Permite avanzar a confirmacion o edicion cuando ya hay datos portables
    // y los campos presentes no estan incompletos ni invalidos.
    val isReadyToConfirm: Boolean
        get() = hasUsableDraftData &&
                !hasIncompleteField &&
                !hasInvalidField &&
                !isReadyToSave
}

// Centraliza decisiones portables del borrador completo sin depender de Android.
object ReminderDraftFormStateResolver {

    fun resolve(draft: ReminderDraft): ReminderDraftFormState {
        return resolve(
            textValue = draft.text,
            dateValue = draft.date,
            timeValue = draft.time
        )
    }

    fun resolve(
        textValue: String?,
        dateValue: String?,
        timeValue: String?
    ): ReminderDraftFormState {
        return ReminderDraftFormState(
            text = ReminderTextFieldFormState(textValue),
            dateTime = DateTimeFormStateResolver.resolve(
                dateValue = dateValue,
                timeValue = timeValue
            )
        )
    }
}
