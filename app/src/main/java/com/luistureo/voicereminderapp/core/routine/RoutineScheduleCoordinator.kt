package com.luistureo.voicereminderapp.core.routine

import android.content.Context
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import java.time.LocalDate

class RoutineScheduleCoordinator(private val context: Context) {
    suspend fun syncAll() {
        val repository = RoutineRepositoryImpl(
            ReminderDatabase.getDatabase(context.applicationContext).routineDao()
        )
        val scheduler = RoutineScheduler(context.applicationContext)
        val notifications = NotificationHelper(context.applicationContext)
        val preferenceStore = RoutinePreferenceStore(context.applicationContext)
        val today = LocalDate.now()
        val routines = repository.getRoutines()
        routines.forEach { routine ->
            val pendingClose = preferenceStore.getPendingDayClose(routine.id)
            if (pendingClose != null && pendingClose < today.toEpochDay()) {
                val result = ApplyRoutineExecutionActionUseCase(repository)(
                    routine.id,
                    LocalDate.ofEpochDay(pendingClose),
                    RoutineExecutionAction.NOT_COMPLETED
                )
                preferenceStore.clearPendingDayClose(routine.id, pendingClose)
                if (result?.applied == true) {
                    notifications.cancelRoutineNotifications(routine.id)
                }
            }
        }
        repository.prepareDay(today)
        routines.forEach { routine ->
            val state = repository.getDailyExecution(routine.id, today)?.state
                ?: RoutineExecutionState.PENDING
            if (routine.enabled) {
                scheduler.syncRoutine(routine, state)
                scheduler.restorePostpones(routine)
            } else {
                scheduler.cancelRoutine(routine.id)
                notifications.cancelRoutineNotifications(routine.id)
            }
        }
    }
}
