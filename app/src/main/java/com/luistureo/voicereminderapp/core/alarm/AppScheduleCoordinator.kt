package com.luistureo.voicereminderapp.core.alarm

import android.content.Context
import com.luistureo.voicereminderapp.core.loan.LoanReminderPolicy
import com.luistureo.voicereminderapp.core.loan.alarm.LoanReminderScheduler
import com.luistureo.voicereminderapp.core.routine.RoutineScheduleCoordinator
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.LoanRepositoryImpl
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl

class AppScheduleCoordinator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun syncAll() {
        runCatching { syncReminders() }
        runCatching { syncLoans() }
        runCatching { RoutineScheduleCoordinator(appContext).syncAll() }
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

    private suspend fun syncLoans() {
        val repository = LoanRepositoryImpl(
            ReminderDatabase.getDatabase(appContext).loanDao()
        )
        val scheduler = LoanReminderScheduler(appContext)
        repository.getLoans().forEach { loan ->
            if (LoanReminderPolicy.shouldSchedule(loan)) {
                scheduler.scheduleLoanReminders(loan)
            } else {
                scheduler.cancelLoanReminders(loan.id)
            }
        }
    }
}
