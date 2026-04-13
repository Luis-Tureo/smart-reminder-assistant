package com.luistureo.voicereminderapp.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DateTimeFormStateResolverTest {

    @Test
    fun resolveDateField_marksValidInputAsReadyAndPrefillable() {
        val fieldState = DateTimeFormStateResolver.resolveDateField("05/03/2026")

        assertTrue(fieldState.isReady)
        assertTrue(fieldState.canUsePrefill)
        assertFalse(fieldState.isIncomplete)
        assertFalse(fieldState.isInvalid)
        assertEquals(
            DateInputParts(
                day = 5,
                month = 3,
                year = 2026
            ),
            fieldState.parts
        )
    }

    @Test
    fun resolveDateField_marksStructuredPrefixAsIncomplete() {
        val fieldState = DateTimeFormStateResolver.resolveDateField("05/03/")

        assertFalse(fieldState.isReady)
        assertTrue(fieldState.isIncomplete)
        assertFalse(fieldState.canUsePrefill)
        assertEquals(null, fieldState.parts)
    }

    @Test
    fun resolveTimeField_marksInvalidInputAsInvalid() {
        val fieldState = DateTimeFormStateResolver.resolveTimeField("24:00")

        assertFalse(fieldState.isReady)
        assertTrue(fieldState.isInvalid)
        assertFalse(fieldState.canUsePrefill)
        assertEquals(null, fieldState.parts)
    }

    @Test
    fun resolve_combinesFieldStatesForPartialPrefill() {
        val formState = DateTimeFormStateResolver.resolve(
            dateValue = "05/03/2026",
            timeValue = ""
        )

        assertTrue(formState.canUseDatePrefill)
        assertFalse(formState.canUseTimePrefill)
        assertTrue(formState.canUseAnyPrefill)
        assertFalse(formState.isReadyForDownstreamLogic)
        assertFalse(formState.hasIncompleteField)
        assertFalse(formState.hasInvalidField)
        assertTrue(formState.time.isMissing)
    }

    @Test
    fun resolve_marksCombinedInputReadyWhenBothFieldsAreValid() {
        val formState = DateTimeFormStateResolver.resolve(
            dateValue = "29/02/2024",
            timeValue = "23:59"
        )

        assertTrue(formState.isReadyForDownstreamLogic)
        assertTrue(formState.canUseDatePrefill)
        assertTrue(formState.canUseTimePrefill)
        assertFalse(formState.hasIncompleteField)
        assertFalse(formState.hasInvalidField)
        assertNotNull(formState.date.parts)
        assertNotNull(formState.time.parts)
    }

    @Test
    fun resolve_detectsIncompleteAndInvalidBlockingStates() {
        val incompleteState = DateTimeFormStateResolver.resolve(
            dateValue = "05/03/",
            timeValue = "09:07"
        )
        val invalidState = DateTimeFormStateResolver.resolve(
            dateValue = "31/04/2026",
            timeValue = "09:07"
        )

        assertTrue(incompleteState.hasIncompleteField)
        assertFalse(incompleteState.hasInvalidField)
        assertFalse(incompleteState.isReadyForDownstreamLogic)

        assertFalse(invalidState.hasIncompleteField)
        assertTrue(invalidState.hasInvalidField)
        assertFalse(invalidState.isReadyForDownstreamLogic)
    }
}
