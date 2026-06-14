package com.luistureo.voicereminderapp.presentation.assistant

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantConversationPolicyTest {

    @Test
    fun timeOnlyInputMustAskForDateBeforeSaving() {
        val draft = ReminderDraft(
            text = "esperando cita medica",
            time = "15:30"
        )

        val missingSlot = AssistantConversationPolicy.missingSlot(
            draft = draft,
            hasPendingAmbiguousTime = false
        )

        assertEquals(AssistantMissingSlot.DATE, missingSlot)
        assertEquals("\u00bfPara qu\u00e9 d\u00eda?", AssistantConversationPolicy.questionFor(missingSlot))
        assertFalse(
            AssistantConversationPolicy.isReadyToSave(
                draft = draft,
                hasPendingAmbiguousTime = false
            )
        )
    }

    @Test
    fun titleOnlyInputAsksForDateThenTime() {
        val titleOnly = ReminderDraft(text = "cita medica")
        val dateOnly = titleOnly.copy(date = "15/06/2026")

        assertEquals(
            AssistantMissingSlot.DATE,
            AssistantConversationPolicy.missingSlot(titleOnly, hasPendingAmbiguousTime = false)
        )
        assertEquals(
            AssistantMissingSlot.TIME,
            AssistantConversationPolicy.missingSlot(dateOnly, hasPendingAmbiguousTime = false)
        )
    }

    @Test
    fun completeInputWithoutAmbiguityIsReadyToSave() {
        val draft = ReminderDraft(
            text = "cita medica",
            date = "15/06/2026",
            time = "15:30"
        )

        assertEquals(
            AssistantMissingSlot.NONE,
            AssistantConversationPolicy.missingSlot(draft, hasPendingAmbiguousTime = false)
        )
        assertTrue(
            AssistantConversationPolicy.isReadyToSave(
                draft = draft,
                hasPendingAmbiguousTime = false
            )
        )
    }

    @Test
    fun ambiguousTimeMustBeClarifiedBeforeSaving() {
        val draft = ReminderDraft(
            text = "cita medica",
            date = "15/06/2026",
            time = "08:00"
        )

        val missingSlot = AssistantConversationPolicy.missingSlot(
            draft = draft,
            hasPendingAmbiguousTime = true
        )

        assertEquals(AssistantMissingSlot.AMBIGUOUS_TIME, missingSlot)
        assertFalse(
            AssistantConversationPolicy.isReadyToSave(
                draft = draft,
                hasPendingAmbiguousTime = true
            )
        )
    }
}
