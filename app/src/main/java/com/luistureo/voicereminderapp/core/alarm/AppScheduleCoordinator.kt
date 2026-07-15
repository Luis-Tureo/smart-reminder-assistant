package com.luistureo.voicereminderapp.core.alarm

import android.content.Context
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl

class AppScheduleCoordinator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun syncAll() {
        runCatching { syncReminders() }
    }

    private suspend fun syncReminders() {
        val repository = ReminderRepositoryImpl(
            ReminderDatabase.getDatabase(appContext).reminderDao()
        )
        val reminders = repository.getAllReminders().map { reminder ->
            ReminderAlarmRecoveryPolicy.recoverInitialSchedule(reminder).also { recovered ->
                if (recovered != reminder) {
                    repository.updateReminder(recovered)
                }
            }
        }
        ReminderScheduler(appContext).syncReminderSchedules(reminders)
    }
}
