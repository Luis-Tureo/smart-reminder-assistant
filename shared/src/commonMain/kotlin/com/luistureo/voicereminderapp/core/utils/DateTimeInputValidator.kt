package com.luistureo.voicereminderapp.core.utils

sealed interface DateInputValidationResult {
    object Missing : DateInputValidationResult
    object Incomplete : DateInputValidationResult
    object Invalid : DateInputValidationResult
    data class Valid(val parts: DateInputParts) : DateInputValidationResult
}

sealed interface TimeInputValidationResult {
    object Missing : TimeInputValidationResult
    object Incomplete : TimeInputValidationResult
    object Invalid : TimeInputValidationResult
    data class Valid(val parts: TimeInputParts) : TimeInputValidationResult
}

data class DateTimeInputValidationResult(
    val date: DateInputValidationResult,
    val time: TimeInputValidationResult
) {
    val isValid: Boolean
        get() = date is DateInputValidationResult.Valid &&
                time is TimeInputValidationResult.Valid
}

// Reune reglas portables de validacion para entradas de fecha y hora.
object DateTimeInputValidator {

    fun validateDateInput(value: String?): DateInputValidationResult {
        val normalizedValue = value.orEmpty().trim()
        if (normalizedValue.isEmpty()) {
            return DateInputValidationResult.Missing
        }

        DateTimeFormatterCore.parseDateParts(normalizedValue)?.let { parts ->
            return DateInputValidationResult.Valid(parts)
        }

        return if (isPotentialDatePrefix(normalizedValue)) {
            DateInputValidationResult.Incomplete
        } else {
            DateInputValidationResult.Invalid
        }
    }

    fun validateTimeInput(value: String?): TimeInputValidationResult {
        val normalizedValue = value.orEmpty().trim()
        if (normalizedValue.isEmpty()) {
            return TimeInputValidationResult.Missing
        }

        DateTimeFormatterCore.parseTimeParts(normalizedValue)?.let { parts ->
            return TimeInputValidationResult.Valid(parts)
        }

        return if (isPotentialTimePrefix(normalizedValue)) {
            TimeInputValidationResult.Incomplete
        } else {
            TimeInputValidationResult.Invalid
        }
    }

    fun validateDateTimeInput(
        dateValue: String?,
        timeValue: String?
    ): DateTimeInputValidationResult {
        return DateTimeInputValidationResult(
            date = validateDateInput(dateValue),
            time = validateTimeInput(timeValue)
        )
    }

    private fun isPotentialDatePrefix(value: String): Boolean {
        return isStructuredPrefix(
            value = value,
            expectedLength = STORED_DATE_LENGTH,
            separatorIndexes = setOf(2, 5),
            separator = '/'
        )
    }

    private fun isPotentialTimePrefix(value: String): Boolean {
        return isStructuredPrefix(
            value = value,
            expectedLength = STORED_TIME_LENGTH,
            separatorIndexes = setOf(2),
            separator = ':'
        )
    }

    private fun isStructuredPrefix(
        value: String,
        expectedLength: Int,
        separatorIndexes: Set<Int>,
        separator: Char
    ): Boolean {
        if (value.isEmpty() || value.length >= expectedLength) {
            return false
        }

        return value.withIndex().all { (index, char) ->
            if (index in separatorIndexes) {
                char == separator
            } else {
                char.isDigit()
            }
        }
    }

    private const val STORED_DATE_LENGTH = 10
    private const val STORED_TIME_LENGTH = 5
}
