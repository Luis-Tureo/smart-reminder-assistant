package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderDraftPromptTokenResolverTest {

    @Test
    fun resolve_mapsBlankDraftToTextPromptToken() {
        val guidance = ReminderDraftPromptTokenResolver.resolve(ReminderDraft())

        assertEquals(ReminderDraftPromptToken.REMINDER_TEXT_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftPromptTokenReason.MISSING_REQUIRED_FIELD, guidance.reason)
        assertEquals(ReminderDraftField.TEXT, guidance.suggestedFocusTarget)
        assertTrue(guidance.shouldPromptForFieldInput)
    }

    @Test
    fun resolve_mapsTextOnlyDraftToDatePromptToken() {
        val guidance = ReminderDraftPromptTokenResolver.resolve(
            ReminderDraft(text = "Pagar cuentas")
        )

        assertEquals(ReminderDraftPromptToken.REMINDER_DATE_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftPromptTokenReason.MISSING_REQUIRED_FIELD, guidance.reason)
        assertEquals(ReminderDraftField.DATE, guidance.suggestedFocusTarget)
        assertTrue(guidance.shouldPromptForFieldInput)
    }

    @Test
    fun resolve_mapsIncompleteDateToDatePromptTokenWithIncompleteReason() {
        val guidance = ReminderDraftPromptTokenResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/",
                time = "09:07"
            )
        )

        assertEquals(ReminderDraftPromptToken.REMINDER_DATE_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftPromptTokenReason.INCOMPLETE_FIELD, guidance.reason)
        assertEquals(ReminderDraftField.DATE, guidance.suggestedFocusTarget)
        assertTrue(guidance.shouldPromptForFieldInput)
    }

    @Test
    fun resolve_mapsInvalidTimeToTimePromptTokenWithInvalidReason() {
        val guidance = ReminderDraftPromptTokenResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/2026",
                time = "24:00"
            )
        )

        assertEquals(ReminderDraftPromptToken.REMINDER_TIME_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftPromptTokenReason.INVALID_FIELD, guidance.reason)
        assertEquals(ReminderDraftField.TIME, guidance.suggestedFocusTarget)
        assertTrue(guidance.shouldPromptForFieldInput)
    }

    @Test
    fun resolve_mapsConfirmationIntentToConfirmationPromptToken() {
        val guidance = ReminderDraftPromptTokenResolver.resolve(
            ReminderDraftPromptIntentGuidance(
                promptIntent = ReminderDraftPromptIntent.SHOW_CONFIRMATION,
                suggestedFocusTarget = null
            )
        )

        assertEquals(ReminderDraftPromptToken.REMINDER_CONFIRMATION_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftPromptTokenReason.CONFIRMATION, guidance.reason)
        assertNull(guidance.suggestedFocusTarget)
        assertFalse(guidance.shouldPromptForFieldInput)
    }

    @Test
    fun resolve_mapsReadyToContinueIntentToReadyPromptToken() {
        val guidance = ReminderDraftPromptTokenResolver.resolve(
            ReminderDraftPromptIntentGuidance(
                promptIntent = ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE,
                suggestedFocusTarget = null
            )
        )

        assertEquals(ReminderDraftPromptToken.REMINDER_READY_TO_CONTINUE_PROMPT, guidance.promptToken)
        assertEquals(ReminderDraftPromptTokenReason.READY_TO_CONTINUE, guidance.reason)
        assertNull(guidance.suggestedFocusTarget)
        assertFalse(guidance.shouldPromptForFieldInput)
    }
}
