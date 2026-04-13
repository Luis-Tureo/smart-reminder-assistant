package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderDraftResponseFamilyResolverTest {

    @Test
    fun resolve_mapsBlankDraftToMissingValueFamily() {
        val guidance = ReminderDraftResponseFamilyResolver.resolve(ReminderDraft())

        assertEquals(ReminderDraftResponseFamily.REQUEST_MISSING_VALUE, guidance.responseFamily)
        assertEquals(ReminderDraftPromptToken.REMINDER_TEXT_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftField.TEXT, guidance.targetField)
        assertEquals(ReminderDraftField.TEXT, guidance.suggestedFocusTarget)
        assertTrue(guidance.requiresValueInput)
        assertFalse(guidance.isConfirmationScenario)
    }

    @Test
    fun resolve_mapsTextOnlyDraftToMissingDateFamily() {
        val guidance = ReminderDraftResponseFamilyResolver.resolve(
            ReminderDraft(text = "Pagar cuentas")
        )

        assertEquals(ReminderDraftResponseFamily.REQUEST_MISSING_VALUE, guidance.responseFamily)
        assertEquals(ReminderDraftPromptToken.REMINDER_DATE_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftField.DATE, guidance.targetField)
        assertTrue(guidance.requiresValueInput)
    }

    @Test
    fun resolve_mapsIncompleteDateToIncompleteFamily() {
        val guidance = ReminderDraftResponseFamilyResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/",
                time = "09:07"
            )
        )

        assertEquals(ReminderDraftResponseFamily.CORRECT_INCOMPLETE_VALUE, guidance.responseFamily)
        assertEquals(ReminderDraftPromptToken.REMINDER_DATE_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftField.DATE, guidance.targetField)
        assertTrue(guidance.requiresValueInput)
    }

    @Test
    fun resolve_mapsInvalidTimeToInvalidFamily() {
        val guidance = ReminderDraftResponseFamilyResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/2026",
                time = "24:00"
            )
        )

        assertEquals(ReminderDraftResponseFamily.CORRECT_INVALID_VALUE, guidance.responseFamily)
        assertEquals(ReminderDraftPromptToken.REMINDER_TIME_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftField.TIME, guidance.targetField)
        assertTrue(guidance.requiresValueInput)
    }

    @Test
    fun resolve_mapsConfirmationPromptTokenToConfirmationFamily() {
        val guidance = ReminderDraftResponseFamilyResolver.resolve(
            ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_CONFIRMATION_PROMPT,
                reason = ReminderDraftPromptTokenReason.CONFIRMATION,
                suggestedFocusTarget = null
            )
        )

        assertEquals(ReminderDraftResponseFamily.CONFIRMATION_SCENARIO, guidance.responseFamily)
        assertEquals(ReminderDraftPromptToken.REMINDER_CONFIRMATION_PROMPT, guidance.promptToken)
        assertNull(guidance.targetField)
        assertFalse(guidance.requiresValueInput)
        assertTrue(guidance.isConfirmationScenario)
    }

    @Test
    fun resolve_mapsReadyToContinuePromptTokenToReadyFamily() {
        val guidance = ReminderDraftResponseFamilyResolver.resolve(
            ReminderDraftPromptTokenGuidance(
                promptToken = ReminderDraftPromptToken.REMINDER_READY_TO_CONTINUE_PROMPT,
                reason = ReminderDraftPromptTokenReason.READY_TO_CONTINUE,
                suggestedFocusTarget = null
            )
        )

        assertEquals(ReminderDraftResponseFamily.READY_TO_CONTINUE_SCENARIO, guidance.responseFamily)
        assertEquals(ReminderDraftPromptToken.REMINDER_READY_TO_CONTINUE_PROMPT, guidance.promptToken)
        assertNull(guidance.targetField)
        assertFalse(guidance.requiresValueInput)
        assertFalse(guidance.isConfirmationScenario)
    }
}
