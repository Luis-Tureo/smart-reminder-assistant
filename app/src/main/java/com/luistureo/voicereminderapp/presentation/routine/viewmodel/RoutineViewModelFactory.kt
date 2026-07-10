package com.luistureo.voicereminderapp.presentation.routine.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.routine.RoutineMotivationCoordinator
import com.luistureo.voicereminderapp.core.routine.RoutineScheduleCoordinator
import com.luistureo.voicereminderapp.core.routine.RoutineScheduler
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.InitializeDailyRoutinesUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.PrepareRoutineDayUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.SaveRoutineUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.UpdateRoutineTaskProgressUseCase

class RoutineViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = RoutineRepositoryImpl(
            ReminderDatabase.getDatabase(appContext).routineDao()
        )
        return RoutineViewModel(
            repository = repository,
            initializeDailyRoutines = InitializeDailyRoutinesUseCase(repository),
            prepareRoutineDay = PrepareRoutineDayUseCase(repository),
            saveRoutineUseCase = SaveRoutineUseCase(repository),
            applyExecutionAction = ApplyRoutineExecutionActionUseCase(repository),
            updateTaskProgress = UpdateRoutineTaskProgressUseCase(repository),
            scheduler = RoutineScheduler(appContext),
            notifications = NotificationHelper(appContext),
            scheduleCoordinator = RoutineScheduleCoordinator(appContext),
            motivationCoordinator = RoutineMotivationCoordinator(appContext)
        ) as T
    }
}
