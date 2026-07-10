package com.luistureo.voicereminderapp.domain.routine.usecase

import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import java.time.LocalDate

class PrepareRoutineDayUseCase(
    private val repository: RoutineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(date: LocalDate) {
        repository.prepareDay(date)
        repository.getRoutines()
            .filter { it.enabled }
            .forEach { routine ->
                if (repository.getDailyExecution(routine.id, date) == null) {
                    repository.saveDailyExecution(
                        RoutineDailyExecution(
                            date = date,
                            routineId = routine.id,
                            state = RoutineExecutionState.PENDING,
                            updatedAtEpochMillis = nowProvider()
                        )
                    )
                }
            }
    }
}
