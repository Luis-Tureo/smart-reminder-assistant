package com.luistureo.voicereminderapp.domain.routine.usecase

import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionResult
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.service.RoutineCompletionCalculator
import com.luistureo.voicereminderapp.domain.routine.service.RoutineExecutionTransitionPolicy
import java.time.LocalDate

class ApplyRoutineExecutionActionUseCase(
    private val repository: RoutineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(
        routineId: Int,
        date: LocalDate,
        action: RoutineExecutionAction,
        assistantGuidanceMode: RoutineAssistantMode? = null
    ): RoutineExecutionResult? = repository.updateExecutionAtomically(
        routineId = routineId,
        date = date
    ) { routine, tasks, storedExecution ->
        val current = storedExecution ?: RoutineDailyExecution(
            date = date,
            routineId = routineId,
            state = RoutineExecutionState.PENDING,
            updatedAtEpochMillis = nowProvider()
        )
        if (!routine.enabled) {
            return@updateExecutionAtomically RoutineExecutionResult(
                routine,
                tasks,
                current,
                null,
                applied = false
            )
        }
        val targetState = RoutineExecutionTransitionPolicy.targetState(current.state, action)
            ?: current.state.takeIf {
                action == RoutineExecutionAction.START &&
                    current.state == RoutineExecutionState.IN_PROGRESS &&
                    assistantGuidanceMode != null &&
                    current.assistantGuidanceMode != assistantGuidanceMode
            }
            ?: return@updateExecutionAtomically RoutineExecutionResult(
                routine,
                tasks,
                current,
                null,
                applied = false
            )
        val updatedTasks = if (action == RoutineExecutionAction.COMPLETE) {
            tasks.map { it.copy(completed = true, completedOn = date) }
        } else {
            tasks
        }
        val completedTasks = updatedTasks.count { it.completed && it.completedOn == date }
        val execution = current.copy(
            state = targetState,
            updatedAtEpochMillis = nowProvider(),
            assistantGuidanceMode = assistantGuidanceMode ?: current.assistantGuidanceMode
        )
        val history = if (RoutineExecutionTransitionPolicy.isTerminal(targetState)) {
            RoutineHistory(
                date = date,
                routineId = routineId,
                completedTasks = completedTasks,
                totalTasks = updatedTasks.size,
                completionPercentage = RoutineCompletionCalculator.percentage(
                    completedTasks,
                    updatedTasks.size
                ),
                finalState = targetState,
                assistantGuidanceMode = execution.assistantGuidanceMode,
                periodAtExecution = routine.period,
                routineNameAtExecution = routine.name,
                pendingTaskTitles = updatedTasks.filterNot {
                    it.completed && it.completedOn == date
                }.map { it.title },
                completedAtEpochMillis = nowProvider()
            )
        } else {
            null
        }
        RoutineExecutionResult(
            routine = routine,
            tasks = updatedTasks,
            execution = execution,
            history = history,
            applied = current.state != targetState || updatedTasks != tasks
        )
    }
}
