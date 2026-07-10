package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.data.local.entity.RoutineDailyExecutionEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineHistoryEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTaskEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateTaskEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineSuggestionEntity
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplateTask
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestion
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestionType
import java.time.LocalDate
import java.time.LocalTime

fun Routine.toEntity() = RoutineEntity(
    id = id,
    name = name,
    description = description,
    category = category,
    icon = icon,
    color = color,
    enabled = enabled,
    period = period.name,
    startTimeMinutes = startTime?.toStoredMinutes(),
    deadlineTimeMinutes = deadlineTime?.toStoredMinutes(),
    assistantMode = assistantMode.name,
    voiceEnabled = voiceEnabled,
    motivationBubbleEnabled = motivationBubbleEnabled,
    motivationScheduleMinutes = motivationSchedule?.toStoredMinutes(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun RoutineEntity.toDomain() = Routine(
    id = id,
    name = name,
    description = description,
    category = category,
    icon = icon,
    color = color,
    enabled = enabled,
    period = enumValueOrDefault(period, RoutinePeriod.MORNING),
    startTime = startTimeMinutes?.toLocalTime(),
    deadlineTime = deadlineTimeMinutes?.toLocalTime(),
    assistantMode = enumValueOrDefault(assistantMode, RoutineAssistantMode.SIMPLE_DISPLAY),
    voiceEnabled = voiceEnabled,
    motivationBubbleEnabled = motivationBubbleEnabled,
    motivationSchedule = motivationScheduleMinutes?.toLocalTime(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun RoutineTask.toEntity() = RoutineTaskEntity(
    id = id,
    routineId = routineId,
    title = title,
    description = description,
    orderPriority = orderPriority,
    completed = completed,
    completedOnEpochDay = completedOn?.toEpochDay(),
    optionalTimeMinutes = optionalTime?.toStoredMinutes(),
    estimatedDurationMinutes = estimatedDurationMinutes,
    notes = notes
)

fun RoutineTaskEntity.toDomain() = RoutineTask(
    id = id,
    routineId = routineId,
    title = title,
    description = description,
    orderPriority = orderPriority,
    completed = completed,
    completedOn = completedOnEpochDay?.let(LocalDate::ofEpochDay),
    optionalTime = optionalTimeMinutes?.toLocalTime(),
    estimatedDurationMinutes = estimatedDurationMinutes,
    notes = notes
)

fun RoutineHistory.toEntity() = RoutineHistoryEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    routineId = routineId,
    completedTasks = completedTasks,
    totalTasks = totalTasks,
    completionPercentage = completionPercentage,
    finalState = finalState.name,
    assistantGuidanceMode = assistantGuidanceMode?.name,
    periodAtExecution = periodAtExecution?.name,
    routineNameAtExecution = routineNameAtExecution,
    pendingTaskTitles = pendingTaskTitles.joinToString("\u001F"),
    completedAtEpochMillis = completedAtEpochMillis
)

fun RoutineHistoryEntity.toDomain() = RoutineHistory(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    routineId = routineId,
    completedTasks = completedTasks,
    totalTasks = totalTasks,
    completionPercentage = completionPercentage,
    finalState = enumValueOrDefault(finalState, RoutineExecutionState.NOT_COMPLETED),
    assistantGuidanceMode = assistantGuidanceMode?.let {
        enumValueOrDefault(it, RoutineAssistantMode.SIMPLE_DISPLAY)
    },
    periodAtExecution = periodAtExecution?.let {
        enumValueOrDefault(it, RoutinePeriod.MORNING)
    },
    routineNameAtExecution = routineNameAtExecution,
    pendingTaskTitles = pendingTaskTitles.split('\u001F').filter(String::isNotBlank),
    completedAtEpochMillis = completedAtEpochMillis
)

fun RoutineDailyExecution.toEntity() = RoutineDailyExecutionEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    routineId = routineId,
    state = state.name,
    updatedAtEpochMillis = updatedAtEpochMillis,
    assistantGuidanceMode = assistantGuidanceMode?.name
)

fun RoutineDailyExecutionEntity.toDomain() = RoutineDailyExecution(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    routineId = routineId,
    state = enumValueOrDefault(state, RoutineExecutionState.PENDING),
    updatedAtEpochMillis = updatedAtEpochMillis,
    assistantGuidanceMode = assistantGuidanceMode?.let {
        enumValueOrDefault(it, RoutineAssistantMode.SIMPLE_DISPLAY)
    }
)

fun RoutineTemplate.toEntity() = RoutineTemplateEntity(
    id = id,
    name = name,
    description = description,
    benefitsExplanation = benefitsExplanation,
    period = period.name,
    estimatedTotalDurationMinutes = estimatedTotalDurationMinutes,
    icon = icon,
    color = color,
    category = category,
    editable = editable,
    builtIn = builtIn,
    builtInKey = builtInKey
)

fun RoutineTemplateTask.toEntity() = RoutineTemplateTaskEntity(
    id = id,
    templateId = templateId,
    title = title,
    description = description,
    orderPriority = orderPriority,
    estimatedDurationMinutes = estimatedDurationMinutes
)

fun RoutineTemplateEntity.toDomain(tasks: List<RoutineTemplateTaskEntity>) = RoutineTemplate(
    id = id,
    name = name,
    description = description,
    benefitsExplanation = benefitsExplanation,
    suggestedTasks = tasks.sortedBy { it.orderPriority }.map { it.toDomain() },
    period = enumValueOrDefault(period, RoutinePeriod.MORNING),
    estimatedTotalDurationMinutes = estimatedTotalDurationMinutes,
    icon = icon,
    color = color,
    category = category,
    editable = editable,
    builtIn = builtIn,
    builtInKey = builtInKey
)

fun RoutineSuggestion.toEntity() = RoutineSuggestionEntity(
    id = id,
    routineId = routineId,
    type = type.name,
    message = message,
    primaryAction = primaryAction,
    secondaryAction = secondaryAction,
    createdAtEpochDay = createdAtEpochDay,
    dismissedAtEpochDay = dismissedAtEpochDay,
    active = active
)

fun RoutineSuggestionEntity.toDomain() = RoutineSuggestion(
    id = id,
    routineId = routineId,
    type = enumValueOrDefault(type, RoutineSuggestionType.LOW_COMPLETION),
    message = message,
    primaryAction = primaryAction,
    secondaryAction = secondaryAction,
    createdAtEpochDay = createdAtEpochDay,
    dismissedAtEpochDay = dismissedAtEpochDay,
    active = active
)

fun RoutineTemplateTaskEntity.toDomain() = RoutineTemplateTask(
    id = id,
    templateId = templateId,
    title = title,
    description = description,
    orderPriority = orderPriority,
    estimatedDurationMinutes = estimatedDurationMinutes
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

private fun LocalTime.toStoredMinutes(): Int = toSecondOfDay() / 60

private fun Int.toLocalTime(): LocalTime = LocalTime.ofSecondOfDay(toLong() * 60)
