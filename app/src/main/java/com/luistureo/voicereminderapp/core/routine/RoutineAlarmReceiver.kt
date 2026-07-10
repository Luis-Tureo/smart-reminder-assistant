package com.luistureo.voicereminderapp.core.routine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getIntExtra(EXTRA_ROUTINE_ID, 0)
        val alarmType = intent.getStringExtra(EXTRA_ALARM_TYPE)
            ?.let { runCatching { RoutineAlarmType.valueOf(it) }.getOrNull() }
        if (routineId <= 0 || alarmType == null) return
        val scheduledEpochDay = intent.getLongExtra(EXTRA_SCHEDULED_EPOCH_DAY, Long.MIN_VALUE)
        val expectedUpdatedAt = intent.getLongExtra(EXTRA_ROUTINE_UPDATED_AT, Long.MIN_VALUE)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(
                    context.applicationContext,
                    routineId,
                    alarmType,
                    scheduledEpochDay,
                    expectedUpdatedAt
                )
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        routineId: Int,
        alarmType: RoutineAlarmType,
        scheduledEpochDay: Long,
        expectedUpdatedAt: Long
    ) {
        val repository = RoutineRepositoryImpl(ReminderDatabase.getDatabase(context).routineDao())
        val scheduler = RoutineScheduler(context)
        val notifications = NotificationHelper(context)
        val routine = repository.getRoutineById(routineId)
        if (routine == null || !routine.enabled) {
            scheduler.cancelRoutine(routineId)
            notifications.cancelRoutineNotifications(routineId)
            return
        }
        val today = LocalDate.now()
        if (expectedUpdatedAt != routine.updatedAtEpochMillis) {
            syncCurrentDay(repository, scheduler, routine.id, today)
            return
        }
        if (alarmType == RoutineAlarmType.DAY_CLOSE) {
            val targetDate = RoutineAlarmDeliveryPolicy.dayCloseTargetDate(
                scheduledEpochDay,
                today
            ) ?: return
            val result = ApplyRoutineExecutionActionUseCase(repository)(
                routineId,
                targetDate,
                RoutineExecutionAction.NOT_COMPLETED
            )
            scheduler.markDayCloseDelivered(routineId, scheduledEpochDay)
            if (result?.applied == true) {
                notifications.cancelRoutineNotifications(routineId)
            }
            syncCurrentDay(repository, scheduler, routine.id, today)
            return
        }
        val targetDate = RoutineAlarmDeliveryPolicy.visibleTargetDate(
            scheduledEpochDay,
            today
        )
        if (targetDate == null) {
            syncCurrentDay(repository, scheduler, routine.id, today)
            return
        }
        repository.prepareDay(today)
        val existingExecution = repository.getDailyExecution(routineId, today)
        val execution = existingExecution ?: RoutineDailyExecution(
                date = today,
                routineId = routineId,
                state = RoutineExecutionState.PENDING,
                updatedAtEpochMillis = System.currentTimeMillis()
            ).also { repository.saveDailyExecution(it) }
        if (alarmType.isSnoozed) scheduler.markPostponeDelivered(routineId, alarmType)
        val tasks = repository.getTasks(routineId)
        val completed = tasks.count { it.completed && it.completedOn == today }
        val remaining = (tasks.size - completed).coerceAtLeast(0)
        val isTerminal = execution.state in setOf(
            RoutineExecutionState.COMPLETED,
            RoutineExecutionState.PARTIALLY_COMPLETED,
            RoutineExecutionState.SKIPPED,
            RoutineExecutionState.NOT_COMPLETED
        )
        val plan = when (alarmType.baseType) {
            RoutineAlarmType.START -> {
                if (execution.state == RoutineExecutionState.PENDING) {
                    RoutineNotificationPlanFactory.start(
                        routine,
                        RoutinePreferenceStore(context).getPreferredName()
                    )
                } else null
            }
            RoutineAlarmType.DEADLINE -> {
                if (!isTerminal && remaining > 0) {
                    RoutineNotificationPlanFactory.deadline(routine, remaining)
                } else null
            }
            RoutineAlarmType.PENDING_TASKS -> {
                if (!isTerminal && routine.motivationBubbleEnabled && remaining > 0) {
                    RoutineNotificationPlanFactory.pending(
                        routine,
                        remaining,
                        execution.state
                    )
                } else null
            }
            else -> null
        }
        if (plan != null) {
            notifications.showRoutineNotification(
                plan = plan,
                routineId = routineId,
                dateEpochDay = targetDate.toEpochDay(),
                alarmType = alarmType.baseType
            )
        }
        if (!alarmType.isSnoozed) scheduler.syncRoutine(routine, execution.state)
    }

    private suspend fun syncCurrentDay(
        repository: RoutineRepository,
        scheduler: RoutineScheduler,
        routineId: Int,
        today: LocalDate
    ) {
        repository.prepareDay(today)
        val routine = repository.getRoutineById(routineId) ?: return
        val state = repository.getDailyExecution(routineId, today)?.state
            ?: RoutineExecutionState.PENDING
        scheduler.syncRoutine(routine, state)
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val EXTRA_ALARM_TYPE = "extra_routine_alarm_type"
        const val EXTRA_SCHEDULED_EPOCH_DAY = "extra_scheduled_epoch_day"
        const val EXTRA_ROUTINE_UPDATED_AT = "extra_routine_updated_at"
    }
}
