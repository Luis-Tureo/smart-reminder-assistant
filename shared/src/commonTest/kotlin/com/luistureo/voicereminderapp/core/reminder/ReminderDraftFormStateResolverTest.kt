package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderDraftFormStateResolverTest {

    @Test
    fun resolve_marksBlankDraftAsMissingRequiredInfo() {
        val formState = ReminderDraftFormStateResolver.resolve(ReminderDraft())

        assertTrue(formState.hasMissingText)
        assertTrue(formState.hasMissingDate)
        assertTrue(formState.hasMissingTime)
        assertTrue(formState.hasMissingRequiredInfo)
        assertFalse(formState.hasUsableDraftData)
        assertFalse(formState.isReadyToConfirm)
        assertFalse(formState.isReadyToSave)
    }

    @Test
    fun resolve_marksTextOnlyDraftAsReadyToConfirm() {
        val draft = ReminderDraft(text = "Pagar cuentas")
        val formState = ReminderDraftFormStateResolver.resolve(draft)

        assertTrue(formState.hasMissingRequiredInfo)
        assertTrue(formState.hasUsableDraftData)
        assertTrue(formState.isReadyToConfirm)
        assertFalse(formState.isReadyForDownstreamLogic)
        assertFalse(formState.isReadyToSave)
        assertFalse(draft.isReadyToSave())
    }

    @Test
    fun resolve_marksScheduleOnlyDraftAsReadyToConfirm() {
        val formState = ReminderDraftFormStateResolver.resolve(
            textValue = null,
            dateValue = "05/03/2026",
            timeValue = "09:07"
        )

        assertTrue(formState.hasMissingText)
        assertTrue(formState.hasUsableDraftData)
        assertTrue(formState.isReadyToConfirm)
        assertFalse(formState.isReadyToSave)
    }

    @Test
    fun resolve_marksIncompleteScheduleAsBlocked() {
        val formState = ReminderDraftFormStateResolver.resolve(
            textValue = "Pagar cuentas",
            dateValue = "05/03/",
            timeValue = "09:07"
        )

        assertTrue(formState.hasIncompleteField)
        assertFalse(formState.hasInvalidField)
        assertFalse(formState.isReadyToConfirm)
        assertFalse(formState.isReadyToSave)
    }

    @Test
    fun resolve_marksInvalidScheduleAsBlocked() {
        val formState = ReminderDraftFormStateResolver.resolve(
            textValue = "Pagar cuentas",
            dateValue = "31/04/2026",
            timeValue = "09:07"
        )

        assertFalse(formState.hasIncompleteField)
        assertTrue(formState.hasInvalidField)
        assertFalse(formState.isReadyToConfirm)
        assertFalse(formState.isReadyToSave)
    }

    @Test
    fun resolve_marksCompleteDraftAsReadyToSave() {
        val draft = ReminderDraft(
            text = "Pagar cuentas",
            date = "05/03/2026",
            time = "09:07"
        )
        val formState = ReminderDraftFormStateResolver.resolve(draft)

        assertFalse(formState.hasMissingRequiredInfo)
        assertFalse(formState.hasIncompleteField)
        assertFalse(formState.hasInvalidField)
        assertTrue(formState.isReadyForDownstreamLogic)
        assertFalse(formState.isReadyToConfirm)
        assertTrue(formState.isReadyToSave)
        assertTrue(draft.isReadyToSave())
    }
}
