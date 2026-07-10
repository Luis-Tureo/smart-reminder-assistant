package com.luistureo.voicereminderapp.domain.routine.usecase

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.ordered
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.validation.DuplicateActiveRoutineException
import com.luistureo.voicereminderapp.domain.routine.validation.RoutineValidationException
import com.luistureo.voicereminderapp.domain.routine.validation.RoutineValidator

class SaveRoutineUseCase(
    private val repository: RoutineRepository,
    private val validator: RoutineValidator = RoutineValidator()
) {
    suspend operator fun invoke(routine: Routine, tasks: List<RoutineTask>): Int {
        val validation = validator.validate(routine, tasks)
        if (!validation.isValid) throw RoutineValidationException(validation.errors)
        if (
            routine.enabled &&
            repository.hasOtherActiveRoutine(routine.period, routine.id)
        ) {
            throw DuplicateActiveRoutineException()
        }
        return repository.saveRoutine(routine, tasks.ordered())
    }
}
