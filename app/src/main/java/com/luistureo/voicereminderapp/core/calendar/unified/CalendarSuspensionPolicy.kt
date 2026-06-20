package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.core.reminder.ReminderScheduleStateResolver

object CalendarSuspensionPolicy {
    const val SUSPENDED_DETAIL_NOTE = "Cita suspendida desde Smart Reminder Assistant"

    fun suspendOccurrence(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long?
    ): Reminder {
        val suspendedReminder = reminder.copy(
            isSuspended = true,
            suspendedOccurrenceAtEpochMillis = occurrenceAtEpochMillis,
            pendingUpdateProviders = reminder.pendingUpdateProviders +
                    reminder.linkedExternalProviders
        )
        return suspendedReminder.copy(
            scheduleState = ReminderScheduleStateResolver().resolveOnSave(suspendedReminder)
        )
    }

    fun reactivate(reminder: Reminder): Reminder {
        val reactivatedReminder = reminder.copy(
            isSuspended = false,
            suspendedOccurrenceAtEpochMillis = null,
            pendingUpdateProviders = reminder.pendingUpdateProviders +
                    reminder.linkedExternalProviders
        )
        return reactivatedReminder.copy(
            scheduleState = ReminderScheduleStateResolver().resolveOnSave(reactivatedReminder)
        )
    }
}
