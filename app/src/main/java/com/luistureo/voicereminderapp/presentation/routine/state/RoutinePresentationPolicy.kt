package com.luistureo.voicereminderapp.presentation.routine.state

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import java.time.LocalDate

object RoutinePresentationPolicy {
    fun dashboardItem(
        routine: Routine,
        tasks: List<RoutineTask>,
        date: LocalDate
    ): RoutineDashboardItem {
        val completed = tasks.count { it.completed && it.completedOn == date }
        val percentage = if (tasks.isEmpty()) 0 else (completed * 100) / tasks.size
        return RoutineDashboardItem(routine, completed, tasks.size, percentage)
    }

    fun toggleTask(task: RoutineTask, completed: Boolean, date: LocalDate): RoutineTask =
        task.copy(
            completed = completed,
            completedOn = date.takeIf { completed }
        )

    fun activeActionsVisible(routine: Routine): Boolean = routine.enabled
}
