package com.luistureo.voicereminderapp.domain.routine.usecase

import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.service.RoutineCompletionCalculator
import java.time.LocalDate

class CompleteRoutineDayUseCase(
    private val repository: RoutineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        routineId: Int,
        date: LocalDate,
        tasks: List<RoutineTask>
    ): RoutineHistory {
        val routine = repository.getRoutineById(routineId)
        val completedTasks = tasks.count { it.completed && it.completedOn == date }
        val finalState = RoutineCompletionCalculator.finalState(completedTasks, tasks.size)
        val history = RoutineHistory(
            date = date,
            routineId = routineId,
            completedTasks = completedTasks,
            totalTasks = tasks.size,
            completionPercentage = RoutineCompletionCalculator.percentage(
                completedTasks,
                tasks.size
            ),
            finalState = finalState,
            periodAtExecution = routine?.period,
            routineNameAtExecution = routine?.name,
            pendingTaskTitles = tasks.filterNot {
                it.completed && it.completedOn == date
            }.map { it.title },
            completedAtEpochMillis = nowProvider()
        )
        repository.finalizeDay(
            tasks = tasks,
            execution = RoutineDailyExecution(
                date = date,
                routineId = routineId,
                state = finalState,
                updatedAtEpochMillis = nowProvider()
            ),
            history = history
        )
        return history
    }
}
