package com.luistureo.voicereminderapp.domain.routine.validation

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.ordered

object RoutineValidationMessages {
    const val EMPTY_NAME = "La rutina debe tener un nombre."
    const val INVALID_TIME_RANGE = "La hora límite no puede ser anterior a la hora de inicio."
    const val INVALID_TASK_ORDER =
        "Las horas de las actividades deben respetar el orden de la rutina."
    const val TASK_OUTSIDE_ROUTINE =
        "La hora de una actividad debe estar dentro del horario de la rutina."
    const val EMPTY_TASK_TITLE = "Cada actividad debe tener un título."
    const val INVALID_TASK_DURATION = "La duración estimada debe ser mayor que cero."
    const val DUPLICATE_TASK_PRIORITY = "Cada actividad debe tener una posición de orden única."
    const val DUPLICATE_ACTIVE_PERIOD =
        "Ya existe una rutina activa para este período del día."
}

data class RoutineValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

class RoutineValidationException(val validationErrors: List<String>) :
    IllegalArgumentException(validationErrors.joinToString(" "))

class DuplicateActiveRoutineException :
    IllegalStateException(RoutineValidationMessages.DUPLICATE_ACTIVE_PERIOD)

class RoutineValidator {
    fun validate(routine: Routine, tasks: List<RoutineTask>): RoutineValidationResult {
        val errors = linkedSetOf<String>()
        if (routine.name.isBlank()) errors += RoutineValidationMessages.EMPTY_NAME
        if (
            routine.startTime != null &&
            routine.deadlineTime != null &&
            routine.deadlineTime.isBefore(routine.startTime)
        ) {
            errors += RoutineValidationMessages.INVALID_TIME_RANGE
        }

        if (tasks.any { it.title.isBlank() }) {
            errors += RoutineValidationMessages.EMPTY_TASK_TITLE
        }
        if (tasks.any { it.estimatedDurationMinutes != null && it.estimatedDurationMinutes <= 0 }) {
            errors += RoutineValidationMessages.INVALID_TASK_DURATION
        }
        if (tasks.map { it.orderPriority }.distinct().size != tasks.size) {
            errors += RoutineValidationMessages.DUPLICATE_TASK_PRIORITY
        }

        val timedTasks = tasks.ordered().filter { it.optionalTime != null }
        if (timedTasks.zipWithNext().any { (current, next) ->
                requireNotNull(next.optionalTime).isBefore(requireNotNull(current.optionalTime))
            }
        ) {
            errors += RoutineValidationMessages.INVALID_TASK_ORDER
        }

        if (timedTasks.any { task ->
                val time = requireNotNull(task.optionalTime)
                (routine.startTime != null && time.isBefore(routine.startTime)) ||
                    (routine.deadlineTime != null && time.isAfter(routine.deadlineTime))
            }
        ) {
            errors += RoutineValidationMessages.TASK_OUTSIDE_ROUTINE
        }

        return RoutineValidationResult(errors.toList())
    }
}
