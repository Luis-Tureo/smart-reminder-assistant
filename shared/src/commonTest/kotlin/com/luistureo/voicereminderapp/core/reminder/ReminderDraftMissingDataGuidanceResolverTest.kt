package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderDraftMissingDataGuidanceResolverTest {

    @Test
    fun resolve_prioritizesTextWhenDraftIsCompletelyBlank() {
        val guidance = ReminderDraftMissingDataGuidanceResolver.resolve(ReminderDraft())

        assertEquals(ReminderDraftField.TEXT, guidance.nextMissingField)
        assertTrue(guidance.shouldRequestMissingFieldFirst)
        assertNull(guidance.blockingField)
        assertFalse(guidance.isReadyToConfirm)
        assertFalse(guidance.isReadyToSave)
    }

    @Test
    fun resolve_prioritizesDateAfterTextIsPresent() {
        val guidance = ReminderDraftMissingDataGuidanceResolver.resolve(
            ReminderDraft(text = "Pagar cuentas")
        )

        assertEquals(ReminderDraftField.DATE, guidance.nextMissingField)
        assertTrue(guidance.shouldRequestMissingFieldFirst)
        assertTrue(guidance.isReadyToConfirm)
        assertFalse(guidance.isReadyToSave)
    }

    @Test
    fun resolve_prioritizesTextForScheduleOnlyDraft() {
        val guidance = ReminderDraftMissingDataGuidanceResolver.resolve(
            ReminderDraft(
                date = "05/03/2026",
                time = "09:07"
            )
        )

        assertEquals(ReminderDraftField.TEXT, guidance.nextMissingField)
        assertTrue(guidance.shouldRequestMissingFieldFirst)
        assertTrue(guidance.isReadyToConfirm)
        assertFalse(guidance.isReadyToSave)
    }

    @Test
    fun resolve_keepsMissingPriorityWhileExposingIncompleteBlocker() {
        val guidance = ReminderDraftMissingDataGuidanceResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/",
                time = null
            )
        )

        assertEquals(ReminderDraftField.TIME, guidance.nextMissingField)
        assertEquals(ReminderDraftField.DATE, guidance.blockingField)
        assertEquals(ReminderDraftBlockingReason.INCOMPLETE, guidance.blockingReason)
        assertTrue(guidance.shouldRequestMissingFieldFirst)
        assertTrue(guidance.isBlockedByIncompleteField)
        assertFalse(guidance.isBlockedByInvalidField)
    }

    @Test
    fun resolve_exposesInvalidBlockerWhenRequiredFieldsArePresent() {
        val guidance = ReminderDraftMissingDataGuidanceResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "31/04/2026",
                time = "09:07"
            )
        )

        assertNull(guidance.nextMissingField)
        assertEquals(ReminderDraftField.DATE, guidance.blockingField)
        assertEquals(ReminderDraftBlockingReason.INVALID, guidance.blockingReason)
        assertFalse(guidance.shouldRequestMissingFieldFirst)
        assertFalse(guidance.isBlockedByIncompleteField)
        assertTrue(guidance.isBlockedByInvalidField)
        assertFalse(guidance.isReadyToSave)
    }

    @Test
    fun resolve_marksCompleteDraftAsReadyToSave() {
        val guidance = ReminderDraftMissingDataGuidanceResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/2026",
                time = "09:07"
            )
        )

        assertNull(guidance.nextMissingField)
        assertNull(guidance.blockingField)
        assertNull(guidance.blockingReason)
        assertFalse(guidance.shouldRequestMissingFieldFirst)
        assertFalse(guidance.isBlockedByIncompleteField)
        assertFalse(guidance.isBlockedByInvalidField)
        assertFalse(guidance.isReadyToConfirm)
        assertTrue(guidance.isReadyToSave)
    }
}
