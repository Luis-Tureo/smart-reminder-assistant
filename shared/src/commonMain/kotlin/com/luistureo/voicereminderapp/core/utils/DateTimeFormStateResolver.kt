package com.luistureo.voicereminderapp.core.utils

data class DateFieldFormState(
    val validation: DateInputValidationResult
) {
    val parts: DateInputParts?
        get() = (validation as? DateInputValidationResult.Valid)?.parts

    val isReady: Boolean
        get() = parts != null

    val isMissing: Boolean
        get() = validation is DateInputValidationResult.Missing

    val isIncomplete: Boolean
        get() = validation is DateInputValidationResult.Incomplete

    val isInvalid: Boolean
        get() = validation is DateInputValidationResult.Invalid

    val canUsePrefill: Boolean
        get() = isReady
}

data class TimeFieldFormState(
    val validation: TimeInputValidationResult
) {
    val parts: TimeInputParts?
        get() = (validation as? TimeInputValidationResult.Valid)?.parts

    val isReady: Boolean
        get() = parts != null

    val isMissing: Boolean
        get() = validation is TimeInputValidationResult.Missing

    val isIncomplete: Boolean
        get() = validation is TimeInputValidationResult.Incomplete

    val isInvalid: Boolean
        get() = validation is TimeInputValidationResult.Invalid

    val canUsePrefill: Boolean
        get() = isReady
}

data class DateTimeFormState(
    val date: DateFieldFormState,
    val time: TimeFieldFormState
) {
    val hasIncompleteField: Boolean
        get() = date.isIncomplete || time.isIncomplete

    val hasInvalidField: Boolean
        get() = date.isInvalid || time.isInvalid

    val canUseDatePrefill: Boolean
        get() = date.canUsePrefill

    val canUseTimePrefill: Boolean
        get() = time.canUsePrefill

    val canUseAnyPrefill: Boolean
        get() = canUseDatePrefill || canUseTimePrefill

    val isReadyForDownstreamLogic: Boolean
        get() = date.isReady && time.isReady
}

// Traduce la validacion portable a decisiones practicas para formularios.
object DateTimeFormStateResolver {

    fun resolveDateField(value: String?): DateFieldFormState {
        return DateFieldFormState(
            validation = DateTimeInputValidator.validateDateInput(value)
        )
    }

    fun resolveTimeField(value: String?): TimeFieldFormState {
        return TimeFieldFormState(
            validation = DateTimeInputValidator.validateTimeInput(value)
        )
    }

    fun resolve(
        dateValue: String?,
        timeValue: String?
    ): DateTimeFormState {
        return DateTimeFormState(
            date = resolveDateField(dateValue),
            time = resolveTimeField(timeValue)
        )
    }
}
