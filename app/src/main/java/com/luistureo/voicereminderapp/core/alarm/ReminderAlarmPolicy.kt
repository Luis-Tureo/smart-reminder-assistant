package com.luistureo.voicereminderapp.core.alarm

import com.luistureo.voicereminderapp.domain.model.Reminder

object ReminderAlarmPolicy {
    fun shouldSchedulePrimaryReminder(reminder: Reminder): Boolean {
        val nextTriggerAt = reminder.scheduleState.nextTriggerAtEpochMillis ?: return false
        return !reminder.isCompleted && !isSuspendedOccurrence(reminder, nextTriggerAt)
    }

    fun shouldScheduleUrgentRepeat(reminder: Reminder): Boolean {
        val activeAlertAt = reminder.scheduleState.activeAlertAtEpochMillis ?: return false
        return reminder.isUrgent &&
                !reminder.isCompleted &&
                reminder.scheduleState.nextUrgentRepeatAtEpochMillis != null &&
                !isSuspendedOccurrence(reminder, activeAlertAt)
    }

    fun isSuspendedOccurrence(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long
    ): Boolean {
        if (!reminder.isSuspended) return false
        val suspendedOccurrence = reminder.suspendedOccurrenceAtEpochMillis
        return suspendedOccurrence == null || suspendedOccurrence == occurrenceAtEpochMillis
    }
}
