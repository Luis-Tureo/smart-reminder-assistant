package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSuspensionPolicyTest {

    @Test
    fun suspendOccurrenceKeepsOnlyOccurrenceMarkerAndPendingLinkedProviders() {
        val occurrenceAt = 1_800_000_000_000L
        val suspended = CalendarSuspensionPolicy.suspendOccurrence(
            reminder = reminder().copy(
                externalIdsByProvider = mapOf(CalendarProvider.GOOGLE_CALENDAR to "g-1")
            ),
            occurrenceAtEpochMillis = occurrenceAt
        )

        assertTrue(suspended.isSuspended)
        assertEquals(occurrenceAt, suspended.suspendedOccurrenceAtEpochMillis)
        assertTrue(CalendarProvider.GOOGLE_CALENDAR in suspended.pendingUpdateProviders)
    }

    @Test
    fun reactivateClearsSuspendedStateAndKeepsProviderUpdatePending() {
        val reactivated = CalendarSuspensionPolicy.reactivate(
            reminder().copy(
                isSuspended = true,
                suspendedOccurrenceAtEpochMillis = 1_800_000_000_000L,
                externalIdsByProvider = mapOf(CalendarProvider.MICROSOFT_CALENDAR to "m-1")
            )
        )

        assertFalse(reactivated.isSuspended)
        assertEquals(null, reactivated.suspendedOccurrenceAtEpochMillis)
        assertTrue(CalendarProvider.MICROSOFT_CALENDAR in reactivated.pendingUpdateProviders)
    }

    @Test
    fun recurringSuspensionSkipsOnlyConflictingOccurrence() {
        val occurrenceAt = 3_786_825_600_000L
        val suspended = CalendarSuspensionPolicy.suspendOccurrence(
            reminder = reminder().copy(
                scheduledAtEpochMillis = occurrenceAt,
                recurrence = ReminderRecurrence(
                    unit = ReminderRecurrenceUnit.DAY,
                    interval = 1
                )
            ),
            occurrenceAtEpochMillis = occurrenceAt
        )

        assertEquals(occurrenceAt, suspended.suspendedOccurrenceAtEpochMillis)
        assertTrue(suspended.scheduleState.nextTriggerAtEpochMillis!! > occurrenceAt)
    }

    private fun reminder(): Reminder {
        return Reminder(
            title = "Reunion",
            detail = "Reunion de equipo",
            scheduledAtEpochMillis = 1_800_000_000_000L
        )
    }
}
