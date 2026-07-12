package com.luistureo.voicereminderapp.core.alarm

import com.luistureo.voicereminderapp.core.reminder.ReminderScheduleStateResolver
import com.luistureo.voicereminderapp.domain.model.Reminder

object ReminderAlarmRecoveryPolicy {
    fun recoverInitialSchedule(
        reminder: Reminder,
        nowEpochMillis: Long = System.currentTimeMillis(),
        resolver: ReminderScheduleStateResolver = ReminderScheduleStateResolver()
    ): Reminder {
        if (!shouldRecoverInitialSchedule(reminder)) return reminder
        return reminder.copy(
            scheduleState = resolver.resolveOnSave(reminder, nowEpochMillis)
        )
    }

    fun shouldRecoverInitialSchedule(reminder: Reminder): Boolean {
        return !reminder.isCompleted &&
            reminder.scheduledAtEpochMillis > 0L &&
            reminder.scheduleState.nextTriggerAtEpochMillis == null &&
            reminder.scheduleState.lastTriggeredAtEpochMillis == null
    }
}
