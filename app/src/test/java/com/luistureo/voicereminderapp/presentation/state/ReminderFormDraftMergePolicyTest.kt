package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderFormDraftMergePolicyTest {

    @Test
    fun selectedCalendarDateIsUsedOnlyWhenDraftHasNoDate() {
        val currentState = ReminderFormState(
            date = "13/06/2026",
            source = ReminderSource.MANUAL
        )
        val draft = ReminderDraft(
            text = "cita medica",
            time = "15:30",
            source = ReminderSource.CAMERA
        )

        val result = ReminderFormDraftMergePolicy.merge(
            currentFormState = currentState,
            draft = draft,
            source = ReminderSource.CAMERA
        )

        assertEquals("13/06/2026", result.date)
        assertEquals("15:30", result.time)
        assertEquals("cita medica", result.detail)
    }

    @Test
    fun explicitDraftDateOverridesSelectedCalendarDate() {
        val currentState = ReminderFormState(
            date = "13/06/2026",
            source = ReminderSource.MANUAL
        )
        val draft = ReminderDraft(
            text = "examen",
            date = "15/06/2026",
            time = "08:00",
            source = ReminderSource.CAMERA
        )

        val result = ReminderFormDraftMergePolicy.merge(
            currentFormState = currentState,
            draft = draft,
            source = ReminderSource.CAMERA
        )

        assertEquals("15/06/2026", result.date)
        assertEquals("08:00", result.time)
        assertEquals("examen", result.detail)
    }
}
