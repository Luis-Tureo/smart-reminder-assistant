package com.luistureo.voicereminderapp.core.alarm

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderAlarmRecoveryPolicyTest {
    @Test
    fun recoversFutureInitialScheduleMissingAfterLegacyMigration() {
        val now = 1_800_000_000_000L
        val scheduledAt = now + 60_000L
        val reminder = reminder(scheduledAt)

        val recovered = ReminderAlarmRecoveryPolicy.recoverInitialSchedule(reminder, now)

        assertEquals(scheduledAt, recovered.scheduleState.nextTriggerAtEpochMillis)
    }

    @Test
    fun doesNotRestoreAlreadyTriggeredOneOffReminder() {
        val now = 1_800_000_000_000L
        val reminder = reminder(now - 60_000L).copy(
            scheduleState = ReminderScheduleState(lastTriggeredAtEpochMillis = now - 30_000L)
        )

        val recovered = ReminderAlarmRecoveryPolicy.recoverInitialSchedule(reminder, now)

        assertNull(recovered.scheduleState.nextTriggerAtEpochMillis)
        assertEquals(reminder, recovered)
    }

    @Test
    fun completedReminderIsNeverRecovered() {
        val now = 1_800_000_000_000L
        val recovered = ReminderAlarmRecoveryPolicy.recoverInitialSchedule(
            reminder(now + 60_000L).copy(isCompleted = true),
            now
        )

        assertNull(recovered.scheduleState.nextTriggerAtEpochMillis)
    }

    private fun reminder(scheduledAt: Long) = Reminder(
        title = "Control",
        detail = "Control medico",
        scheduledAtEpochMillis = scheduledAt
    )
}
