package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderDraftSemanticActionGuidanceResolverTest {

    @Test
    fun resolve_mapsBlankDraftToRequestText() {
        val guidance = ReminderDraftSemanticActionGuidanceResolver.resolve(ReminderDraft())

        assertEquals(ReminderDraftSemanticAction.REQUEST_MISSING_FIELD, guidance.nextAction)
        assertEquals(ReminderDraftField.TEXT, guidance.field)
        assertTrue(guidance.shouldRequestFieldInput)
        assertFalse(guidance.shouldCorrectFieldInput)
        assertFalse(guidance.canConfirmDraft)
        assertFalse(guidance.canSaveOrContinue)
    }

    @Test
    fun resolve_mapsTextOnlyDraftToRequestDate() {
        val guidance = ReminderDraftSemanticActionGuidanceResolver.resolve(
            ReminderDraft(text = "Pagar cuentas")
        )

        assertEquals(ReminderDraftSemanticAction.REQUEST_MISSING_FIELD, guidance.nextAction)
        assertEquals(ReminderDraftField.DATE, guidance.field)
        assertTrue(guidance.shouldRequestFieldInput)
        assertFalse(guidance.shouldCorrectFieldInput)
        assertTrue(guidance.canConfirmDraft)
        assertFalse(guidance.canSaveOrContinue)
    }

    @Test
    fun resolve_mapsIncompleteDateToCorrectionAction() {
        val guidance = ReminderDraftSemanticActionGuidanceResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/",
                time = "09:07"
            )
        )

        assertEquals(ReminderDraftSemanticAction.CORRECT_INCOMPLETE_FIELD, guidance.nextAction)
        assertEquals(ReminderDraftField.DATE, guidance.field)
        assertFalse(guidance.shouldRequestFieldInput)
        assertTrue(guidance.shouldCorrectFieldInput)
        assertFalse(guidance.canConfirmDraft)
        assertFalse(guidance.canSaveOrContinue)
    }

    @Test
    fun resolve_mapsInvalidTimeToCorrectionAction() {
        val guidance = ReminderDraftSemanticActionGuidanceResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/2026",
                time = "24:00"
            )
        )

        assertEquals(ReminderDraftSemanticAction.CORRECT_INVALID_FIELD, guidance.nextAction)
        assertEquals(ReminderDraftField.TIME, guidance.field)
        assertFalse(guidance.shouldRequestFieldInput)
        assertTrue(guidance.shouldCorrectFieldInput)
        assertFalse(guidance.canConfirmDraft)
        assertFalse(guidance.canSaveOrContinue)
    }

    @Test
    fun resolve_canExpressConfirmDraftFromPortableGuidance() {
        val guidance = ReminderDraftSemanticActionGuidanceResolver.resolve(
            ReminderDraftMissingDataGuidance(
                nextMissingField = null,
                blockingField = null,
                blockingReason = null,
                isReadyToConfirm = true,
                isReadyToSave = false
            )
        )

        assertEquals(ReminderDraftSemanticAction.CONFIRM_DRAFT, guidance.nextAction)
        assertNull(guidance.field)
        assertFalse(guidance.shouldRequestFieldInput)
        assertFalse(guidance.shouldCorrectFieldInput)
        assertTrue(guidance.canConfirmDraft)
        assertFalse(guidance.canSaveOrContinue)
    }

    @Test
    fun resolve_mapsCompleteDraftToAllowSaveOrContinue() {
        val guidance = ReminderDraftSemanticActionGuidanceResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/2026",
                time = "09:07"
            )
        )

        assertEquals(ReminderDraftSemanticAction.ALLOW_SAVE_OR_CONTINUE, guidance.nextAction)
        assertNull(guidance.field)
        assertFalse(guidance.shouldRequestFieldInput)
        assertFalse(guidance.shouldCorrectFieldInput)
        assertFalse(guidance.canConfirmDraft)
        assertTrue(guidance.canSaveOrContinue)
    }
}
