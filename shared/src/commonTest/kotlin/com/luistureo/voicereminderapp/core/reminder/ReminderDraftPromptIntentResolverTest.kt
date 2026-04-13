package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderDraftPromptIntentResolverTest {

    @Test
    fun resolve_mapsBlankDraftToAskReminderText() {
        val guidance = ReminderDraftPromptIntentResolver.resolve(ReminderDraft())

        assertEquals(ReminderDraftPromptIntent.ASK_REMINDER_TEXT, guidance.promptIntent)
        assertEquals(ReminderDraftField.TEXT, guidance.suggestedFocusTarget)
        assertFalse(guidance.shouldShowConfirmation)
        assertFalse(guidance.shouldAllowSaveOrContinue)
    }

    @Test
    fun resolve_mapsTextOnlyDraftToAskReminderDate() {
        val guidance = ReminderDraftPromptIntentResolver.resolve(
            ReminderDraft(text = "Pagar cuentas")
        )

        assertEquals(ReminderDraftPromptIntent.ASK_REMINDER_DATE, guidance.promptIntent)
        assertEquals(ReminderDraftField.DATE, guidance.suggestedFocusTarget)
        assertFalse(guidance.shouldShowConfirmation)
        assertFalse(guidance.shouldAllowSaveOrContinue)
    }

    @Test
    fun resolve_mapsIncompleteDateToCorrectionIntent() {
        val guidance = ReminderDraftPromptIntentResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/",
                time = "09:07"
            )
        )

        assertEquals(ReminderDraftPromptIntent.CORRECT_INCOMPLETE_DATE, guidance.promptIntent)
        assertEquals(ReminderDraftField.DATE, guidance.suggestedFocusTarget)
        assertFalse(guidance.shouldShowConfirmation)
        assertFalse(guidance.shouldAllowSaveOrContinue)
    }

    @Test
    fun resolve_mapsInvalidTimeToCorrectionIntent() {
        val guidance = ReminderDraftPromptIntentResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/2026",
                time = "24:00"
            )
        )

        assertEquals(ReminderDraftPromptIntent.CORRECT_INVALID_TIME, guidance.promptIntent)
        assertEquals(ReminderDraftField.TIME, guidance.suggestedFocusTarget)
        assertFalse(guidance.shouldShowConfirmation)
        assertFalse(guidance.shouldAllowSaveOrContinue)
    }

    @Test
    fun resolve_mapsSemanticConfirmationToShowConfirmationIntent() {
        val guidance = ReminderDraftPromptIntentResolver.resolve(
            ReminderDraftSemanticActionGuidance(
                nextAction = ReminderDraftSemanticAction.CONFIRM_DRAFT,
                field = null,
                canConfirmDraft = true,
                canSaveOrContinue = false
            )
        )

        assertEquals(ReminderDraftPromptIntent.SHOW_CONFIRMATION, guidance.promptIntent)
        assertNull(guidance.suggestedFocusTarget)
        assertTrue(guidance.shouldShowConfirmation)
        assertFalse(guidance.shouldAllowSaveOrContinue)
    }

    @Test
    fun resolve_mapsCompleteDraftToAllowSaveOrContinueIntent() {
        val guidance = ReminderDraftPromptIntentResolver.resolve(
            ReminderDraft(
                text = "Pagar cuentas",
                date = "05/03/2026",
                time = "09:07"
            )
        )

        assertEquals(ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE, guidance.promptIntent)
        assertNull(guidance.suggestedFocusTarget)
        assertFalse(guidance.shouldShowConfirmation)
        assertTrue(guidance.shouldAllowSaveOrContinue)
    }
}
