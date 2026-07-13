package com.luistureo.voicereminderapp.core.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecoveryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, 0)
        val expectedUpdatedAt = intent.getLongExtra(EXTRA_EXPECTED_UPDATED_AT, Long.MIN_VALUE)
        if (reminderId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RecoveryRuntime.repository(context)
                val reminder = repository.getEnabledReminders().firstOrNull { it.id == reminderId }
                if (reminder == null || reminder.updatedAtEpochMillis != expectedUpdatedAt) {
                    RecoveryScheduler(context).cancel(reminderId)
                    RecoveryNotificationHelper(context).cancel(reminderId)
                    return@launch
                }
                val goal = repository.getGoal(reminder.goalId)
                if (goal == null || goal.status != RecoveryGoalStatus.ACTIVE) {
                    RecoveryScheduler(context).cancel(reminderId)
                    RecoveryNotificationHelper(context).cancel(reminderId)
                    return@launch
                }
                RecoveryNotificationHelper(context).show(reminder)
                RecoveryScheduler(context).scheduleNext(reminder)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_recovery_reminder_id"
        const val EXTRA_EXPECTED_UPDATED_AT = "extra_recovery_updated_at"
    }
}
