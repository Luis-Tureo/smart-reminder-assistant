package com.luistureo.voicereminderapp.presentation.routine.state

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate

data class RoutineDashboardItem(
    val routine: Routine,
    val completedTasks: Int,
    val totalTasks: Int,
    val percentage: Int
)

data class RoutineUiState(
    val dashboardItems: List<RoutineDashboardItem> = emptyList(),
    val selectedRoutine: Routine? = null,
    val tasks: List<RoutineTask> = emptyList(),
    val templates: List<RoutineTemplate> = emptyList(),
    val selectedTemplate: RoutineTemplate? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val showCompletionFeedback: Boolean = false
)
