package com.luistureo.voicereminderapp.domain.routine.model

import java.time.LocalDate
import java.time.LocalTime

enum class RoutinePeriod {
    MORNING,
    AFTERNOON,
    NIGHT
}

enum class RoutineAssistantMode {
    SIMPLE_DISPLAY,
    STEP_BY_STEP_GUIDE,
    SMART_GUIDE
}

enum class RoutineExecutionState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    PARTIALLY_COMPLETED,
    SKIPPED,
    NOT_COMPLETED
}

enum class RoutineExecutionAction {
    START,
    COMPLETE,
    PARTIAL,
    SKIP,
    NOT_COMPLETED
}

data class Routine(
    val id: Int = 0,
    val name: String,
    val description: String,
    val category: String,
    val icon: String,
    val color: Int,
    val enabled: Boolean,
    val period: RoutinePeriod,
    val startTime: LocalTime? = null,
    val deadlineTime: LocalTime? = null,
    val assistantMode: RoutineAssistantMode = RoutineAssistantMode.SIMPLE_DISPLAY,
    val voiceEnabled: Boolean = false,
    val motivationBubbleEnabled: Boolean = true,
    val motivationSchedule: LocalTime? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

data class RoutineTask(
    val id: Int = 0,
    val routineId: Int = 0,
    val title: String,
    val description: String? = null,
    val orderPriority: Int,
    val completed: Boolean = false,
    val completedOn: LocalDate? = null,
    val optionalTime: LocalTime? = null,
    val estimatedDurationMinutes: Int? = null,
    val notes: String? = null
)

data class RoutineHistory(
    val id: Int = 0,
    val date: LocalDate,
    val routineId: Int,
    val completedTasks: Int,
    val totalTasks: Int,
    val completionPercentage: Double,
    val finalState: RoutineExecutionState,
    val assistantGuidanceMode: RoutineAssistantMode? = null,
    val periodAtExecution: RoutinePeriod? = null,
    val routineNameAtExecution: String? = null,
    val pendingTaskTitles: List<String> = emptyList(),
    val completedAtEpochMillis: Long? = null
)

data class RoutineDailyExecution(
    val id: Int = 0,
    val date: LocalDate,
    val routineId: Int,
    val state: RoutineExecutionState,
    val updatedAtEpochMillis: Long,
    val assistantGuidanceMode: RoutineAssistantMode? = null
)

data class RoutineTemplate(
    val id: Int = 0,
    val name: String,
    val description: String,
    val benefitsExplanation: String,
    val suggestedTasks: List<RoutineTemplateTask>,
    val period: RoutinePeriod = RoutinePeriod.MORNING,
    val estimatedTotalDurationMinutes: Int = 0,
    val icon: String? = null,
    val color: Int? = null,
    val category: String = "Organización",
    val editable: Boolean = true,
    val builtIn: Boolean = true,
    val builtInKey: String? = null
)

data class RoutineTemplateTask(
    val id: Int = 0,
    val templateId: Int = 0,
    val title: String,
    val description: String? = null,
    val orderPriority: Int,
    val estimatedDurationMinutes: Int? = null
)

data class RoutineDailyProgress(
    val date: LocalDate,
    val state: RoutineExecutionState = RoutineExecutionState.PENDING,
    val completedTaskIds: Set<Int> = emptySet()
)

data class RoutineExecutionResult(
    val routine: Routine,
    val tasks: List<RoutineTask>,
    val execution: RoutineDailyExecution,
    val history: RoutineHistory?,
    val applied: Boolean
)

fun List<RoutineTask>.ordered(): List<RoutineTask> =
    sortedWith(compareBy<RoutineTask> { it.orderPriority }.thenBy { it.id })
