package com.luistureo.voicereminderapp.core.routine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutineActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getIntExtra(EXTRA_ROUTINE_ID, 0)
        val action = intent.getStringExtra(EXTRA_EXECUTION_ACTION)
            ?.let { runCatching { RoutineExecutionAction.valueOf(it) }.getOrNull() }
            ?: intent.getStringExtra(EXTRA_NOTIFICATION_ACTION)
                ?.let(::notificationActionToExecutionAction)
        val dateEpochDay = intent.getLongExtra(EXTRA_DATE_EPOCH_DAY, Long.MIN_VALUE)
        if (routineId <= 0 || action == null) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                val notifications = NotificationHelper(context.applicationContext)
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (dateEpochDay != today.toEpochDay()) {
                    if (notificationId != 0) notifications.cancelNotification(notificationId)
                    return@launch
                }
                val repository = RoutineRepositoryImpl(
                    ReminderDatabase.getDatabase(context.applicationContext).routineDao()
                )
                val result = ApplyRoutineExecutionActionUseCase(repository)(
                    routineId,
                    today,
                    action
                ) ?: return@launch
                if (notificationId != 0) notifications.cancelNotification(notificationId)
                if (!result.applied) return@launch
                val scheduler = RoutineScheduler(context.applicationContext)
                if (result.history != null) {
                    scheduler.cancelRoutine(routineId)
                    notifications.cancelRoutineNotifications(routineId)
                }
                scheduler.syncRoutine(result.routine, result.execution.state)
                RoutineMotivationCoordinator(context.applicationContext).deliver(
                    result.routine,
                    result.execution.state,
                    today,
                    allowVoice = false
                )
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notificationActionToExecutionAction(value: String): RoutineExecutionAction? =
        when (runCatching { RoutineNotificationAction.valueOf(value) }.getOrNull()) {
            RoutineNotificationAction.START -> RoutineExecutionAction.START
            RoutineNotificationAction.COMPLETE -> RoutineExecutionAction.COMPLETE
            RoutineNotificationAction.PARTIAL -> RoutineExecutionAction.PARTIAL
            else -> null
        }

    companion object {
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val EXTRA_DATE_EPOCH_DAY = "extra_routine_date_epoch_day"
        const val EXTRA_NOTIFICATION_ACTION = "extra_routine_notification_action"
        const val EXTRA_EXECUTION_ACTION = "extra_routine_execution_action"
        const val EXTRA_NOTIFICATION_ID = "extra_routine_notification_id"
    }
}
