package com.luistureo.voicereminderapp.domain.routine.usecase

import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionResult
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.service.RoutineCompletionCalculator
import java.time.LocalDate

class UpdateRoutineTaskProgressUseCase(
    private val repository: RoutineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        task: RoutineTask,
        completed: Boolean,
        date: LocalDate,
        assistantGuidanceMode: RoutineAssistantMode? = null
    ): RoutineExecutionResult? = repository.updateExecutionAtomically(
        routineId = task.routineId,
        date = date
    ) operation@{ routine, storedTasks, current ->
        if (!routine.enabled) return@operation null
        val persistedTask = storedTasks.firstOrNull { it.id == task.id } ?: return@operation null
        val updatedTask = persistedTask.copy(
            completed = completed,
            completedOn = date.takeIf { completed }
        )
        val tasks = storedTasks.map { storedTask ->
            if (storedTask.id == task.id) updatedTask else storedTask
        }
        val completedTasks = tasks.count { it.completed && it.completedOn == date }
        val state = when {
            tasks.isNotEmpty() && completedTasks == tasks.size -> RoutineExecutionState.COMPLETED
            completedTasks > 0 -> RoutineExecutionState.IN_PROGRESS
            current != null && current.state != RoutineExecutionState.PENDING ->
                RoutineExecutionState.IN_PROGRESS
            else -> RoutineExecutionState.PENDING
        }
        val execution = RoutineDailyExecution(
            id = current?.id ?: 0,
            date = date,
            routineId = task.routineId,
            state = state,
            updatedAtEpochMillis = nowProvider(),
            assistantGuidanceMode = assistantGuidanceMode ?: current?.assistantGuidanceMode
        )
        val history = if (state == RoutineExecutionState.COMPLETED) {
            RoutineHistory(
                date = date,
                routineId = task.routineId,
                completedTasks = completedTasks,
                totalTasks = tasks.size,
                completionPercentage = RoutineCompletionCalculator.percentage(
                    completedTasks,
                    tasks.size
                ),
                finalState = state,
                assistantGuidanceMode = execution.assistantGuidanceMode,
                periodAtExecution = routine.period,
                routineNameAtExecution = routine.name,
                pendingTaskTitles = tasks.filterNot {
                    it.completed && it.completedOn == date
                }.map { it.title },
                completedAtEpochMillis = nowProvider()
            )
        } else {
            null
        }
        RoutineExecutionResult(
            routine = routine,
            tasks = tasks,
            execution = execution,
            history = history,
            applied = true
        )
    }
}
