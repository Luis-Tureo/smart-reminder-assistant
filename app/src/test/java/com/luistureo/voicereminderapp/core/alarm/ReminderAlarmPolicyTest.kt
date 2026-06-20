package com.luistureo.voicereminderapp.core.alarm

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAlarmPolicyTest {

    @Test
    fun suspendedAppointmentDoesNotSchedulePrimaryAlarm() {
        val triggerAt = 1_800_000_000_000L

        assertFalse(
            ReminderAlarmPolicy.shouldSchedulePrimaryReminder(
                reminder(triggerAt).copy(
                    isSuspended = true,
                    suspendedOccurrenceAtEpochMillis = triggerAt
                )
            )
        )
    }

    @Test
    fun reactivatedAppointmentSchedulesPrimaryAlarmAgain() {
        val triggerAt = 1_800_000_000_000L

        assertTrue(ReminderAlarmPolicy.shouldSchedulePrimaryReminder(reminder(triggerAt)))
    }

    @Test
    fun suspendedAppointmentDoesNotScheduleUrgentRepeat() {
        val triggerAt = 1_800_000_000_000L
        val suspended = reminder(triggerAt).copy(
            isUrgent = true,
            isSuspended = true,
            suspendedOccurrenceAtEpochMillis = triggerAt,
            scheduleState = ReminderScheduleState(
                activeAlertAtEpochMillis = triggerAt,
                activeAlertRepeatCount = 1,
                nextUrgentRepeatAtEpochMillis = triggerAt + 15_000L
            )
        )

        assertFalse(ReminderAlarmPolicy.shouldScheduleUrgentRepeat(suspended))
    }

    private fun reminder(triggerAt: Long): Reminder {
        return Reminder(
            title = "Reunion",
            detail = "Reunion de equipo",
            scheduledAtEpochMillis = triggerAt,
            scheduleState = ReminderScheduleState(nextTriggerAtEpochMillis = triggerAt)
        )
    }
}
