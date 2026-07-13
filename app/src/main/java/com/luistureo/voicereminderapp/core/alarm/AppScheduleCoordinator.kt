package com.luistureo.voicereminderapp.core.alarm

import android.content.Context
import com.luistureo.voicereminderapp.core.loan.LoanReminderPolicy
import com.luistureo.voicereminderapp.core.loan.alarm.LoanReminderScheduler
import com.luistureo.voicereminderapp.core.nutrition.NutritionScheduler
import com.luistureo.voicereminderapp.core.recovery.RecoveryRuntime
import com.luistureo.voicereminderapp.core.recovery.RecoveryScheduler
import com.luistureo.voicereminderapp.core.routine.RoutineScheduleCoordinator
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.LoanRepositoryImpl
import com.luistureo.voicereminderapp.data.repository.NutritionRepositoryProvider
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus

class AppScheduleCoordinator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun syncAll() {
        runCatching { syncReminders() }
        runCatching { syncLoans() }
        runCatching { RoutineScheduleCoordinator(appContext).syncAll() }
        runCatching { syncNutrition() }
        runCatching { syncRecovery() }
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

    private suspend fun syncNutrition() {
        val repository = NutritionRepositoryProvider.from(appContext)
        NutritionScheduler(appContext).syncAll(repository)
    }

    private suspend fun syncRecovery() {
        val repository = RecoveryRuntime.repository(appContext)
        val scheduler = RecoveryScheduler(appContext)
        repository.getEnabledReminders().forEach { reminder ->
            if (repository.getGoal(reminder.goalId)?.status == RecoveryGoalStatus.ACTIVE) {
                scheduler.scheduleNext(reminder)
            } else {
                scheduler.cancel(reminder.id)
            }
        }
    }
}
