package com.luistureo.voicereminderapp.domain.routine.service

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyProgress
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestion
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestionType
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import java.time.Duration
import java.time.LocalDate

object RoutineCompletionCalculator {
    fun percentage(completedTasks: Int, totalTasks: Int): Double {
        if (totalTasks <= 0) return 0.0
        return (completedTasks.coerceIn(0, totalTasks) * 100.0) / totalTasks
    }

    fun finalState(completedTasks: Int, totalTasks: Int): RoutineExecutionState = when {
        totalTasks <= 0 || completedTasks <= 0 -> RoutineExecutionState.NOT_COMPLETED
        completedTasks >= totalTasks -> RoutineExecutionState.COMPLETED
        else -> RoutineExecutionState.PARTIALLY_COMPLETED
    }
}

object DailyRoutineResetPolicy {
    fun progressFor(date: LocalDate, previous: RoutineDailyProgress?): RoutineDailyProgress =
        if (previous?.date == date) previous else RoutineDailyProgress(date = date)

    fun resetTasksFor(date: LocalDate, tasks: List<RoutineTask>): List<RoutineTask> =
        tasks.map { task ->
            if (task.completedOn == date) task else task.copy(completed = false, completedOn = null)
        }
}

class RoutineSuggestionEngine(
    private val excessiveTaskThreshold: Int = 10,
    private val historyThreshold: Int = 3
) {
    fun evaluate(
        routine: Routine,
        tasks: List<RoutineTask>,
        recentHistory: List<RoutineHistory>,
        today: LocalDate = LocalDate.now(),
        recentPostponements: Int = 0,
        inactiveDays: Int = 7
    ): List<RoutineSuggestion> = buildList {
        val recent = recentHistory.sortedBy { it.date }.takeLast(7)
        val average = recent.map { it.completionPercentage }.average().takeUnless(Double::isNaN) ?: 0.0
        fun suggestion(
            type: RoutineSuggestionType,
            message: String,
            primaryAction: String,
            secondaryAction: String = "Mantener igual"
        ) = RoutineSuggestion(
            routineId = routine.id,
            type = type,
            message = message,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            createdAtEpochDay = today.toEpochDay()
        )
        if (tasks.size > excessiveTaskThreshold && recent.size >= historyThreshold && average < 40.0) {
            add(
                suggestion(RoutineSuggestionType.EXCESSIVE_TASKS,
                    "Esta rutina tiene muchas actividades. ¿Quieres simplificarla?", "Revisar tareas")
            )
        }
        if (recent.size >= historyThreshold && average < 40.0) {
            add(
                suggestion(RoutineSuggestionType.LOW_COMPLETION,
                    "Una rutina más corta podría ser más fácil de completar. ¿Quieres revisarla?",
                    "Reducir tareas")
            )
        }
        recent.flatMap { it.pendingTaskTitles }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.takeIf { it.value >= historyThreshold }?.let { pending ->
                add(suggestion(RoutineSuggestionType.REPEATED_PENDING_TASK,
                    "${pending.key} suele quedar pendiente. Puedes cambiarla, moverla o mantenerla.",
                    "Editar actividad", "Mantener"))
            }
        if (recentPostponements >= 3) {
            add(suggestion(RoutineSuggestionType.FREQUENT_POSTPONEMENT,
                "Parece que este horario podría no ajustarse a tu día. ¿Quieres revisarlo?",
                "Cambiar horario", "Mantener horario"))
        }
        val availableMinutes = if (routine.startTime != null && routine.deadlineTime != null) {
            Duration.between(routine.startTime, routine.deadlineTime).toMinutes()
        } else {
            null
        }
        val estimatedMinutes = tasks.sumOf { it.estimatedDurationMinutes ?: 0 }.toLong()
        if (availableMinutes != null && estimatedMinutes > availableMinutes) {
            add(
                suggestion(RoutineSuggestionType.SCHEDULE_MISMATCH,
                    "El horario podría no alcanzar para todas las actividades. ¿Quieres revisarlo?",
                    "Cambiar horario", "Mantener horario")
            )
        }
        if (recent.size >= 4 && recent.takeLast(2).map { it.completionPercentage }.average() >
            recent.take(2).map { it.completionPercentage }.average() + 10.0) {
            add(suggestion(RoutineSuggestionType.GOOD_PROGRESS,
                "Has avanzado en tus rutinas esta semana. Sigue a tu ritmo.", "Ver progreso"))
        }
        val createdDate = java.time.Instant.ofEpochMilli(routine.createdAtEpochMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val lastUse = recentHistory.maxOfOrNull { it.date } ?: createdDate
        if (java.time.temporal.ChronoUnit.DAYS.between(lastUse, today) >= inactiveDays) {
            add(suggestion(RoutineSuggestionType.INACTIVE_PERIOD,
                "Hace varios días que no utilizas esta rutina. ¿Quieres ajustarla o desactivarla?",
                "Editar", "Mantener"))
        }
    }
}
