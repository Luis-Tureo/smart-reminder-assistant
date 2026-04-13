package com.luistureo.voicereminderapp.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DateTimeInputValidatorTest {

    @Test
    fun validateDateInput_returnsMissing_forBlankValue() {
        assertEquals(
            DateInputValidationResult.Missing,
            DateTimeInputValidator.validateDateInput("   ")
        )
    }

    @Test
    fun validateDateInput_returnsValid_forStoredDateAndTrimsWhitespace() {
        assertEquals(
            DateInputValidationResult.Valid(
                DateInputParts(
                    day = 5,
                    month = 3,
                    year = 2026
                )
            ),
            DateTimeInputValidator.validateDateInput(" 05/03/2026 ")
        )
    }

    @Test
    fun validateDateInput_returnsIncomplete_forStructuredPrefix() {
        assertEquals(
            DateInputValidationResult.Incomplete,
            DateTimeInputValidator.validateDateInput("05/03/")
        )
        assertEquals(
            DateInputValidationResult.Incomplete,
            DateTimeInputValidator.validateDateInput("05/03/202")
        )
    }

    @Test
    fun validateDateInput_returnsInvalid_forMalformedOrImpossibleDate() {
        assertEquals(
            DateInputValidationResult.Invalid,
            DateTimeInputValidator.validateDateInput("5/03/2026")
        )
        assertEquals(
            DateInputValidationResult.Invalid,
            DateTimeInputValidator.validateDateInput("31/04/2026")
        )
    }

    @Test
    fun validateTimeInput_returnsMissing_forBlankValue() {
        assertEquals(
            TimeInputValidationResult.Missing,
            DateTimeInputValidator.validateTimeInput(null)
        )
    }

    @Test
    fun validateTimeInput_returnsValid_forStoredTimeAndTrimsWhitespace() {
        assertEquals(
            TimeInputValidationResult.Valid(
                TimeInputParts(
                    hour = 9,
                    minute = 7
                )
            ),
            DateTimeInputValidator.validateTimeInput(" 09:07 ")
        )
    }

    @Test
    fun validateTimeInput_returnsIncomplete_forStructuredPrefix() {
        assertEquals(
            TimeInputValidationResult.Incomplete,
            DateTimeInputValidator.validateTimeInput("09:")
        )
        assertEquals(
            TimeInputValidationResult.Incomplete,
            DateTimeInputValidator.validateTimeInput("09:7")
        )
    }

    @Test
    fun validateTimeInput_returnsInvalid_forMalformedOrImpossibleTime() {
        assertEquals(
            TimeInputValidationResult.Invalid,
            DateTimeInputValidator.validateTimeInput("9:07")
        )
        assertEquals(
            TimeInputValidationResult.Invalid,
            DateTimeInputValidator.validateTimeInput("24:00")
        )
    }

    @Test
    fun validateDateTimeInput_returnsCombinedState_forMixedInput() {
        val validation = DateTimeInputValidator.validateDateTimeInput(
            dateValue = "05/03/2026",
            timeValue = ""
        )

        assertFalse(validation.isValid)
        assertEquals(
            DateInputValidationResult.Valid(
                DateInputParts(
                    day = 5,
                    month = 3,
                    year = 2026
                )
            ),
            validation.date
        )
        assertEquals(TimeInputValidationResult.Missing, validation.time)
    }

    @Test
    fun validateDateTimeInput_returnsValid_whenBothInputsAreValid() {
        val validation = DateTimeInputValidator.validateDateTimeInput(
            dateValue = "29/02/2024",
            timeValue = "23:59"
        )

        assertTrue(validation.isValid)
        assertEquals(
            DateInputValidationResult.Valid(
                DateInputParts(
                    day = 29,
                    month = 2,
                    year = 2024
                )
            ),
            validation.date
        )
        assertEquals(
            TimeInputValidationResult.Valid(
                TimeInputParts(
                    hour = 23,
                    minute = 59
                )
            ),
            validation.time
        )
    }
}
